package com.rupi.service;

import com.rupi.api.dto.AccountResponse;
import com.rupi.api.dto.AuthResponse;
import com.rupi.api.dto.LoginRequest;
import com.rupi.api.dto.RegisterRequest;
import com.rupi.api.error.ApiException;
import com.rupi.api.error.ErrorCode;
import com.rupi.config.RupiProperties;
import com.rupi.domain.Account;
import com.rupi.domain.AccountRepository;
import com.rupi.domain.AppUser;
import com.rupi.domain.AppUserRepository;
import com.rupi.security.AuthenticatedUser;
import com.rupi.security.JwtService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RupiProperties properties;

    public AuthService(
            AppUserRepository appUserRepository,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RupiProperties properties) {
        this.appUserRepository = appUserRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        if (appUserRepository.existsByUsername(username)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ErrorCode.USERNAME_TAKEN, "Username is already taken.");
        }

        Instant now = Instant.now();
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        BigDecimal startingBalance =
                properties.demo().signupCredits().setScale(2, RoundingMode.UNNECESSARY);

        AppUser user =
                new AppUser(userId, username, passwordEncoder.encode(request.password()), now);
        Account account = new Account(accountId, userId, startingBalance, now);

        try {
            appUserRepository.saveAndFlush(user);
            accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ErrorCode.USERNAME_TAKEN, "Username is already taken.");
        }

        String token = jwtService.createToken(userId, username);
        return new AuthResponse(token, userId, username, accountId, startingBalance);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String username = normalizeUsername(request.username());
        AppUser user = appUserRepository
                .findByUsername(username)
                .orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        Account account = accountRepository
                .findByUserId(user.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        ErrorCode.INTERNAL_ERROR,
                        "Account is missing for this user."));

        String token = jwtService.createToken(user.getId(), user.getUsername());
        return new AuthResponse(
                token, user.getId(), user.getUsername(), account.getId(), account.getBalance());
    }

    @Transactional(readOnly = true)
    public AccountResponse currentAccount(AuthenticatedUser authenticatedUser) {
        Account account = accountRepository
                .findByUserId(authenticatedUser.userId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Account was not found."));
        return new AccountResponse(
                account.getId(),
                authenticatedUser.userId(),
                authenticatedUser.username(),
                account.getBalance(),
                properties.demo().enabled());
    }

    private ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Invalid username or password.");
    }

    static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
