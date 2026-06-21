package com.antigravity.resumeanalyzer.controller;

import com.antigravity.resumeanalyzer.dto.DashboardDTO;
import com.antigravity.resumeanalyzer.entity.Analysis;
import com.antigravity.resumeanalyzer.entity.Candidate;
import com.antigravity.resumeanalyzer.entity.Job;
import com.antigravity.resumeanalyzer.repository.AnalysisRepository;
import com.antigravity.resumeanalyzer.repository.CandidateRepository;
import com.antigravity.resumeanalyzer.repository.JobRepository;
import com.antigravity.resumeanalyzer.service.MatchService;
import com.antigravity.resumeanalyzer.service.PdfExportService;
import com.antigravity.resumeanalyzer.service.ResumeParserService;
import com.antigravity.resumeanalyzer.exception.ResourceNotFoundException;
import com.lowagie.text.DocumentException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@RestController
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class RestApiController {

    private final ResumeParserService resumeParserService;
    private final MatchService matchService;
    private final PdfExportService pdfExportService;
    private final CandidateRepository candidateRepository;
    private final JobRepository jobRepository;
    private final AnalysisRepository analysisRepository;

    @Value("${file.upload-dir:./uploads/}")
    private String uploadDir;

    /**
     * POST /uploadResume
     * Uploads candidate resume, extracts text and skills, and stores it in MySQL.
     */
    @PostMapping("/uploadResume")
    public ResponseEntity<?> uploadResume(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File is empty. Please select a valid PDF.");
        }

        if (!"application/pdf".equals(file.getContentType())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid file type. Only PDF files are allowed.");
        }

        try {
            // Ensure uploads directory exists
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Generate a unique file name to avoid overwrite conflicts
            String originalFileName = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFileName != null && originalFileName.contains(".")) {
                fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            }
            String savedFileName = UUID.randomUUID().toString() + fileExtension;
            Path path = Paths.get(uploadDir + savedFileName);

            // Copy file to uploads folder
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

            // Parse PDF content
            String extractedText = resumeParserService.extractTextFromPdf(file);

            // If Name and Email are not supplied, parse them from PDF text
            String finalName = (name == null || name.trim().isEmpty()) 
                    ? resumeParserService.extractName(extractedText, originalFileName) : name.trim();
            String finalEmail = (email == null || email.trim().isEmpty()) 
                    ? resumeParserService.extractEmail(extractedText) : email.trim();

            // Extract technical skills matching our pre-defined dictionary
            List<String> skillsList = resumeParserService.extractSkills(extractedText);
            String skillsCsv = String.join(", ", skillsList);

            // Build Candidate entity
            Candidate candidate = Candidate.builder()
                    .name(finalName)
                    .email(finalEmail)
                    .resumePath("uploads/" + savedFileName)
                    .extractedSkills(skillsCsv)
                    .extractedText(extractedText)
                    .build();

            Candidate savedCandidate = candidateRepository.save(candidate);
            return ResponseEntity.ok(savedCandidate);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing file upload: " + e.getMessage());
        }
    }

    /**
     * POST /createJob
     * Creates and stores a job description with required skills.
     */
    @PostMapping("/createJob")
    public ResponseEntity<?> createJob(@RequestBody Job job) {
        if (job.getJobTitle() == null || job.getJobTitle().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Job title cannot be empty.");
        }
        if (job.getRequiredSkills() == null || job.getRequiredSkills().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Required skills list cannot be empty.");
        }

        Job savedJob = jobRepository.save(job);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedJob);
    }

    /**
     * GET /analyze/{candidateId}/{jobId}
     * Computes the skill-match, generates suggestions, and saves analysis.
     */
    @GetMapping("/analyze/{candidateId}/{jobId}")
    public ResponseEntity<?> analyze(
            @PathVariable("candidateId") Long candidateId,
            @PathVariable("jobId") Long jobId) {
        try {
            Analysis analysis = matchService.analyzeMatch(candidateId, jobId);
            return ResponseEntity.ok(analysis);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * GET /dashboard
     * Returns key metrics: candidate count, job count, match average, and top rankings.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDTO> getDashboardData() {
        long totalCandidates = candidateRepository.count();
        long totalJobs = jobRepository.count();
        
        Double averagePct = analysisRepository.findAverageMatchPercentage();
        if (averagePct == null) {
            averagePct = 0.0;
        }
        // Round to 2 decimal places
        averagePct = Math.round(averagePct * 100.0) / 100.0;

        List<Analysis> topCandidates = analysisRepository.findTop5ByOrderByMatchPercentageDesc();

        DashboardDTO dashboard = DashboardDTO.builder()
                .totalCandidates(totalCandidates)
                .totalJobs(totalJobs)
                .averageMatchPercentage(averagePct)
                .topCandidates(topCandidates)
                .build();

        return ResponseEntity.ok(dashboard);
    }

    /**
     * GET /analysis/{analysisId}/download
     * Exposes download of the analysis report in PDF format.
     */
    @GetMapping("/analysis/{analysisId}/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable("analysisId") Long analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis record not found with id: " + analysisId));

        try {
            byte[] pdfBytes = pdfExportService.generateAnalysisReport(analysis);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            // Format filename safely
            String safeCandidateName = analysis.getCandidate().getName().replaceAll("[^a-zA-Z0-9]", "_");
            headers.setContentDispositionFormData("attachment", "Resume_Match_Report_" + safeCandidateName + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (DocumentException | IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                    .body(("Failed to generate PDF report: " + e.getMessage()).getBytes());
        }
    }
}
