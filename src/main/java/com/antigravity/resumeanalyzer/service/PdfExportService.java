package com.antigravity.resumeanalyzer.service;

import com.antigravity.resumeanalyzer.entity.Analysis;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

@Service
public class PdfExportService {

    // Define Custom Color Palette (Premium Dark/Modern Navy Theme)
    private static final Color primaryColor = new Color(15, 23, 42);   // Slate 900
    private static final Color secondaryColor = new Color(79, 70, 229); // Indigo 600
    private static final Color lightBgColor = new Color(248, 250, 252);  // Slate 50
    private static final Color successColor = new Color(22, 163, 74);  // Green 600
    private static final Color dangerColor = new Color(220, 38, 38);   // Red 600
    private static final Color darkText = new Color(51, 65, 85);       // Slate 700
    
    // Fonts
    private static final Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, primaryColor);
    private static final Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, secondaryColor);
    private static final Font sectionHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);
    private static final Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, darkText);
    private static final Font regularFont = FontFactory.getFont(FontFactory.HELVETICA, 10, darkText);
    private static final Font italicFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, darkText);
    private static final Font scoreFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, secondaryColor);

    public byte[] generateAnalysisReport(Analysis analysis) throws DocumentException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        // Initialize A4 document with 36pt (0.5 inch) margins
        Document document = new Document(PageSize.A4, 36, 36, 54, 36);
        PdfWriter.getInstance(document, out);
        
        document.open();
        
        // 1. Header Banner
        PdfPTable headerTable = new PdfPTable(1);
        headerTable.setWidthPercentage(100);
        PdfPCell headerCell = new PdfPCell(new Phrase("AI-POWERED RESUME MATCHING REPORT", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.WHITE)));
        headerCell.setBackgroundColor(primaryColor);
        headerCell.setPadding(15);
        headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        headerCell.setBorder(Rectangle.NO_BORDER);
        headerTable.addCell(headerCell);
        document.add(headerTable);
        
        document.add(new Paragraph("\n"));
        
        // 2. Candidate & Job Metadata Section (2 columns)
        PdfPTable metaTable = new PdfPTable(2);
        metaTable.setWidthPercentage(100);
        metaTable.setWidths(new float[]{60f, 40f}); // 60% info, 40% score panel
        
        // Details Cell
        PdfPCell detailsCell = new PdfPCell();
        detailsCell.setBorder(Rectangle.NO_BORDER);
        detailsCell.setPaddingRight(10);
        
        Paragraph candidateNamePara = new Paragraph(analysis.getCandidate().getName().toUpperCase(), titleFont);
        candidateNamePara.setSpacingAfter(5);
        detailsCell.addElement(candidateNamePara);
        
        detailsCell.addElement(new Paragraph("Email: " + analysis.getCandidate().getEmail(), regularFont));
        detailsCell.addElement(new Paragraph("Target Position: " + analysis.getJob().getJobTitle(), boldFont));
        
        String formattedDate = "";
        if (analysis.getAnalyzedAt() != null) {
            formattedDate = analysis.getAnalyzedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } else {
            formattedDate = "Recently";
        }
        detailsCell.addElement(new Paragraph("Analysis Date: " + formattedDate, italicFont));
        metaTable.addCell(detailsCell);
        
        // Score Panel Cell
        PdfPCell scoreCell = new PdfPCell();
        scoreCell.setBackgroundColor(lightBgColor);
        scoreCell.setPadding(15);
        scoreCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        scoreCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        scoreCell.setBorderColor(secondaryColor);
        scoreCell.setBorderWidth(1.5f);
        
        Paragraph pctHeading = new Paragraph("MATCH FIT", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, darkText));
        pctHeading.setAlignment(Element.ALIGN_CENTER);
        scoreCell.addElement(pctHeading);
        
        Paragraph pctVal = new Paragraph(analysis.getMatchPercentage() + "%", scoreFont);
        pctVal.setAlignment(Element.ALIGN_CENTER);
        pctVal.setSpacingBefore(5);
        scoreCell.addElement(pctVal);
        
        metaTable.addCell(scoreCell);
        document.add(metaTable);
        
        document.add(new Paragraph("\n"));
        
        // 3. Skill Comparison Table
        PdfPTable skillsTable = new PdfPTable(2);
        skillsTable.setWidthPercentage(100);
        skillsTable.setSpacingBefore(10);
        skillsTable.setSpacingAfter(10);
        
        // Headers
        PdfPCell matchHeader = new PdfPCell(new Phrase("MATCHED SKILLS", sectionHeaderFont));
        matchHeader.setBackgroundColor(successColor);
        matchHeader.setPadding(8);
        matchHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        skillsTable.addCell(matchHeader);
        
        PdfPCell missingHeader = new PdfPCell(new Phrase("MISSING SKILLS (GAPS)", sectionHeaderFont));
        missingHeader.setBackgroundColor(dangerColor);
        missingHeader.setPadding(8);
        missingHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        skillsTable.addCell(missingHeader);
        
        // Content
        String matched = (analysis.getMatchedSkills() == null || analysis.getMatchedSkills().isEmpty()) 
                ? "None" : analysis.getMatchedSkills();
        String missing = (analysis.getMissingSkills() == null || analysis.getMissingSkills().isEmpty()) 
                ? "None" : analysis.getMissingSkills();
        
        PdfPCell matchedCell = new PdfPCell(new Phrase(matched, regularFont));
        matchedCell.setPadding(10);
        matchedCell.setBackgroundColor(lightBgColor);
        skillsTable.addCell(matchedCell);
        
        PdfPCell missingCell = new PdfPCell(new Phrase(missing, regularFont));
        missingCell.setPadding(10);
        missingCell.setBackgroundColor(lightBgColor);
        skillsTable.addCell(missingCell);
        
        document.add(skillsTable);
        
        document.add(new Paragraph("\n"));
        
        // 4. AI Recommendation Section
        Paragraph recTitle = new Paragraph("AI RECOMMENDATIONS & UP-SKILLING PATHWAYS", subTitleFont);
        recTitle.setSpacingAfter(10);
        document.add(recTitle);
        
        // Parse the markdown recommendations and build simple PDF blocks
        String recs = analysis.getRecommendations();
        if (recs != null && !recs.isEmpty()) {
            String[] lines = recs.split("\\r?\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                
                if (trimmed.startsWith("###")) {
                    // Header 3 (Skip or make bold sub-section)
                    String content = trimmed.substring(3).trim();
                    Paragraph h3 = new Paragraph(content, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, primaryColor));
                    h3.setSpacingBefore(8);
                    h3.setSpacingAfter(4);
                    document.add(h3);
                } else if (trimmed.startsWith("####")) {
                    // Header 4
                    String content = trimmed.substring(4).trim();
                    Paragraph h4 = new Paragraph(content, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, secondaryColor));
                    h4.setSpacingBefore(8);
                    h4.setSpacingAfter(4);
                    document.add(h4);
                } else if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
                    // Bullet Point
                    String content = trimmed.substring(1).trim();
                    // Clean markdown bold text formatting: **text**
                    content = content.replaceAll("\\*\\*", "");
                    Paragraph bullet = new Paragraph("• " + content, regularFont);
                    bullet.setIndentationLeft(15);
                    bullet.setSpacingAfter(3);
                    document.add(bullet);
                } else {
                    // Standard text
                    String content = trimmed.replaceAll("\\*\\*", "");
                    Paragraph textPara = new Paragraph(content, regularFont);
                    textPara.setSpacingAfter(5);
                    document.add(textPara);
                }
            }
        } else {
            document.add(new Paragraph("No recommendations generated.", regularFont));
        }
        
        // Footer line
        document.add(new Paragraph("\n"));
        PdfPTable footerTable = new PdfPTable(1);
        footerTable.setWidthPercentage(100);
        PdfPCell footerCell = new PdfPCell(new Phrase("Generated automatically by AI-Powered Resume Analyzer and Job Matching System.", italicFont));
        footerCell.setBorder(Rectangle.TOP);
        footerCell.setBorderColor(Color.LIGHT_GRAY);
        footerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        footerCell.setPaddingTop(10);
        footerTable.addCell(footerCell);
        document.add(footerTable);
        
        document.close();
        return out.toByteArray();
    }
}
