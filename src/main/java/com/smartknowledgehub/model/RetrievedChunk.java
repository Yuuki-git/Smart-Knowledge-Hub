package com.smartknowledgehub.model;

public class RetrievedChunk {
    private String id;
    private String text;
    private double score;
    private ChunkSource source;

    public RetrievedChunk() {
    }

    public RetrievedChunk(String id, String text, double score, ChunkSource source) {
        this.id = id;
        this.text = text;
        this.score = score;
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

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public ChunkSource getSource() {
        return source;
    }

    public void setSource(ChunkSource source) {
        this.source = source;
    }
}
