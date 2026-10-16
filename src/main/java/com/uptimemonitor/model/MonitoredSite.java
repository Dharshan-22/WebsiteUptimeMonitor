package com.uptimemonitor.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a website that the user is monitoring.
 * Holds the site's identity (id, name, url) and the result of the most
 * recent check.  All fields are intentionally simple – no JPA annotations
 * because we use in-memory storage only in Phase 2.
 */
public class MonitoredSite {

    /** Stable identifier used in URLs (remove / check-now buttons). */
    private final String id;

    /** Human-readable display name chosen by the user. */
    private String name;

    /** The canonical URL to monitor (always has http/https scheme). */
    private String url;

    /** Result of the most recent check; null until first check is run. */
    private CheckResult lastResult;

    /** When the site was added. */
    private final LocalDateTime addedAt;

    /** When the last check was performed; null if never checked. */
    private LocalDateTime lastCheckedAt;

    public MonitoredSite(String name, String url) {
        this.id        = UUID.randomUUID().toString();
        this.name      = name;
        this.url       = url;
        this.addedAt   = LocalDateTime.now();
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getId()                   { return id; }
    public String getName()                 { return name; }
    public String getUrl()                  { return url; }
    public CheckResult getLastResult()      { return lastResult; }
    public LocalDateTime getAddedAt()       { return addedAt; }
    public LocalDateTime getLastCheckedAt() { return lastCheckedAt; }

    /** Used when reconstructing a site from a database row (id and addedAt already exist). */
    public MonitoredSite(String id, String name, String url, LocalDateTime addedAt) {
        this.id      = id;
        this.name    = name;
        this.url     = url;
        this.addedAt = addedAt;
    }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setName(String name)                         { this.name = name; }
    public void setUrl(String url)                           { this.url  = url; }
    public void setLastResult(CheckResult lastResult)        { this.lastResult = lastResult; }
    public void setLastCheckedAt(LocalDateTime lastCheckedAt){ this.lastCheckedAt = lastCheckedAt; }

    // ── Convenience helpers used in Thymeleaf ────────────────────────────────

    /** Returns true when at least one check has been run. */
    public boolean isChecked() {
        return lastResult != null;
    }

    /** Returns "UP", "DOWN", or "UNKNOWN" (never checked yet). */
    public String getStatusLabel() {
        if (lastResult == null) return "UNKNOWN";
        return lastResult.getStatus();
    }
}
