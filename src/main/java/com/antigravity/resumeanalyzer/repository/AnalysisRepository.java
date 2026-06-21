package com.antigravity.resumeanalyzer.repository;

import com.antigravity.resumeanalyzer.entity.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    // Find previous analysis for specific candidate and job
    Optional<Analysis> findByCandidateIdAndJobId(Long candidateId, Long jobId);

    // Get top 5 matching candidates for resume ranking
    List<Analysis> findTop5ByOrderByMatchPercentageDesc();

    // Custom query to calculate the average match percentage across all analyses
    @Query("SELECT AVG(a.matchPercentage) FROM Analysis a")
    Double findAverageMatchPercentage();
}
