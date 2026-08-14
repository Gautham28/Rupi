package com.rupi.api;

import com.rupi.api.dto.AccountResponse;
import com.rupi.security.AuthenticatedUser;
import com.rupi.service.AuthService;
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
        return authService.currentAccount(user);
    }
}
