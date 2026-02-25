package com.smartknowledgehub.model;

import java.util.HashMap;
import java.util.Map;

public class ChunkPayload {
    private String id;
    private String text;
    private ChunkSource source;
    private Map<String, Object> attributes = new HashMap<>();

    public ChunkPayload() {
    }

    public ChunkPayload(String id, String text, ChunkSource source) {
        this.id = id;
        this.text = text;
        this.source = source;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public ChunkSource getSource() {
        return source;
    }

    public void setSource(ChunkSource source) {
        this.source = source;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}
