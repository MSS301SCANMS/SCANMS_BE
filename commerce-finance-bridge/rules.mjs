export class ContractError extends Error {
  constructor(message, status = 409) { super(message); this.status = status; }
}
export function money(value) {
  const text = String(value);
  if (!/^\d+(?:\.0+)?$/.test(text)) throw new ContractError('Source amount must be integer VND');
  const amount = Number(text);
  if (!Number.isSafeInteger(amount) || amount < 0) throw new ContractError('Source amount exceeds integer VND bounds');
  return amount;
}
export function managed(order) {
  if (order.rawPayload?.financeAuthority !== 'SPRING') throw new ContractError('Historical order uses legacy finance; explicit reconciliation is required');
}
export function eligible(order, now = new Date(), days = 14) {
  managed(order);
  const isPaid = order.rawPayload?.paymentStatus === 'PAID' || (order.rawPayload?.paymentMethod === 'COD' && (order.status === 'COMPLETED' || order.deliveredAt));
  if (!isPaid) throw new ContractError('Order has no verified payment');
  const delivered = order.deliveredAt && new Date(order.deliveredAt);
  if (order.status !== 'COMPLETED' || !delivered || now.getTime() < delivered.getTime() + days * 86400000)
    throw new ContractError('Order is not complete or its return window is open');
  if (order.returnRequest && !['SHOP_REJECTED', 'CLOSED', 'REFUNDED'].includes(order.returnRequest.status)) throw new ContractError('Return request is unresolved');
}
export function breakdown(order) {
  const gross = money(order.subtotalAmount), discount = money(order.discountAmount), paid = money(order.finalAmount), shipping = money(order.shippingFee);
  const platform = money(order.couponRedemption?.platformFundedAmount ?? 0);
  if (platform > discount || gross - discount + shipping !== paid) throw new ContractError('Discount/payment snapshot does not balance');
  const refunded = money(order.refundedAmount), itemPaid = paid - shipping;
  if (refunded > paid || itemPaid < 0) throw new ContractError('Refund exceeds verified paid total');
  // This legacy ReturnRequest covers the whole order, including shipping. Future partial returns need item-level allocations.
  const itemRefund = refunded === paid ? itemPaid : Math.min(refunded, itemPaid);
  const retained = itemPaid - itemRefund;
  const subsidy = itemPaid ? Number(BigInt(platform) * BigInt(retained) / BigInt(itemPaid)) : 0;
  return { grossRevenueVnd: gross - platform, refundVnd: itemRefund, sellerDiscountVnd: discount - platform, platformSubsidyVnd: subsidy, feeBasisVnd: retained };
}
export function finalizedCommission(order) {
  const basis = breakdown(order).feeBasisVnd;
  if (!order.attributedCollaboratorId) return { amountVnd: 0, attributed: false };
  if (!order.orderItems?.length) throw new ContractError('Attributed order has no commission items');
  if (order.orderItems.some(item=>!Number.isSafeInteger(item.quantity)||item.quantity<=0)) throw new ContractError('Invalid item quantity');
  const weights = order.orderItems.map(item => money(item.unitPrice) * item.quantity);
  if (weights.some(x => !Number.isSafeInteger(x) || x < 0)) throw new ContractError('Invalid item allocation');
  const sum = weights.reduce((a, b) => a + BigInt(b), 0n);
  if (!sum) throw new ContractError('Commission allocation has no basis');
  let allocated = 0, total = 0;
  order.orderItems.forEach((item, index) => {
    if (!item.commissionSnapshotAt) throw new ContractError('Commission policy snapshot is missing');
    const rate = String(item.appliedCommissionRate);
    if (!/^\d+(?:\.\d{1,2})?$/.test(rate) || Number(rate) > 100) throw new ContractError('Invalid commission rate snapshot');
    const itemBasis = index === weights.length - 1 ? basis - allocated : Number(BigInt(basis) * BigInt(weights[index]) / sum);
    allocated += itemBasis;
    const parts = rate.split('.'); const bp = BigInt(parts[0]) * 100n + BigInt((parts[1] || '').padEnd(2, '0'));
    total += Number((BigInt(itemBasis) * bp + 5000n) / 10000n);
  });
  if (!Number.isSafeInteger(total) || total > basis) throw new ContractError('Commission exceeds retained paid amount');
  return { amountVnd: total, attributed: true };
}
