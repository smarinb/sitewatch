package dev.sergiomarin.sitewatch.site;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import dev.sergiomarin.sitewatch.user.AppUser;

@Entity
@Table(name = "site")
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private AppUser owner;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(name = "check_interval_minutes", nullable = false)
    private int checkIntervalMinutes;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SiteStatus status = SiteStatus.UNKNOWN;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "down_since")
    private Instant downSince;

    @Column(name = "ssl_alerted_expiry")
    private Instant sslAlertedExpiry;

    protected Site() {
    }

    public Site(AppUser owner, String name, String url, int checkIntervalMinutes, boolean active) {
        this.owner = owner;
        this.createdAt = Instant.now();
        update(name, url, checkIntervalMinutes, active);
    }

    public void update(String name, String url, int checkIntervalMinutes, boolean active) {
        if (this.url != null && !this.url.equals(url)) {
            // A different URL is a different site: forget its history and check it right away.
            applyCheck(null, MonitorState.INITIAL);
        }
        this.name = name;
        this.url = url;
        this.checkIntervalMinutes = checkIntervalMinutes;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public int getCheckIntervalMinutes() {
        return checkIntervalMinutes;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public SiteStatus getStatus() {
        return status;
    }

    public MonitorState monitorState() {
        return new MonitorState(status, consecutiveFailures, downSince, sslAlertedExpiry);
    }

    /** Records that a check ran and stores the resulting alert state. */
    public void applyCheck(Instant checkedAt, MonitorState state) {
        this.lastCheckedAt = checkedAt;
        this.status = state.status();
        this.consecutiveFailures = state.consecutiveFailures();
        this.downSince = state.downSince();
        this.sslAlertedExpiry = state.sslAlertedExpiry();
    }
}
