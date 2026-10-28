package com.mbc.puf.handover.service;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import jakarta.persistence.*;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.common.security.*;
import com.mbc.puf.handover.repository.ShiftHandoverRepository;
import com.mbc.puf.member.entity.*;
import com.mbc.puf.member.repository.*;
import com.mbc.puf.pm.dto.*;
import com.mbc.puf.pm.service.PmReportService;
import com.mbc.puf.stream.StreamPublisher;
import tools.jackson.databind.ObjectMapper;

class HandoverPopupTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 27, 14, 30);
    private final LoginUser user = new LoginUser(1L, "MANAGER", "REGIONAL_ADMIN");
    private final ShiftHandoverRepository saved = mock(ShiftHandoverRepository.class);
    private final ShiftRepository shifts = mock(ShiftRepository.class);
    private final ManagerRepository managers = mock(ManagerRepository.class);
    private final PmReportService pm = mock(PmReportService.class);
    private final ScopeResolver scopes = mock(ScopeResolver.class);
    private final DbClock clock = mock(DbClock.class);
    private final EntityManager em = mock(EntityManager.class);
    private HandoverServiceImpl service() throws Exception {
        when(clock.now()).thenReturn(now);
        when(scopes.regionsOf(user, null)).thenReturn(List.of(9L));
        var service = new HandoverServiceImpl(saved, shifts, managers, pm, scopes,
                mock(StreamPublisher.class), clock, new ObjectMapper());
        var field = HandoverServiceImpl.class.getDeclaredField("em");
        field.setAccessible(true); field.set(service, em);
        return service;
    }
    private PmPeriodSummary live(int count) {
        var event = new PmEventItem(); event.setEventId(7L); event.setName("P-1-1");
        event.setUnderpassName("테스트 지하차도"); event.setCreatedAt(now.minusDays(2));
        var counts = new PmLevelCounts(0, 0, 0, 0);
        return new PmPeriodSummary(now.minusHours(8), now, counts, counts, List.of(),
                0, List.of(), 0, List.of(), count, List.of(event), List.of(), 0, List.of(), null, null);
    }
    private Shift shift(long id, int from, int to) {
        var s = mock(Shift.class); when(s.getId()).thenReturn(id); when(s.getName()).thenReturn("근무조" + id);
        when(s.getStartTime()).thenReturn(LocalTime.of(from, 0)); when(s.getEndTime()).thenReturn(LocalTime.of(to, 0));
        return s;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Tuple person(long id, String name) {
        var tuple = mock(Tuple.class);
        List<TupleElement<?>> fields = new ArrayList<>();
        String[] keys = {"manager_id", "name", "phone"}; Object[] values = {id, name, "01000000000"};
        for (int i = 0; i < keys.length; i++) {
            TupleElement e = mock(TupleElement.class); when(e.getAlias()).thenReturn(keys[i]);
            fields.add(e); when(tuple.get(e)).thenReturn(values[i]);
        }
        when(tuple.getElements()).thenReturn(fields); return tuple;
    }
    @Test void unassignedShiftUsesLivePmAndNeverLoadsAnUnrelatedSavedReport() throws Exception {
        var service = service();
        when(managers.findById(1L)).thenReturn(Optional.empty());
        when(pm.summarize(anyList(), isNull(), any(), any())).thenReturn(live(7), live(9));
        var first = service.getPrevious(user); var second = service.getPrevious(user);
        assertEquals("이전 근무자 미배정", first.authorName());
        assertTrue(first.summary().get("demo").asBoolean());
        assertTrue(first.summary().get("pmLive").asBoolean());
        assertFalse(first.written()); assertNull(first.id());
        assertEquals(7, first.summary().get("counts").get("pmPending").asInt());
        assertEquals(9, second.summary().get("counts").get("pmPending").asInt());
        assertTrue(first.summary().get("activities").toString().contains("P-1-1"));
        verifyNoInteractions(saved, em);
    }
    @Test void previousWorkersAreLoadedWithinAssignedRegionsAndMultipleNamesAreKept() throws Exception {
        var service = service(); var me = mock(Manager.class); when(me.getShiftId()).thenReturn(2L);
        var morning = shift(1, 6, 14); var afternoon = shift(2, 14, 22);
        when(managers.findById(1L)).thenReturn(Optional.of(me));
        when(shifts.findById(2L)).thenReturn(Optional.of(afternoon));
        when(shifts.findAllByOrderByStartTimeAsc()).thenReturn(List.of(morning, afternoon));
        var query = mock(Query.class); when(em.createNativeQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        var people = List.of(person(2, "이전근무자A"), person(3, "이전근무자B"));
        when(query.getResultList()).thenReturn(people);
        when(pm.summarize(anyList(), isNull(), any(), any())).thenReturn(live(2));
        var result = service.getPrevious(user);
        assertEquals("이전근무자A · 이전근무자B", result.authorName());
        assertNull(result.managerId());
        assertEquals(now.toLocalDate().atTime(6, 0), result.windowStart());
        assertEquals(now.toLocalDate().atTime(14, 0), result.windowEnd());
        verify(query).setParameter("regionIds", List.of(9L));
        verify(query).setParameter("shiftId", 1L);
        verify(query).setParameter("currentManagerId", 1L);
        verifyNoInteractions(saved);
    }
    @Test void previousNightShiftCrossesMidnight() throws Exception {
        var service = service(); var me = mock(Manager.class); when(me.getShiftId()).thenReturn(1L);
        var morning = shift(1, 6, 14); var night = shift(3, 22, 6);
        when(managers.findById(1L)).thenReturn(Optional.of(me));
        when(shifts.findById(1L)).thenReturn(Optional.of(morning));
        when(shifts.findAllByOrderByStartTimeAsc()).thenReturn(List.of(morning, night));
        var query = mock(Query.class); when(em.createNativeQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        when(pm.summarize(anyList(), isNull(), any(), any())).thenReturn(live(0));
        var result = service.getPrevious(user);
        assertEquals(LocalDateTime.of(2026, 10, 26, 22, 0), result.windowStart());
        assertEquals(LocalDateTime.of(2026, 10, 27, 6, 0), result.windowEnd());
    }
}
