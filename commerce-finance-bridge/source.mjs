import { randomUUID } from 'node:crypto';
import { ContractError, money, managed, eligible, breakdown, finalizedCommission } from './rules.mjs';
const include = { orderItems: true, returnRequest: true, couponRedemption: true, commissions: true };
const admin = claims => claims.realm_access.roles.some(x => ['SYSTEM_ADMIN', 'SYSTEM_MANAGER', 'PAYMENT_INTERNAL'].includes(x));
export class CommerceSource {
  constructor(prisma, days = 14) { this.db = prisma; this.days = days; }
  async locked(tx, id) {
    await tx.$queryRaw`SELECT id FROM orders WHERE id=${id}::uuid FOR UPDATE`;
    const order = await tx.order.findUnique({ where: { id }, include });
    if (!order) throw new ContractError('Order not found', 404);
    managed(order); return order;
  }
  async order(id, claims) {
    const order = await this.db.order.findUnique({ where: { id }, include });
    if (!order) throw new ContractError('Order not found', 404);
    if (!admin(claims) && claims.sub !== order.customerId) throw new ContractError('Order belongs to another customer', 403);
    managed(order); return order;
  }
  view(order) {
    return { orderId: order.id, customerId: order.customerId, currency: 'VND', payableVnd: money(order.finalAmount),
      status: ['CANCELLED','RETURNED'].includes(order.status) ? 'CANCELLED' : order.rawPayload?.paymentStatus === 'PAID' ? 'PAID' : 'AWAITING_PAYMENT' };
  }
  async paid(id, input) {
    return this.db.$transaction(async tx => {
      const order = await this.locked(tx, id); const raw = order.rawPayload || {};
      if (!input.paymentId || !input.verifiedAt || input.currency !== 'VND' || input.amountVnd !== money(order.finalAmount) || !['PAYOS','WALLET'].includes(input.providerCode) || !input.providerReference) throw new ContractError('Payment identity or amount mismatch');
      if (raw.financePaidPaymentId && raw.financePaidPaymentId !== input.paymentId) throw new ContractError('Different payment already paid this order');
      if(raw.financeProvider && (raw.financeProvider!==input.providerCode || raw.financeProviderReference!==input.providerReference))throw new ContractError('Verified provider reference changed');
      if(raw.paymentStatus==='PAID' && raw.transactionId===`SPRING:${input.paymentId}`) return {orderId:id,status:'PAID'};
      if (['CANCELLED','RETURNED'].includes(order.status)) throw new ContractError('Late payment requires reconciliation');
      if (raw.paymentStatus === 'PAID' && raw.transactionId !== `SPRING:${input.paymentId}`) throw new ContractError('Order was paid through another channel');
      const ref = `SPRING:${input.paymentId}`;
      const old = await tx.paymentTransaction.findUnique({ where: { transactionId: ref } });
      if (!old) await tx.paymentTransaction.create({ data: { orderId: id, transactionId: ref, amount: input.amountVnd, currency: 'VND', paymentMethod: input.providerCode, status: 'SUCCESS' } });
      await tx.order.update({ where: { id }, data: { rawPayload: { ...raw, financePaymentId: input.paymentId, financePaidPaymentId: input.paymentId, paymentStatus: 'PAID', transactionId: ref, paidAt: input.verifiedAt, financeProvider: input.providerCode, financeProviderReference: input.providerReference } } });
      return { orderId: id, status: 'PAID' };
    });
  }
  async refundBasis(id) {
    return this.db.$transaction(async tx => {
      const request = await tx.returnRequest.findUnique({ where: { id } });
      if (!request) throw new ContractError('ReturnRequest not found', 404);
      const order = await this.locked(tx, request.orderId); const raw = order.rawPayload || {};
      if (raw.financeRefund) {
        if (raw.financeRefund.returnRequestId !== id) throw new ContractError('Refund reference changed');
        return raw.financeRefund;
      }
      const isPaid = raw.paymentStatus === 'PAID' || (raw.paymentMethod === 'COD' && (order.status === 'COMPLETED' || order.deliveredAt || request.originalOrderStatus === 'COMPLETED'));
      if (request.status !== 'SHOP_APPROVED' || !isPaid || !order.customerId || request.customerId !== order.customerId || !order.orderItems.length)
        throw new ContractError('Return is not approved against a verified customer payment');
      if (request.submittedAt > request.deadlineAt) throw new ContractError('Return was requested after its deadline');
      if (order.commissions.some?.(c => c.status === 'APPROVED')) throw new ContractError('Credited commission requires reconciliation');
      const amount = money(order.finalAmount) - money(order.refundedAmount);
      if (amount <= 0) throw new ContractError('No refundable balance remains');
      const snapshot = { returnRequestId: id, orderItemId: order.orderItems[0].id, orderItemIds: order.orderItems.map(x => x.id), orderId: order.id, customerId: order.customerId, amountVnd: amount, currency: 'VND', scope: 'WHOLE_ORDER' };
      await tx.order.update({ where: { id: order.id }, data: { rawPayload: { ...raw, paymentStatus: 'PAID', financeRefund: snapshot } } });
      return snapshot;
    });
  }
  async refunded(id, input) {
    return this.db.$transaction(async tx => {
      const request = await tx.returnRequest.findUnique({ where: { id } });
      if (!request) throw new ContractError('ReturnRequest not found',404);
      const order = await this.locked(tx, request.orderId); const raw = order.rawPayload || {}, basis = raw.financeRefund;
      if (!basis || basis.returnRequestId !== id || basis.amountVnd !== input.amountVnd || !input.transactionId) throw new ContractError('Refund acknowledgement mismatch');
      if (basis.transactionId && basis.transactionId !== input.transactionId) throw new ContractError('Return was credited by another transaction');
      if (!basis.transactionId) {
        await tx.orderRefund.create({ data: { id, orderId: order.id, amount: input.amountVnd, status: 'COMPLETED', reason: `Customer wallet REFUND:${input.transactionId}` } });
        await tx.order.update({ where: { id: order.id }, data: { refundedAmount: { increment: input.amountVnd }, status: 'RETURNED', rawPayload: { ...raw, paymentStatus: 'PAID', financeRefund: { ...basis, transactionId: input.transactionId } } } });
        await tx.returnRequest.update({ where: { id }, data: { status: 'REFUNDED' } });
      }
      return { returnRequestId: id, status: 'REFUNDED' };
    });
  }
  async finalize(id, actor = 'finance-worker') {
    return this.db.$transaction(async tx => {
      const order = await this.locked(tx,id); eligible(order,new Date(),this.days);
      const value = finalizedCommission(order), raw = order.rawPayload || {};
      if (raw.financeCommission) {
        if (raw.financeCommission.amountVnd !== value.amountVnd) throw new ContractError('Finalized commission changed; reconcile ledger first');
        return raw.financeCommission;
      }
      if(value.attributed) {
        const collaborator=await tx.user.findUnique({where:{id:order.attributedCollaboratorId},select:{role:true,isActive:true,isDeleted:true,collaboratorProfile:{select:{kycStatus:true}}}});
        if(collaborator?.role!=='COLLABORATOR'||!collaborator.isActive||collaborator.isDeleted||collaborator.collaboratorProfile?.kycStatus!=='VERIFIED')throw new ContractError('Attributed collaborator is not approved');
      }
      const rows = await tx.commission.findMany({ where: { orderId: id } });
      if (rows.some(x => x.status !== 'PENDING' || x.storeWalletTracked)) throw new ContractError('Existing commission requires explicit reconciliation');
      let commissionId = null;
      if (value.attributed && value.amountVnd > 0) {
        const row = await tx.commission.upsert({ where: { orderId_collaboratorId: { orderId: id, collaboratorId: order.attributedCollaboratorId } },
          create: { id: randomUUID(), orderId: id, collaboratorId: order.attributedCollaboratorId, commissionAmount: value.amountVnd, eligibleAt: new Date(), availableAt: new Date(), storeWalletTracked: false },
          update: { commissionAmount: value.amountVnd, status: 'PENDING', storeWalletTracked: false } });
        commissionId = row.id;
      } else if(rows.length) throw new ContractError('Existing attribution rows need source reconciliation');
      const snapshot = { finalized: true, amountVnd: value.amountVnd, commissionId, actor, at: new Date().toISOString() };
      await tx.order.update({ where: { id }, data: { rawPayload: { ...raw, financeCommission: snapshot } } });
      return snapshot;
    });
  }
  async commissionBasis(id) {
    const row = await this.db.commission.findUnique({ where: { id }, include: { order: { include } } });
    if (!row) throw new ContractError('Commission not found',404);
    eligible(row.order,new Date(),this.days);
    const finalized = await this.finalize(row.orderId);
    if (finalized.commissionId !== id || finalized.amountVnd !== money(row.commissionAmount)) throw new ContractError('Commission snapshot mismatch');
    return { commissionId: id, collaboratorId: row.collaboratorId, orderItemId: row.order.orderItems[0].id, amountVnd: finalized.amountVnd, currency: 'VND' };
  }
  async credited(id,input) {
    return this.db.$transaction(async tx => {
      const row = await tx.commission.findUnique({ where: { id } });
      if (!row) throw new ContractError('Commission not found',404);
      const order = await this.locked(tx,row.orderId); const raw = order.rawPayload || {}, basis = raw.financeCommission;
      if (!basis || basis.commissionId !== id || basis.amountVnd !== input.amountVnd || !input.transactionId) throw new ContractError('Commission acknowledgement mismatch');
      if (basis.transactionId && basis.transactionId !== input.transactionId) throw new ContractError('Commission has another ledger reference');
      await tx.commission.update({ where: { id }, data: { status: 'APPROVED', approvedAt: new Date() } });
      await tx.order.update({ where: { id: order.id }, data: { rawPayload: { ...raw, financeCommission: { ...basis, transactionId: input.transactionId } } } });
      return { commissionId: id, status: 'CREDITED' };
    });
  }
  async settlement(id) {
    const order = await this.db.order.findUnique({ where: { id }, include });
    if (!order) throw new ContractError('Seller order not found',404);
    eligible(order,new Date(),this.days); await this.finalize(id);
    return { sellerOrderId: id, storeId: order.storeId, orderItemIds: order.orderItems.map(x=>x.id).sort().join(','), currency: 'VND', ...breakdown(order) };
  }
  async commissionTotal(ids) {
    const unique = [...new Set(ids.split(','))];
    if (!unique.length || unique.some(x => !x)) throw new ContractError('OrderItem references required',400);
    const items = await this.db.orderItem.findMany({ where: { id: { in: unique } }, select: { id: true, orderId: true } });
    if(items.length !== unique.length) throw new ContractError('Unknown OrderItem',404);
    let amount = 0;
    for(const id of new Set(items.map(x=>x.orderId))) {
      const order = await this.db.order.findUnique({ where:{id},include });
      if(order.orderItems.some(x=>!unique.includes(x.id))) throw new ContractError('Legacy commission is per-order; request all its items');
      amount += (await this.finalize(id)).amountVnd;
    }
    return { amountVnd: money(amount), finalized:true };
  }
}
