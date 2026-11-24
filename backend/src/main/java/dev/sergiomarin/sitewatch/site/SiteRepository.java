package dev.sergiomarin.sitewatch.site;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteRepository extends JpaRepository<Site, Long> {

    List<Site> findAllByOwnerIdOrderByIdAsc(Long ownerId);

    Optional<Site> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByOwnerIdAndUrl(Long ownerId, String url);

    boolean existsByOwnerIdAndUrlAndIdNot(Long ownerId, String url, Long id);
}
