package com.serjnn.ProductService.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record IdsRequest(
        @NotEmpty
        List<@NotNull @Positive Long> ids
) {}
