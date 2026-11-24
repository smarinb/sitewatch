package dev.sergiomarin.sitewatch.check;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.sergiomarin.sitewatch.alert.AlertEvaluator;
import dev.sergiomarin.sitewatch.alert.AlertEvaluator.Evaluation;
import dev.sergiomarin.sitewatch.alert.AlertEvent;
import dev.sergiomarin.sitewatch.site.Site;
import dev.sergiomarin.sitewatch.site.SiteRepository;

/**
 * Stores a check result, advances the site's schedule and alert state, and
 * returns the alerts to send, all in one short transaction. Sending happens
 * after commit, so an alert never goes out for a result that was rolled back.
 */
@Service
public class CheckRecorder {

    private final SiteRepository sites;
    private final SiteCheckRepository checks;
    private final AlertEvaluator evaluator;

    public CheckRecorder(SiteRepository sites, SiteCheckRepository checks, AlertEvaluator evaluator) {
        this.sites = sites;
        this.checks = checks;
        this.evaluator = evaluator;
    }

    /** Returns the alerts triggered; none if the site was deleted while it was being checked. */
    @Transactional
    public List<AlertEvent> record(long siteId, Instant checkedAt, CheckResult result) {
        Site site = sites.findById(siteId).orElse(null);
        if (site == null) {
            return List.of();
        }
        Evaluation evaluation = evaluator.evaluate(site, result, checkedAt);
        site.applyCheck(checkedAt, evaluation.state());
        checks.save(new SiteCheck(siteId, checkedAt, result));
        return evaluation.events();
    }

    /** Moves the schedule forward without a result, after an unexpected error in our own code. */
    @Transactional
    public void skip(long siteId, Instant checkedAt) {
        sites.findById(siteId).ifPresent(site -> site.applyCheck(checkedAt, site.monitorState()));
    }

    /** Deletes history older than the cutoff and returns how many rows went. */
    @Transactional
    public int deleteChecksBefore(Instant cutoff) {
        return checks.deleteByCheckedAtBefore(cutoff);
    }
}
