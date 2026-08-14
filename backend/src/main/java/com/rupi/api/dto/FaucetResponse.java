package com.rupi.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.UUID;

public record FaucetResponse(
        UUID accountId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal granted,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance) {}
