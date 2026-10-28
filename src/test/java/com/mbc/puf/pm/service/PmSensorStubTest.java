package com.mbc.puf.pm.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.mbc.puf.common.jpa.DbClock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

class PmSensorStubTest {
    @Test
    void appendBindsAllFourTypesAndLimitsCatchUp() {
        EntityManager em = mock(EntityManager.class);
        DbClock clock = mock(DbClock.class);
        LocalDateTime now = LocalDateTime.of(2026, 10, 25, 12, 0);
        when(clock.now()).thenReturn(now);
        List<String> sqls = new ArrayList<>();
        List<Query> inserts = new ArrayList<>();
        when(em.createNativeQuery(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            Query q = mock(Query.class);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            if (sql.contains("pg_try_advisory_xact_lock")) {
                when(q.getSingleResult()).thenReturn(true);
            } else if (sql.startsWith("INSERT")) {
                sqls.add(sql);
                inserts.add(q);
                when(q.executeUpdate()).thenReturn(2);
            }
            return q;
        });
        var service = new PmSensorBackfillServiceImpl(clock, 14, 4, 0);
        ReflectionTestUtils.setField(service, "em", em);
        assertThat(service.appendRecent(12)).isEqualTo(8);
        assertThat(sqls).hasSize(4).allSatisfy(sql -> assertThat(sql)
                .contains("sensor_interval_sec", "date_bin", "e.last_at", ":maxSamples - 1", "ON CONFLICT DO NOTHING")
                .doesNotContain("{N}", "{SEV}", "{TABLE}"));
        String[] types = {"PUMP", "CCTV", "DISPLAY", "WATER_LEVEL"};
        for (int i = 0; i < types.length; i++) {
            verify(inserts.get(i)).setParameter("type", types[i]);
            verify(inserts.get(i)).setParameter("now", now);
            verify(inserts.get(i)).setParameter("maxSamples", 12);
        }
    }

    @Test
    void concurrentInstanceAndInvalidLimitsDoNotInsert() {
        EntityManager em = mock(EntityManager.class);
        Query lock = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(lock);
        when(lock.getSingleResult()).thenReturn(false);
        var service = new PmSensorBackfillServiceImpl(mock(DbClock.class), 14, 4, 0);
        ReflectionTestUtils.setField(service, "em", em);
        assertThat(service.appendRecent(12)).isZero();
        verify(lock, never()).executeUpdate();
        assertThatThrownBy(() -> service.appendRecent(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.appendRecent(361)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startupBackfillUsesActualSensorRowsInsteadOfMetadataFlag() {
        EntityManager em = mock(EntityManager.class);
        DbClock clock = mock(DbClock.class);
        when(clock.now()).thenReturn(LocalDateTime.of(2026, 10, 25, 12, 0));
        List<String> targetSql = new ArrayList<>();
        List<String> insertSql = new ArrayList<>();
        when(em.createNativeQuery(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            Query q = mock(Query.class);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            if (sql.contains("pg_advisory_xact_lock")) {
                when(q.getSingleResult()).thenReturn(true);
            } else if (sql.startsWith("SELECT COUNT")) {
                when(q.getSingleResult()).thenReturn(1L);
            } else if (sql.startsWith("SELECT e.id")) {
                targetSql.add(sql);
                when(q.getResultList()).thenReturn(List.of());
            } else if (sql.startsWith("INSERT")) {
                insertSql.add(sql);
            }
            return q;
        });
        var service = new PmSensorBackfillServiceImpl(clock, 14, 4, 0);
        ReflectionTestUtils.setField(service, "em", em);
        var result = service.backfill(null, null, null, false, null);
        assertThat(result.skipped()).isEqualTo(4);
        assertThat(insertSql).isEmpty();
        assertThat(targetSql).hasSize(4).allSatisfy(sql -> assertThat(sql)
                .contains("NOT EXISTS", "s.equip_id = e.id")
                .doesNotContain("e.data_started_at IS NULL"));
    }

    @Test
    void schedulerWaitsForBackfillAndRecoversAfterFailedTick() {
        PmSensorBackfillService sensors = mock(PmSensorBackfillService.class);
        var scheduler = new PmSensorStubScheduler(sensors, 12);
        scheduler.tick();
        verifyNoInteractions(sensors);
        scheduler.onReady();
        when(sensors.appendRecent(12)).thenThrow(new IllegalStateException("temporary DB failure")).thenReturn(4);
        assertThatCode(scheduler::tick).doesNotThrowAnyException();
        assertThatCode(scheduler::tick).doesNotThrowAnyException();
        verify(sensors, times(2)).appendRecent(12);
        assertThatThrownBy(() -> new PmSensorStubScheduler(sensors, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
