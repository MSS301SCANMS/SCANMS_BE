package com.scanms.payment.service;

import com.scanms.payment.constant.*;
import com.scanms.payment.dto.MoneyDtos.*;
import com.scanms.payment.dto.request.CreateFeeConfigRequest;
import com.scanms.payment.dto.response.FeeConfigResponse;
import com.scanms.payment.dto.response.WalletTransactionResponse;
import com.scanms.payment.entity.BankAccount;
import com.scanms.payment.entity.SellerSettlement;
import com.scanms.payment.entity.Wallet;
import com.scanms.payment.entity.WithdrawalRequest;
import com.scanms.payment.exception.AppException;
import com.scanms.payment.exception.ErrorCode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public interface FinanceService {
    Wallet ensureWallet(WalletOwnerType type, String owner);

    WalletView myWallet(WalletOwnerType type, String storeId);

    Wallet ownedWallet(String id);

    WalletView wallet(String id);

    Wallet lockOwnedWallet(String id);

    boolean sandboxTopUpAvailable();

    Map<String, Object> capabilities();

    WalletTransactionResponse sandboxTopUp(TopUpInput input);

    PageView<LedgerView> history(String id, int page, int size);

    PageView<LedgerView> history(String id, int page, int size, WalletTransactionType type, Instant from, Instant to, String reference);

    WalletTransactionResponse transaction(String walletId, String transactionId);

    PageView<WalletView> walletList(WalletOwnerType type, String owner, int page, int size);

    WalletView walletStatus(String id, WalletStatus status);

    BankView registerBank(BankInput input);

    List<BankView> bankList(WalletOwnerType type, String owner);

    BankView bankStatus(String id, BankAccountVerificationStatus status);

    BankView replaceBank(String id, BankInput input);

    BankView syncKycBank(KycBankInput input);

    BankView deactivateBank(String id);

    WithdrawalView requestWithdrawal(WithdrawalInput input);

    WithdrawalView approveWithdrawal(String id);

    WithdrawalView rejectWithdrawal(String id, String reason, boolean cancel);

    WithdrawalRequest lockedWithdrawal(String id);

    PageView<WithdrawalView> withdrawalList(String walletId, int page, int size);

    WalletTransactionResponse refund(String id);

    WalletTransactionResponse refundResult(String id);

    WalletTransactionResponse commission(String id);

    SettlementView computeSettlement(String sellerOrderId);

    SettlementView reconcileSettlement(String id, String reason);

    SettlementView creditSettlement(String id);

    PageView<SettlementView> settlementList(String storeId, int page, int size);

    FeeConfigResponse createFee(CreateFeeConfigRequest input);

    FeeConfigResponse updateFee(String id, CreateFeeConfigRequest input);

    List<FeeConfigResponse> feeList();

    FeeConfigResponse feeStatus(String id, FeeConfigStatus status);

    WalletTransactionResponse post(String walletId, WalletTransactionType type, WalletTransactionDirection direction, long amount, String refType, String ref, String key);

    WalletView walletView(Wallet w);

    BankView bankView(BankAccount b);

    WithdrawalView withdrawalView(WithdrawalRequest w);

    SettlementView settlementView(SellerSettlement s);

    public static long number(Map<String, Object> map, String key) {
        try {
            return new BigDecimal(Objects.toString(map.get(key))).longValueExact();
        } catch (Exception ex) {
            throw new AppException(ErrorCode.CONFLICT, "Missing or invalid contract field: " + key);
        }
    }

    public static String json(Object value) {
        return JsonMapper.builder().build().writeValueAsString(value);
    }
}
