package com.smartknowledgehub.model;

public class Citation {
    private String sourceType;
    private String sourceRef;
    private String snippet;

    public Citation() {
    }

    public Citation(String sourceType, String sourceRef, String snippet) {
        this.sourceType = sourceType;
        this.sourceRef = sourceRef;
        this.snippet = snippet;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }
}
