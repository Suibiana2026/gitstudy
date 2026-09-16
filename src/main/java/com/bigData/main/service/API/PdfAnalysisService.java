package com.bigData.main.service.API;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PdfAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(PdfAnalysisService.class);
    private static final int MAX_INPUT_LENGTH = 65536;
    private static final int BUFFER = 100;

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.api.model}")
    private String model;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PdfAnalysisService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public String extractTextFromPdf(byte[] pdfBytes) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            logger.error("PDF bytes are null or empty");
            throw new IllegalArgumentException("PDF bytes cannot be null or empty");
        }

        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            logger.info("Extracted text from PDF (length: {}): {}", text.length(), text);
            return text;
        } catch (IOException e) {
            logger.error("Failed to extract text from PDF: ", e);
            throw new IOException("Text extraction failed: " + e.getMessage(), e);
        }
    }

    public String analyzeText(String text) throws IOException {
        if (text == null || text.trim().isEmpty()) {
            logger.warn("Text to analyze is null or empty, returning default message");
            return "无可分析的文本";
        }

        List<String> segments = splitText(text, MAX_INPUT_LENGTH - BUFFER);
        List<String> analyses = new ArrayList<>();

        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            String analysis = analyzeSegment(segment, i + 1, segments.size());
            analyses.add(analysis);
        }

        String combinedAnalysis = combineAnalyses(analyses);
        logger.info("Combined AI analysis: {}", combinedAnalysis);
        return combinedAnalysis;
    }

    private List<String> splitText(String text, int maxLength) {
        List<String> segments = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + maxLength, text.length());
            if (end < text.length()) {
                int lastPeriod = text.lastIndexOf(".", end);
                int lastNewline = text.lastIndexOf("\n", end);
                int splitPoint = Math.max(lastPeriod, lastNewline);
                if (splitPoint > start) {
                    end = splitPoint + 1;
                }
            }
            segments.add(text.substring(start, end));
            start = end;
        }

        logger.debug("Text split into {} segments", segments.size());
        return segments;
    }

    private String analyzeSegment(String segment, int segmentNumber, int totalSegments) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        String prompt = String.format("请分析以下文本并提供简短总结（第 %d 段，共 %d 段）。所有回答必须使用中文，包括标题和内容:\n%s",
                segmentNumber, totalSegments, segment);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        requestBody.put("messages", Collections.singletonList(message));
        requestBody.put("max_tokens", 500);

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(requestBody);
            logger.debug("Generated JSON request body for segment {}: {}", segmentNumber, jsonBody);
        } catch (Exception e) {
            logger.error("Failed to serialize request body for segment {}: ", segmentNumber, e);
            return "第 " + segmentNumber + " 段分析失败: JSON 序列化错误 - " + e.getMessage();
        }

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

        try {
            String response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class).getBody();
            logger.info("AI response for segment {}: {}", segmentNumber, response);

            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode choices = rootNode.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode messageNode = firstChoice.path("message");
                JsonNode contentNode = messageNode.path("content");
                if (contentNode.isTextual()) {
                    String analysis = contentNode.asText().replace("\\n", "\n");
                    logger.info("Parsed AI analysis for segment {}: {}", segmentNumber, analysis);
                    return analysis;
                }
            }
            logger.error("Failed to parse AI response for segment {}: {}", segmentNumber, response);
            return "第 " + segmentNumber + " 段分析失败: 无法解析响应";
        } catch (HttpClientErrorException e) {
            logger.error("AI API returned error for segment {}: Status={}, Response={}",
                    segmentNumber, e.getStatusCode(), e.getResponseBodyAsString());
            return "第 " + segmentNumber + " 段分析失败: " + e.getStatusCode() + " - " + e.getResponseBodyAsString();
        } catch (Exception e) {
            logger.error("Error calling AI API for segment {}: ", segmentNumber, e);
            return "第 " + segmentNumber + " 段分析失败: " + e.getMessage();
        }
    }

    private String combineAnalyses(List<String> analyses) {
        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < analyses.size(); i++) {
            combined.append("第 ").append(i + 1).append(" 段总结:\n");
            combined.append(analyses.get(i)).append("\n\n");
        }
        return combined.toString().trim();
    }

    public byte[] appendAnalysisToPdf(byte[] pdfBytes, String analysis) throws IOException {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDPage page = document.getPage(document.getNumberOfPages() - 1);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true)) {
                PDFont font = PDType0Font.load(document, new ClassPathResource("static/font/simsunb.ttf").getInputStream());

                contentStream.beginText();
                contentStream.setFont(font, 12);
                contentStream.newLineAtOffset(50, 50);
                contentStream.showText("AI 分析:");
                contentStream.newLineAtOffset(0, -15);
                contentStream.setFont(font, 10);
                for (String line : analysis.split("\n")) {
                    contentStream.showText(line);
                    contentStream.newLineAtOffset(0, -15);
                }
                contentStream.endText();
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            byte[] updatedPdf = outputStream.toByteArray();
            logger.info("PDF updated with analysis, new length: {}", updatedPdf.length);
            return updatedPdf;
        } catch (IOException e) {
            logger.error("Failed to append analysis to PDF: ", e);
            throw new IOException("Failed to append analysis to PDF: " + e.getMessage(), e);
        }
    }
}