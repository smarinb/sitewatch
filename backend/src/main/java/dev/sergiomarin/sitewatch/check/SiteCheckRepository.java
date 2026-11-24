package dev.sergiomarin.sitewatch.check;

import java.time.Instant;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SiteCheckRepository extends JpaRepository<SiteCheck, Long> {

    List<SiteCheck> findBySiteIdOrderByCheckedAtDesc(Long siteId, Limit limit);

    List<SiteCheck> findBySiteIdAndCheckedAtGreaterThanEqualOrderByCheckedAtDesc(Long siteId, Instant since,
            Limit limit);

    /** The newest check of each of the owner's sites; a tie on the timestamp can yield two rows for a site. */
    @Query("""
            SELECT c FROM SiteCheck c
            WHERE c.siteId IN (SELECT s.id FROM Site s WHERE s.owner.id = :ownerId)
              AND c.checkedAt = (SELECT MAX(c2.checkedAt) FROM SiteCheck c2 WHERE c2.siteId = c.siteId)
            """)
    List<SiteCheck> findLatestByOwner(Long ownerId);

    @Modifying
    @Query("DELETE FROM SiteCheck c WHERE c.checkedAt < :cutoff")
    int deleteByCheckedAtBefore(Instant cutoff);
}
