package com.smartknowledgehub.model;

public class ChunkSource {
    private String fileName;
    private Integer pageNumber;
    private String className;
    private String methodName;

    public ChunkSource() {
    }

    public ChunkSource(String fileName, Integer pageNumber, String className, String methodName) {
        this.fileName = fileName;
        this.pageNumber = pageNumber;
        this.className = className;
        this.methodName = methodName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
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
}
