package com.mbc.puf.common.security;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.member.repository.ManagerRegionRepository;
import com.mbc.puf.underpass.entity.Underpass;
import com.mbc.puf.underpass.repository.UnderpassRepository;

/**
 * [공통 조건 묶음 3: Scope] 관리자 조회·조작 범위를 한 곳에서 결정한다.
 * 관리자는 모두 지역담당자이고, 담당 지역은 manager_region 에 여러 개 있을 수 있다(매 요청 DB에서 읽음).
 *  - 조회: regionId 를 주면 그 지역만(담당 지역이 아니면 403), 안 주면 담당 지역 전체
 *          underpassId 를 주면 그 지하차도만(담당 지역 밖이면 403, 없으면 404)
 *  - 조작: requireOwnable(loginUser, regionId) — 담당 지역이 아니면 403
 *  - 관리자가 아닌 사용자(로그인 안 한 고객 화면 등)는 요청값 그대로
 */
@Component
public class ScopeResolver {

    // 담당 지역이 하나도 없을 때 IN 절이 비지 않도록 넣는 값 (존재할 수 없는 id → 결과 0건)
    private static final List<Long> NONE = List.of(-1L);

    private final UnderpassRepository underpassRepository;
    private final ManagerRegionRepository managerRegionRepository;

    public ScopeResolver(UnderpassRepository underpassRepository, ManagerRegionRepository managerRegionRepository) {
        this.underpassRepository = underpassRepository;
        this.managerRegionRepository = managerRegionRepository;
    }

    public ScopeQuery resolve(LoginUser loginUser, ScopeQuery requested) {
        Long regionId = requested == null ? null : requested.getRegionId();
        Long underpassId = requested == null ? null : requested.getUnderpassId();

        Underpass underpass = null;
        if (underpassId != null) {
            underpass = underpassRepository.findByIdAndDeletedFalse(underpassId)
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                            "존재하지 않는 지하차도입니다. id=" + requested.getUnderpassId()));
        }

        if (loginUser == null || !loginUser.isManager()) {
            return ScopeQuery.resolved(regionId == null ? null : List.of(regionId), underpassId);
        }

        List<Long> mine = managerRegionRepository.findRegionIds(loginUser.id());
        if (regionId != null && !mine.contains(regionId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "담당 지역이 아닌 지역은 조회할 수 없습니다.");
        }
        if (underpass != null && !mine.contains(underpass.getRegionId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "담당 지역 밖의 지하차도는 조회할 수 없습니다.");
        }
        List<Long> regionIds = regionId != null ? List.of(regionId) : (mine.isEmpty() ? NONE : mine);
        return ScopeQuery.resolved(regionIds, underpassId);
    }

    // 지역 범위만 필요할 때
    public List<Long> regionsOf(LoginUser loginUser, Long requestedRegionId) {
        return resolve(loginUser, ScopeQuery.of(requestedRegionId, null)).getRegionIds();
    }

    // 관리자의 담당 지역 목록 (화면 표시·드롭다운용)
    public List<Long> myRegionIds(LoginUser loginUser) {
        return managerRegionRepository.findRegionIds(loginUser.id());
    }

    // 등록·수정·삭제·차단 같은 조작은 담당 지역 안에서만
    public void requireOwnable(LoginUser loginUser, Long regionId) {
        if (regionId == null || !managerRegionRepository.findRegionIds(loginUser.id()).contains(regionId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "담당 지역 외에는 등록·수정·삭제할 수 없습니다.");
        }
    }
}
