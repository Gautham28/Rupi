package com.rupi.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiErrorContractTest {

    @Test
    void envelopeCarriesCodeMessageAndRequestId() {
        ApiError error = ApiError.of(
                ErrorCode.INSUFFICIENT_FUNDS, "Account does not have enough sandbox credits.", "req-1");

        assertThat(error.code()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
        assertThat(error.message()).contains("sandbox credits");
        assertThat(error.requestId()).isEqualTo("req-1");
        assertThat(error.fieldErrors()).isEmpty();
    }

    @Test
    void apiExceptionPreservesHttpStatus() {
        ApiException ex =
                new ApiException(HttpStatus.CONFLICT, ErrorCode.USERNAME_TAKEN, "Username is taken.");

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(ErrorCode.USERNAME_TAKEN);
    }
}
