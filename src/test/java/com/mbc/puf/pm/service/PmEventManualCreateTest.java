package com.mbc.puf.pm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.security.ScopeResolver;
import com.mbc.puf.pm.dto.PmEventCreateRequest;
import com.mbc.puf.pm.entity.PmEvent;
import com.mbc.puf.pm.repository.PmActionRepository;
import com.mbc.puf.pm.repository.PmEventRepository;
import com.mbc.puf.stream.StreamPublisher;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

class PmEventManualCreateTest {

    private PmEventServiceImpl service(EntityManager em, PmEventRepository events) throws Exception {
        var s = new PmEventServiceImpl(events, mock(PmActionRepository.class), mock(ScopeResolver.class),
                mock(StreamPublisher.class), mock(PmStubModeService.class));
        var f = PmEventServiceImpl.class.getDeclaredField("em");
        f.setAccessible(true);
        f.set(s, em);
        return s;
    }

    private EntityManager equipmentRow(String type) {
        var em = mock(EntityManager.class);
        var q = mock(Query.class);
        when(em.createNativeQuery(any(String.class))).thenReturn(q);
        when(q.setParameter(any(String.class), any())).thenReturn(q);
        when(q.getResultList()).thenReturn(java.util.Collections.<Object>singletonList(new Object[] {type, 2L}));
        return em;
    }

    private PmEventCreateRequest request(String content) {
        var r = new PmEventCreateRequest();
        r.setEquipId(5L);
        r.setContent(content);
        return r;
    }

    @Test
    void openEventBlocksManualRegistration() throws Exception {
        var events = mock(PmEventRepository.class);
        when(events.findByEquipIdInAndStatusIn(any(), any())).thenReturn(List.of(new PmEvent()));
        var ex = assertThrows(BusinessException.class, () -> service(equipmentRow("PUMP"), events).create(null, request("x")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(events, never()).saveAndFlush(any());
    }

    @Test
    void roadEquipmentAndBlankContentAreRejected() throws Exception {
        var events = mock(PmEventRepository.class);
        assertThrows(BusinessException.class, () -> service(equipmentRow("ROAD"), events).create(null, request("x")));
        assertThrows(BusinessException.class, () -> service(equipmentRow("PUMP"), events).create(null, request("  ")));
        verify(events, never()).saveAndFlush(any());
    }

    @Test
    void ingestCooldownIsNotConsulted() throws Exception {
        // 수동 등록은 PmActionRepository(쿨다운 판단용 findMaintenance)를 조회하지 않는다
        var events = mock(PmEventRepository.class);
        when(events.findByEquipIdInAndStatusIn(any(), any())).thenReturn(List.of());
        var actions = mock(PmActionRepository.class);
        var s = new PmEventServiceImpl(events, actions, mock(ScopeResolver.class), mock(StreamPublisher.class),
                mock(PmStubModeService.class));
        var f = PmEventServiceImpl.class.getDeclaredField("em");
        f.setAccessible(true);
        f.set(s, equipmentRow("PUMP"));
        try { s.create(null, request("소음")); } catch (RuntimeException ignore) { /* 저장 이후 단계는 목으로 끝까지 못 간다 */ }
        verify(actions, never()).findMaintenance(any());
        verify(events).saveAndFlush(any());
    }
}
