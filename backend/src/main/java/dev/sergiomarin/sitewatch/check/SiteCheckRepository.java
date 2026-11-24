package dev.sergiomarin.sitewatch.check;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SiteCheckRepository extends JpaRepository<SiteCheck, Long> {

    List<SiteCheck> findBySiteIdOrderByCheckedAtDesc(Long siteId, Limit limit);

    @Modifying
    @Query("DELETE FROM SiteCheck c WHERE c.checkedAt < :cutoff")
    int deleteByCheckedAtBefore(Instant cutoff);
}
