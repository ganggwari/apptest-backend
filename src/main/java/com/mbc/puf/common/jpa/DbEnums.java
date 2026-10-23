package com.mbc.puf.common.jpa;

import com.mbc.puf.common.enums.DbEnum;

/** DB enum 라벨 문자열 → 자바 enum 변환 (네이티브 쿼리 결과처럼 문자열로 받은 값을 바꿀 때 사용) */
public final class DbEnums {

    private DbEnums() {
    }

    // DbEnum 구현 enum(RiskLevel)은 getDbValue(), 나머지는 name()으로 비교
    public static <E extends Enum<E>> E fromDbValue(Class<E> type, String dbValue) {
        if (dbValue == null) {
            return null;
        }
        for (E e : type.getEnumConstants()) {
            if (toDbValue(e).equals(dbValue)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unknown DB enum value '" + dbValue + "' for " + type.getSimpleName());
    }

    public static String toDbValue(Enum<?> e) {
        if (e == null) {
            return null;
        }
        return (e instanceof DbEnum dbEnum) ? dbEnum.getDbValue() : e.name();
    }
}
