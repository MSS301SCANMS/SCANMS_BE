package com.scanms.payment.service;

import com.scanms.payment.constant.*;
import com.scanms.payment.entity.FeeConfig;
import com.scanms.payment.exception.*;
import com.scanms.payment.repository.FeeConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class FeeCalculator {
    private final FeeConfigRepository repository;
    public record Quote(long amountVnd, Map<String,Object> snapshot) {}
    public void validate(FeeConfig c) {
        if (c.getValidFrom() == null || (c.getValidTo() != null && !c.getValidTo().isAfter(c.getValidFrom()))) invalid("Invalid effective period");
        if (c.getCalculationType() == FeeCalculationType.PERCENTAGE) {
            if (c.getRatePercent() == null || c.getRatePercent().signum() < 0 || c.getRatePercent().compareTo(BigDecimal.valueOf(100)) > 0 || c.getRatePercent().stripTrailingZeros().scale()>6
                    || c.getFixedAmountVnd() != null) invalid("Percentage fee requires a 0–100 rate and no fixed amount");
        } else if (c.getFixedAmountVnd() == null || c.getFixedAmountVnd() < 0 || c.getRatePercent() != null) invalid("Fixed fee requires a nonnegative amount and no rate");
        if ((c.getMinFeeVnd() != null && c.getMinFeeVnd() < 0) || (c.getMaxFeeVnd() != null && c.getMaxFeeVnd() < 0)
                || (c.getMinFeeVnd() != null && c.getMaxFeeVnd() != null && c.getMinFeeVnd() > c.getMaxFeeVnd())) invalid("Invalid fee limits");
        if (c.getStatus() == FeeConfigStatus.ACTIVE) {
            boolean overlaps = repository.findAll().stream().anyMatch(other -> !Objects.equals(other.getFeeConfigId(), c.getFeeConfigId())
                    && other.getFeeType() == c.getFeeType() && other.getStatus() == FeeConfigStatus.ACTIVE
                    && (other.getValidTo() == null || other.getValidTo().isAfter(c.getValidFrom()))
                    && (c.getValidTo() == null || c.getValidTo().isAfter(other.getValidFrom())));
            if (overlaps) throw new AppException(ErrorCode.CONFLICT, "Active fee periods overlap");
        }
    }
    public Quote quote(FeeType type, long basis, Instant at) {
        if (basis < 0) invalid("Negative fee basis");
        var matches = repository.findAll().stream().filter(c -> c.getFeeType() == type && c.getStatus() == FeeConfigStatus.ACTIVE
                && !at.isBefore(c.getValidFrom()) && (c.getValidTo() == null || at.isBefore(c.getValidTo()))).toList();
        if (matches.size() > 1) throw new AppException(ErrorCode.CONFLICT, "Ambiguous active fee configuration");
        if (matches.isEmpty()) return new Quote(0, Map.of("feeType", type.name(), "basisVnd", basis, "amountVnd", 0, "policy", "NO_ACTIVE_FEE"));
        FeeConfig c = matches.getFirst();
        long amount = calculate(c, basis);
        Map<String,Object> snap = new LinkedHashMap<>();
        snap.put("feeConfigId", c.getFeeConfigId()); snap.put("version", c.getVersion());
        snap.put("feeType", type); snap.put("calculationType", c.getCalculationType());
        snap.put("ratePercent", c.getRatePercent()); snap.put("fixedAmountVnd", c.getFixedAmountVnd());
        snap.put("minFeeVnd",c.getMinFeeVnd()); snap.put("maxFeeVnd",c.getMaxFeeVnd());
        snap.put("basisVnd", basis); snap.put("amountVnd", amount); snap.put("quotedAt", at.toString());
        return new Quote(amount, snap);
    }
    public long calculate(FeeConfig c, long basis) {
        try {
            long fee = c.getCalculationType() == FeeCalculationType.PERCENTAGE
                    ? BigDecimal.valueOf(basis).multiply(c.getRatePercent()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact()
                    : c.getFixedAmountVnd();
            if (c.getMinFeeVnd() != null) fee = Math.max(fee, c.getMinFeeVnd());
            if (c.getMaxFeeVnd() != null) fee = Math.min(fee, c.getMaxFeeVnd());
            return fee;
        } catch (ArithmeticException ex) { throw new AppException(ErrorCode.INVALID_REQUEST, "Fee overflow"); }
    }
    public Quote requote(Map<String,Object> original,long basis) {
        if(basis<0) invalid("Negative fee basis");
        Map<String,Object> snapshot=new LinkedHashMap<>(original);
        if("NO_ACTIVE_FEE".equals(original.get("policy"))) { snapshot.put("basisVnd",basis); return new Quote(0,snapshot); }
        FeeConfig c=new FeeConfig();
        c.setCalculationType(FeeCalculationType.valueOf(original.get("calculationType").toString()));
        if(original.get("ratePercent")!=null) c.setRatePercent(new BigDecimal(original.get("ratePercent").toString()));
        if(original.get("fixedAmountVnd")!=null) c.setFixedAmountVnd(new BigDecimal(original.get("fixedAmountVnd").toString()).longValueExact());
        FeeConfig legacy=repository.findById(original.get("feeConfigId").toString()).orElseThrow(() -> new AppException(ErrorCode.CONFLICT,"Original fee version is missing"));
        Object min=original.containsKey("minFeeVnd")?original.get("minFeeVnd"):legacy.getMinFeeVnd();
        Object max=original.containsKey("maxFeeVnd")?original.get("maxFeeVnd"):legacy.getMaxFeeVnd();
        if(min!=null) c.setMinFeeVnd(new BigDecimal(min.toString()).longValueExact());
        if(max!=null) c.setMaxFeeVnd(new BigDecimal(max.toString()).longValueExact());
        long amount=calculate(c,basis); snapshot.put("basisVnd",basis); snapshot.put("amountVnd",amount); return new Quote(amount,snapshot);
    }
    private void invalid(String message) { throw new AppException(ErrorCode.INVALID_REQUEST, message); }
}
