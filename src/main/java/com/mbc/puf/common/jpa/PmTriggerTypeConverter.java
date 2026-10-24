package com.mbc.puf.common.jpa;

import com.mbc.puf.common.enums.PmTriggerType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** PmTriggerType ↔ DB pm_trigger_type('위험진입'/'급상승'/'미수신') 변환. PmLevelConverter 와 같은 방식 */
@Converter(autoApply = true)
public class PmTriggerTypeConverter implements AttributeConverter<PmTriggerType, String> {

    @Override
    public String convertToDatabaseColumn(PmTriggerType type) {
        return type == null ? null : type.getDbValue();
    }

    @Override
    public PmTriggerType convertToEntityAttribute(String dbValue) {
        return DbEnums.fromDbValue(PmTriggerType.class, dbValue);
    }
}
