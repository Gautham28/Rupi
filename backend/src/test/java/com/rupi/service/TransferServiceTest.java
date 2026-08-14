package com.rupi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rupi.api.dto.TransferRequest;
import com.rupi.api.error.ApiException;
import com.rupi.api.error.ErrorCode;
import com.rupi.domain.Account;
import com.rupi.domain.AccountRepository;
import com.rupi.domain.IdempotencyRecord;
import com.rupi.domain.IdempotencyRecordRepository;
import com.rupi.domain.Transfer;
import com.rupi.domain.TransferRepository;
import com.rupi.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Mock
    private IdempotencyCache idempotencyCache;

    @Mock
    private TransactionTemplate transactionTemplate;

    private TransferService transferService;

    private final UUID userId = UUID.randomUUID();
    private final UUID senderAccountId = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final UUID recipientAccountId = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private final AuthenticatedUser user = new AuthenticatedUser(userId, "alice");

    @BeforeEach
    void setUp() {
        transferService = new TransferService(
                accountRepository,
                transferRepository,
                idempotencyRecordRepository,
                idempotencyCache,
                transactionTemplate);
    }

    private void stubTransactionTemplate() {
        when(transactionTemplate.execute(any())).thenAnswer((Answer<Object>) invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void transfersDebitAndCreditWithOrderedLocks() {
        stubTransactionTemplate();
        UUID key = UUID.randomUUID();
        TransferRequest request = new TransferRequest(recipientAccountId, new BigDecimal("25.50"));
        Account sender = new Account(senderAccountId, userId, new BigDecimal("1000.00"), Instant.now());
        Account recipient =
                new Account(recipientAccountId, UUID.randomUUID(), new BigDecimal("100.00"), Instant.now());

        when(idempotencyCache.fingerprint(eq(userId), any(), any())).thenReturn("hash");
        when(idempotencyCache.lookup(userId, key)).thenReturn(IdempotencyCache.CachedLookup.miss());
        when(idempotencyCache.tryBegin(userId, key)).thenReturn(true);
        when(idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, key))
                .thenReturn(Optional.empty());
        when(idempotencyRecordRepository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencyRecordRepository.save(any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(sender));
        when(accountRepository.findById(recipientAccountId)).thenReturn(Optional.of(recipient));
        when(accountRepository.findByIdForUpdate(senderAccountId)).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdForUpdate(recipientAccountId)).thenReturn(Optional.of(recipient));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransferService.TransferExecution execution = transferService.transfer(user, key, request);

        assertThat(execution.replayed()).isFalse();
        assertThat(execution.httpStatus()).isEqualTo(201);
        assertThat(execution.body().amount()).isEqualByComparingTo("25.50");
        assertThat(execution.body().direction()).isEqualTo("OUTGOING");
        assertThat(sender.getBalance()).isEqualByComparingTo("974.50");
        assertThat(recipient.getBalance()).isEqualByComparingTo("125.50");
        verify(accountRepository).findByIdForUpdate(senderAccountId);
        verify(accountRepository).findByIdForUpdate(recipientAccountId);
        verify(idempotencyCache).complete(eq(userId), eq(key), any());
    }

    @Test
    void rejectsInsufficientFunds() {
        stubTransactionTemplate();
        UUID key = UUID.randomUUID();
        TransferRequest request = new TransferRequest(recipientAccountId, new BigDecimal("5000.00"));
        Account sender = new Account(senderAccountId, userId, new BigDecimal("100.00"), Instant.now());
        Account recipient =
                new Account(recipientAccountId, UUID.randomUUID(), new BigDecimal("0.00"), Instant.now());

        when(idempotencyCache.fingerprint(eq(userId), any(), any())).thenReturn("hash");
        when(idempotencyCache.lookup(userId, key)).thenReturn(IdempotencyCache.CachedLookup.miss());
        when(idempotencyCache.tryBegin(userId, key)).thenReturn(true);
        when(idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, key))
                .thenReturn(Optional.empty());
        when(idempotencyRecordRepository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(sender));
        when(accountRepository.findById(recipientAccountId)).thenReturn(Optional.of(recipient));
        when(accountRepository.findByIdForUpdate(senderAccountId)).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdForUpdate(recipientAccountId)).thenReturn(Optional.of(recipient));

        assertThatThrownBy(() -> transferService.transfer(user, key, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        verify(transferRepository, never()).save(any());
        verify(idempotencyCache).clear(userId, key);
    }

    @Test
    void redisInProgressReturnsConflict() {
        UUID key = UUID.randomUUID();
        when(idempotencyCache.fingerprint(eq(userId), any(), any())).thenReturn("hash");
        when(idempotencyCache.lookup(userId, key)).thenReturn(IdempotencyCache.CachedLookup.inProgress());

        assertThatThrownBy(() -> transferService.transfer(
                        user, key, new TransferRequest(recipientAccountId, new BigDecimal("10.00"))))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                        .isEqualTo(ErrorCode.IDEMPOTENCY_IN_PROGRESS));
    }
}
