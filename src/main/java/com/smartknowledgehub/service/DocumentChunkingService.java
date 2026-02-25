package com.smartknowledgehub.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.smartknowledgehub.model.ChunkPayload;
import com.smartknowledgehub.model.ChunkSource;
import com.smartknowledgehub.model.MetadataKeys;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentChunkingService {
    private static final Logger log = LoggerFactory.getLogger(DocumentChunkingService.class);
    // 每个 chunk 的最大字符数
    private static final int MAX_CHARS = 1200;
    // 段落重叠数量，避免上下文断裂
    private static final int OVERLAP_PARAGRAPHS = 1;

    private final Tika tika = new Tika();

    // Tika 解析可能抛出 TikaException，统一上抛由调用方处理
    public List<ChunkPayload> chunk(Path path, String documentId, String originalName) throws IOException, TikaException {
        String fileName = originalName != null ? originalName : path.getFileName().toString();
        String extension = extensionOf(fileName);
        return switch (extension) {
            case "pdf" -> chunkPdf(path, documentId, fileName);
            case "md", "markdown" -> chunkMarkdown(readText(path), documentId, fileName);
            case "java" -> chunkJava(readText(path), documentId, fileName);
            default -> chunkPlainText(extractWithTika(path), documentId, fileName);
        };
    }

    private List<ChunkPayload> chunkPdf(Path path, String documentId, String fileName) throws IOException {
        // PDF 按页切片，保留页码用于引用
        List<ChunkPayload> chunks = new ArrayList<>();
        try (PDDocument document = PDDocument.load(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int pages = document.getNumberOfPages();
            int index = 0;
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);
                if (pageText == null || pageText.isBlank()) {
                    continue;
                }
                ChunkSource source = new ChunkSource(fileName, page, null, null);
                chunks.addAll(buildChunks(pageText, documentId, source, index));
                index = chunks.size();
            }
        }
        return chunks;
    }

    private List<ChunkPayload> chunkMarkdown(String text, String documentId, String fileName) {
        // Markdown 按标题分段
        List<ChunkPayload> chunks = new ArrayList<>();
        List<String> sections = splitMarkdownSections(text);
        int index = 0;
        for (String section : sections) {
            ChunkSource source = new ChunkSource(fileName, null, null, null);
            List<ChunkPayload> sectionChunks = buildChunks(section, documentId, source, index);
            chunks.addAll(sectionChunks);
            index = chunks.size();
        }
        return chunks;
    }

    private List<ChunkPayload> chunkJava(String code, String documentId, String fileName) {
        // Java 代码按类/方法结构切片
        List<ChunkPayload> chunks = new ArrayList<>();
        try {
            CompilationUnit unit = StaticJavaParser.parse(code);
            int index = 0;
            for (ClassOrInterfaceDeclaration declaration : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                String className = declaration.getNameAsString();
                List<MethodDeclaration> methods = declaration.getMethods();
                List<ConstructorDeclaration> constructors = declaration.getConstructors();
                if (methods.isEmpty() && constructors.isEmpty()) {
                    ChunkSource source = new ChunkSource(fileName, null, className, null);
                    chunks.addAll(buildChunks(declaration.toString(), documentId, source, index));
                    index = chunks.size();
                    continue;
                }
                for (MethodDeclaration method : methods) {
                    ChunkSource source = new ChunkSource(fileName, null, className, method.getNameAsString());
                    chunks.add(makeChunk(documentId, source, method.toString(), index++));
                }
                for (ConstructorDeclaration constructor : constructors) {
                    ChunkSource source = new ChunkSource(fileName, null, className, constructor.getNameAsString());
                    chunks.add(makeChunk(documentId, source, constructor.toString(), index++));
                }
            }
        } catch (Exception ex) {
            // 解析失败时退化为纯文本切片
            log.warn("Java parse failed, fallback to plain text. file={}", fileName, ex);
            return chunkPlainText(code, documentId, fileName);
        }
        if (chunks.isEmpty()) {
            return chunkPlainText(code, documentId, fileName);
        }
        return chunks;
    }

    private List<ChunkPayload> chunkPlainText(String text, String documentId, String fileName) {
        ChunkSource source = new ChunkSource(fileName, null, null, null);
        return buildChunks(text, documentId, source, 0);
    }

    private List<ChunkPayload> buildChunks(String text, String documentId, ChunkSource source, int startIndex) {
        // 将文本拆为段落窗口，按长度聚合为 chunk
        List<String> paragraphs = splitParagraphs(text);
        List<ChunkPayload> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int index = startIndex;
        List<String> window = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (current.length() + paragraph.length() + 2 > MAX_CHARS && current.length() > 0) {
                chunks.add(makeChunk(documentId, source, current.toString(), index++));
                List<String> overlap = overlapParagraphs(window);
                current.setLength(0);
                for (String item : overlap) {
                    current.append(item).append("\n\n");
                }
                window = new ArrayList<>(overlap);
            }
            current.append(paragraph).append("\n\n");
            window.add(paragraph);
        }
        if (current.length() > 0) {
            chunks.add(makeChunk(documentId, source, current.toString(), index));
        }
        return chunks;
    }

    private List<String> splitParagraphs(String text) {
        List<String> paragraphs = new ArrayList<>();
        if (text == null) {
            return paragraphs;
        }
        for (String part : text.split("\\R\\s*\\R")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                paragraphs.add(trimmed);
            }
        }
        if (paragraphs.isEmpty() && text != null && !text.isBlank()) {
            paragraphs.add(text.trim());
        }
        return paragraphs;
    }

    private List<String> splitMarkdownSections(String text) {
        List<String> sections = new ArrayList<>();
        if (text == null) {
            return sections;
        }
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\\R")) {
            if (line.startsWith("#")) {
                if (current.length() > 0) {
                    sections.add(current.toString());
                    current.setLength(0);
                }
            }
            current.append(line).append("\n");
        }
        if (current.length() > 0) {
            sections.add(current.toString());
        }
        if (sections.isEmpty()) {
            sections.add(text);
        }
        return sections;
    }

    private List<String> overlapParagraphs(List<String> window) {
        if (window.isEmpty() || OVERLAP_PARAGRAPHS <= 0) {
            return List.of();
        }
        int start = Math.max(0, window.size() - OVERLAP_PARAGRAPHS);
        return window.subList(start, window.size());
    }

    private ChunkPayload makeChunk(String documentId, ChunkSource source, String text, int index) {
        ChunkPayload payload = new ChunkPayload();
        payload.setId(UUID.randomUUID().toString());
        payload.setText(text.trim());
        payload.setSource(source);
        payload.getAttributes().put(MetadataKeys.DOCUMENT_ID, documentId);
        payload.getAttributes().put(MetadataKeys.CHUNK_INDEX, index);
        return payload;
    }

    // 交给上层统一处理异常，避免在底层堆叠 try-catch
    private String extractWithTika(Path path) throws IOException, TikaException {
        return tika.parseToString(path.toFile());
    }

    private String readText(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private String extensionOf(String name) {
        int idx = name.lastIndexOf('.');
        if (idx == -1) {
            return "";
        }
        return name.substring(idx + 1).toLowerCase(Locale.ROOT);
    }
}
