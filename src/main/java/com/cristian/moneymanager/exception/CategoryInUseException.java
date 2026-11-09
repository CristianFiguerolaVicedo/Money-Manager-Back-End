package com.cristian.moneymanager.exception;

import lombok.Getter;

/** Thrown when deleting a category that still has incomes/expenses and no reassignment target was given. */
@Getter
public class CategoryInUseException extends RuntimeException {

    private final long transactionCount;

    public CategoryInUseException(long transactionCount) {
        super("Esta categoría tiene " + transactionCount + " movimiento(s). Elige otra categoría a la que moverlos antes de borrarla.");
        this.transactionCount = transactionCount;
    }
}
