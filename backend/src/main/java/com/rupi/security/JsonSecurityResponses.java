package com.rupi.security;

import com.rupi.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

final class JsonSecurityResponses {

    private JsonSecurityResponses() {}

    static void writeUnauthenticated(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required.");
    }

    static void writeForbidden(HttpServletResponse response) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Not allowed.");
    }

    private static void write(
            HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        String requestId = Optional.ofNullable(MDC.get(RequestIdFilter.MDC_KEY))
                .filter(id -> !id.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter()
                .write(
                        "{\"code\":\""
                                + code
                                + "\",\"message\":\""
                                + message
                                + "\",\"requestId\":\""
                                + requestId
                                + "\",\"fieldErrors\":[]}");
    }
}
