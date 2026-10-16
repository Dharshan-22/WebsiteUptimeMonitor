package com.uptimemonitor.service;

import com.uptimemonitor.model.CheckResult;
import org.springframework.stereotype.Service;

import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Core service that validates URLs, blocks dangerous destinations, and
 * performs the actual HTTP check with timing.
 *
 * <h3>Security – redirect handling</h3>
 * Phase 1 used {@code HttpClient.Redirect.NORMAL}, which followed redirects
 * silently. This meant an attacker who owns a public domain could redirect it
 * to a private IP and bypass the validation. Phase 2 fixes this by:
 * <ol>
 *   <li>Setting the client to {@code Redirect.NEVER}.</li>
 *   <li>Manually following redirects (up to {@code MAX_REDIRECTS} hops).</li>
 *   <li>Re-running {@code validateHostSecurity()} on every redirect target
 *       before issuing the next request.</li>
 * </ol>
 */
@Service
public class UptimeCheckerService {

    // Connection and read timeout (seconds)
    private static final int TIMEOUT_SECONDS = 10;

    // Maximum number of redirects to follow before giving up
    private static final int MAX_REDIRECTS = 5;

    // HTTP status code descriptions (most common ones)
    private static final Map<Integer, String> STATUS_TEXTS = new HashMap<>();

    static {
        STATUS_TEXTS.put(200, "OK");
        STATUS_TEXTS.put(201, "Created");
        STATUS_TEXTS.put(204, "No Content");
        STATUS_TEXTS.put(301, "Moved Permanently");
        STATUS_TEXTS.put(302, "Found");
        STATUS_TEXTS.put(303, "See Other");
        STATUS_TEXTS.put(304, "Not Modified");
        STATUS_TEXTS.put(307, "Temporary Redirect");
        STATUS_TEXTS.put(308, "Permanent Redirect");
        STATUS_TEXTS.put(400, "Bad Request");
        STATUS_TEXTS.put(401, "Unauthorized");
        STATUS_TEXTS.put(403, "Forbidden");
        STATUS_TEXTS.put(404, "Not Found");
        STATUS_TEXTS.put(405, "Method Not Allowed");
        STATUS_TEXTS.put(408, "Request Timeout");
        STATUS_TEXTS.put(429, "Too Many Requests");
        STATUS_TEXTS.put(500, "Internal Server Error");
        STATUS_TEXTS.put(502, "Bad Gateway");
        STATUS_TEXTS.put(503, "Service Unavailable");
        STATUS_TEXTS.put(504, "Gateway Timeout");
    }

    // -------------------------------------------------------------------------
    //  Public API
    // -------------------------------------------------------------------------

    /**
     * Validates the raw URL string and returns a normalised, safe URL string.
     * Returns {@code null} and populates {@code errorOut[0]} with a message
     * when validation fails.
     *
     * <p>Used by the dashboard to validate a URL before adding a site,
     * without performing a live HTTP check.</p>
     *
     * @param rawUrl   the URL submitted by the user
     * @param errorOut single-element array; errorOut[0] is set on failure
     * @return the normalised URL, or {@code null} on error
     */
    public String validateUrl(String rawUrl, String[] errorOut) {
        if (rawUrl == null || rawUrl.isBlank()) {
            errorOut[0] = "URL must not be empty.";
            return null;
        }

        String trimmed = rawUrl.trim();

        // Auto-prepend https:// if no scheme is present
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }

        // Parse
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            errorOut[0] = "Invalid URL format: " + e.getReason();
            return null;
        }

        // Scheme check
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            errorOut[0] = "Only http:// and https:// URLs are supported.";
            return null;
        }

        // Host check
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            errorOut[0] = "URL is missing a valid hostname.";
            return null;
        }

        // Security: block private / local destinations on the original host
        try {
            validateHostSecurity(host);
        } catch (SecurityException e) {
            errorOut[0] = e.getMessage();
            return null;
        }

        return trimmed;
    }

    /**
     * Validates and checks the given URL (Phase 1 entry point, still used by
     * {@link com.uptimemonitor.controller.UptimeController}).
     *
     * @param rawUrl the URL string submitted by the user
     * @return a {@link CheckResult} describing the outcome
     */
    public CheckResult check(String rawUrl) {
        String[] errorOut = new String[1];
        String url = validateUrl(rawUrl, errorOut);
        if (url == null) {
            return CheckResult.error(rawUrl, errorOut[0]);
        }
        return performCheck(url);
    }

    // -------------------------------------------------------------------------
    //  Security validation
    // -------------------------------------------------------------------------

    /**
     * Throws {@link SecurityException} when the host resolves to a private,
     * loopback, link-local, or site-local address, or is an explicit localhost
     * hostname/alias.
     *
     * <p>This method is intentionally package-private so that
     * {@link #performCheck} can call it again after each redirect.</p>
     */
    void validateHostSecurity(String host) {
        // Block by hostname before DNS resolution
        String lower = host.toLowerCase();
        if (lower.equals("localhost") || lower.endsWith(".localhost") ||
            lower.equals("local")     || lower.endsWith(".local")) {
            throw new SecurityException("Requests to localhost and .local domains are not allowed.");
        }

        // Resolve and inspect every returned address
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new SecurityException("Could not resolve hostname: " + host);
        }

        for (InetAddress addr : addresses) {
            if (addr.isLoopbackAddress()) {
                throw new SecurityException("Requests to loopback addresses are not allowed.");
            }
            if (addr.isSiteLocalAddress()) {
                throw new SecurityException(
                    "Requests to private/internal network addresses are not allowed (RFC-1918).");
            }
            if (addr.isLinkLocalAddress()) {
                throw new SecurityException("Requests to link-local addresses are not allowed.");
            }
            if (addr.isAnyLocalAddress()) {
                throw new SecurityException("Requests to wildcard addresses are not allowed.");
            }
            // Explicitly block 0.0.0.0 and :: even if isAnyLocalAddress() misses them
            String ip = addr.getHostAddress();
            if (ip.equals("0.0.0.0") || ip.equals("::") || ip.equals("0:0:0:0:0:0:0:0")) {
                throw new SecurityException("Requests to the unspecified address are not allowed.");
            }
        }
    }

    // -------------------------------------------------------------------------
    //  HTTP check (with manual redirect loop to preserve SSRF protection)
    // -------------------------------------------------------------------------

    /**
     * Performs the actual HTTP GET, timing the round trip.
     *
     * <p>Redirects are followed manually (up to {@link #MAX_REDIRECTS} hops)
     * so that every redirect target is validated by
     * {@link #validateHostSecurity} before the next request is issued.
     * This prevents SSRF via open redirects on trusted domains.</p>
     */
    public CheckResult performCheck(String startUrl) {
        // Build a client that does NOT follow redirects automatically
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

        String currentUrl = startUrl;
        long totalStart = System.currentTimeMillis();

        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {

            HttpRequest request;
            try {
                request = HttpRequest.newBuilder()
                    .uri(URI.create(currentUrl))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .header("User-Agent", "UptimeMonitor/1.0")
                    .build();
            } catch (IllegalArgumentException e) {
                return CheckResult.error(startUrl, "Malformed redirect URL: " + currentUrl);
            }

            HttpResponse<Void> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (java.net.http.HttpTimeoutException e) {
                return CheckResult.error(startUrl,
                    "Connection timed out after " + TIMEOUT_SECONDS + " seconds.");
            } catch (java.io.IOException e) {
                String msg = e.getMessage() != null ? e.getMessage() : "I/O error during connection.";
                msg = msg.replaceFirst("^.*Exception.*?: ", "");
                return CheckResult.error(startUrl, "Connection failed: " + msg);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CheckResult.error(startUrl, "Check was interrupted.");
            } catch (Exception e) {
                return CheckResult.error(startUrl, "Unexpected error: " + e.getMessage());
            }

            int code = response.statusCode();

            // Is this a redirect?
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                if (hop == MAX_REDIRECTS) {
                    return CheckResult.error(startUrl,
                        "Too many redirects (max " + MAX_REDIRECTS + ").");
                }

                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null || location.isBlank()) {
                    return CheckResult.error(startUrl, "Redirect response missing Location header.");
                }

                // Resolve relative redirects against the current URL
                try {
                    URI resolved = URI.create(currentUrl).resolve(location);
                    location = resolved.toString();
                } catch (IllegalArgumentException e) {
                    return CheckResult.error(startUrl, "Invalid redirect location: " + location);
                }

                // *** SSRF check: validate the redirect target before following ***
                URI redirectUri;
                try {
                    redirectUri = new URI(location);
                } catch (URISyntaxException e) {
                    return CheckResult.error(startUrl, "Malformed redirect URL: " + location);
                }

                String redirectHost = redirectUri.getHost();
                if (redirectHost == null || redirectHost.isBlank()) {
                    return CheckResult.error(startUrl,
                        "Redirect target has no valid hostname: " + location);
                }

                try {
                    validateHostSecurity(redirectHost);
                } catch (SecurityException e) {
                    return CheckResult.error(startUrl,
                        "Redirect blocked – " + e.getMessage() + " (target: " + location + ")");
                }

                currentUrl = location;
                continue; // follow the redirect
            }

            // Non-redirect: record and return result
            long elapsed = System.currentTimeMillis() - totalStart;
            String codeText = STATUS_TEXTS.getOrDefault(code, "Unknown");
            return CheckResult.success(startUrl, code, codeText, elapsed);
        }

        // Should never reach here
        return CheckResult.error(startUrl, "Redirect loop detected.");
    }
}
