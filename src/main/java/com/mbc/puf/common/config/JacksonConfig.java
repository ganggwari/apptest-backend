package com.mbc.puf.common.config;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;

/**
 * JSON 응답의 날짜·시간 표기를 맞춘다.
 *  - LocalDateTime 은 초 단위까지만 내보낸다 (예: 2026-10-21T19:04:32). 밀리초·마이크로초는 붙이지 않는다.
 *  - DTO 필드에 @JsonFormat 으로 따로 지정한 곳은 그 형식을 그대로 쓴다.
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Bean
    public JsonMapperBuilderCustomizer localDateTimeToSecondsCustomizer() {
        SimpleModule module = new SimpleModule("puf-local-date-time-seconds");
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(SECONDS));
        return builder -> builder.addModule(module);
    }
}
