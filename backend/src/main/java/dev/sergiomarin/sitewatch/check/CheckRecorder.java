package dev.sergiomarin.sitewatch.check;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.sergiomarin.sitewatch.site.SiteRepository;

/** Stores a check result and moves the site's schedule forward, in one short transaction. */
@Service
public class CheckRecorder {

    private final SiteRepository sites;
    private final SiteCheckRepository checks;

    public CheckRecorder(SiteRepository sites, SiteCheckRepository checks) {
        this.sites = sites;
        this.checks = checks;
    }

    /** Returns {@code false} when the site was deleted while it was being checked. */
    @Transactional
    public boolean record(long siteId, Instant checkedAt, CheckResult result) {
        if (sites.markChecked(siteId, checkedAt) == 0) {
            return false;
        }
        checks.save(new SiteCheck(siteId, checkedAt, result));
        return true;
    }
}
