package com.rupi.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        String username,
        UUID accountId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance) {}
