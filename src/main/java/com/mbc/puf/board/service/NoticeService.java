package com.mbc.puf.board.service;

import com.mbc.puf.board.dto.NoticeCreateRequest;
import com.mbc.puf.board.dto.NoticeDetailResponse;
import com.mbc.puf.board.dto.NoticeListItem;
import com.mbc.puf.board.dto.NoticeUpdateRequest;
import com.mbc.puf.common.enums.NoticeCategory;
import com.mbc.puf.common.response.PageResponse;

public interface NoticeService {

    // 목록 (제목+내용 검색, 분류·중요공지 필터, 페이징). keyword/category/important 는 null 가능
    PageResponse<NoticeListItem> getNoticeList(String keyword, NoticeCategory category, Boolean important, int page, int size);

    // 상세. countView 가 true 일 때만 조회수를 1 올린다 (컨트롤러가 쿠키로 "처음 보는 글인지" 판단해서 넘긴다)
    NoticeDetailResponse getNotice(Long id, boolean countView);

    // 등록. authorId 는 컨트롤러가 세션에서 꺼내 넘긴다
    NoticeDetailResponse createNotice(Long authorId, NoticeCreateRequest request);

    // 수정
    NoticeDetailResponse updateNotice(Long id, NoticeUpdateRequest request);

    // 삭제 (소프트 삭제)
    void deleteNotice(Long id);
}