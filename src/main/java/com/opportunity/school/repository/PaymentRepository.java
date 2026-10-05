package com.opportunity.school.repository;

import com.opportunity.school.model.Payment;
import com.opportunity.school.model.Term;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByStudentIdOrderByPaymentDateDescIdDesc(Long studentId);

    List<Payment> findByTermOrderByIdDesc(Term term);

    Optional<Payment> findByReceiptNumber(String receiptNumber);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.term = :term")
    BigDecimal sumAmountByTerm(@Param("term") Term term);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.term = :term AND p.student.id = :studentId")
    BigDecimal sumByStudentAndTerm(@Param("studentId") Long studentId, @Param("term") Term term);

    /** Max id for receipt number generation. */
    @Query("SELECT COALESCE(MAX(p.id), 0) FROM Payment p")
    long findMaxId();
}
