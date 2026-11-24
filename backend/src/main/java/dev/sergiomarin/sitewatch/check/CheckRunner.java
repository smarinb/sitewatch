package dev.sergiomarin.sitewatch.check;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.sergiomarin.sitewatch.alert.AlertDispatcher;
import dev.sergiomarin.sitewatch.alert.AlertEvent;
import dev.sergiomarin.sitewatch.site.SiteRepository;
import dev.sergiomarin.sitewatch.site.SiteRepository.DueSite;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Checks every site that is due, each on its own virtual thread, with a cap on
 * how many run at once. Returns once all of them are recorded, so with a
 * fixed-delay schedule two rounds never overlap.
 */
@Service
public class CheckRunner {

    private static final Logger log = LoggerFactory.getLogger(CheckRunner.class);

    private final SiteRepository sites;
    private final SiteChecker checker;
    private final CheckRecorder recorder;
    private final CheckerProperties properties;
    private final AlertDispatcher alerts;
    private final MeterRegistry meters;

    public CheckRunner(SiteRepository sites, SiteChecker checker, CheckRecorder recorder,
            CheckerProperties properties, AlertDispatcher alerts, MeterRegistry meters) {
        this.sites = sites;
        this.checker = checker;
        this.recorder = recorder;
        this.properties = properties;
        this.alerts = alerts;
        this.meters = meters;
    }

    /** Runs all due checks and returns how many sites were checked. */
    public int runDueChecks() {
        List<DueSite> due = sites.findDueForCheck(properties.batchSize());
        if (due.isEmpty()) {
            return 0;
        }
        Semaphore permits = new Semaphore(properties.maxConcurrent());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (DueSite site : due) {
                executor.execute(() -> {
                    permits.acquireUninterruptibly();
                    try {
                        checkAndRecord(site);
                    } finally {
                        permits.release();
                    }
                });
            }
        } // close() waits for every task
        log.debug("Checked {} site(s)", due.size());
        return due.size();
    }

    private void checkAndRecord(DueSite site) {
        Instant checkedAt = Instant.now();
        List<AlertEvent> events;
        try {
            Timer.Sample sample = Timer.start(meters);
            CheckResult result = checker.check(site.getUrl());
            sample.stop(meters.timer("sitewatch.checks", "outcome", result.up() ? "up" : "down"));
            events = recorder.record(site.getId(), checkedAt, result);
        } catch (RuntimeException e) {
            log.error("Check of site {} failed unexpectedly", site.getId(), e);
            try {
                // A bug on our side is not the site's fault: no result, no alert, but move the
                // schedule forward so the site is not retried on every tick.
                recorder.skip(site.getId(), checkedAt);
            } catch (RuntimeException ignored) {
                log.warn("Could not reschedule site {} after a failed check", site.getId());
            }
            return;
        }
        alerts.dispatch(events);
    }

    /** Deletes check history older than the configured retention. */
    public int purgeOldChecks() {
        int deleted = recorder.deleteChecksBefore(Instant.now().minus(properties.retention()));
        log.info("Deleted {} check(s) older than {}", deleted, properties.retention());
        return deleted;
    }
}
