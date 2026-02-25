package com.smartknowledgehub.config;

import com.smartknowledgehub.model.ModelProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rewrite")
public class QueryRewriteProperties {
    // 是否启用查询改写
    private boolean enabled = false;
    // 选择改写模型
    private ModelProvider provider = ModelProvider.AUTO;
    // 改写后的最大长度
    private int maxLength = 256;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ModelProvider getProvider() {
        return provider;
    }

    public void setProvider(ModelProvider provider) {
        this.provider = provider;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }
}
