package com.rupi.api;

import com.rupi.api.dto.TransactionPageResponse;
import com.rupi.api.dto.TransactionResponse;
import com.rupi.api.dto.TransferRequest;
import com.rupi.api.error.ApiException;
import com.rupi.api.error.ErrorCode;
import com.rupi.security.AuthenticatedUser;
import com.rupi.service.TransferService;
import com.rupi.service.TransferService.TransferExecution;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransferService transferService;

    public TransactionController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody TransferRequest request,
            HttpServletResponse response) {
        requireUser(user);
        TransferExecution execution = transferService.transfer(user, idempotencyKey, request);
        if (execution.replayed()) {
            response.setHeader("Idempotent-Replayed", "true");
        }
        return ResponseEntity.status(execution.httpStatus()).body(execution.body());
    }

    @GetMapping
    public TransactionPageResponse list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        requireUser(user);
        return transferService.history(user, limit, cursor);
    }

    private static void requireUser(AuthenticatedUser user) {
        if (user == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Authentication required.");
        }
    }
}
