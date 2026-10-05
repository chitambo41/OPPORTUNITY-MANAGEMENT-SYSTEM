package com.opportunity.school.controller;

import com.opportunity.school.dto.ExamDtos;
import com.opportunity.school.service.ExamService;
import com.opportunity.school.service.ReportCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/exams")
@RequiredArgsConstructor
public class AdminExamController {

    private final ExamService examService;
    private final ReportCardService reportCardService;

    @GetMapping
    public List<ExamDtos.ExamDto> list(@RequestParam(required = false) Long termId,
                                       @RequestParam(required = false) Long classId) {
        return examService.list(termId, classId);
    }

    @PostMapping
    public ExamDtos.ExamDto create(@Valid @RequestBody ExamDtos.CreateExamRequest request) {
        return examService.create(request);
    }

    @PostMapping("/{id}/status")
    public ExamDtos.ExamDto updateStatus(@PathVariable Long id,
                                         @Valid @RequestBody ExamDtos.UpdateExamStatusRequest request) {
        return examService.updateStatus(id, request);
    }

    /** Full students x subjects table (review). */
    @GetMapping("/{id}/results")
    public ExamDtos.ResultsTableDto results(@PathVariable Long id) {
        return examService.resultsTable(id);
    }

    @PostMapping("/{id}/approve")
    public ExamDtos.ExamDto approve(@PathVariable Long id,
                                    @RequestBody(required = false) Map<String, String> body) {
        return examService.approve(id, body == null ? null : body.get("comment"));
    }

    @PostMapping("/{id}/return")
    public ExamDtos.ExamDto returnResults(@PathVariable Long id,
                                          @Valid @RequestBody ExamDtos.ReturnResultsRequest request) {
        return examService.returnToTeacher(id, request);
    }

    /** Report card data for ONE student (only when APPROVED). */
    @GetMapping("/{id}/report-card/{studentId}")
    public Map<String, Object> reportCard(@PathVariable Long id, @PathVariable Long studentId) {
        return reportCardService.reportCard(id, studentId);
    }

    /** Report card data for ALL students of the class (only when APPROVED). */
    @GetMapping("/{id}/report-cards")
    public Map<String, Object> reportCards(@PathVariable Long id) {
        return reportCardService.reportCardsForClass(id);
    }
}
