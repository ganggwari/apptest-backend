package com.mbc.puf.common.security;

import java.io.Serializable;

/**
 * 로그인한 사용자 정보. 세션에 저장되므로 Serializable 이어야 한다(세션을 파일/DB/Redis에 저장하는 경우 대비).
 * 관리자의 담당 지역은 여기 넣지 않는다. 담당 지역이 바뀌면 바로 반영되도록 ScopeResolver 가 매 요청마다
 * manager_region 에서 읽는다.
 *
 * @param id   고객 id 또는 관리자 id
 * @param type "CUSTOMER" / "MANAGER"
 * @param role "CUSTOMER" / "REGIONAL_ADMIN"  (권한명은 ROLE_ + role)
 */
public record LoginUser(Long id, String type, String role) implements Serializable {

    public boolean isManager() {
        return "MANAGER".equals(type);
    }
}
