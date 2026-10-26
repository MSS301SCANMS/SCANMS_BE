package com.scanms.payment.controller;

import com.scanms.payment.constant.*;
import com.scanms.payment.dto.ApiResponse;
import com.scanms.payment.dto.MoneyDtos.*;
import com.scanms.payment.dto.request.CreateFeeConfigRequest;
import com.scanms.payment.dto.response.*;
import com.scanms.payment.exception.AppException;
import com.scanms.payment.exception.ErrorCode;
import com.scanms.payment.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
public class FinanceController {
    private final BankBusinessCalendar calendar;

    public record ClosedDayInput(LocalDate date, String reason) {}

    @GetMapping("/bank-calendar")
    public ApiResponse<List<Map<String, Object>>> calendar() {
        return ApiResponse.success(calendar.configuredDays());
    }

    @PostMapping("/bank-calendar")
    public ApiResponse<String> closeDay(@RequestBody ClosedDayInput input) {
        if (input.date() == null) throw new AppException(ErrorCode.INVALID_REQUEST);
        calendar.closeDay(input.date(), input.reason());
        return ApiResponse.success("Configured");
    }

    private final FinanceService service;
    private final PaymentWorkflow payments;
    private final WithdrawalProcessor transfers;
    private final PayosProvider provider;
    private final FinanceOutbox outbox;

    @GetMapping("/events/pending")
    public ApiResponse<Map<String, Object>> pending(@RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success(outbox.pending(page));
    }

    @PostMapping("/events/{id}/retry")
    public ApiResponse<String> retry(@PathVariable String id) {
        outbox.retry(id);
        return ApiResponse.success("Scheduled");
    }

    @GetMapping("/capabilities")
    public ApiResponse<Map<String, Object>> capabilities() {
        Map<String, Object> info = new LinkedHashMap<>(service.capabilities());
        info.put("paymentAvailable", provider.available());
        info.put("payoutAvailable", provider.payoutAvailable());
        return ApiResponse.success(info);
    }

    @GetMapping("/wallets/me")
    public ApiResponse<WalletView> me(
            @RequestParam(defaultValue = "CUSTOMER") WalletOwnerType ownerType,
            @RequestParam(required = false) String storeId) {
        return ApiResponse.success(service.myWallet(ownerType, storeId));
    }

    @GetMapping("/wallets/{id}")
    public ApiResponse<WalletView> wallet(@PathVariable String id) {
        return ApiResponse.success(service.wallet(id));
    }

    @GetMapping("/wallets/{id}/transactions")
    public ApiResponse<PageView<LedgerView>> history(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) WalletTransactionType type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String reference) {
        return ApiResponse.success(service.history(id, page, size, type, from, to, reference));
    }

    @GetMapping("/wallets/{id}/transactions/{transactionId}")
    public ApiResponse<WalletTransactionResponse> transaction(
            @PathVariable String id, @PathVariable String transactionId) {
        return ApiResponse.success(service.transaction(id, transactionId));
    }

    @GetMapping("/wallets")
    public ApiResponse<PageView<WalletView>> wallets(
            @RequestParam(required = false) WalletOwnerType ownerType,
            @RequestParam(required = false) String ownerRefId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.walletList(ownerType, ownerRefId, page, size));
    }

    @PatchMapping("/wallets/{id}/status")
    public ApiResponse<WalletView> walletStatus(@PathVariable String id, @RequestParam WalletStatus status) {
        return ApiResponse.success(service.walletStatus(id, status));
    }

    @GetMapping("/bank-accounts")
    public ApiResponse<List<BankView>> banks(
            @RequestParam(required = false) WalletOwnerType ownerType,
            @RequestParam(required = false) String ownerRefId) {
        return ApiResponse.success(service.bankList(ownerType, ownerRefId));
    }

    @PostMapping("/bank-accounts")
    public ApiResponse<BankView> bank(@Valid @RequestBody BankInput input) {
        return ApiResponse.created(service.registerBank(input));
    }

    @PatchMapping("/bank-accounts/{id}/verification")
    public ApiResponse<BankView> verify(@PathVariable String id, @Valid @RequestBody Verification input) {
        return ApiResponse.success(service.bankStatus(id, input.status()));
    }

    @DeleteMapping("/bank-accounts/{id}")
    public ApiResponse<BankView> deactivate(@PathVariable String id) {
        return ApiResponse.success(service.deactivateBank(id));
    }

    @PostMapping("/bank-accounts/{id}/replace")
    public ApiResponse<BankView> replace(@PathVariable String id, @Valid @RequestBody BankInput input) {
        return ApiResponse.created(service.replaceBank(id, input));
    }

    @PostMapping("/bank-accounts/kyc-sync")
    public ApiResponse<BankView> syncKyc(@Valid @RequestBody KycBankInput input) {
        return ApiResponse.success(service.syncKycBank(input));
    }

    @GetMapping("/withdrawals")
    public ApiResponse<PageView<WithdrawalView>> withdrawals(
            @RequestParam(required = false) String walletId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.withdrawalList(walletId, page, size));
    }

    @PostMapping("/withdrawals")
    public ApiResponse<WithdrawalView> withdraw(@Valid @RequestBody WithdrawalInput input) {
        return ApiResponse.created(service.requestWithdrawal(input));
    }

    @PostMapping("/withdrawals/{id}/approve")
    public ApiResponse<WithdrawalView> approve(@PathVariable String id) {
        return ApiResponse.success(service.approveWithdrawal(id));
    }

    @PostMapping("/withdrawals/{id}/reject")
    public ApiResponse<WithdrawalView> reject(@PathVariable String id, @Valid @RequestBody Decision decision) {
        return ApiResponse.success(service.rejectWithdrawal(id, decision.reason(), false));
    }

    @PostMapping("/withdrawals/{id}/cancel")
    public ApiResponse<WithdrawalView> cancel(@PathVariable String id, @Valid @RequestBody Decision decision) {
        return ApiResponse.success(service.rejectWithdrawal(id, decision.reason(), true));
    }

    @PostMapping("/withdrawals/{id}/execute")
    public ApiResponse<WithdrawalView> execute(@PathVariable String id) {
        return ApiResponse.success(transfers.execute(id));
    }

    @PostMapping("/withdrawals/{id}/reconcile")
    public ApiResponse<WithdrawalView> reconcile(@PathVariable String id) {
        return ApiResponse.success(transfers.reconcile(id));
    }

    @PostMapping("/refunds/{returnId}")
    public ApiResponse<WalletTransactionResponse> refund(@PathVariable String returnId) {
        return ApiResponse.success(service.refund(returnId));
    }

    @PostMapping("/commissions/{commissionId}/credit")
    public ApiResponse<WalletTransactionResponse> commission(@PathVariable String commissionId) {
        return ApiResponse.success(service.commission(commissionId));
    }

    @GetMapping("/settlements")
    public ApiResponse<PageView<SettlementView>> settlements(
            @RequestParam(required = false) String storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.settlementList(storeId, page, size));
    }

    @PostMapping("/settlements")
    public ApiResponse<SettlementView> settlement(@Valid @RequestBody SettlementInput input) {
        return ApiResponse.created(service.computeSettlement(input.sellerOrderId()));
    }

    @PostMapping("/settlements/{id}/credit")
    public ApiResponse<SettlementView> credit(@PathVariable String id) {
        return ApiResponse.success(service.creditSettlement(id));
    }

    @GetMapping("/fees")
    public ApiResponse<List<FeeConfigResponse>> fees() {
        return ApiResponse.success(service.feeList());
    }

    @PostMapping("/fees")
    public ApiResponse<FeeConfigResponse> fee(@Valid @RequestBody CreateFeeConfigRequest input) {
        return ApiResponse.created(service.createFee(input));
    }

    @PatchMapping("/fees/{id}")
    public ApiResponse<FeeConfigResponse> updateFee(
            @PathVariable String id, @Valid @RequestBody CreateFeeConfigRequest input) {
        return ApiResponse.success(service.updateFee(id, input));
    }

    @PatchMapping("/fees/{id}/status")
    public ApiResponse<FeeConfigResponse> feeStatus(@PathVariable String id, @RequestParam FeeConfigStatus status) {
        return ApiResponse.success(service.feeStatus(id, status));
    }

    @PostMapping("/payments")
    public ApiResponse<PaymentView> payment(@Valid @RequestBody PaymentInput input) {
        return ApiResponse.created(payments.create(input));
    }

    @GetMapping("/payments")
    public ApiResponse<PageView<PaymentView>> payments(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(payments.history(page, size));
    }

    @PostMapping("/top-ups")
    public ApiResponse<PaymentView> topup(@Valid @RequestBody TopUpInput input) {
        return ApiResponse.created(payments.topup(input));
    }

    @PostMapping("/sandbox/top-ups")
    public ApiResponse<WalletTransactionResponse> sandboxTopup(@Valid @RequestBody TopUpInput input) {
        return ApiResponse.created(service.sandboxTopUp(input));
    }

    @PostMapping("/wallet-payments")
    public ApiResponse<PaymentView> payWallet(@Valid @RequestBody WalletPayInput input) {
        return ApiResponse.created(payments.payWallet(input));
    }

    @GetMapping("/payments/{id}")
    public ApiResponse<PaymentView> payment(
            @PathVariable String id, @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponse.success(payments.status(id, refresh));
    }

    @PostMapping("/payments/{id}/cancel")
    public ApiResponse<PaymentView> paymentCancel(@PathVariable String id) {
        return ApiResponse.success(payments.cancel(id));
    }

    @PostMapping("/payments/{id}/resume")
    public ApiResponse<PaymentView> paymentResume(@PathVariable String id) {
        return ApiResponse.success(payments.resume(id));
    }

    @PostMapping("/payments/{id}/refund-unapplied")
    public ApiResponse<PaymentView> refundUnapplied(@PathVariable String id, @Valid @RequestBody Decision decision) {
        return ApiResponse.success(payments.refundUnapplied(id, decision.reason()));
    }

    @PostMapping("/payments/{id}/refund-surplus")
    public ApiResponse<PaymentView> refundSurplus(@PathVariable String id, @Valid @RequestBody Decision decision) {
        return ApiResponse.success(payments.refundSurplus(id, decision.reason()));
    }

    @PostMapping("/settlements/{id}/reconcile")
    public ApiResponse<SettlementView> reconcileSettlement(
            @PathVariable String id, @Valid @RequestBody Decision decision) {
        return ApiResponse.success(service.reconcileSettlement(id, decision.reason()));
    }

    @GetMapping("/refunds/{id}")
    public ApiResponse<WalletTransactionResponse> refundResult(@PathVariable String id) {
        return ApiResponse.success(service.refundResult(id));
    }
}
