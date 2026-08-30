package com.serjnn.ProductService.exceptions;

public class InvalidDiscountException extends ProductServiceException {
    public InvalidDiscountException(String message) {
        super(message);
    }
}
