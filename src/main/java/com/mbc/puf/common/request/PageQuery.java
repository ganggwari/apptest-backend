package com.mbc.puf.common.request;

/**
 * [공통 조건 묶음 1: Page] 모든 목록 API 공통.  ?page=1&size=20
 * 컨트롤러에서 @ModelAttribute PageQuery page 로 받는다. 응답은 항상 PageResponse.
 */
public class PageQuery {

    private int page = 1;    // 1부터
    private int size = 20;   // 기본 20, 최대 100 (목록에 따라 상한이 다를 수 있음)

    public PageQuery() {
    }

    public PageParams toParams() {
        return PageParams.of(page, size);
    }

    public PageParams toParams(int maxSize) {
        return PageParams.of(page, size, maxSize);
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
