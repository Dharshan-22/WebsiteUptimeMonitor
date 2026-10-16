package com.uptimemonitor.service;

import com.uptimemonitor.model.CheckHistoryItem;
import com.uptimemonitor.model.CheckResult;
import com.uptimemonitor.model.MonitoredSite;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Writes and reads check results to/from the {@code check_history} table.
 *
 * <p>Every time the user clicks Check Now, {@link com.uptimemonitor.controller.DashboardController} calls
 * {@link #save} to persist the result. {@link #loadLastResultInto} is called
 * when the dashboard loads to hydrate each {@link MonitoredSite} with its most
 * recent check result so the UI can display UP/DOWN status, HTTP code and
 * response time.</p>
 *
 * <p>Phase 4 adds {@link #findAllRecent} and {@link #findBySiteId} to support
 * displaying historical checks ordered with the most recent checks first.</p>
 */
@Repository
public class CheckHistoryRepository {

    private final JdbcTemplate jdbc;

    public CheckHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── RowMapper for history items ──────────────────────────────────────────

    private static final RowMapper<CheckHistoryItem> HISTORY_MAPPER = (rs, rowNum) -> {
        CheckHistoryItem item = new CheckHistoryItem();
        item.setId(rs.getLong("id"));
        item.setSiteId(rs.getString("site_id"));
        item.setSiteName(rs.getString("site_name"));
        item.setUrl(rs.getString("url"));
        item.setCheckedAt(rs.getObject("checked_at", LocalDateTime.class));
        item.setStatus(rs.getString("status"));
        item.setHttpStatusCode(rs.getInt("http_status_code"));
        item.setHttpStatusText(rs.getString("http_status_text"));
        item.setResponseTimeMs(rs.getLong("response_time_ms"));
        item.setErrorMessage(rs.getString("error_message"));
        return item;
    };

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Inserts one row into {@code check_history} for the given site and result.
     *
     * @param siteId    the UUID of the monitored site
     * @param checkedAt the exact moment the check was performed
     * @param result    the {@link CheckResult} returned by UptimeCheckerService
     */
    public void save(String siteId, LocalDateTime checkedAt, CheckResult result) {
        jdbc.update(
            """
            INSERT INTO check_history
                (site_id, checked_at, status, http_status_code,
                 http_status_text, response_time_ms, error_message)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            siteId,
            checkedAt,
            result.getStatus(),
            result.getHttpStatusCode(),
            result.getHttpStatusText(),
            result.getResponseTimeMs(),
            result.getErrorMessage()   // NULL when no error occurred
        );
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Loads the most recent check result for the given site and applies it
     * to the in-memory {@link MonitoredSite} object so the dashboard can
     * display UP/DOWN, HTTP code, and response time.
     *
     * <p>If the site has never been checked the method returns without
     * modifying the site object (lastResult stays {@code null}, which the
     * template already handles with "Not checked yet").</p>
     *
     * @param site the site to hydrate with its latest result
     */
    public void loadLastResultInto(MonitoredSite site) {
        List<CheckResult> rows = jdbc.query(
            """
            SELECT status, http_status_code, http_status_text,
                   response_time_ms, error_message, checked_at
            FROM   check_history
            WHERE  site_id = ?
            ORDER  BY checked_at DESC
            LIMIT  1
            """,
            (rs, rowNum) -> {
                CheckResult r = new CheckResult();
                r.setUrl(site.getUrl());
                r.setStatus(rs.getString("status"));
                r.setHttpStatusCode(rs.getInt("http_status_code"));
                r.setHttpStatusText(rs.getString("http_status_text"));
                r.setResponseTimeMs(rs.getLong("response_time_ms"));
                r.setErrorMessage(rs.getString("error_message"));
                return r;
            },
            site.getId()
        );

        if (!rows.isEmpty()) {
            site.setLastResult(rows.get(0));

            LocalDateTime checkedAt = jdbc.queryForObject(
                "SELECT checked_at FROM check_history WHERE site_id = ? ORDER BY checked_at DESC LIMIT 1",
                LocalDateTime.class,
                site.getId()
            );
            site.setLastCheckedAt(checkedAt);
        }
    }

    /**
     * Fetches recent check history across all monitored websites,
     * ordered with the most recent checks first.
     */
    public List<CheckHistoryItem> findAllRecent() {
        String sql = """
            SELECT h.id, h.site_id, s.name AS site_name, s.url,
                   h.checked_at, h.status, h.http_status_code,
                   h.http_status_text, h.response_time_ms, h.error_message
            FROM check_history h
            JOIN monitored_sites s ON h.site_id = s.id
            ORDER BY h.checked_at DESC
            LIMIT 100
            """;
        return jdbc.query(sql, HISTORY_MAPPER);
    }

    /**
     * Fetches check history for a specific website,
     * ordered with the most recent checks first.
     *
     * @param siteId the unique identifier of the monitored site
     */
    public List<CheckHistoryItem> findBySiteId(String siteId) {
        String sql = """
            SELECT h.id, h.site_id, s.name AS site_name, s.url,
                   h.checked_at, h.status, h.http_status_code,
                   h.http_status_text, h.response_time_ms, h.error_message
            FROM check_history h
            JOIN monitored_sites s ON h.site_id = s.id
            WHERE h.site_id = ?
            ORDER BY h.checked_at DESC
            LIMIT 100
            """;
        return jdbc.query(sql, HISTORY_MAPPER, siteId);
    }

    /**
     * Returns how many check records exist for the given site.
     * Useful for debugging and history metrics.
     */
    public int countBySiteId(String siteId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM check_history WHERE site_id = ?",
            Integer.class,
            siteId
        );
        return count != null ? count : 0;
    }
}
