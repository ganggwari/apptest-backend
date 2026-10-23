package com.mbc.puf.member.service;

import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.member.dto.CustomerListItem;

public interface CustomerSearchService {

    /**
     * 관리자 화면의 고객 목록 조회.
     * keyword 는 아이디·이름·이메일·전화번호·차량번호를 한꺼번에 찾고(일치 점수 순), 나머지 조건은 걸면 좁혀진다.
     */
    PageResponse<CustomerListItem> search(String keyword, String loginId, String name, String email,
                                          String phone, String carNum, boolean includeWithdrawn,
                                          int page, int size);
}