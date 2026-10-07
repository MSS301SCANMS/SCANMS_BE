import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { money, breakdown, eligible, finalizedCommission } from '../rules.mjs';
import { identity, equalSecret } from '../identity.mjs';
const order = () => ({ id:'order',rawPayload:{financeAuthority:'SPRING',paymentStatus:'PAID'},status:'COMPLETED',deliveredAt:new Date('2026-09-01'),subtotalAmount:450000,discountAmount:0,finalAmount:450000,shippingFee:0,refundedAmount:0,attributedCollaboratorId:'kol',orderItems:[{id:'item',unitPrice:450000,quantity:1,appliedCommissionRate:'10.00',commissionSnapshotAt:new Date()}] });
test('450000 paid with 5% fee and 10% commission leaves 382500 for Store',()=>{
  const source=order();const b=breakdown(source);const commission=finalizedCommission(source);
  assert.equal(commission.amountVnd,45000);assert.equal(b.grossRevenueVnd-b.refundVnd-b.sellerDiscountVnd-22500-commission.amountVnd+b.platformSubsidyVnd,382500);
});
test('platform discount is added exactly once and a full refund removes its subsidy',()=>{
  const o=order();Object.assign(o,{subtotalAmount:500000,discountAmount:50000,finalAmount:450000,couponRedemption:{platformFundedAmount:30000}});
  const b=breakdown(o);assert.equal(b.grossRevenueVnd-b.sellerDiscountVnd+b.platformSubsidyVnd,480000);
  o.refundedAmount=450000;const refunded=breakdown(o);assert.equal(refunded.grossRevenueVnd-refunded.refundVnd-refunded.sellerDiscountVnd+refunded.platformSubsidyVnd,0);
});
test('missing source snapshots, fractional VND and over-refund block money posting',()=>{
  assert.throws(()=>money('0.01'));assert.throws(()=>money('9007199254740992'));
  const o=order();o.refundedAmount=450001;assert.throws(()=>breakdown(o));o.refundedAmount=0;o.orderItems[0].commissionSnapshotAt=null;assert.throws(()=>finalizedCommission(o));
});
test('return window, unresolved return and legacy ownership block settlement',()=>{
  const o=order();assert.doesNotThrow(()=>eligible(o,new Date('2026-10-03')));
  assert.throws(()=>eligible(o,new Date('2026-09-02')));o.returnRequest={status:'SHOP_APPROVED'};assert.throws(()=>eligible(o,new Date('2026-10-03')));
  o.returnRequest=null;o.rawPayload.financeAuthority='LEGACY';assert.throws(()=>eligible(o,new Date('2026-10-03')));
});
test('finance broker identity survives restart, rejects tampering and never accepts blank service secret',()=>{
  const directory=mkdtempSync(join(tmpdir(),'scanms-identity-'));
  try{const broker=identity(directory,'http://127.0.0.1:3302');const token=broker.token({id:'customer',role:'CUSTOMER'});
    assert.equal(identity(directory,'http://127.0.0.1:3302').verify(token).sub,'customer');
    const parts=token.split('.');parts[1]=Buffer.from(JSON.stringify({sub:'admin',exp:9999999999})).toString('base64url');assert.throws(()=>broker.verify(parts.join('.')));
    assert.equal(equalSecret('',''),false);assert.equal(equalSecret('a','b'),false);assert.equal(equalSecret('secret','secret'),true);
  }finally{rmSync(directory,{recursive:true,force:true});}
});
