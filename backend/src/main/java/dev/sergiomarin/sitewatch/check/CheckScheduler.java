package dev.sergiomarin.sitewatch.check;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Triggers a round of checks every tick and purges old history once a day.
 * Disabled with {@code sitewatch.checker.enabled=false}.
 */
@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty(name = "sitewatch.checker.enabled", matchIfMissing = true)
class CheckScheduler {

    private final CheckRunner runner;

    CheckScheduler(CheckRunner runner) {
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${sitewatch.checker.tick}", initialDelayString = "${sitewatch.checker.tick}")
    void tick() {
        runner.runDueChecks();
    }

    @Scheduled(cron = "${sitewatch.checker.retention-cron}")
    void purge() {
        runner.purgeOldChecks();
    }
}
