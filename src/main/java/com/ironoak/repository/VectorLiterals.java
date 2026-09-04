package com.ironoak.repository;

import java.util.StringJoiner;

/**
 * Formats a float[] as the pgvector text literal the native queries cast to vector:
 * "[0.12,-0.03,0.98]". Kept in one place so the format cannot drift between callers.
 */
public final class VectorLiterals {

    private VectorLiterals() {
    }

    public static String of(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("Embedding must not be empty");
        }
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Embedding contains a non-finite value");
            }
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }
}
