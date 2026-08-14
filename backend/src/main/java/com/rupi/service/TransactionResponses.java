package com.rupi.service;

import com.rupi.api.dto.TransactionResponse;
import com.rupi.domain.Transfer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

final class TransactionResponses {

    private TransactionResponses() {}

    static TransactionResponse fromTransfer(Transfer transfer, UUID viewerAccountId) {
        boolean outgoing = transfer.getFromAccountId().equals(viewerAccountId);
        String direction = outgoing ? "OUTGOING" : "INCOMING";
        UUID counterparty =
                outgoing ? transfer.getToAccountId() : transfer.getFromAccountId();
        return new TransactionResponse(
                transfer.getId(),
                transfer.getFromAccountId(),
                transfer.getToAccountId(),
                counterparty,
                transfer.getAmount(),
                direction,
                transfer.getStatus(),
                transfer.getCreatedAt());
    }

    static String toJson(TransactionResponse response) {
        return "{"
                + "\"id\":\""
                + response.id()
                + "\","
                + "\"fromAccountId\":\""
                + response.fromAccountId()
                + "\","
                + "\"toAccountId\":\""
                + response.toAccountId()
                + "\","
                + "\"counterpartyAccountId\":\""
                + response.counterpartyAccountId()
                + "\","
                + "\"amount\":\""
                + response.amount().toPlainString()
                + "\","
                + "\"direction\":\""
                + response.direction()
                + "\","
                + "\"status\":\""
                + response.status()
                + "\","
                + "\"createdAt\":\""
                + response.createdAt()
                + "\""
                + "}";
    }

    static TransactionResponse fromJson(String json) {
        return new TransactionResponse(
                uuid(json, "id"),
                uuid(json, "fromAccountId"),
                uuid(json, "toAccountId"),
                uuid(json, "counterpartyAccountId"),
                new BigDecimal(string(json, "amount")),
                string(json, "direction"),
                string(json, "status"),
                Instant.parse(string(json, "createdAt")));
    }

    private static UUID uuid(String json, String field) {
        return UUID.fromString(string(json, field));
    }

    private static String string(String json, String field) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            throw new IllegalArgumentException("Missing field " + field);
        }
        start += needle.length();
        int end = json.indexOf('"', start);
        if (end < 0) {
            throw new IllegalArgumentException("Malformed field " + field);
        }
        return json.substring(start, end);
    }
}
