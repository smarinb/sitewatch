package dev.sergiomarin.sitewatch.site;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.sergiomarin.sitewatch.common.ApiException;
import dev.sergiomarin.sitewatch.user.AppUserRepository;

/**
 * Sites are always looked up together with their owner: another user's site
 * answers 404, exactly like one that does not exist, so ids leak nothing.
 */
@Service
public class SiteService {

    private final SiteRepository sites;
    private final AppUserRepository users;

    public SiteService(SiteRepository sites, AppUserRepository users) {
        this.sites = sites;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<SiteResponse> list(long ownerId) {
        return sites.findAllByOwnerIdOrderByIdAsc(ownerId).stream().map(SiteResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SiteResponse get(long ownerId, long siteId) {
        return SiteResponse.from(find(ownerId, siteId));
    }

    @Transactional
    public SiteResponse create(long ownerId, SiteRequest request) {
        String url = request.url().trim();
        if (sites.existsByOwnerIdAndUrl(ownerId, url)) {
            throw duplicateUrl();
        }
        Site site = new Site(users.getReferenceById(ownerId), request.name().trim(), url,
                request.intervalOrDefault(), request.activeOrDefault());
        return SiteResponse.from(saveAndFlush(site));
    }

    @Transactional
    public SiteResponse update(long ownerId, long siteId, SiteRequest request) {
        Site site = find(ownerId, siteId);
        String url = request.url().trim();
        if (sites.existsByOwnerIdAndUrlAndIdNot(ownerId, url, siteId)) {
            throw duplicateUrl();
        }
        site.update(request.name().trim(), url, request.intervalOrDefault(), request.activeOrDefault());
        return SiteResponse.from(saveAndFlush(site));
    }

    @Transactional
    public void delete(long ownerId, long siteId) {
        sites.delete(find(ownerId, siteId));
    }

    /** Throws 404 unless the site exists and belongs to the user. */
    @Transactional(readOnly = true)
    public void requireOwned(long ownerId, long siteId) {
        find(ownerId, siteId);
    }

    private Site find(long ownerId, long siteId) {
        return sites.findByIdAndOwnerId(siteId, ownerId)
                .orElseThrow(() -> ApiException.notFound("Site " + siteId + " not found"));
    }

    private Site saveAndFlush(Site site) {
        try {
            return sites.saveAndFlush(site);
        } catch (DataIntegrityViolationException e) {
            // Concurrent request inserted the same URL between the check and the write.
            throw duplicateUrl();
        }
    }

    private static ApiException duplicateUrl() {
        return ApiException.conflict("You are already monitoring this URL");
    }
}
