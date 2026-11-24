package dev.sergiomarin.sitewatch.check;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row of a site's check history; append-only. */
@Entity
@Table(name = "site_check")
public class SiteCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_id", nullable = false, updatable = false)
    private Long siteId;

    @Column(name = "checked_at", nullable = false, updatable = false)
    private Instant checkedAt;

    @Column(nullable = false)
    private boolean up;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "ssl_expires_at")
    private Instant sslExpiresAt;

    @Column(name = "wp_version", length = 20)
    private String wpVersion;

    @Column
    private String error;

    protected SiteCheck() {
    }

    SiteCheck(long siteId, Instant checkedAt, CheckResult result) {
        this.siteId = siteId;
        this.checkedAt = checkedAt;
        this.up = result.up();
        this.httpStatus = result.httpStatus();
        this.responseTimeMs = result.responseTimeMs();
        this.sslExpiresAt = result.sslExpiresAt();
        this.wpVersion = result.wpVersion();
        this.error = result.error();
    }

    public Long getId() {
        return id;
    }

    public Long getSiteId() {
        return siteId;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public boolean isUp() {
        return up;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public Integer getResponseTimeMs() {
        return responseTimeMs;
    }

    public Instant getSslExpiresAt() {
        return sslExpiresAt;
    }

    public String getWpVersion() {
        return wpVersion;
    }

    public String getError() {
        return error;
    }
}
