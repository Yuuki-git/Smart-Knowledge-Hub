package com.smartknowledgehub.model;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class IndexRequest {
    @NotEmpty
    private List<ChunkPayload> chunks;

    public List<ChunkPayload> getChunks() {
        return chunks;
    }

    public void setChunks(List<ChunkPayload> chunks) {
        this.chunks = chunks;
    }
}
