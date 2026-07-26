package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.payloads.PostDto;
import com.BlogApplication.Blog.services.PostPdfService;
import com.BlogApplication.Blog.util.TimeFormatter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// Renders a Post as a simple, paginated PDF (title, byline, tags, body) for the
// "Download PDF" action - one page's worth of wrapped lines at a time via PDFBox,
// since PDFBox has no built-in flow/reflow layout of its own.
@Service
public class PostPdfServiceImpl implements PostPdfService {

    private static final float MARGIN = 56f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private static final float BODY_FONT_SIZE = 11f;
    private static final float BODY_LEADING = 16f;

    @Override
    public byte[] renderToPdf(PostDto post) {
        try (PDDocument document = new PDDocument()) {
            PDFont titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont metaFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
            PDFont bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            List<String> bodyLines = wrap(post.getContent() != null ? post.getContent() : "", bodyFont, BODY_FONT_SIZE, CONTENT_WIDTH);

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(document, page);
            float y = PAGE_HEIGHT - MARGIN;

            y = drawWrappedText(cs, post.getTitle() != null ? post.getTitle() : "Untitled", titleFont, 20f, MARGIN, y, CONTENT_WIDTH, 24f);
            y -= 6f;

            String meta = "By " + (post.getAuthor() != null ? post.getAuthor() : "Unknown")
                    + (post.getUpdatedAt() != null ? " · " + TimeFormatter.absolute(post.getUpdatedAt()) : "");
            cs.setFont(metaFont, 10f);
            cs.beginText();
            cs.newLineAtOffset(MARGIN, y);
            cs.showText(meta);
            cs.endText();
            y -= 16f;

            if (post.getTags() != null && !post.getTags().isBlank()) {
                String tags = List.of(post.getTags().split(",")).stream()
                        .map(String::trim)
                        .filter(t -> !t.isEmpty())
                        .collect(Collectors.joining("   ·   "));
                cs.setFont(metaFont, 9.5f);
                cs.beginText();
                cs.newLineAtOffset(MARGIN, y);
                cs.showText(tags);
                cs.endText();
                y -= 20f;
            } else {
                y -= 6f;
            }

            // Divider line under the header block.
            cs.moveTo(MARGIN, y);
            cs.lineTo(PAGE_WIDTH - MARGIN, y);
            cs.stroke();
            y -= 22f;

            cs.setFont(bodyFont, BODY_FONT_SIZE);
            int pageNumber = 1;

            cs.beginText();
            cs.newLineAtOffset(MARGIN, y);
            float cursor = y;
            for (String line : bodyLines) {
                if (cursor < MARGIN + 30f) {
                    cs.endText();
                    cs.close();

                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    cs = new PDPageContentStream(document, page);
                    cs.setFont(bodyFont, BODY_FONT_SIZE);
                    cursor = PAGE_HEIGHT - MARGIN;
                    cs.beginText();
                    cs.newLineAtOffset(MARGIN, cursor);
                    pageNumber++;
                }
                cs.showText(line);
                cs.newLineAtOffset(0, -BODY_LEADING);
                cursor -= BODY_LEADING;
            }
            cs.endText();
            cs.close();

            addFooters(document, pageNumber);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render post PDF", e);
        }
    }

    private void addFooters(PDDocument document, int totalPages) throws IOException {
        PDFont footerFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        int pageIndex = 1;
        for (PDPage page : document.getPages()) {
            try (PDPageContentStream cs = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true)) {
                String footer = "Bodh Sea · Page " + pageIndex + " of " + totalPages;
                cs.setFont(footerFont, 9f);
                cs.beginText();
                cs.newLineAtOffset(MARGIN, MARGIN - 26f);
                cs.showText(footer);
                cs.endText();
            }
            pageIndex++;
        }
    }

    // Word-wraps text to fit maxWidth, honoring explicit newlines already in the source content.
    private List<String> wrap(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (font.getStringWidth(candidate) / 1000 * fontSize > maxWidth && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            lines.add(current.toString());
        }
        return lines;
    }

    // Draws (possibly wrapping) large text such as the title, returning the y position after it -
    // i.e. one full leading below the last line's baseline, so the next element never overlaps it
    // (this must NOT add "leading" back, since that would return the last line's own baseline).
    private float drawWrappedText(PDPageContentStream cs, String text, PDFont font, float fontSize, float x, float y, float maxWidth, float leading) throws IOException {
        List<String> lines = wrap(text, font, fontSize, maxWidth);
        cs.setFont(font, fontSize);
        cs.beginText();
        cs.newLineAtOffset(x, y);
        float cursor = y;
        for (String line : lines) {
            cs.showText(line);
            cs.newLineAtOffset(0, -leading);
            cursor -= leading;
        }
        cs.endText();
        return cursor;
    }
}
