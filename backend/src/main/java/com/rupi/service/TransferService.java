package com.rupi.service;

import com.rupi.api.dto.TransactionPageResponse;
import com.rupi.api.dto.TransactionResponse;
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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransferService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");
    private static final String ROUTE = "POST /api/v1/transactions";

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final IdempotencyCache idempotencyCache;
    private final TransactionTemplate transactionTemplate;

    public TransferService(
            AccountRepository accountRepository,
            TransferRepository transferRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            IdempotencyCache idempotencyCache,
            TransactionTemplate transactionTemplate) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.idempotencyCache = idempotencyCache;
        this.transactionTemplate = transactionTemplate;
    }

    public TransferExecution transfer(
            AuthenticatedUser user, UUID idempotencyKey, TransferRequest request) {
        BigDecimal amount = normalizeAmount(request.amount());
        String requestHash = idempotencyCache.fingerprint(
                user.userId(),
                ROUTE,
                request.toAccountId() + "|" + amount.toPlainString());

        IdempotencyCache.CachedLookup cached =
                idempotencyCache.lookup(user.userId(), idempotencyKey);
        if (cached.kind() == IdempotencyCache.CachedLookup.Kind.IN_PROGRESS) {
            throw inProgress();
        }
        if (cached.kind() == IdempotencyCache.CachedLookup.Kind.COMPLETED) {
            return TransferExecution.replay(TransactionResponses.fromJson(cached.responseJson()));
        }

        if (!idempotencyCache.tryBegin(user.userId(), idempotencyKey)) {
            throw inProgress();
        }

        try {
            TransferExecution execution = transactionTemplate.execute(
                    status -> executeInDatabase(user, idempotencyKey, request, amount, requestHash));
            if (execution == null) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        ErrorCode.INTERNAL_ERROR,
                        "Transfer did not complete.");
            }
            idempotencyCache.complete(
                    user.userId(), idempotencyKey, TransactionResponses.toJson(execution.body()));
            return execution;
        } catch (RuntimeException ex) {
            idempotencyCache.clear(user.userId(), idempotencyKey);
            throw ex;
        }
    }

    private TransferExecution executeInDatabase(
            AuthenticatedUser user,
            UUID idempotencyKey,
            TransferRequest request,
            BigDecimal amount,
            String requestHash) {
        Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findByUserIdAndIdempotencyKey(
                user.userId(), idempotencyKey);
        if (existing.isPresent()) {
            return replayOrReject(existing.get(), requestHash, user.userId());
        }

        IdempotencyRecord claim = IdempotencyRecord.inProgress(
                UUID.randomUUID(), user.userId(), idempotencyKey, requestHash, Instant.now());
        try {
            idempotencyRecordRepository.saveAndFlush(claim);
        } catch (DataIntegrityViolationException ex) {
            IdempotencyRecord raced = idempotencyRecordRepository
                    .findByUserIdAndIdempotencyKey(user.userId(), idempotencyKey)
                    .orElseThrow(() -> ex);
            return replayOrReject(raced, requestHash, user.userId());
        }

        Account sender = accountRepository
                .findByUserId(user.userId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        ErrorCode.INTERNAL_ERROR,
                        "Sender account is missing."));
        Account recipient = accountRepository
                .findById(request.toAccountId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Recipient account was not found."));

        if (sender.getId().equals(recipient.getId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "Cannot transfer to your own account.");
        }

        UUID firstId =
                sender.getId().compareTo(recipient.getId()) < 0 ? sender.getId() : recipient.getId();
        UUID secondId = firstId.equals(sender.getId()) ? recipient.getId() : sender.getId();

        Account first = accountRepository
                .findByIdForUpdate(firstId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Account was not found."));
        Account second = accountRepository
                .findByIdForUpdate(secondId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Account was not found."));

        Account lockedSender = first.getId().equals(sender.getId()) ? first : second;
        Account lockedRecipient = first.getId().equals(recipient.getId()) ? first : second;

        if (lockedSender.getBalance().compareTo(amount) < 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.INSUFFICIENT_FUNDS,
                    "Account does not have enough sandbox credits.");
        }

        lockedSender.setBalance(lockedSender.getBalance().subtract(amount));
        lockedRecipient.setBalance(lockedRecipient.getBalance().add(amount));
        accountRepository.save(lockedSender);
        accountRepository.save(lockedRecipient);

        Instant now = Instant.now();
        Transfer transfer = new Transfer(
                UUID.randomUUID(),
                lockedSender.getId(),
                lockedRecipient.getId(),
                amount,
                "COMPLETED",
                now);
        transferRepository.save(transfer);

        TransactionResponse body = TransactionResponses.fromTransfer(transfer, lockedSender.getId());
        claim.markCompleted(201, TransactionResponses.toJson(body), transfer.getId(), now);
        idempotencyRecordRepository.save(claim);

        return TransferExecution.created(body);
    }

    @Transactional(readOnly = true)
    public TransactionPageResponse history(AuthenticatedUser user, Integer limit, String cursor) {
        int pageSize = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
        Account account = accountRepository
                .findByUserId(user.userId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Account was not found."));

        Instant cursorCreatedAt = null;
        UUID cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String decoded =
                        new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                String[] parts = decoded.split("\\|", 2);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("bad cursor");
                }
                cursorCreatedAt = Instant.parse(parts[0]);
                cursorId = UUID.fromString(parts[1]);
            } catch (RuntimeException ex) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Cursor is invalid.");
            }
        }

        boolean hasCursor = cursorCreatedAt != null && cursorId != null;
        List<Transfer> rows = transferRepository.findHistoryPage(
                account.getId(), hasCursor, cursorCreatedAt, cursorId, pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<Transfer> page = hasMore ? rows.subList(0, pageSize) : rows;
        List<TransactionResponse> items = page.stream()
                .map(transfer -> TransactionResponses.fromTransfer(transfer, account.getId()))
                .toList();

        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            Transfer last = page.get(page.size() - 1);
            nextCursor = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            (last.getCreatedAt() + "|" + last.getId()).getBytes(StandardCharsets.UTF_8));
        }
        return new TransactionPageResponse(items, nextCursor, hasMore);
    }

    private TransferExecution replayOrReject(
            IdempotencyRecord record, String requestHash, UUID userId) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Idempotency key was already used with a different request.");
        }
        if (IdempotencyRecord.STATE_IN_PROGRESS.equals(record.getState())) {
            throw inProgress();
        }
        if (record.getResponseBody() != null) {
            return TransferExecution.replay(TransactionResponses.fromJson(record.getResponseBody()));
        }
        if (record.getTransferId() != null) {
            Transfer transfer = transferRepository
                    .findById(record.getTransferId())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            ErrorCode.INTERNAL_ERROR,
                            "Completed transfer is missing."));
            Account sender = accountRepository
                    .findByUserId(userId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            ErrorCode.INTERNAL_ERROR,
                            "Sender account is missing."));
            return TransferExecution.replay(
                    TransactionResponses.fromTransfer(transfer, sender.getId()));
        }
        throw inProgress();
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount.scale() > 2) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "Amount must have at most 2 decimal places.");
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Amount must be positive.");
        }
        if (normalized.compareTo(MAX_AMOUNT) > 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "Amount exceeds the maximum of 1000000.00.");
        }
        return normalized;
    }

    private static ApiException inProgress() {
        return new ApiException(
                HttpStatus.CONFLICT,
                ErrorCode.IDEMPOTENCY_IN_PROGRESS,
                "A request with this idempotency key is already in progress.");
    }

    public record TransferExecution(TransactionResponse body, boolean replayed) {
        static TransferExecution created(TransactionResponse body) {
            return new TransferExecution(body, false);
        }

        static TransferExecution replay(TransactionResponse body) {
            return new TransferExecution(body, true);
        }

        public int httpStatus() {
            return replayed ? 200 : 201;
        }
    }
}
