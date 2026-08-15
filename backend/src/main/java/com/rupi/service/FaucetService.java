package com.rupi.service;

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
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FaucetService {

    private final AccountRepository accountRepository;
    private final DemoCreditEventRepository demoCreditEventRepository;
    private final RupiProperties properties;

    public FaucetService(
            AccountRepository accountRepository,
            DemoCreditEventRepository demoCreditEventRepository,
            RupiProperties properties) {
        this.accountRepository = accountRepository;
        this.demoCreditEventRepository = demoCreditEventRepository;
        this.properties = properties;
    }

    @Transactional
    public FaucetResponse grant(AuthenticatedUser user, UUID idempotencyKey) {
        if (!properties.demo().enabled()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN,
                    "Demo faucet is disabled.");
        }

        Account owned = accountRepository
                .findByUserId(user.userId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Account was not found."));

        Account account = accountRepository
                .findByIdForUpdate(owned.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Account was not found."));

        Optional<DemoCreditEvent> existing =
                demoCreditEventRepository.findByAccountIdAndIdempotencyKey(
                        account.getId(), idempotencyKey);
        if (existing.isPresent()) {
            return new FaucetResponse(
                    account.getId(), existing.get().getAmount(), account.getBalance());
        }

        Instant cooldownSince =
                Instant.now().minus(Duration.ofHours(properties.demo().faucetCooldownHours()));
        if (demoCreditEventRepository.existsRecentGrant(account.getId(), cooldownSince)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.FAUCET_COOLDOWN,
                    "Demo faucet can only be used once every "
                            + properties.demo().faucetCooldownHours()
                            + " hours.");
        }

        BigDecimal grant = properties.demo().faucetAmount().setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal nextBalance = account.getBalance().add(grant);
        if (nextBalance.compareTo(properties.demo().maxBalance()) > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.FAUCET_CAP_REACHED,
                    "Demo balance cannot exceed " + properties.demo().maxBalance().toPlainString()
                            + " sandbox credits.");
        }

        account.setBalance(nextBalance);
        accountRepository.save(account);
        demoCreditEventRepository.save(
                new DemoCreditEvent(
                        UUID.randomUUID(), account.getId(), grant, idempotencyKey, Instant.now()));

        return new FaucetResponse(account.getId(), grant, account.getBalance());
    }
}
