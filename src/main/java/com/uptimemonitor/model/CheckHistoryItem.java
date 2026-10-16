package com.uptimemonitor.model;

import java.time.LocalDateTime;

/**
 * Represents a single record from the {@code check_history} table,
 * enriched with website name and URL from {@code monitored_sites}.
 */
public class CheckHistoryItem {

    private Long id;
    private String siteId;
    private String siteName;
    private String url;
    private LocalDateTime checkedAt;
    private String status;              // "UP" or "DOWN"
    private int httpStatusCode;         // e.g. 200, 404, 0
    private String httpStatusText;      // e.g. "OK", "Not Found", "N/A"
    private long responseTimeMs;        // round-trip ms
    private String errorMessage;        // null if check was successful

    public CheckHistoryItem() {}

    public CheckHistoryItem(Long id, String siteId, String siteName, String url,
                            LocalDateTime checkedAt, String status, int httpStatusCode,
                            String httpStatusText, long responseTimeMs, String errorMessage) {
        this.id = id;
        this.siteId = siteId;
        this.siteName = siteName;
        this.url = url;
        this.checkedAt = checkedAt;
        this.status = status;
        this.httpStatusCode = httpStatusCode;
        this.httpStatusText = httpStatusText;
        this.responseTimeMs = responseTimeMs;
        this.errorMessage = errorMessage;
    }

    // ── Getters and Setters ──────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(int httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public String getHttpStatusText() {
        return httpStatusText;
    }

    public void setHttpStatusText(String httpStatusText) {
        this.httpStatusText = httpStatusText;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean isUp() {
        return "UP".equalsIgnoreCase(status);
    }
}
