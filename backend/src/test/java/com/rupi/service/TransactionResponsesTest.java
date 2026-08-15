package com.rupi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.rupi.api.dto.TransactionResponse;
import com.rupi.domain.Transfer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionResponsesTest {

    @Test
    void roundTripsJson() {
        UUID id = UUID.randomUUID();
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-14T12:00:00Z");
        TransactionResponse original = new TransactionResponse(
                id, from, to, to, new BigDecimal("12.34"), "OUTGOING", "COMPLETED", createdAt);

        TransactionResponse parsed = TransactionResponses.fromJson(TransactionResponses.toJson(original));

        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void mapsDirectionForViewer() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        Transfer transfer =
                new Transfer(UUID.randomUUID(), from, to, new BigDecimal("5.00"), "COMPLETED", Instant.now());

        assertThat(TransactionResponses.fromTransfer(transfer, from).direction()).isEqualTo("OUTGOING");
        assertThat(TransactionResponses.fromTransfer(transfer, to).direction()).isEqualTo("INCOMING");
    }
}
