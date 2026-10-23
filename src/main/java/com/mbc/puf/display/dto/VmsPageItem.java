package com.mbc.puf.display.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 전광판(VMS) 페이지 1장. content 는 줄바꿈("\n")으로 이어진 문구이고, lines 는 그걸 줄마다 나눈 것이다.
 * color 는 RED / YELLOW / GREEN (자동 페이지는 종류에 따라, 수동 페이지는 YELLOW).
 * defaultPage 가 true 면 DB 에 없는 기본 문구(표출할 자동·수동 페이지가 하나도 없을 때)라는 뜻이다.
 */
public record VmsPageItem(Long id, Long vmsId, Integer pageNo, Long underpassId, String underpassName,
                          String content, List<String> lines, String color, boolean manual, boolean defaultPage,
                          Long updatedBy, LocalDateTime updatedAt) {
}
