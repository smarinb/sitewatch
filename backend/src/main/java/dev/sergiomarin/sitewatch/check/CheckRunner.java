package dev.sergiomarin.sitewatch.check;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    private final MeterRegistry meters;

    public CheckRunner(SiteRepository sites, SiteChecker checker, CheckRecorder recorder,
            CheckerProperties properties, MeterRegistry meters) {
        this.sites = sites;
        this.checker = checker;
        this.recorder = recorder;
        this.properties = properties;
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
        try {
            Timer.Sample sample = Timer.start(meters);
            CheckResult result = checker.check(site.getUrl());
            sample.stop(meters.timer("sitewatch.checks", "outcome", result.up() ? "up" : "down"));
            recorder.record(site.getId(), checkedAt, result);
        } catch (RuntimeException e) {
            log.error("Check of site {} failed unexpectedly", site.getId(), e);
            try {
                // Still move the schedule forward so a broken site is not retried every tick.
                recorder.record(site.getId(), checkedAt, CheckResult.failure("Internal error during check"));
            } catch (RuntimeException ignored) {
                log.warn("Could not record failed check of site {}", site.getId());
            }
        }
    }
}
