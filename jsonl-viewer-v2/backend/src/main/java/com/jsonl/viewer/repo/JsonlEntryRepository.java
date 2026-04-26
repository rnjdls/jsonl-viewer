package com.jsonl.viewer.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JsonlEntryRepository extends JpaRepository<JsonlEntry, Long>, JsonlEntryRepositoryCustom {
  @Modifying
  @Query("DELETE FROM JsonlEntry e WHERE e.filePath = :filePath")
  void deleteByFilePath(@Param("filePath") String filePath);
}
