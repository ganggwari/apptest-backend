package com.mbc.puf.sinkhole;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import jakarta.validation.constraints.*;

/** YOLO -> Spring. 신뢰도는 0~1이며 고장 위험 점수가 아니다. */
public record SinkholeResultRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String jobId,
        @NotBlank String underpassName,
        Long cameraId,
        @NotNull OffsetDateTime analyzedAt,
        @NotNull Boolean detected,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
        @Min(0) int detectionCount,
        @DecimalMin("0.0") Double frameSeconds,
        @NotBlank @Size(max = 80) String modelVersion) {
}
