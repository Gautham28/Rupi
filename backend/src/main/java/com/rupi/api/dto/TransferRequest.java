package com.rupi.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        @NotNull UUID toAccountId,
        @NotNull
                @DecimalMin(value = "0.01", inclusive = true)
                @DecimalMax(value = "1000000.00", inclusive = true)
                @Digits(integer = 17, fraction = 2)
                @JsonFormat(shape = JsonFormat.Shape.STRING)
                BigDecimal amount) {}
