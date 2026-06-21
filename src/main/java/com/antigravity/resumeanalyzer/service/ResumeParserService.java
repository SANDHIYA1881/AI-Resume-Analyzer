package com.antigravity.resumeanalyzer.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ResumeParserService {

    // Predefined dictionary of popular technical skills mapped to regex patterns for high-precision extraction
    private static final Map<String, Pattern> SKILL_PATTERNS = new LinkedHashMap<>();

    static {
        SKILL_PATTERNS.put("Java", Pattern.compile("\\bjava\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("Spring Boot", Pattern.compile("\\bspring\\s+boot\\b|\\bspringboot\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("Hibernate", Pattern.compile("\\bhibernate\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("MySQL", Pattern.compile("\\bmysql\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("SQL", Pattern.compile("\\bsql\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("Python", Pattern.compile("\\bpython\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("React", Pattern.compile("\\breact(?:js|\\.js)?\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("Angular", Pattern.compile("\\bangular(?:js|\\.js)?\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("Docker", Pattern.compile("\\bdocker\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("Kubernetes", Pattern.compile("\\bkubernetes\\b|\\bk8s\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("AWS", Pattern.compile("\\baws\\b|\\bamazon\\s+web\\s+services\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("Azure", Pattern.compile("\\bazure\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("Git", Pattern.compile("\\bgit\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("REST API", Pattern.compile("\\brest(?:ful)?\\s+api\\b|\\brest\\b", Pattern.CASE_INSENSITIVE));
        // Additional skills for comprehensive coverage
        SKILL_PATTERNS.put("JavaScript", Pattern.compile("\\bjavascript\\b|\\bjs\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("TypeScript", Pattern.compile("\\btypescript\\b|\\bts\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("Node.js", Pattern.compile("\\bnode(?:\\.js)?\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("MongoDB", Pattern.compile("\\bmongodb\\b|\\bmongo\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("PostgreSQL", Pattern.compile("\\bpostgres(?:ql)?\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("Kafka", Pattern.compile("\\bkafka\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("CI/CD", Pattern.compile("\\bci/cd\\b|\\bci\\s+cd\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("Jenkins", Pattern.compile("\\bjenkins\\b", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("C++", Pattern.compile("\\bc\\+\\+(?!\\w)", Pattern.CASE_INSENSITIVE));
        SKILL_PATTERNS.put("C#", Pattern.compile("\\bc#\\b|\\bc#(?!\\w)", Pattern.CASE_INSENSITIVE));
    }

    /**
     * Extracts all plain text from a PDF file using Apache PDFBox.
     */
    public String extractTextFromPdf(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream();
             PDDocument document = PDDocument.load(is)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * Extracts candidate email from raw text using regular expressions.
     */
    public String extractEmail(String text) {
        Pattern emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        Matcher matcher = emailPattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return "unknown@example.com";
    }

    /**
     * Extracts candidate name using simple heuristics.
     * Looks at the first few lines of text that are not blank, don't look like URLs or contact info.
     */
    public String extractName(String text, String originalFilename) {
        if (text == null || text.trim().isEmpty()) {
            return getFallbackName(originalFilename);
        }

        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            // Skip empty lines, lines with emails, phone numbers, or typical resume headers
            if (trimmed.isEmpty() 
                    || trimmed.contains("@") 
                    || trimmed.matches(".*\\d{5,}.*") // looks like a phone number or zip code
                    || trimmed.toLowerCase().contains("resume") 
                    || trimmed.toLowerCase().contains("curriculum")
                    || trimmed.toLowerCase().contains("cv")
                    || trimmed.toLowerCase().startsWith("http")
                    || trimmed.length() > 50) {
                continue;
            }
            // Capitalized word check (heuristic for a name)
            if (trimmed.matches("^[A-Z][a-z]+(\\s+[A-Z][a-z]+)+$")) {
                return trimmed;
            }
            // If it's a short 2-3 word line, assume it could be the name
            String[] words = trimmed.split("\\s+");
            if (words.length >= 2 && words.length <= 4) {
                return trimmed;
            }
        }
        return getFallbackName(originalFilename);
    }

    private String getFallbackName(String originalFilename) {
        if (originalFilename == null || originalFilename.isEmpty()) {
            return "Unknown Candidate";
        }
        // Remove file extension
        String nameWithoutExt = originalFilename;
        int lastDotIdx = originalFilename.lastIndexOf('.');
        if (lastDotIdx > 0) {
            nameWithoutExt = originalFilename.substring(0, lastDotIdx);
        }
        // Clean up dashes or underscores
        nameWithoutExt = nameWithoutExt.replaceAll("[_-]", " ").trim();
        // Capitalize words
        return Arrays.stream(nameWithoutExt.split("\\s+"))
                .map(word -> word.isEmpty() ? "" : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    /**
     * Scans the resume text against the pre-defined skills list.
     */
    public List<String> extractSkills(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> extractedSkills = new ArrayList<>();
        for (Map.Entry<String, Pattern> entry : SKILL_PATTERNS.entrySet()) {
            Matcher matcher = entry.getValue().matcher(text);
            if (matcher.find()) {
                extractedSkills.add(entry.getKey());
            }
        }
        return extractedSkills;
    }
}
