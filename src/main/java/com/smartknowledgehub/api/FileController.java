package com.smartknowledgehub.api;

import com.smartknowledgehub.model.UploadResponse;
import com.smartknowledgehub.service.IngestionService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Validated
public class FileController {
    private final IngestionService ingestionService;

    public FileController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<UploadResponse> upload(@RequestPart("file") FilePart file) {
        // 上传后触发解析与索引
        return ingestionService.ingest(file);
    }

    @GetMapping("/ingestion/{jobId}")
    public Mono<Map<String, String>> status(@PathVariable @NotBlank String jobId) {
        // 查询入库任务状态
        return ingestionService.status(jobId)
                .map(status -> Map.of("jobId", jobId, "status", status))
                .defaultIfEmpty(Map.of("jobId", jobId, "status", "NOT_FOUND"));
    }
}
