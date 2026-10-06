import test from 'node:test';
import assert from 'node:assert/strict';
import { CommerceSource } from '../source.mjs';
function fixture() {
  const state={order:{id:'order',storeId:'store',customerId:'customer',status:'PENDING',rawPayload:{financeAuthority:'SPRING',paymentMethod:'PAYOS'},finalAmount:100,refundedAmount:0,orderItems:[{id:'item'}],commissions:[]},request:{id:'return',orderId:'order',customerId:'customer',status:'SHOP_APPROVED',submittedAt:new Date('2026-09-01'),deadlineAt:new Date('2026-09-15')},payments:[],refunds:[],commissions:[]};
  const db={
    $queryRaw:async()=>[],
    order:{findUnique:async()=>structuredClone(state.order),update:async({data})=>{const before=state.order.refundedAmount;const increment=data.refundedAmount?.increment||0;Object.assign(state.order,data);if(increment)state.order.refundedAmount=before+increment;return structuredClone(state.order);}},
    returnRequest:{findUnique:async()=>structuredClone(state.request),update:async({data})=>Object.assign(state.request,data)},
    paymentTransaction:{findUnique:async({where})=>state.payments.find(p=>p.transactionId===where.transactionId),create:async({data})=>state.payments.push(data)},
    orderRefund:{create:async({data})=>state.refunds.push(data)},
    user:{findUnique:async()=>({role:'COLLABORATOR',isActive:true,isDeleted:false,collaboratorProfile:{kycStatus:'VERIFIED'}})},
    commission:{
      findMany:async()=>structuredClone(state.commissions),
      upsert:async({create})=>{state.commissions.push({...create,status:'PENDING'});return structuredClone(state.commissions.at(-1));},
      findUnique:async({where,include})=>{const row=state.commissions.find(x=>x.id===where.id);return row?{...structuredClone(row),...(include?{order:structuredClone(state.order)}:{})}:null;},
      update:async({where,data})=>Object.assign(state.commissions.find(x=>x.id===where.id),data),
    },
  };
  db.$transaction=async work=>{const copy=structuredClone(state);try{return await work(db);}catch(error){Object.assign(state,copy);throw error;}};
  return {state,source:new CommerceSource(db)};
}
test('verified payment acknowledgement retries create one source transaction and refuse replacement',async()=>{
  const {state,source}=fixture();const input={paymentId:'payment',amountVnd:100,currency:'VND',verifiedAt:'2026-10-03T00:00:00Z',providerCode:'PAYOS',providerReference:'bank-ref'};
  await source.paid('order',input);await source.paid('order',input);
  assert.equal(state.payments.length,1);assert.equal(state.order.rawPayload.paymentStatus,'PAID');
  await assert.rejects(source.paid('order',{...input,paymentId:'another'}));assert.equal(state.payments.length,1);
  await assert.rejects(source.paid('order',{...input,providerReference:'changed'}));assert.equal(state.order.rawPayload.financeProviderReference,'bank-ref');
  state.order.status='RETURNED';await source.paid('order',input);assert.equal(state.payments.length,1);assert.equal(state.order.status,'RETURNED');
});
test('late payment cannot mark cancelled order paid',async()=>{
  const {state,source}=fixture();state.order.status='CANCELLED';
  await assert.rejects(source.paid('order',{paymentId:'payment',amountVnd:100,currency:'VND',verifiedAt:'2026-10-03T00:00:00Z',providerCode:'PAYOS',providerReference:'bank-ref'}));
  assert.equal(state.payments.length,0);assert.notEqual(state.order.rawPayload.paymentStatus,'PAID');
});
test('a cancelled attempt does not block a later verified wallet payment',async()=>{
  const {state,source}=fixture();state.order.rawPayload.financePaymentId='cancelled-payOS';
  await source.paid('order',{paymentId:'wallet-payment',amountVnd:100,currency:'VND',verifiedAt:'2026-10-03T00:00:00Z',providerCode:'WALLET',providerReference:'ledger-debit'});
  assert.equal(state.order.rawPayload.financePaidPaymentId,'wallet-payment');assert.equal(state.payments[0].paymentMethod,'WALLET');
});
test('ReturnRequest reserves authoritative amount and repeated ledger acknowledgements refund once',async()=>{
  const {state,source}=fixture();state.order.rawPayload.paymentStatus='PAID';
  const basis=await source.refundBasis('return');assert.equal(basis.amountVnd,100);assert.equal(basis.orderItemId,'item');
  assert.deepEqual(await source.refundBasis('return'),basis);
  await assert.rejects(source.refunded('return',{transactionId:'ledger',amountVnd:101}));
  await source.refunded('return',{transactionId:'ledger',amountVnd:100});await source.refunded('return',{transactionId:'ledger',amountVnd:100});
  assert.equal(state.refunds.length,1);assert.equal(state.order.refundedAmount,100);assert.equal(state.request.status,'REFUNDED');
  await assert.rejects(source.refunded('return',{transactionId:'another-ledger',amountVnd:100}));assert.equal(state.refunds.length,1);
});
test('source contract rejects a foreign customer and legacy order',async()=>{
  const {state,source}=fixture();const claims={sub:'foreign',realm_access:{roles:['CUSTOMER']}};
  await assert.rejects(source.order('order',claims));state.order.rawPayload.financeAuthority='LEGACY';
  await assert.rejects(source.order('order',{...claims,sub:'customer'}));
});
test('affiliate finalization and repeated ledger acknowledgements retain one credited commission',async()=>{
  const {state,source}=fixture();Object.assign(state.order,{status:'COMPLETED',deliveredAt:new Date('2025-01-01'),subtotalAmount:450000,discountAmount:0,finalAmount:450000,shippingFee:0,attributedCollaboratorId:'kol',orderItems:[{id:'item',unitPrice:450000,quantity:1,appliedCommissionRate:'10.00',commissionSnapshotAt:new Date('2025-01-01')}]});state.order.rawPayload.paymentStatus='PAID';
  const finalized=await source.finalize('order');assert.equal(finalized.amountVnd,45000);
  assert.equal((await source.finalize('order')).commissionId,finalized.commissionId);assert.equal(state.commissions.length,1);
  assert.equal((await source.commissionBasis(finalized.commissionId)).amountVnd,45000);
  const ack={transactionId:'ledger-credit',amountVnd:45000};await source.credited(finalized.commissionId,ack);await source.credited(finalized.commissionId,ack);
  assert.equal(state.commissions[0].status,'APPROVED');assert.equal(state.order.rawPayload.financeCommission.transactionId,'ledger-credit');
  await assert.rejects(source.credited(finalized.commissionId,{...ack,transactionId:'duplicate'}));
});
test('source finalization blocks commission previously paid by legacy finance',async()=>{
  const {state,source}=fixture();Object.assign(state.order,{status:'COMPLETED',deliveredAt:new Date('2025-01-01'),subtotalAmount:100,discountAmount:0,shippingFee:0});state.order.rawPayload.paymentStatus='PAID';state.commissions=[{status:'PAID',storeWalletTracked:false}];
  await assert.rejects(source.finalize('order'));assert.equal(state.order.rawPayload.financeCommission,undefined);
});
