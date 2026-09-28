package com.serjnn.ProductService.externaltask.enums;

public enum ExternalTaskState {
    PENDING,
    IN_FLIGHT_POST,
    FAILED_CHECK_NEEDED,
    IN_FLIGHT_AUDIT,
    SUCCEEDED,
    FATAL_FAILED
}
