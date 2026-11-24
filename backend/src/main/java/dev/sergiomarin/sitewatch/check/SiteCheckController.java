package dev.sergiomarin.sitewatch.check;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.Limit;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.sergiomarin.sitewatch.auth.CurrentUser;
import dev.sergiomarin.sitewatch.site.SiteService;

@RestController
public class SiteCheckController {

    private final SiteService siteService;
    private final SiteCheckRepository checks;

    public SiteCheckController(SiteService siteService, SiteCheckRepository checks) {
        this.siteService = siteService;
        this.checks = checks;
    }

    /** Most recent checks first, optionally only those at or after {@code since}. */
    @GetMapping("/api/sites/{id}/checks")
    @Transactional(readOnly = true)
    public List<CheckResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        siteService.requireOwned(CurrentUser.id(jwt), id);
        List<SiteCheck> history = since == null
                ? checks.findBySiteIdOrderByCheckedAtDesc(id, Limit.of(limit))
                : checks.findBySiteIdAndCheckedAtGreaterThanEqualOrderByCheckedAtDesc(id, since, Limit.of(limit));
        return history.stream().map(CheckResponse::from).toList();
    }

    /** The latest check of every site the user owns (sites never checked are absent). */
    @GetMapping("/api/checks/latest")
    @Transactional(readOnly = true)
    public List<LatestCheckResponse> latest(@AuthenticationPrincipal Jwt jwt) {
        Map<Long, LatestCheckResponse> bySite = new LinkedHashMap<>();
        for (SiteCheck check : checks.findLatestByOwner(CurrentUser.id(jwt))) {
            bySite.putIfAbsent(check.getSiteId(), LatestCheckResponse.from(check));
        }
        return List.copyOf(bySite.values());
    }
}
