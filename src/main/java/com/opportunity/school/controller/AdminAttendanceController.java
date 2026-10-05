package com.opportunity.school.controller;

import com.opportunity.school.dto.AttendanceDtos;
import com.opportunity.school.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/attendance")
@RequiredArgsConstructor
public class AdminAttendanceController {

    private final AttendanceService attendanceService;

    /** Students of a class with their status for a date. */
    @GetMapping
    public List<AttendanceDtos.StudentAttendanceRow> classDay(@RequestParam Long classId,
                                                              @RequestParam LocalDate date) {
        return attendanceService.classDayList(classId, date);
    }

    /** Daily summary (present/absent/late/unmarked) for a class + date. */
    @GetMapping("/summary")
    public AttendanceDtos.DailySummaryDto summary(@RequestParam Long classId,
                                                  @RequestParam LocalDate date) {
        return attendanceService.dailySummary(classId, date);
    }

    /** Per-student report for a class within a range, with percentage. */
    @GetMapping("/report")
    public List<AttendanceDtos.StudentReportRow> report(@RequestParam Long classId,
                                                        @RequestParam LocalDate start,
                                                        @RequestParam LocalDate end) {
        return attendanceService.classReport(classId, start, end);
    }
}
