package com.smartknowledgehub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.persistence.postgres")
public class PostgresPersistenceProperties {
    // 是否启用 PostgreSQL 元数据持久化
    private boolean enabled = false;
    // JDBC 连接地址
    private String url = "jdbc:postgresql://localhost:5432/smart_knowledge_hub";
    // 数据库用户名
    private String username = "postgres";
    // 数据库密码
    private String password = "postgres";
    // 启动时是否自动建表
    private boolean initSchema = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isInitSchema() {
        return initSchema;
    }

    public void setInitSchema(boolean initSchema) {
        this.initSchema = initSchema;
    }
}
