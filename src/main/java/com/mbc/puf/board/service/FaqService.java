package com.mbc.puf.board.service;

import com.mbc.puf.board.dto.FaqCreateRequest;
import com.mbc.puf.board.dto.FaqDetailResponse;
import com.mbc.puf.board.dto.FaqListItem;
import com.mbc.puf.board.dto.FaqOrderRequest;
import com.mbc.puf.board.dto.FaqUpdateRequest;
import com.mbc.puf.common.enums.FaqCategory;
import com.mbc.puf.common.response.PageResponse;

import java.util.List;

public interface FaqService {

    // 목록 (질문+답변 검색, 페이징). keyword는 null 가능
    PageResponse<FaqListItem> getFaqList(String keyword, FaqCategory category, int page, int size);

    // 자주 보는 질문: 조회수 높은 순 상위 limit 개 (1~20, 기본 5)
    List<FaqListItem> getPopularFaqs(int limit);

    // 상세. countView 가 true 일 때만 조회수를 1 올린다 (컨트롤러가 쿠키로 판단해서 넘긴다)
    FaqDetailResponse getFaq(Long id, boolean countView);

    // 순서 변경: 보낸 id 들이 차지하던 순서 번호를 보낸 순서대로 다시 나눠 갖는다. 없는 id 가 있으면 404
    void reorderFaqs(FaqOrderRequest request);

    // 등록. authorId는 컨트롤러가 세션에서 꺼내 넘긴다
    FaqDetailResponse createFaq(Long authorId, FaqCreateRequest request);

    // 수정
    FaqDetailResponse updateFaq(Long id, FaqUpdateRequest request);

    // 삭제 (소프트 삭제)
    void deleteFaq(Long id);
}