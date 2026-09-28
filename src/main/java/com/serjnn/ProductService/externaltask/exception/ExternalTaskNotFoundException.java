package com.serjnn.ProductService.externaltask.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class ExternalTaskNotFoundException extends RuntimeException {
    private final UUID businessKey;
    private final Long id;

    public ExternalTaskNotFoundException(UUID businessKey) {
        super(String.format("External task with business key '%s' not found", businessKey));
        this.businessKey = businessKey;
        this.id = null;
    }

    public ExternalTaskNotFoundException(Long id) {
        super(String.format("External task with ID '%d' not found", id));
        this.businessKey = null;
        this.id = id;
    }
}
