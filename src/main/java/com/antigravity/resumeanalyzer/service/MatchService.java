package com.antigravity.resumeanalyzer.service;

import com.antigravity.resumeanalyzer.entity.Analysis;
import com.antigravity.resumeanalyzer.entity.Candidate;
import com.antigravity.resumeanalyzer.entity.Job;
import com.antigravity.resumeanalyzer.repository.AnalysisRepository;
import com.antigravity.resumeanalyzer.repository.CandidateRepository;
import com.antigravity.resumeanalyzer.repository.JobRepository;
import com.antigravity.resumeanalyzer.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final CandidateRepository candidateRepository;
    private final JobRepository jobRepository;
    private final AnalysisRepository analysisRepository;

    /**
     * Executes the skill gap analysis and matching between a Candidate and a Job.
     * Persists the analysis result or updates it if it already exists.
     */
    public Analysis analyzeMatch(Long candidateId, Long jobId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found with id: " + candidateId));

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));

        // Parse candidate skills
        Set<String> candidateSkills = parseSkillsToSet(candidate.getExtractedSkills());

        // Parse job required skills
        Set<String> requiredSkills = parseSkillsToSet(job.getRequiredSkills());

        if (requiredSkills.isEmpty()) {
            throw new IllegalArgumentException("Job required skills list is empty. Cannot perform analysis.");
        }

        // Normalization maps to bridge variations (e.g., "Spring Boot" -> "springboot")
        Map<String, String> normalizedToOriginalJobSkills = new LinkedHashMap<>();
        for (String reqSkill : requiredSkills) {
            normalizedToOriginalJobSkills.put(normalize(reqSkill), reqSkill);
        }

        Map<String, String> normalizedToOriginalCandidateSkills = new LinkedHashMap<>();
        for (String candSkill : candidateSkills) {
            normalizedToOriginalCandidateSkills.put(normalize(candSkill), candSkill);
        }

        // Perform matching on normalized keys
        Set<String> matchedKeys = new HashSet<>(normalizedToOriginalCandidateSkills.keySet());
        matchedKeys.retainAll(normalizedToOriginalJobSkills.keySet()); // Intersection

        Set<String> missingKeys = new HashSet<>(normalizedToOriginalJobSkills.keySet());
        missingKeys.removeAll(normalizedToOriginalCandidateSkills.keySet()); // Difference

        // Map back to original human-readable strings
        Set<String> matched = matchedKeys.stream()
                .map(normalizedToOriginalCandidateSkills::get)
                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));

        Set<String> missing = missingKeys.stream()
                .map(normalizedToOriginalJobSkills::get)
                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));

        // Formula: Match Percentage = (Matched Skills / Required Skills) * 100
        double percentage = ((double) matched.size() / requiredSkills.size()) * 100.0;
        // Round to 2 decimal places
        percentage = Math.round(percentage * 100.0) / 100.0;

        String matchedStr = String.join(", ", matched);
        String missingStr = String.join(", ", missing);

        // Generate AI Recommendations
        String recommendations = generateAiRecommendations(matched, missing, candidate.getName(), job.getJobTitle());

        // Save or update analysis
        Optional<Analysis> existingAnalysisOpt = analysisRepository.findByCandidateIdAndJobId(candidateId, jobId);
        Analysis analysis;
        if (existingAnalysisOpt.isPresent()) {
            analysis = existingAnalysisOpt.get();
            analysis.setMatchPercentage(percentage);
            analysis.setMatchedSkills(matchedStr);
            analysis.setMissingSkills(missingStr);
            analysis.setRecommendations(recommendations);
        } else {
            analysis = Analysis.builder()
                    .candidate(candidate)
                    .job(job)
                    .matchPercentage(percentage)
                    .matchedSkills(matchedStr)
                    .missingSkills(missingStr)
                    .recommendations(recommendations)
                    .build();
        }

        return analysisRepository.save(analysis);
    }

    private String normalize(String skill) {
        if (skill == null) return "";
        String mapped = skill.toLowerCase()
                             .replace("c++", "cpp")
                             .replace("c#", "csharp");
        return mapped.replaceAll("[^a-zA-Z0-9]", "").trim();
    }

    private Set<String> parseSkillsToSet(String skillsCsv) {
        if (skillsCsv == null || skillsCsv.trim().isEmpty()) {
            return Collections.emptySet();
        }
        return Arrays.stream(skillsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
    }

    /**
     * Heuristic AI Recommendation Module
     * Formulates dynamic resume advice and targeted learning actions based on the skill gaps.
     */
    private String generateAiRecommendations(Set<String> matched, Set<String> missing, String candidateName, String jobTitle) {
        StringBuilder sb = new StringBuilder();

        sb.append("### AI Recommendation Report for ").append(candidateName).append("\n");
        sb.append("Applying for: **").append(jobTitle).append("**\n\n");

        // Section 1: Resume Enhancements
        sb.append("#### 1. Resume Optimization & Formatting Suggestions\n");
        if (matched.isEmpty()) {
            sb.append("- **Critical Alert:** Your resume does not show strong matches for key technical skills. Re-structure your profile to place a technical skills matrix at the top of the first page.\n");
        } else {
            sb.append("- **Highlight Matches:** Bold your matching skills (**").append(String.join(", ", matched)).append("**) in your professional experience and projects sections.\n");
        }
        sb.append("- **Action-Oriented Verbs:** Describe your experiences using active verbs (e.g., *Built, Engineered, Architected, Refactored*) rather than passive descriptions.\n");
        sb.append("- **Impact Metrics:** Quantify your work achievements where possible (e.g., *'Optimized SQL queries reducing loading latency by 35%'*).\n");
        
        if (!missing.isEmpty()) {
            sb.append("- **Contextualize Gaps:** Even if you don't possess a skill full-time, list academic projects, bootcamps, or certifications related to: *").append(String.join(", ", missing)).append("*.\n");
        }
        sb.append("\n");

        // Section 2: Technical Upskilling Plan
        sb.append("#### 2. Technical Upskilling & Mini-Learning Paths\n");
        if (missing.isEmpty()) {
            sb.append("- **Perfect Match!** You have listed all required technologies. We recommend preparing for behavioral interviews and reviewing system design concepts related to the stack.\n");
        } else {
            sb.append("Based on the required stack, we recommend learning the following tools:\n\n");
            for (String skill : missing) {
                sb.append("- **").append(skill).append("**:\n");
                sb.append("  - *Action:* ").append(getSkillLearningAction(skill)).append("\n");
            }
        }
        
        return sb.toString();
    }

    /**
     * Map specific skills to specific action items and online certification suggestions.
     */
    private String getSkillLearningAction(String skill) {
        String s = skill.toLowerCase().trim();
        if (s.contains("spring boot") || s.contains("springboot") || s.contains("hibernate")) {
            return "Build a modular REST API using Spring Boot, Spring Data JPA, and Hibernate. Understand dependency injection and exception handling. *Resource: Official Spring Guides or Baeldung Tutorials.*";
        } else if (s.contains("java")) {
            return "Review Java 17 features (Records, Pattern Matching, Text Blocks). Practice OOP design principles and collections framework. *Resource: Java documentation or Oracle certifications.*";
        } else if (s.contains("mysql") || s.contains("sql") || s.contains("database")) {
            return "Practice writing complex joins, indexes, transaction isolation levels, and optimizing query execution plans. *Resource: LeetCode Database exercises.*";
        } else if (s.contains("docker")) {
            return "Learn containerization: write a Dockerfile for a Spring Boot application, compile it, and manage local volumes/networks. *Resource: Docker official get-started guide.*";
        } else if (s.contains("kubernetes") || s.contains("k8s")) {
            return "Understand Pods, Deployments, and Services. Set up a local single-node cluster using Minikube or Kind to orchestrate services. *Resource: Kubernetes basics interactive tutorials.*";
        } else if (s.contains("aws") || s.contains("amazon")) {
            return "Study core services like EC2, S3, RDS, Lambda, and IAM. Consider studying for the AWS Certified Cloud Practitioner or Developer Associate. *Resource: AWS Skill Builder.*";
        } else if (s.contains("azure")) {
            return "Explore Azure App Services, VM provisioning, blob storage, and Resource Managers. Consider studying for the Microsoft AZ-900 exam. *Resource: Microsoft Learn.*";
        } else if (s.contains("react")) {
            return "Build single-page apps (SPA) utilizing functional components, hooks (useState, useEffect, useContext), and state managers. *Resource: React Dev docs.*";
        } else if (s.contains("angular")) {
            return "Understand TypeScript-driven Angular modules, components, services, dependency injection, and RxJS observables. *Resource: Angular Tour of Heroes guide.*";
        } else if (s.contains("git")) {
            return "Master Git branching models (GitFlow), cherry-picking, interactive rebase, and resolving merge conflicts. *Resource: Git branching visual game.*";
        } else if (s.contains("rest api")) {
            return "Read about RESTful architectural constraints, HTTP status codes, media types, and API design guidelines. Document APIs using Swagger/OpenAPI. *Resource: RESTful API design manuals.*";
        } else {
            return "Review foundational tutorials, read documentation, and complete a hands-on project to demonstrate competence. Include it in your GitHub portfolio.";
        }
    }
}
