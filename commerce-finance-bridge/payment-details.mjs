export function paymentDetails(value) {
  return {
    paymentId: value.paymentId,
    checkoutUrl: value.checkoutUrl,
    qrCode: value.qrCode,
    amount: value.amountVnd,
    bin: value.bin ?? null,
    accountNumber: value.accountNumber ?? null,
    accountName: value.accountName ?? null,
    description: value.description ?? null,
  };
}
