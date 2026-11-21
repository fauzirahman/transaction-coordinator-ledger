package com.fauzi.ledger.account;

import com.fauzi.ledger.account.dto.AccountSummaryResponse;
import com.fauzi.ledger.ledger.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AccountControllerTests {

    @Test
    void listsOnlyAccountsOwnedByAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(accountId);
        when(account.getAccountNumber()).thenReturn("A-10001");
        when(account.getAccountName()).thenReturn("Checking");
        when(account.getCurrency()).thenReturn("USD");
        when(account.getBalance()).thenReturn(new BigDecimal("25.0000"));
        when(account.getStatus()).thenReturn("ACTIVE");

        AccountRepository accountRepository = mock(AccountRepository.class);
        when(accountRepository.findAllByOwnerUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(account));
        AccountController controller = new AccountController(
                accountRepository, mock(LedgerEntryRepository.class)
        );

        List<AccountSummaryResponse> result = controller.getAccounts(jwt(userId, "USER"));

        assertEquals(List.of(new AccountSummaryResponse(
                accountId, "A-10001", "Checking", "USD",
                new BigDecimal("25.0000"), "ACTIVE"
        )), result);
        verify(accountRepository).findAllByOwnerUserIdOrderByCreatedAtDesc(userId);
        verify(accountRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    private static Jwt jwt(UUID userId, String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", userId.toString())
                .claim("roles", List.of(role))
                .build();
    }
}
