package com.smartknowledgehub.model;

import java.util.Collections;
import java.util.List;

public class ChatChunk {
    private String type;
    private String content;
    private List<Citation> citations;
    private boolean done;

    public ChatChunk() {
    }

    public ChatChunk(String type, String content, List<Citation> citations, boolean done) {
        this.type = type;
        this.content = content;
        this.citations = citations;
        this.done = done;
    }

    public static ChatChunk delta(String content) {
        return new ChatChunk("delta", content, Collections.emptyList(), false);
    }

    public static ChatChunk finalChunk(String content, List<Citation> citations) {
        return new ChatChunk("final", content, citations, true);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<Citation> getCitations() {
        return citations;
    }

    public void setCitations(List<Citation> citations) {
        this.citations = citations;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }
}
