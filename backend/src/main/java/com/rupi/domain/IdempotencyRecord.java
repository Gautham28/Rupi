package com.rupi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    public static final String STATE_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATE_COMPLETED = "COMPLETED";

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false, length = 32)
    private String state;

    @Column(name = "http_status")
    private Integer httpStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private String responseBody;

    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IdempotencyRecord() {}

    public static IdempotencyRecord inProgress(
            UUID id, UUID userId, UUID idempotencyKey, String requestHash, Instant now) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.id = id;
        record.userId = userId;
        record.idempotencyKey = idempotencyKey;
        record.requestHash = requestHash;
        record.state = STATE_IN_PROGRESS;
        record.createdAt = now;
        record.updatedAt = now;
        return record;
    }

    public void markCompleted(int httpStatus, String responseBody, UUID transferId, Instant now) {
        this.state = STATE_COMPLETED;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
        this.transferId = transferId;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getState() {
        return state;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public UUID getTransferId() {
        return transferId;
    }
}
