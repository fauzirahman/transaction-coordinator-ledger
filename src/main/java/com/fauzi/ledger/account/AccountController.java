package com.fauzi.ledger.account;

import com.fauzi.ledger.account.dto.AccountSummaryResponse;
import com.fauzi.ledger.transfer.TransferNotFoundException;
import com.fauzi.ledger.transfer.TransferService;
import com.fauzi.ledger.transfer.dto.LedgerEntryResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountRepository accountRepository;
    private final com.fauzi.ledger.ledger.LedgerEntryRepository ledgerEntryRepository;

    public AccountController(
            AccountRepository accountRepository,
            com.fauzi.ledger.ledger.LedgerEntryRepository ledgerEntryRepository
    ) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AccountSummaryResponse> getAccounts(@AuthenticationPrincipal Jwt jwt) {
        var roles = jwt.getClaimAsStringList("roles");
        List<Account> accounts = roles != null && roles.contains("ADMIN")
                ? accountRepository.findAllByOrderByCreatedAtDesc()
                : accountRepository.findAllByOwnerUserIdOrderByCreatedAtDesc(
                        UUID.fromString(jwt.getSubject())
                );
        return accounts.stream()
                .map(account -> new AccountSummaryResponse(
                        account.getId(),
                        account.getAccountNumber(),
                        account.getAccountName(),
                        account.getCurrency(),
                        account.getBalance(),
                        account.getStatus()
                ))
                .toList();
    }

    @GetMapping("/{id}/balance")
    @Transactional(readOnly = true)
    public AccountBalanceResponse getBalance(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Account account = findVisibleAccount(id, jwt);
        return new AccountBalanceResponse(
                account.getId(), account.getBalance(), account.getCurrency()
        );
    }

    @GetMapping("/{id}/ledger")
    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> getLedger(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        findVisibleAccount(id, jwt);
        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(id)
                .stream()
                .map(TransferService::toLedgerEntry)
                .toList();
    }

    private Account findVisibleAccount(UUID id, Jwt jwt) {
        var roles = jwt.getClaimAsStringList("roles");
        if (roles != null && roles.contains("ADMIN")) {
            return accountRepository.findById(id)
                    .orElseThrow(() -> new TransferNotFoundException("Account not found"));
        }
        return accountRepository.findOwnedById(id, UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new TransferNotFoundException("Account not found"));
    }

    public record AccountBalanceResponse(UUID accountId, BigDecimal balance, String currency) {
    }
}
