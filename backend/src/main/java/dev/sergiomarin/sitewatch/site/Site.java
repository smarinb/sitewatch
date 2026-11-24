package dev.sergiomarin.sitewatch.site;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    protected Site() {
    }

    public Site(AppUser owner, String name, String url, int checkIntervalMinutes, boolean active) {
        this.owner = owner;
        this.createdAt = Instant.now();
        update(name, url, checkIntervalMinutes, active);
    }

    public void update(String name, String url, int checkIntervalMinutes, boolean active) {
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
}
