package com.scanms.payment.repository;
import com.scanms.payment.entity.BankClosedDay;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
public interface BankClosedDayRepository extends JpaRepository<BankClosedDay,LocalDate> {}
