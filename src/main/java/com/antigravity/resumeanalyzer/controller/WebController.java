package com.antigravity.resumeanalyzer.controller;

import com.antigravity.resumeanalyzer.entity.Analysis;
import com.antigravity.resumeanalyzer.entity.Candidate;
import com.antigravity.resumeanalyzer.entity.Job;
import com.antigravity.resumeanalyzer.repository.AnalysisRepository;
import com.antigravity.resumeanalyzer.repository.CandidateRepository;
import com.antigravity.resumeanalyzer.repository.JobRepository;
import com.antigravity.resumeanalyzer.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class WebController {

    private final CandidateRepository candidateRepository;
    private final JobRepository jobRepository;
    private final AnalysisRepository analysisRepository;
    private final MatchService matchService;

    /**
     * GET /
     * Home Page (Welcome Page)
     */
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("title", "AI Resume Analyzer - Home");
        return "home";
    }

    /**
     * GET /dashboard-view
     * Dashboard Page (Metrics, Charts, Top Candidates)
     */
    @GetMapping("/dashboard-view")
    public String dashboard(Model model) {
        long totalCandidates = candidateRepository.count();
        long totalJobs = jobRepository.count();
        
        Double averagePct = analysisRepository.findAverageMatchPercentage();
        if (averagePct == null) {
            averagePct = 0.0;
        }
        averagePct = Math.round(averagePct * 100.0) / 100.0;

        List<Analysis> topCandidates = analysisRepository.findTop5ByOrderByMatchPercentageDesc();

        model.addAttribute("title", "AI Resume Analyzer - Dashboard");
        model.addAttribute("totalCandidates", totalCandidates);
        model.addAttribute("totalJobs", totalJobs);
        model.addAttribute("averageMatchPercentage", averagePct);
        model.addAttribute("topCandidates", topCandidates);

        return "dashboard";
    }

    /**
     * GET /upload
     * Resume Upload Page (Lists uploaded resumes and has an upload form)
     */
    @GetMapping("/upload")
    public String upload(Model model) {
        List<Candidate> candidates = candidateRepository.findAll();
        model.addAttribute("title", "AI Resume Analyzer - Upload Resume");
        model.addAttribute("candidates", candidates);
        return "upload";
    }

    /**
     * GET /jobs
     * Job Creation Page (Lists jobs and has a form to add a new job)
     */
    @GetMapping("/jobs")
    public String jobs(Model model) {
        List<Job> jobs = jobRepository.findAll();
        model.addAttribute("title", "AI Resume Analyzer - Manage Jobs");
        model.addAttribute("jobs", jobs);
        return "jobs";
    }

    /**
     * GET /analysis
     * Analysis Result Page
     * Performs analysis if candidateId and jobId are supplied, and displays results.
     */
    @GetMapping("/analysis")
    public String analysis(
            @RequestParam(value = "candidateId", required = false) Long candidateId,
            @RequestParam(value = "jobId", required = false) Long jobId,
            Model model) {
        
        List<Candidate> candidates = candidateRepository.findAll();
        List<Job> jobs = jobRepository.findAll();

        model.addAttribute("title", "AI Resume Analyzer - Match Analysis");
        model.addAttribute("candidates", candidates);
        model.addAttribute("jobs", jobs);

        if (candidateId != null && jobId != null) {
            try {
                Analysis analysisResult = matchService.analyzeMatch(candidateId, jobId);
                model.addAttribute("analysis", analysisResult);
                model.addAttribute("selectedCandidateId", candidateId);
                model.addAttribute("selectedJobId", jobId);
            } catch (Exception e) {
                model.addAttribute("error", "Error performing analysis: " + e.getMessage());
            }
        }

        return "analysis";
    }
}
