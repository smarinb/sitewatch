package dev.sergiomarin.sitewatch.check;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.Limit;
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

    /** Most recent checks first. */
    @GetMapping("/api/sites/{id}/checks")
    @Transactional(readOnly = true)
    public List<CheckResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        siteService.requireOwned(CurrentUser.id(jwt), id);
        return checks.findBySiteIdOrderByCheckedAtDesc(id, Limit.of(limit)).stream()
                .map(CheckResponse::from).toList();
    }
}
