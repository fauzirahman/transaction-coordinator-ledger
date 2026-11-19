package com.fauzi.ledger.transfer;

public class TransferConflictException extends RuntimeException {
    public TransferConflictException(String message) {
        super(message);
    }
}
