package dev.sergiomarin.sitewatch.check;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteCheckRepository extends JpaRepository<SiteCheck, Long> {

    List<SiteCheck> findBySiteIdOrderByCheckedAtDesc(Long siteId, Limit limit);
}
