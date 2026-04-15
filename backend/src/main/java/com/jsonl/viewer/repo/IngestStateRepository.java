package com.jsonl.viewer.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IngestStateRepository extends JpaRepository<IngestState, String> {
  @Query(
      "SELECT s FROM IngestState s " +
      "WHERE s.indexedRevision < s.sourceRevision " +
      "ORDER BY s.lastIngestedAt ASC"
  )
  List<IngestState> findPendingFieldIndexBuilds();
}
