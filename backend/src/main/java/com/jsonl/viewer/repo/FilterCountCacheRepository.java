package com.jsonl.viewer.repo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FilterCountCacheRepository extends JpaRepository<FilterCountCacheEntry, FilterCountCacheKey> {
  Optional<FilterCountCacheEntry> findByFilePathAndRequestHash(String filePath, String requestHash);

  @Modifying
  @Query("DELETE FROM FilterCountCacheEntry e WHERE e.filePath = :filePath")
  void deleteByFilePath(@Param("filePath") String filePath);
}
