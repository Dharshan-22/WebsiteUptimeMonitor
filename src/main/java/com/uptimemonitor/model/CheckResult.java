package com.uptimemonitor.model;

/**
 * Represents the result of a single URL check.
 */
public class CheckResult {

    private String url;
    private String status;          // "UP" or "DOWN"
    private int httpStatusCode;     // e.g. 200, 404, 0 if connection failed
    private String httpStatusText;  // e.g. "OK", "Not Found", "Connection Refused"
    private long responseTimeMs;    // round-trip time in milliseconds
    private String errorMessage;    // populated only when an error occurs

    public CheckResult() {}

    // ----- Getters and Setters -----

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getHttpStatusCode() { return httpStatusCode; }
    public void setHttpStatusCode(int httpStatusCode) { this.httpStatusCode = httpStatusCode; }

    public String getHttpStatusText() { return httpStatusText; }
    public void setHttpStatusText(String httpStatusText) { this.httpStatusText = httpStatusText; }

    public long getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(long responseTimeMs) { this.responseTimeMs = responseTimeMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    // ----- Convenience factory methods -----

    public static CheckResult success(String url, int code, String codeText, long responseMs) {
        CheckResult r = new CheckResult();
        r.url = url;
        r.status = (code >= 100 && code < 500) ? "UP" : "DOWN";
        r.httpStatusCode = code;
        r.httpStatusText = codeText;
        r.responseTimeMs = responseMs;
        return r;
    }

    public static CheckResult error(String url, String message) {
        CheckResult r = new CheckResult();
        r.url = url;
        r.status = "DOWN";
        r.httpStatusCode = 0;
        r.httpStatusText = "N/A";
        r.responseTimeMs = 0;
        r.errorMessage = message;
        return r;
    }
}
