package com.mbc.puf.common.jpa;

import com.mbc.puf.common.enums.PmLevel;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * PmLevel ↔ DB pm_level('안전'/'주의'/'위험') 변환.
 * DB 라벨이 자바 상수명(SAFE/CAUTION/DANGER)과 달라서 @Enumerated 대신 이 변환기를 쓴다.
 * autoApply = true 라서 엔티티의 PmLevel 필드에는 아무 어노테이션도 안 붙여도 자동 적용된다.
 */
@Converter(autoApply = true)
public class PmLevelConverter implements AttributeConverter<PmLevel, String> {

    @Override
    public String convertToDatabaseColumn(PmLevel level) {
        return level == null ? null : level.getDbValue();
    }

    @Override
    public PmLevel convertToEntityAttribute(String dbValue) {
        return DbEnums.fromDbValue(PmLevel.class, dbValue);
    }
}
