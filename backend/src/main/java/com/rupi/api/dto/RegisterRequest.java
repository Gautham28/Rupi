package com.rupi.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
                @Size(min = 3, max = 32)
                @Pattern(
                        regexp = "[A-Za-z0-9._-]+",
                        message = "may contain letters, digits, dots, underscores, and hyphens")
                String username,
        @NotBlank @Size(min = 10, max = 72) String password) {}
