package com.opportunity.school.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "result_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false, unique = true)
    @JsonIgnore
    private Exam exam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ResultStatus status;

    /** Class teacher who sent the results. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by_id", nullable = false)
    @JsonIgnore
    private Teacher submittedBy;

    @Column(length = 255)
    private String teacherComment;

    /** Admin comment when approving or returning. */
    @Column(length = 255)
    private String adminComment;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @Column
    private LocalDateTime reviewedAt;

    public enum ResultStatus {
        PENDING,
        APPROVED,
        RETURNED
    }
}
