package com.rupi.api;

import com.rupi.api.dto.AccountResponse;
import com.rupi.api.error.ApiException;
import com.rupi.api.error.ErrorCode;
import com.rupi.security.AuthenticatedUser;
import com.rupi.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AuthService authService;

    public AccountController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Authentication required.");
        }
        return authService.currentAccount(user);
    }
}
