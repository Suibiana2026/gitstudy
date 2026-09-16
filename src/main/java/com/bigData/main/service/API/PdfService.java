package com.bigData.main.service.API;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class PdfService {

    private static final Logger logger = LoggerFactory.getLogger(PdfService.class);
    private static final String FONT_NAME = "STSong-Light"; // 来自 font-asian
    private static final String ENCODING = "UniGB-UCS2-H";  // 中文编码

    public byte[] generatePdf(String content, String timeRange) throws IOException {
        if (content == null || content.trim().isEmpty()) {
            logger.error("Content is null or empty");
            throw new IllegalArgumentException("Content cannot be null or empty");
        }
        if (timeRange == null || timeRange.trim().isEmpty()) {
            logger.error("Time range is null or empty");
            throw new IllegalArgumentException("Time range cannot be null or empty");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        try {
            // 加载中文字体
            PdfFont font = PdfFontFactory.createFont(FONT_NAME, ENCODING);
            if (font == null) {
                logger.error("Failed to load font: {}", FONT_NAME);
                throw new IOException("Font loading failed: " + FONT_NAME);
            }
            logger.debug("Font loaded successfully: {}", font.getFontProgram().getFontNames());

            // 设置默认字体和大小
            document.setFont(font);
            document.setFontSize(12);

            // 添加标题（基于时间范围）
            String title = String.format("销售报告 - %s", timeRange);
            Paragraph titleParagraph = new Paragraph(title)
                    .setFont(font)
                    .setFontSize(18)
                    // 注意：STSong-Light 无粗体变体，这里仅增大字体模拟突出效果
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(titleParagraph);

            // 添加正文内容
            Paragraph contentParagraph = new Paragraph(content)
                    .setFont(font)
                    .setFontSize(12)
                    .setTextAlignment(TextAlignment.LEFT)
                    .setMultipliedLeading(1.5f); // 行距调整为 1.5 倍
            document.add(contentParagraph);

            logger.info("PDF generated successfully for time range: {}", timeRange);
        } catch (Exception e) {
            logger.error("Error during PDF generation for time range {}: ", timeRange, e);
            throw new IOException("PDF generation failed: " + e.getMessage(), e);
        } finally {
            document.close();
        }

        return baos.toByteArray();
    }
}