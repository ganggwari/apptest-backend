package com.mbc.puf.common.util;

import java.security.SecureRandom;

public final class VerificationCodeGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private VerificationCodeGenerator() {
    }

    // 6자리 숫자 (앞자리가 0이어도 6자리 유지)
    public static String generate() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }
}