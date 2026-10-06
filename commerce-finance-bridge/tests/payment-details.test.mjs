import test from 'node:test';
import assert from 'node:assert/strict';
import { paymentDetails } from '../payment-details.mjs';

test('checkout and status retain the receiving virtual account from the provider', () => {
  const value = { paymentId: 'payment', checkoutUrl: 'https://pay.payos.vn/web/test', qrCode: 'vietqr', amountVnd: 680000,
    bin: '970418', accountNumber: 'V3CAS-TEST', accountName: 'TEST OWNER', description: 'TEST ORDER' };
  const { amountVnd, ...providerDetails } = value;
  assert.deepEqual(paymentDetails(value), { ...providerDetails, amount: amountVnd });
});

test('older payments have absent details rather than invented receiving account information', () => {
  const details = paymentDetails({ paymentId: 'old', amountVnd: 680000 });
  assert.equal(details.bin, null);
  assert.equal(details.accountNumber, null);
  assert.equal(details.accountName, null);
  assert.equal(details.description, null);
});
