package dev.sergiomarin.sitewatch.site;

import java.util.List;
import java.util.Optional;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SiteRepository extends JpaRepository<Site, Long> {

    List<Site> findAllByOwnerIdOrderByIdAsc(Long ownerId);

    Optional<Site> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByOwnerIdAndUrl(Long ownerId, String url);

    boolean existsByOwnerIdAndUrlAndIdNot(Long ownerId, String url, Long id);

    /** Active sites never checked or whose interval has elapsed, longest-waiting first. */
    @Query(value = """
            SELECT s.id AS id, s.url AS url
            FROM site s
            WHERE s.active
              AND (s.last_checked_at IS NULL
                   OR s.last_checked_at <= now() - make_interval(mins => s.check_interval_minutes))
            ORDER BY s.last_checked_at NULLS FIRST, s.id
            LIMIT :limit
            """, nativeQuery = true)
    List<DueSite> findDueForCheck(int limit);

    @Modifying
    @Query("UPDATE Site s SET s.lastCheckedAt = :checkedAt WHERE s.id = :id")
    int markChecked(Long id, Instant checkedAt);

    interface DueSite {
        Long getId();

        String getUrl();
    }
}
