package at.fhtw.repository;

import at.fhtw.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    @Modifying
    @Query("""
            update Document d set d.summary = :summary, d.summaryStartedAt = null, d.updatedAt = :updatedAt
            where d.id = :id and d.filePath = :objectKey and d.summary is null
            """)
    int storeSummary(@Param("id") long id, @Param("objectKey") String objectKey,
                     @Param("summary") String summary, @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying
    @Query("""
            update Document d set d.summaryStartedAt = :startedAt
            where d.id = :id and d.filePath = :objectKey and d.summary is null
            """)
    int setSummaryStartedAt(@Param("id") long id, @Param("objectKey") String objectKey,
                            @Param("startedAt") Instant startedAt);
}
