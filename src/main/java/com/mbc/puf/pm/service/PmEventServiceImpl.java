package com.mbc.puf.pm.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.PmActionKind;
import com.mbc.puf.common.enums.PmEventStatus;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.jpa.NativeSql;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.ScopeResolver;
import com.mbc.puf.pm.dto.PmActionItem;
import com.mbc.puf.pm.dto.PmActionRequest;
import com.mbc.puf.pm.dto.PmEventItem;
import com.mbc.puf.pm.dto.PmEventStatusRequest;
import com.mbc.puf.pm.entity.PmAction;
import com.mbc.puf.pm.entity.PmEvent;
import com.mbc.puf.pm.repository.PmActionRepository;
import com.mbc.puf.pm.repository.PmEventRepository;
import com.mbc.puf.stream.StreamPublisher;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 예지보전 점검 처리.
 *
 * 상태 흐름: 확인필요(CHECK_NEEDED) → 점검중(IN_PROGRESS) → 조치완료(RESOLVED) / 기타(ETC)
 *   · 확인필요에서 바로 조치완료·기타로 끝내는 것도 가능. 끝난 이벤트는 다시 열지 않는다.
 *   · 이벤트가 끝나야 같은 설비에서 다음 점검 대상·알림이 새로 만들어진다 (PmIngestServiceImpl).
 *   · 조치완료·기타로 끝낼 때는 조치 내용이 필수이고, pm_action 에 남아 점검 이력이 된다.
 * 바뀐 내용은 SSE data-changed 로 같은 지역 관리자 화면에 알린다 (resource: pm-event / pm-action).
 */
@Service
public class PmEventServiceImpl implements PmEventService {

    private static final String EVENT_SELECT = """
            SELECT ev.id AS event_id, e.id AS equip_id, CAST(e.type AS text) AS type, e.name AS name, u.id AS underpass_id,
                   u.name AS underpass_name, u.region_id AS region_id, CAST(ev.trigger_type AS text) AS trigger_type, CAST(ev.level AS text) AS level, ev.score AS score,
                   CAST(ev.status AS text) AS status, ev.created_at AS created_at,
                   ls.score AS current_score, CAST(ls.level AS text) AS current_level
             FROM pm_event ev
             JOIN pm_equipment e ON e.id = ev.equip_id
             JOIN underpass u ON u.id = e.underpass_id
             LEFT JOIN LATERAL (SELECT s.score, s.level FROM pm_score s WHERE s.equip_id = e.id
                                 ORDER BY s.predicted_at DESC LIMIT 1) ls ON TRUE
             WHERE ev.id = :eventId
            """;
    private static final String ACTION_SELECT = """
            SELECT a.id AS action_id, ev.id AS event_id, e.id AS equip_id, CAST(e.type AS text) AS type, e.name AS name,
                   u.id AS underpass_id, u.name AS underpass_name, u.region_id AS region_id, a.content AS content, CAST(a.kind AS text) AS kind,
                   a.manager_id AS manager_id, emp.name AS manager_name, a.created_at AS created_at,
                   CAST(ev.level AS text) AS event_level, ev.score AS score_before,
                   (SELECT s.score FROM pm_score s WHERE s.equip_id = e.id AND s.predicted_at > a.created_at
                     ORDER BY s.predicted_at ASC LIMIT 1) AS score_after
             FROM pm_action a
             JOIN pm_event ev ON ev.id = a.event_id
             JOIN pm_equipment e ON e.id = ev.equip_id
             JOIN underpass u ON u.id = e.underpass_id
             LEFT JOIN manager m ON m.id = a.manager_id
             LEFT JOIN employee emp ON emp.id = m.employee_id
             WHERE a.id = :actionId
            """;

    @PersistenceContext
    private EntityManager em;

    private final PmStubModeService stubModes;
    private final PmEventRepository eventRepository;
    private final PmActionRepository actionRepository;
    private final ScopeResolver scopeResolver;
    private final StreamPublisher streamPublisher;

    public PmEventServiceImpl(PmEventRepository eventRepository, PmActionRepository actionRepository,
                              ScopeResolver scopeResolver, StreamPublisher streamPublisher, PmStubModeService stubModes) {
        this.stubModes=stubModes;
        this.eventRepository = eventRepository;
        this.actionRepository = actionRepository;
        this.scopeResolver = scopeResolver;
        this.streamPublisher = streamPublisher;
    }

    @Override
    @Transactional
    public PmEventItem changeStatus(LoginUser loginUser, Long eventId, PmEventStatusRequest request) {
        PmEvent event = findOwnedEvent(loginUser, eventId);
        if (event.getTriggerType() == com.mbc.puf.common.enums.PmTriggerType.SINKHOLE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "노면 탐지는 장비 현황의 사진 확인 영역에서 처리해 주세요.");
        }
        PmEventStatus from = event.getStatus();
        PmEventStatus to = request.getStatus();
        String content = request.getContent() == null ? "" : request.getContent().trim();

        if (!from.isOpen()) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 끝난 점검입니다. 다시 열 수 없습니다.");
        }
        if (to == PmEventStatus.CHECK_NEEDED || to == from) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "상태는 점검중(IN_PROGRESS), 조치완료(RESOLVED), 기타(ETC) 중에서 지금과 다른 값으로 바꿀 수 있습니다.");
        }
        if (!to.isOpen() && content.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "점검을 끝낼 때는 조치 내용을 적어 주세요.");
        }

        event.setStatus(to);
        eventRepository.save(event);
        if(to==PmEventStatus.RESOLVED)stubModes.resolved(loginUser,event.getEquipId());
        if (!content.isEmpty()) {
            saveAction(loginUser, eventId, content, kindOf(to));
        }
        Long regionId = regionOfEvent(eventId);
        streamPublisher.dataChangedToAdmins(regionId, "pm-event", "UPDATE", eventId);
        return loadEvent(eventId);
    }

    @Override
    @Transactional
    public PmActionItem addAction(LoginUser loginUser, Long eventId, PmActionRequest request) {
        findOwnedEvent(loginUser, eventId);
        PmAction action = saveAction(loginUser, eventId, request.getContent().trim(), PmActionKind.RECORD);
        streamPublisher.dataChangedToAdmins(regionOfEvent(eventId), "pm-action", "CREATE", action.getId());
        List<PmActionItem> rows = new NativeSql(ACTION_SELECT).param("actionId", action.getId())
                .list(em, PmActionItem.class);
        return rows.get(0);
    }

    // ------------------------------------------------------------------

    /** 바꾼 상태에 맞는 기록 종류 */
    private static PmActionKind kindOf(PmEventStatus to) {
        return switch (to) {
            case RESOLVED -> PmActionKind.RESOLVED;
            case ETC -> PmActionKind.ETC;
            default -> PmActionKind.START;
        };
    }

    private PmAction saveAction(LoginUser loginUser, Long eventId, String content, PmActionKind kind) {
        PmAction action = new PmAction();
        action.setEventId(eventId);
        action.setContent(content);
        action.setManagerId(loginUser.id());
        action.setKind(kind);
        PmAction saved = actionRepository.saveAndFlush(action);
        em.refresh(saved);   // created_at(DB 기본값)을 읽어 온다
        return saved;
    }

    /** 이벤트 1건 (없으면 404, 담당 지역 밖이면 403) */
    private PmEvent findOwnedEvent(LoginUser loginUser, Long eventId) {
        PmEvent event = eventRepository.findById(eventId).orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 점검 대상입니다. id=" + eventId));
        scopeResolver.requireOwnable(loginUser, regionOfEvent(eventId));
        return event;
    }

    private Long regionOfEvent(Long eventId) {
        Number region = (Number) em.createNativeQuery("""
                SELECT u.region_id FROM pm_event ev
                  JOIN pm_equipment e ON e.id = ev.equip_id
                  JOIN underpass u ON u.id = e.underpass_id
                 WHERE ev.id = :eventId
                """).setParameter("eventId", eventId).getSingleResult();
        return region.longValue();
    }

    private PmEventItem loadEvent(Long eventId) {
        em.flush();
        return new NativeSql(EVENT_SELECT).param("eventId", eventId).list(em, PmEventItem.class).get(0);
    }
}
