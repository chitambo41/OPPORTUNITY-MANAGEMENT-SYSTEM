package com.opportunity.school.controller;

import com.opportunity.school.dto.PromotionDtos;
import com.opportunity.school.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/promotion")
@RequiredArgsConstructor
public class AdminPromotionController {

    private final PromotionService promotionService;

    /** Dry run: what would happen if we promoted into the given year. */
    @GetMapping("/preview")
    public PromotionDtos.PromotionResult preview(@RequestParam Integer toYear) {
        return promotionService.preview(toYear);
    }

    /** Executes the promotion into the given year. */
    @PostMapping
    public PromotionDtos.PromotionResult promote(@RequestParam Integer toYear) {
        return promotionService.promote(toYear);
    }
}
