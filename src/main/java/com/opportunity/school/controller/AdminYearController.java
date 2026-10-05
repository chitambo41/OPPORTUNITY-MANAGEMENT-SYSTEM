package com.opportunity.school.controller;

import com.opportunity.school.dto.YearDtos;
import com.opportunity.school.service.AcademicYearService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/years")
@RequiredArgsConstructor
public class AdminYearController {

    private final AcademicYearService yearService;

    @PostMapping
    public YearDtos.YearDto create(@Valid @RequestBody YearDtos.CreateYearRequest request) {
        return yearService.createYear(request);
    }

    @GetMapping
    public List<YearDtos.YearDto> list() {
        return yearService.listYears();
    }

    @PostMapping("/set-current")
    public YearDtos.YearDto setCurrent(@Valid @RequestBody YearDtos.SetCurrentRequest request) {
        return yearService.setCurrent(request);
    }

    @PutMapping("/terms/{termId}")
    public YearDtos.YearDto updateTerm(@PathVariable Long termId,
                                       @Valid @RequestBody YearDtos.TermUpdateRequest request) {
        return yearService.updateTermDates(termId, request);
    }

    @GetMapping("/current-context")
    public YearDtos.CurrentContextDto currentContext() {
        return yearService.getCurrentContext();
    }
}
