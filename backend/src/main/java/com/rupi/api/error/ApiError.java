package com.rupi.api.error;

import java.util.List;

public record ApiError(
        ErrorCode code, String message, String requestId, List<FieldErrorDetail> fieldErrors) {

    public static ApiError of(ErrorCode code, String message, String requestId) {
        return new ApiError(code, message, requestId, List.of());
    }
}
