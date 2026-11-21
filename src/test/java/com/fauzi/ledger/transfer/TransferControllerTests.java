package com.fauzi.ledger.transfer;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TransferControllerTests {

    @Test
    void listsTransfersForAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        TransferService transferService = mock(TransferService.class);
        TransferController controller = new TransferController(transferService);
        when(transferService.list(userId, false)).thenReturn(List.of());

        List<?> result = controller.getTransfers(jwt(userId, "USER"));

        assertEquals(List.of(), result);
        verify(transferService).list(userId, false);
    }

    @Test
    void adminCanRequestAllTransfers() {
        UUID adminId = UUID.randomUUID();
        TransferService transferService = mock(TransferService.class);
        TransferController controller = new TransferController(transferService);
        when(transferService.list(adminId, true)).thenReturn(List.of());

        controller.getTransfers(jwt(adminId, "ADMIN"));

        verify(transferService).list(adminId, true);
    }

    private static Jwt jwt(UUID userId, String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", userId.toString())
                .claim("roles", List.of(role))
                .build();
    }
}
