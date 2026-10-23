package com.mbc.puf.common.enums;

/**
 * DB enum 라벨이 Java 상수명(name())과 다를 때 구현하는 인터페이스.
 * 구현하지 않은 enum은 name()이 그대로 DB 값으로 쓰인다.
 */
public interface DbEnum {
    String getDbValue();
}
