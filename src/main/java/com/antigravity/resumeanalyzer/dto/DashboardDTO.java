package com.antigravity.resumeanalyzer.dto;

import com.antigravity.resumeanalyzer.entity.Analysis;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardDTO {
    private Long totalCandidates;
    private Long totalJobs;
    private Double averageMatchPercentage;
    private List<Analysis> topCandidates;
}
