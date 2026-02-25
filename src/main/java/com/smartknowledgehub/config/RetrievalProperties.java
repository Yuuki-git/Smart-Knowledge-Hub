package com.smartknowledgehub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.retrieval")
public class RetrievalProperties {
    // 默认返回的 topK 数量
    private int topK = 5;
    // 相似度阈值，低于该值将被过滤
    private double similarityThreshold = 0.7;
    // RRF 融合参数
    private int rrfK = 60;

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public int getRrfK() {
        return rrfK;
    }

    public void setRrfK(int rrfK) {
        this.rrfK = rrfK;
    }
}
