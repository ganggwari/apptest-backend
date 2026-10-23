package com.mbc.puf.common.response;

import java.util.List;

/** 목록 조회 응답의 공통 형식. ApiResponse 의 data 에 담겨 나간다. */
public class PageResponse<T> {

    private final List<T> items;      // 이번 페이지의 항목
    private final int page;           // 현재 페이지 (1부터)
    private final int size;           // 페이지 크기
    private final long totalItems;    // 조건에 맞는 전체 항목 수
    private final int totalPages;     // 전체 페이지 수

    public PageResponse(List<T> items, int page, int size, long totalItems) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.totalItems = totalItems;
        this.totalPages = (int) ((totalItems + size - 1) / size);
    }

    public List<T> getItems() { return items; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalItems() { return totalItems; }
    public int getTotalPages() { return totalPages; }
}
