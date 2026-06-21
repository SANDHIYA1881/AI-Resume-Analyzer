package com.antigravity.resumeanalyzer.repository;

import com.antigravity.resumeanalyzer.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {
}
