package com.mbc.puf.plate.service;

import java.math.BigDecimal;

/** Select manual-review candidates without changing OCR output or recognition status. */
public final class PlateReviewPolicy {
    private static final BigDecimal FLOOR = new BigDecimal("0.970");
    private PlateReviewPolicy() {}

    public static boolean requiresReview(String number, BigDecimal confidence, BigDecimal recentAverage) {
        if (number == null || confidence == null) return false;
        BigDecimal threshold = FLOOR;
        if (recentAverage != null && recentAverage.signum() >= 0 && recentAverage.compareTo(BigDecimal.ONE) <= 0)
            threshold = threshold.max(recentAverage);
        return confidence.compareTo(threshold) < 0;
    }
}
