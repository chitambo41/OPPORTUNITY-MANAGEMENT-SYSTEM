package com.opportunity.school.controller;

import com.opportunity.school.dto.FeeDtos;
import com.opportunity.school.service.FeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/fees")
@RequiredArgsConstructor
public class AdminFeeController {

    private final FeeService feeService;

    @PostMapping("/structures")
    public FeeDtos.FeeStructureDto setFee(@Valid @RequestBody FeeDtos.SetFeeRequest request) {
        return feeService.setFee(request);
    }

    @GetMapping("/structures")
    public List<FeeDtos.FeeStructureDto> listFees(@RequestParam Long termId) {
        return feeService.listFees(termId);
    }

    @PostMapping("/payments")
    public FeeDtos.PaymentDto recordPayment(@Valid @RequestBody FeeDtos.RecordPaymentRequest request) {
        return feeService.recordPayment(request);
    }

    @GetMapping("/statuses")
    public List<FeeDtos.StudentFeeStatusDto> statuses(@RequestParam Long termId,
                                                      @RequestParam(required = false) Long classId,
                                                      @RequestParam(required = false) String status) {
        return feeService.statuses(termId, classId, status);
    }

    @GetMapping("/summary")
    public FeeDtos.FeeSummaryDto summary(@RequestParam Long termId) {
        return feeService.summary(termId);
    }

    @GetMapping("/statement/{studentId}")
    public FeeDtos.FeeStatementDto statement(@PathVariable Long studentId) {
        return feeService.statement(studentId);
    }

    @GetMapping("/receipt/{receiptNumber}")
    public FeeDtos.PaymentDto receipt(@PathVariable String receiptNumber) {
        return feeService.receipt(receiptNumber);
    }
}
