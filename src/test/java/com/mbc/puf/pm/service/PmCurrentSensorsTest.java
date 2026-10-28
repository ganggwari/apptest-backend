package com.mbc.puf.pm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.mbc.puf.common.enums.PmEquipmentType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

class PmCurrentSensorsTest {
    @Test
    void returnsLatestRawRowForEverySensorTypeIncludingZeroAndNull() {
        for (PmEquipmentType type : List.of(PmEquipmentType.PUMP, PmEquipmentType.CCTV,
                PmEquipmentType.DISPLAY, PmEquipmentType.WATER_LEVEL)) {
            EntityManager em = mock(EntityManager.class);
            Query query = mock(Query.class);
            when(em.createNativeQuery(anyString())).thenAnswer(call -> {
                assertThat((String) call.getArgument(0))
                    .contains("FROM " + PmSensorServiceImpl.tableOf(type), "ORDER BY s.recorded_at DESC LIMIT 1")
                    .doesNotContain("AVG(", "pm_score", "date_trunc");
                return query;
            });
            when(query.setParameter(anyString(), any())).thenReturn(query);
            var columns = PmSensorServiceImpl.columnsOf(type);
            var at = LocalDateTime.of(2026, 10, 28, 12, 10);
            Object[] row = new Object[columns.size() + 1];
            row[0] = Timestamp.valueOf(at);
            row[1] = 0.0;
            when(query.getResultList()).thenReturn(Collections.singletonList(row));
            var service = new PmQueryServiceImpl(null, null, null, null);
            ReflectionTestUtils.setField(service, "em", em);
            var result = service.currentSensors(15L, type);
            assertThat(result.recordedAt()).isEqualTo(at);
            assertThat(result.values()).hasSize(columns.size()).containsEntry(columns.get(0), 0.0);
            if (columns.size() > 1) assertThat(result.values()).containsEntry(columns.get(1), null);
            verify(query).setParameter("equipId", 15L);
        }
    }

    @Test
    void missingSamplesAndRoadDoNotInventCurrentValues() {
        EntityManager em = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        var service = new PmQueryServiceImpl(null, null, null, null);
        ReflectionTestUtils.setField(service, "em", em);
        var missing = service.currentSensors(15L, PmEquipmentType.PUMP);
        assertThat(missing.recordedAt()).isNull();
        assertThat(missing.values()).isEmpty();
        clearInvocations(em);
        assertThat(service.currentSensors(15L, PmEquipmentType.ROAD)).isNull();
        verifyNoInteractions(em);
    }
}
