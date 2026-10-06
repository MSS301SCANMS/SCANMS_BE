package com.scanms.payment.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
@Component
public class BankBusinessCalendar {
    @Value("${scanms.payment.bank-closed-dates:}") private String closedDates;
    @org.springframework.beans.factory.annotation.Autowired private com.scanms.payment.repository.BankClosedDayRepository days;
    @org.springframework.beans.factory.annotation.Autowired private PaymentAccess access;
    public java.util.List<Map<String,Object>> configuredDays() {
        access.operatorOnly();
        return days.findAll().stream().sorted(Comparator.comparing(com.scanms.payment.entity.BankClosedDay::getClosedDate))
                .map(d -> Map.<String,Object>of("date",d.getClosedDate().toString(),"reason",d.getReason(),"actor",d.getConfiguredBy(),"configuredAt",d.getConfiguredAt())).toList();
    }
    @org.springframework.transaction.annotation.Transactional
    public void closeDay(LocalDate date,String reason) {
        access.operatorOnly();
        if(reason==null || reason.isBlank() || reason.length()>500 || date.isBefore(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))))
            throw new com.scanms.payment.exception.AppException(com.scanms.payment.exception.ErrorCode.INVALID_REQUEST,"Future bank closure and reason required");
        var row=days.findById(date).orElseGet(com.scanms.payment.entity.BankClosedDay::new);
        row.setClosedDate(date); row.setReason(reason.trim()); row.setConfiguredAt(Instant.now());
        row.setConfiguredBy(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName()); days.save(row);
    }
    public Instant nextProcessingAt(Instant now) {
        ZoneId zone=ZoneId.of("Asia/Ho_Chi_Minh"); LocalDate day=now.atZone(zone).toLocalDate();
        Set<String> closed=new HashSet<>(Arrays.stream(closedDates.split(",")).map(String::trim).toList());
        if(days!=null) days.findAll().forEach(d -> closed.add(d.getClosedDate().toString()));
        while(day.getDayOfWeek()==DayOfWeek.SATURDAY || day.getDayOfWeek()==DayOfWeek.SUNDAY || closed.contains(day.toString())) day=day.plusDays(1);
        return day.equals(now.atZone(zone).toLocalDate()) ? now : day.atTime(9,0).atZone(zone).toInstant();
    }
}
