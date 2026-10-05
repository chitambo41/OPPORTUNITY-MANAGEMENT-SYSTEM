package com.opportunity.school.service;

import com.opportunity.school.dto.YearDtos;
import com.opportunity.school.exception.BusinessException;
import com.opportunity.school.exception.NotFoundException;
import com.opportunity.school.model.AcademicYear;
import com.opportunity.school.model.Term;
import com.opportunity.school.model.enums.TermNumber;
import com.opportunity.school.repository.AcademicYearRepository;
import com.opportunity.school.repository.TermRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AcademicYearService {

    private final AcademicYearRepository yearRepository;
    private final TermRepository termRepository;

    @Transactional
    public YearDtos.YearDto createYear(YearDtos.CreateYearRequest req) {
        if (yearRepository.existsByYear(req.getYear())) {
            throw new BusinessException("Academic year " + req.getYear() + " already exists");
        }

        // Validate term date ordering
        if (!req.getTerm1Start().isBefore(req.getTerm1End())) {
            throw new BusinessException("Term 1 start date must be before its end date");
        }
        if (!req.getTerm2Start().isBefore(req.getTerm2End())) {
            throw new BusinessException("Term 2 must start before it ends");
        }
        if (!req.getTerm1End().isBefore(req.getTerm2Start())) {
            throw new BusinessException("Term 1 must end before Term 2 starts (no overlap)");
        }

        // Validate no overlap with terms of other years
        List<Term> overlapping = termRepository.findOverlapping(req.getTerm1Start(), req.getTerm2End());
        if (!overlapping.isEmpty()) {
            Term t = overlapping.get(0);
            throw new BusinessException("Term dates overlap an existing term (" +
                    t.getAcademicYear().getYear() + " " + t.getNumber() + ": " +
                    t.getStartDate() + " to " + t.getEndDate() + ")");
        }

        boolean firstYear = yearRepository.count() == 0;
        AcademicYear year = AcademicYear.builder()
                .year(req.getYear())
                .current(firstYear) // first year becomes current automatically
                .startDate(req.getTerm1Start())
                .endDate(req.getTerm2End())
                .build();
        year = yearRepository.save(year);

        // Exactly two terms auto-created
        termRepository.save(Term.builder()
                .academicYear(year).number(TermNumber.TERM_1)
                .startDate(req.getTerm1Start()).endDate(req.getTerm1End())
                .current(firstYear)
                .build());
        termRepository.save(Term.builder()
                .academicYear(year).number(TermNumber.TERM_2)
                .startDate(req.getTerm2Start()).endDate(req.getTerm2End())
                .current(false)
                .build());

        return toDto(year);
    }

    @Transactional
    public YearDtos.YearDto setCurrent(YearDtos.SetCurrentRequest req) {
        AcademicYear year = yearRepository.findById(req.getAcademicYearId())
                .orElseThrow(() -> NotFoundException.entity("Academic year", req.getAcademicYearId()));

        Term targetTerm = null;
        if (req.getTermNumber() != null && !req.getTermNumber().isBlank()) {
            TermNumber number = parseTermNumber(req.getTermNumber());
            targetTerm = termRepository.findByAcademicYearAndNumber(year, number)
                    .orElseThrow(() -> new BusinessException("Year " + year.getYear() + " has no " + req.getTermNumber()));
        }

        // Clear current flags on all years/terms, then set
        List<AcademicYear> allYears = yearRepository.findAll();
        for (AcademicYear y : allYears) {
            y.setCurrent(false);
            for (Term t : termRepository.findByAcademicYearOrderByNumberAsc(y)) {
                t.setCurrent(false);
            }
        }
        year.setCurrent(true);
        if (targetTerm != null) {
            targetTerm.setCurrent(true);
        } else {
            // default to first term if none specified
            List<Term> terms = termRepository.findByAcademicYearOrderByNumberAsc(year);
            if (!terms.isEmpty()) {
                terms.get(0).setCurrent(true);
            }
        }
        yearRepository.save(year);
        return toDto(year);
    }

    @Transactional
    public YearDtos.YearDto updateTermDates(Long termId, YearDtos.TermUpdateRequest req) {
        Term term = termRepository.findById(termId)
                .orElseThrow(() -> NotFoundException.entity("Term", termId));
        if (!req.getStartDate().isBefore(req.getEndDate())) {
            throw new BusinessException("Term start date must be before end date");
        }
        List<Term> overlapping = termRepository.findOverlapping(req.getStartDate(), req.getEndDate()).stream()
                .filter(t -> !t.getId().equals(termId))
                .toList();
        if (!overlapping.isEmpty()) {
            Term t = overlapping.get(0);
            throw new BusinessException("Dates overlap an existing term (" +
                    t.getAcademicYear().getYear() + " " + t.getNumber() + ")");
        }
        term.setStartDate(req.getStartDate());
        term.setEndDate(req.getEndDate());
        termRepository.save(term);
        return toDto(term.getAcademicYear());
    }

    public List<YearDtos.YearDto> listYears() {
        return yearRepository.findAll().stream()
                .map(this::toDto)
                .sorted(Comparator.comparing(YearDtos.YearDto::getYear).reversed())
                .toList();
    }

    public YearDtos.CurrentContextDto getCurrentContext() {
        AcademicYear year = yearRepository.findByCurrentTrue().orElse(null);
        if (year == null) {
            return new YearDtos.CurrentContextDto(null, null, null, null, null, null);
        }
        Term term = termRepository.findByCurrentTrue().orElse(null);
        if (term == null) {
            List<Term> terms = termRepository.findByAcademicYearOrderByNumberAsc(year);
            term = terms.isEmpty() ? null : terms.get(0);
        }
        return new YearDtos.CurrentContextDto(
                year.getId(), year.getYear(),
                term != null ? term.getId() : null,
                term != null ? term.getNumber().name() : null,
                term != null ? term.getStartDate() : null,
                term != null ? term.getEndDate() : null);
    }

    private TermNumber parseTermNumber(String value) {
        try {
            return TermNumber.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid term number: " + value + " (use TERM_1 or TERM_2)");
        }
    }

    private YearDtos.YearDto toDto(AcademicYear year) {
        List<YearDtos.TermDto> terms = termRepository.findByAcademicYearOrderByNumberAsc(year).stream()
                .map(t -> new YearDtos.TermDto(t.getId(), t.getNumber().name(), t.getStartDate(),
                        t.getEndDate(), t.isCurrent(), year.getYear()))
                .toList();
        return new YearDtos.YearDto(year.getId(), year.getYear(), year.isCurrent(),
                year.getStartDate(), year.getEndDate(), terms);
    }
}
