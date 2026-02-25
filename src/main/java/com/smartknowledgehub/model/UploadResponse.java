package com.smartknowledgehub.model;

public class UploadResponse {
    private String documentId;
    private String jobId;
    private String status;

    public UploadResponse() {
    }

    public UploadResponse(String documentId, String jobId, String status) {
        this.documentId = documentId;
        this.jobId = jobId;
        this.status = status;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
