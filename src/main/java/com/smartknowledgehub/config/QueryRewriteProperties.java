package com.smartknowledgehub.config;

import com.smartknowledgehub.model.ModelProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rewrite")
public class QueryRewriteProperties {
    private boolean enabled = false;
    private ModelProvider provider = ModelProvider.AUTO;
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
