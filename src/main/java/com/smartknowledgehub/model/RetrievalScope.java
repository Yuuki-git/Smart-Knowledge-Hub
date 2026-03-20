package com.smartknowledgehub.model;

public class RetrievalScope {
    private String documentId;
    private String fileName;
    private String className;
    private String methodName;

    public static RetrievalScope empty() {
        return new RetrievalScope();
    }

    public boolean isEmpty() {
        return isBlank(documentId)
                && isBlank(fileName)
                && isBlank(className)
                && isBlank(methodName);
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
