package com.mbc.puf.common.jpa;

import com.mbc.puf.common.enums.RiskLevel;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * RiskLevel ↔ DB risk_level('안전'/'주의'/'위험') 변환.
 * DB 라벨이 자바 상수명(SAFE/WARNING/DANGER)과 달라서 @Enumerated 대신 이 변환기를 쓴다.
 * autoApply = true 라서 엔티티의 RiskLevel 필드에는 아무 어노테이션도 안 붙여도 자동 적용된다.
 */
@Converter(autoApply = true)
public class RiskLevelConverter implements AttributeConverter<RiskLevel, String> {

    @Override
    public String convertToDatabaseColumn(RiskLevel level) {
        return level == null ? null : level.getDbValue();
    }

    @Override
    public RiskLevel convertToEntityAttribute(String dbValue) {
        return DbEnums.fromDbValue(RiskLevel.class, dbValue);
    }
}
