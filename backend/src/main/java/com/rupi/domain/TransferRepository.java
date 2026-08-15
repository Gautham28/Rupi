package com.rupi.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    @Query(
            value =
                    """
                    SELECT * FROM transfer t
                    WHERE (t.from_account_id = :accountId OR t.to_account_id = :accountId)
                      AND (
                        :hasCursor = false
                        OR t.created_at < :cursorCreatedAt
                        OR (t.created_at = :cursorCreatedAt AND t.id < :cursorId)
                      )
                    ORDER BY t.created_at DESC, t.id DESC
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<Transfer> findHistoryPage(
            @Param("accountId") UUID accountId,
            @Param("hasCursor") boolean hasCursor,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);
}
