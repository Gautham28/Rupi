package com.rupi.api;

import com.rupi.api.dto.FaucetResponse;
import com.rupi.api.error.ApiException;
import com.rupi.api.error.ErrorCode;
import com.rupi.security.AuthenticatedUser;
import com.rupi.service.FaucetService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

    private final FaucetService faucetService;

    public DemoController(FaucetService faucetService) {
        this.faucetService = faucetService;
    }

    @PostMapping("/faucet")
    public FaucetResponse faucet(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey) {
        if (user == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Authentication required.");
        }
        return faucetService.grant(user, idempotencyKey);
    }
}
