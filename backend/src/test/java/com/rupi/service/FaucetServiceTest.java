package com.rupi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rupi.api.dto.FaucetResponse;
import com.rupi.api.error.ApiException;
import com.rupi.api.error.ErrorCode;
import com.rupi.config.RupiProperties;
import com.rupi.domain.Account;
import com.rupi.domain.AccountRepository;
import com.rupi.domain.DemoCreditEvent;
import com.rupi.domain.DemoCreditEventRepository;
import com.rupi.security.AuthenticatedUser;
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

@ExtendWith(MockitoExtension.class)
class FaucetServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DemoCreditEventRepository demoCreditEventRepository;

    private FaucetService faucetService;

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final AuthenticatedUser user = new AuthenticatedUser(userId, "alice");

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
                        new BigDecimal("5000.00")),
                new RupiProperties.RateLimit(10, 10));
        faucetService = new FaucetService(accountRepository, demoCreditEventRepository, properties);
    }

    @Test
    void grantsCreditsAndWritesAuditEvent() {
        UUID key = UUID.randomUUID();
        Account account = new Account(accountId, userId, new BigDecimal("1000.00"), Instant.now());

        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(demoCreditEventRepository.findByAccountIdAndIdempotencyKey(accountId, key))
                .thenReturn(Optional.empty());
        when(demoCreditEventRepository.existsRecentGrant(eq(accountId), any())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(demoCreditEventRepository.save(any(DemoCreditEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FaucetResponse response = faucetService.grant(user, key);

        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.granted()).isEqualByComparingTo("500.00");
        assertThat(response.balance()).isEqualByComparingTo("1500.00");

        ArgumentCaptor<DemoCreditEvent> eventCaptor = ArgumentCaptor.forClass(DemoCreditEvent.class);
        verify(demoCreditEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getIdempotencyKey()).isEqualTo(key);
        assertThat(eventCaptor.getValue().getAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void replaysSameIdempotencyKeyWithoutDoubleGrant() {
        UUID key = UUID.randomUUID();
        Account account = new Account(accountId, userId, new BigDecimal("1500.00"), Instant.now());
        DemoCreditEvent existing =
                new DemoCreditEvent(UUID.randomUUID(), accountId, new BigDecimal("500.00"), key, Instant.now());

        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(demoCreditEventRepository.findByAccountIdAndIdempotencyKey(accountId, key))
                .thenReturn(Optional.of(existing));

        FaucetResponse response = faucetService.grant(user, key);

        assertThat(response.granted()).isEqualByComparingTo("500.00");
        assertThat(response.balance()).isEqualByComparingTo("1500.00");
        verify(demoCreditEventRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void rejectsWhenCooldownActive() {
        UUID key = UUID.randomUUID();
        Account account = new Account(accountId, userId, new BigDecimal("1000.00"), Instant.now());

        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(demoCreditEventRepository.findByAccountIdAndIdempotencyKey(accountId, key))
                .thenReturn(Optional.empty());
        when(demoCreditEventRepository.existsRecentGrant(eq(accountId), any())).thenReturn(true);

        assertThatThrownBy(() -> faucetService.grant(user, key))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getCode()).isEqualTo(ErrorCode.FAUCET_COOLDOWN);
                });
    }

    @Test
    void rejectsWhenCapWouldBeExceeded() {
        UUID key = UUID.randomUUID();
        Account account = new Account(accountId, userId, new BigDecimal("4700.00"), Instant.now());

        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(demoCreditEventRepository.findByAccountIdAndIdempotencyKey(accountId, key))
                .thenReturn(Optional.empty());
        when(demoCreditEventRepository.existsRecentGrant(eq(accountId), any())).thenReturn(false);

        assertThatThrownBy(() -> faucetService.grant(user, key))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getCode()).isEqualTo(ErrorCode.FAUCET_CAP_REACHED);
                });
    }

    @Test
    void rejectsWhenDemoDisabled() {
        RupiProperties disabled = new RupiProperties(
                new RupiProperties.Jwt("change-me-dev-only-use-at-least-32-chars!!", 86_400_000L),
                new RupiProperties.Cors("http://localhost:5173"),
                new RupiProperties.Demo(
                        false,
                        new BigDecimal("1000.00"),
                        new BigDecimal("500.00"),
                        24,
                        new BigDecimal("5000.00")),
                new RupiProperties.RateLimit(10, 10));
        FaucetService disabledService =
                new FaucetService(accountRepository, demoCreditEventRepository, disabled);

        assertThatThrownBy(() -> disabledService.grant(user, UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(api.getCode()).isEqualTo(ErrorCode.FORBIDDEN);
                });
    }
}
