package com.rupi.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
        UUID accountId,
        UUID userId,
        String username,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance,
        boolean demoMode) {}
