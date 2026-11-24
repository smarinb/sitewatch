package dev.sergiomarin.sitewatch.site;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.sergiomarin.sitewatch.auth.CurrentUser;

@RestController
@RequestMapping("/api/sites")
public class SiteController {

    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    @GetMapping
    public List<SiteResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return siteService.list(CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    public SiteResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return siteService.get(CurrentUser.id(jwt), id);
    }

    @PostMapping
    public ResponseEntity<SiteResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SiteRequest request) {
        SiteResponse created = siteService.create(CurrentUser.id(jwt), request);
        return ResponseEntity.created(URI.create("/api/sites/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public SiteResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @Valid @RequestBody SiteRequest request) {
        return siteService.update(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        siteService.delete(CurrentUser.id(jwt), id);
        return ResponseEntity.noContent().build();
    }
}
