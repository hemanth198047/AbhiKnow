package com.abhiknow.domain;

import java.util.Objects;

public record Identifier(IdScheme scheme, String value) {
    public Identifier {
        Objects.requireNonNull(scheme);
        Objects.requireNonNull(value);
    }
}
