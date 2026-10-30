package com.scanms.ops;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Cấu hình cho thông tin tiến trình và bộ đệm log, tiền tố {@code scanms.ops}. */
@ConfigurationProperties("scanms.ops")
public class OpsProperties {
    private boolean enabled = true;
    /** Tên hiển thị của service; mặc định lấy {@code spring.application.name}. */
    private String serviceName;
    /** Số dòng log gần nhất giữ trong bộ nhớ. */
    private int logBufferSize = 300;
    /** Số lỗi ERROR gần nhất (kèm stack trace) giữ trong bộ nhớ. */
    private int errorBufferSize = 20;
    private int maxStackLines = 30;
    private int maxStackChars = 4096;
    private int maxMessageChars = 2000;
    /** Role Keycloak được xem thông tin và log. */
    private List<String> allowedRoles = List.of("ADMIN", "SYSTEM_ADMIN");

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public int getLogBufferSize() { return logBufferSize; }
    public void setLogBufferSize(int logBufferSize) { this.logBufferSize = logBufferSize; }
    public int getErrorBufferSize() { return errorBufferSize; }
    public void setErrorBufferSize(int errorBufferSize) { this.errorBufferSize = errorBufferSize; }
    public int getMaxStackLines() { return maxStackLines; }
    public void setMaxStackLines(int maxStackLines) { this.maxStackLines = maxStackLines; }
    public int getMaxStackChars() { return maxStackChars; }
    public void setMaxStackChars(int maxStackChars) { this.maxStackChars = maxStackChars; }
    public int getMaxMessageChars() { return maxMessageChars; }
    public void setMaxMessageChars(int maxMessageChars) { this.maxMessageChars = maxMessageChars; }
    public List<String> getAllowedRoles() { return allowedRoles; }
    public void setAllowedRoles(List<String> allowedRoles) { this.allowedRoles = allowedRoles; }
}
