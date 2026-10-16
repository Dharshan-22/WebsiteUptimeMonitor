# Website Uptime Monitor

A robust, full-stack website availability and performance monitoring application built with **Java 17**, **Spring Boot 3**, and **MySQL**. The application enables users to verify the reachability of public websites, track response latency, inspect HTTP status codes, and maintain a persistent audit trail of monitoring checks through a modern, responsive web dashboard.

---

## Table of Contents

- [Problem Statement](#problem-statement)
- [Key Features](#key-features)
- [Technologies Used](#technologies-used)
- [System Architecture](#system-architecture)
- [Project Directory Structure](#project-directory-structure)
- [Security & SSRF Mitigation](#security--ssrf-mitigation)
- [Database Setup](#database-setup)
- [Configuration](#configuration)
- [How to Run the Application](#how-to-run-the-application)
  - [Prerequisites](#prerequisites)
  - [Run with Maven](#run-with-maven)
  - [Build and Run Standalone JAR](#build-and-run-standalone-jar)
- [Accessing the Application](#accessing-the-application)
- [Testing Performed](#testing-performed)
- [Known Limitations](#known-limitations)

---

## Problem Statement

Unplanned website outages and slow response times directly impact user trust, operational reliability, and business revenue. Developers and system administrators require a fast, intuitive tool to check whether public endpoints are operational without setting up bloated enterprise monitoring suites. 

Furthermore, naive uptime checkers that accept arbitrary URLs can expose internal infrastructure to **Server-Side Request Forgery (SSRF)** attacks, allowing attackers to probe internal networks, loopback interfaces, or cloud metadata services. 

This project solves both challenges: providing an accessible, database-backed multi-website uptime dashboard with built-in, multi-layered SSRF defenses and response-time measurement.

---

## Key Features

- **Ad-Hoc Quick Check**: Instant single-URL availability verification from the home page without requiring registration or saving.
- **Multi-Website Dashboard**: Add and track multiple websites simultaneously with user-defined display names and target URLs.
- **On-Demand Health Checking**: Execute immediate HTTP health checks via "Check Now" buttons with visual loading indicators.
- **Live Performance Metrics**: Real-time measurement and display of:
  - Availability state (`UP` or `DOWN`)
  - HTTP status codes (e.g., `200`, `301`, `404`, `500`)
  - HTTP status text (e.g., `OK`, `Moved Permanently`, `Bad Gateway`)
  - Round-trip response time in milliseconds (`ms`)
- **Persistent Monitoring History**: Every check result is permanently logged to MySQL with a high-precision timestamp, status code, latency, and descriptive error messages if a check fails.
- **Per-Site History Filtering**: Filter historical logs by specific website or view all recent checks across all sites in chronological order (most recent first).
- **Graceful Lifecycle Management**: Deleting a monitored website automatically cleans up all associated historical records via database-level cascading deletion (`ON DELETE CASCADE`).
- **Enterprise-Grade SSRF Protection**:
  - Rejects `localhost`, `*.localhost`, and `*.local` domains.
  - Blocks IPv4 and IPv6 loopback addresses (`127.0.0.1`, `::1`).
  - Blocks RFC 1918 private network subnets (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`).
  - Blocks link-local addresses (`169.254.0.0/16`, `fe80::/10`) and unspecified/wildcard addresses (`0.0.0.0`, `::`).
  - **Redirect Protection**: Follows redirects manually (up to 5 hops) and re-evaluates security rules on each hop to prevent open-redirect SSRF bypasses.
- **Modern Dark UI**: Built with pure CSS3 and semantic HTML5, featuring a clean dark theme, status pill indicators, and zero external frontend dependencies.

---

## Technologies Used

| Technology | Version | Purpose in Project |
|---|---|---|
| **Java** | 17 (LTS) | Core backend programming language; uses modern features like text blocks and `java.net.http.HttpClient`. |
| **Spring Boot** | 3.2.5 | Application framework providing embedded Tomcat, dependency injection, and MVC routing. |
| **Spring JDBC** | 3.2.5 | Lightweight data access using `JdbcTemplate` for thread-safe, transparent SQL operations without heavy ORM overhead. |
| **MySQL** | 8.0+ | Relational database management system for persistent storage of websites and check histories. |
| **MySQL Connector/J**| 8.x | Official JDBC driver connecting Spring Boot to the MySQL database. |
| **Thymeleaf** | 3.x | Server-side template engine for dynamic rendering of dashboard and quick-check views. |
| **HTML5 & CSS3** | Modern Standards | Pure vanilla frontend styling with CSS Grid/Flexbox, responsive design, and status indicator dots. |
| **JavaScript** | Vanilla ES6 | Lightweight client-side interactivity (spinner display, URL dropdown filtering, auto-focus). |
| **Apache Maven** | 3.8+ | Build automation, dependency management, and executable JAR packaging. |

---

## System Architecture

The application adopts a clean, layered architecture separating presentation, business logic, and persistence:

```
                  ┌───────────────────────────────┐
                  │       Web Browser (Client)    │
                  └───────────────┬───────────────┘
                                  │ HTTP Requests (GET / POST)
                                  ▼
 ┌─────────────────────────────────────────────────────────────────┐
 │                       Spring Boot Application                   │
 │                                                                 │
 │   ┌─────────────────────────────────────────────────────────┐   │
 │   │                      Controller Layer                   │   │
 │   │  • UptimeController      (Quick single-check page)       │   │
 │   │  • DashboardController   (Dashboard, add, remove, check)│   │
 │   └────────────────────────────┬────────────────────────────┘   │
 │                                │                                │
 │       ┌────────────────────────┴────────────────────────┐       │
 │       ▼                                                 ▼       │
 │  ┌─────────────────────────┐             ┌────────────────────┐ │
 │  │      Service Layer      │             │  Repository Layer  │ │
 │  │                         │             │                    │ │
 │  │  • UptimeCheckerService │             │  • SiteRepository  │ │
 │  │    - URL Validation     │             │  • CheckHistoryRepo│ │
 │  │    - SSRF Guard         │             │    (JdbcTemplate)  │ │
 │  │    - Redirect Loop (5x) │             └─────────┬──────────┘ │
 │  │    - HTTP Timing Client │                       │            │
 │  └─────────────────────────┘                       │            │
 └────────────────────────────────────────────────────┼────────────┘
                                                      │ JDBC / SQL
                                                      ▼
                                       ┌─────────────────────────┐
                                       │     MySQL Database      │
                                       │    (uptime_monitor)     │
                                       │  • monitored_sites      │
                                       │  • check_history        │
                                       └─────────────────────────┘
```

---

## Project Directory Structure

```
WebsiteUptimeMonitor/
├── pom.xml                                      # Maven dependencies and build configuration
├── README.md                                    # Project documentation
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── uptimemonitor/
│       │           ├── UptimeMonitorApplication.java    # Spring Boot application entry point
│       │           ├── controller/
│       │           │   ├── DashboardController.java     # Handles /dashboard, /add, /remove, /check-now
│       │           │   └── UptimeController.java        # Handles / (home page) and /check (quick-check)
│       │           ├── model/
│       │           │   ├── CheckHistoryItem.java        # Model representing a historical check entry
│       │           │   ├── CheckResult.java             # Value object for HTTP check outcomes
│       │           │   └── MonitoredSite.java           # Domain model representing a monitored website
│       │           └── service/
│       │               ├── CheckHistoryRepository.java  # JDBC repository for check_history table
│       │               ├── SiteRepository.java          # JDBC repository for monitored_sites table
│       │               └── UptimeCheckerService.java    # URL validator, SSRF firewall, and HTTP checker
│       └── resources/
│           ├── application.properties                   # Datasource, port, and template configurations
│           ├── schema.sql                               # Database DDL script executed on startup
│           └── templates/
│               ├── dashboard.html                       # Multi-site dashboard & history UI
│               └── index.html                           # Single-URL quick-check UI
└── target/
    └── website-uptime-monitor-1.0.0.jar                 # Standalone packaged executable JAR
```

---

## Security & SSRF Mitigation

A primary focus of this project is mitigating **Server-Side Request Forgery (SSRF)**. When an uptime monitor takes user-submitted URLs and sends outbound HTTP requests, it must not become a proxy for internal network exploration.

### Defensive Measures in `UptimeCheckerService`:
1. **Scheme Enforcement**: Only `http://` and `https://` schemes are permitted. File, FTP, Gopher, and LDAP schemes are rejected.
2. **Auto-Normalization**: Bare domain inputs (e.g., `google.com`) are automatically prepended with `https://`.
3. **DNS Resolution Inspection**: Before connecting, the host is resolved via DNS. All returned IP addresses are inspected:
   - Loopback (`127.0.0.1`, `::1`)
   - Site-local / RFC 1918 private ranges (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`)
   - Link-local (`169.254.0.0/16`, `fe80::/10`)
   - Wildcard/any-local (`0.0.0.0`, `::`)
4. **Manual Redirect Traversal**: `HttpClient` redirect following is set to `Redirect.NEVER`. The service manually inspects HTTP `301`, `302`, `303`, `307`, and `308` responses, resolves relative redirect targets, and **re-evaluates all security constraints** on the `Location` header before continuing. A maximum hop limit of 5 prevents redirect loops.
5. **Connection & Read Timeouts**: Both connection and request timeouts are capped at 10 seconds to prevent resource exhaustion and Slowloris-style denial-of-service.

---

## Database Setup

The project uses a dedicated MySQL database named `uptime_monitor`.

### 1. Create Database in MySQL Workbench or Shell

Connect to your local MySQL instance (using MySQL Workbench or the `mysql` CLI) and execute:

```sql
CREATE DATABASE IF NOT EXISTS uptime_monitor
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

### 2. Automatic Schema Creation

The application includes `src/main/resources/schema.sql`. Because `spring.sql.init.mode=always` is configured in `application.properties`, Spring Boot will automatically create the tables on startup if they do not already exist:

```sql
CREATE TABLE IF NOT EXISTS monitored_sites (
    id           VARCHAR(36)   NOT NULL,
    name         VARCHAR(100)  NOT NULL,
    url          VARCHAR(2048) NOT NULL,
    added_at     DATETIME      NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS check_history (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    site_id          VARCHAR(36)  NOT NULL,
    checked_at       DATETIME     NOT NULL,
    status           VARCHAR(10)  NOT NULL,
    http_status_code INT          NOT NULL,
    http_status_text VARCHAR(50)  NOT NULL,
    response_time_ms BIGINT       NOT NULL,
    error_message    TEXT,
    PRIMARY KEY (id),
    CONSTRAINT fk_history_site
        FOREIGN KEY (site_id) REFERENCES monitored_sites(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## Configuration

Database credentials and server parameters are configured in [`src/main/resources/application.properties`](file:///c:/Users/sachi/OneDrive/Desktop/WebsiteUptimeMonitor/src/main/resources/application.properties).

Edit this file with your local MySQL credentials:

```properties
server.port=8080
spring.application.name=WebsiteUptimeMonitor

# ── Thymeleaf Settings ────────────────────────────────────────────────────────
spring.thymeleaf.cache=false
spring.thymeleaf.encoding=UTF-8
spring.thymeleaf.mode=HTML

# ── MySQL Database Configuration ──────────────────────────────────────────────
spring.datasource.url=jdbc:mysql://localhost:3306/uptime_monitor?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=YOUR_MYSQL_USERNAME
spring.datasource.password=YOUR_MYSQL_PASSWORD
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# ── Connection Pool (HikariCP) ────────────────────────────────────────────────
spring.datasource.hikari.maximum-pool-size=5
spring.datasource.hikari.connection-timeout=20000

# ── Schema Initialization ─────────────────────────────────────────────────────
spring.sql.init.mode=always
```

> **Security Note:** Never commit plain-text production passwords to source control. For local development, replace `YOUR_MYSQL_USERNAME` (typically `root`) and `YOUR_MYSQL_PASSWORD` with your local database credentials.

---

## How to Run the Application

### Prerequisites

Ensure you have the following installed on your machine:
- **Java JDK 17** or higher (`java -version`)
- **Apache Maven 3.8+** (`mvn -version`)
- **MySQL Server 8.0+** running on `localhost:3306`

---

### Run with Maven

1. Open PowerShell or Command Prompt and navigate to the project root directory:
   ```powershell
   cd C:\Users\sachi\OneDrive\Desktop\WebsiteUptimeMonitor
   ```

2. Start the application:
   ```powershell
   mvn spring-boot:run
   ```

3. When you see:
   ```
   Started UptimeMonitorApplication in X.XXX seconds (process running for ...)
   ```
   the application is live and listening on port `8080`.

---

### Build and Run Standalone JAR

To package the application into a self-contained executable JAR:

1. Package the application:
   ```powershell
   mvn clean package -DskipTests
   ```

2. Run the generated JAR:
   ```powershell
   java -jar target\website-uptime-monitor-1.0.0.jar
   ```

3. To stop the application at any time, press `Ctrl + C` in the terminal window.

---

## Accessing the Application

Once the server is running, open your web browser:

| View | URL | Description |
|---|---|---|
| **Monitoring Dashboard** | [http://localhost:8080/dashboard](http://localhost:8080/dashboard) | Add websites, check uptime, view history table, and manage monitored sites. |
| **Quick Check Page** | [http://localhost:8080/](http://localhost:8080/) | Instant single-URL availability and response-time checker. |

---

## Testing Performed

The application has undergone thorough manual and integration testing:

1. **Uptime Verification**:
   - Tested functional public websites (`https://google.com`, `https://github.com`, `https://cloudflare.com`).
   - Verified that status resolves to `UP`, HTTP `200 OK` is returned, and round-trip response time is accurately measured in milliseconds.
2. **Error & Outage Simulation**:
   - Checked non-existent domains (`https://thisdomaindoesnotexist12345.org`). Verified clean `DOWN` status and friendly `Could not resolve hostname` messaging without application crashes.
   - Tested HTTP `404` and `500` error endpoints. Verified status text and HTTP code extraction.
3. **SSRF & Security Testing**:
   - Attempted input: `http://localhost:8080` → **Blocked** with `Requests to localhost and .local domains are not allowed.`
   - Attempted input: `http://127.0.0.1:3306` → **Blocked** with `Requests to loopback addresses are not allowed.`
   - Attempted input: `http://192.168.1.1` and `http://10.0.0.1` → **Blocked** with `Requests to private/internal network addresses are not allowed (RFC-1918).`
   - Attempted input: `http://0.0.0.0` → **Blocked** with `Requests to the unspecified address are not allowed.`
4. **Redirect Re-validation Testing**:
   - Verified that public domains redirecting across protocols (HTTP to HTTPS) succeed up to 5 hops.
   - Confirmed that redirect destinations resolving to internal or loopback IP ranges are aborted with a security exception.
5. **Persistence & Integrity Testing**:
   - Added websites, clicked "Check Now", and verified records in MySQL via `SELECT * FROM monitored_sites;` and `SELECT * FROM check_history;`.
   - Stopped and restarted the Spring Boot application. Confirmed all sites and previous check results reloaded intact.
   - Tested site deletion. Confirmed that removing a site automatically cascaded and purged associated rows in `check_history`.
6. **History UI Testing**:
   - Verified history table sorting with the newest checks displayed first.
   - Tested website dropdown filtering: confirmed only the selected website's checks appear and "✕ Show All" restores the full list.
   - Verified empty states display descriptive guidance when no checks have been recorded yet.

---

## Known Limitations

This project was built incrementally across four focused phases. The current implementation has the following known architectural limitations:

- **On-Demand Checks Only**: Health checks are triggered manually by user interaction ("Check Now"). The application does not yet include a background cron scheduler (`@Scheduled`) to ping URLs automatically at set intervals.
- **Single-User Scope**: The application operates in a single-tenant environment without user authentication (Spring Security), login sessions, or per-user data isolation.
- **Alerting**: The system does not currently dispatch email, SMS, or webhook notifications (e.g., Slack or Discord) when a monitored website goes down.
- **HTTP Methods**: Checks are restricted to HTTP `GET` requests; custom payloads, custom HTTP request headers, and SSL certificate expiration checks are not supported in this version.
