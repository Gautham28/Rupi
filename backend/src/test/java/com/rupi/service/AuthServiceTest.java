package com.rupi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.rupi.security.JwtService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        RupiProperties properties = new RupiProperties(
                new RupiProperties.Jwt("change-me-dev-only-use-at-least-32-chars!!", 86_400_000L),
                new RupiProperties.Cors("http://localhost:5173"),
                new RupiProperties.Demo(
                        true,
                        new BigDecimal("1000.00"),
                        new BigDecimal("500.00"),
                        24,
                        new BigDecimal("5000.00")));
        authService = new AuthService(
                appUserRepository, accountRepository, passwordEncoder, jwtService, properties);
    }

    @Test
    void registerCreatesUserAccountAndToken() {
        when(appUserRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("correct-horse-battery")).thenReturn("hashed");
        when(jwtService.createToken(any(), any())).thenReturn("jwt-token");
        when(appUserRepository.saveAndFlush(any(AppUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.saveAndFlush(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response =
                authService.register(new RegisterRequest("Alice", "correct-horse-battery"));

        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.balance()).isEqualByComparingTo("1000.00");
        assertThat(response.token()).isEqualTo("jwt-token");

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("alice");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    void registerRejectsTakenUsername() {
        when(appUserRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(
                        () -> authService.register(new RegisterRequest("alice", "correct-horse-battery")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getCode()).isEqualTo(ErrorCode.USERNAME_TAKEN);
                });

        verify(appUserRepository, never()).saveAndFlush(any());
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AppUser user = new AppUser(userId, "alice", "hashed", Instant.now());
        Account account = new Account(accountId, userId, new BigDecimal("1000.00"), Instant.now());

        when(appUserRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-horse-battery", "hashed")).thenReturn(true);
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(jwtService.createToken(userId, "alice")).thenReturn("jwt-token");

        AuthResponse response = authService.login(new LoginRequest("Alice", "correct-horse-battery"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.balance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void loginRejectsBadPassword() {
        AppUser user = new AppUser(UUID.randomUUID(), "alice", "hashed", Instant.now());
        when(appUserRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong-password")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
