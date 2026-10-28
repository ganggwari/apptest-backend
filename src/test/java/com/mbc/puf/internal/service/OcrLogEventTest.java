package com.mbc.puf.internal.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.internal.dto.PlateLogCreateRequest;
import com.mbc.puf.plate.entity.PlateLog;
import com.mbc.puf.plate.repository.PlateLogRepository;
import com.mbc.puf.stream.StreamPublisher;
import com.mbc.puf.underpass.entity.*;
import com.mbc.puf.underpass.repository.*;

class OcrLogEventTest {
    record Fixture(InternalIngestServiceImpl service, PlateLogRepository logs,
                   StreamPublisher stream, PlateLogCreateRequest request) {}
    Fixture fixture(String confidence) throws Exception {
        Map<Class<?>, Object> deps = new HashMap<>();
        var constructor = InternalIngestServiceImpl.class.getConstructors()[0];
        var args = Arrays.stream(constructor.getParameterTypes())
                .map(t -> deps.computeIfAbsent(t, k -> mock(k))).toArray();
        var service = (InternalIngestServiceImpl) constructor.newInstance(args);
        var camera = mock(Camera.class); when(camera.getId()).thenReturn(226L); when(camera.getUnderpassId()).thenReturn(1L);
        when(((CameraRepository)deps.get(CameraRepository.class)).findByIdAndDeletedFalse(226L)).thenReturn(Optional.of(camera));
        var underpass = mock(Underpass.class); when(underpass.getId()).thenReturn(1L); when(underpass.getRegionId()).thenReturn(4L);
        when(((UnderpassRepository)deps.get(UnderpassRepository.class)).findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(underpass));
        var now = LocalDateTime.of(2026, 10, 27, 15, 0);
        when(((DbClock)deps.get(DbClock.class)).now()).thenReturn(now);
        var request = new PlateLogCreateRequest(); request.setCameraId(226L);
        request.setDetectedNum("12가3456"); request.setConfidence(new BigDecimal(confidence)); request.setDetectedAt(now);
        var logs = (PlateLogRepository) deps.get(PlateLogRepository.class);
        when(logs.save(any(PlateLog.class))).thenAnswer(invocation -> {
            PlateLog row = invocation.getArgument(0);
            var id = PlateLog.class.getDeclaredField("id"); id.setAccessible(true); id.set(row, 51L); return row;
        });
        return new Fixture(service, logs, (StreamPublisher)deps.get(StreamPublisher.class), request);
    }
    @Test void ordinaryOcrLogPublishesCreateWithoutRequiringTransitOrReview() throws Exception {
        var f = fixture("0.99"); var result = f.service.receivePlateLog(f.request, null);
        assertEquals(51L, result.getId());
        verify(f.stream).dataChangedToAdmins(4L, "plate-log", "CREATE", 51L);
        verifyNoMoreInteractions(f.stream);
    }
    @Test void reviewQueuedLogStillPublishesGeneralLogCreateOnce() throws Exception {
        var f = fixture("0.50"); f.service.receivePlateLog(f.request, null);
        verify(f.stream, times(1)).dataChangedToAdmins(4L, "plate-log", "CREATE", 51L);
        verify(f.stream).publishToAdmins(eq(4L), eq("plate-queue-add"), any());
    }
    @Test void duplicateDoesNotPublishAnotherCreate() throws Exception {
        var f = fixture("0.99"); var old = mock(PlateLog.class);
        when(f.logs.findFirstByCameraIdAndDetectedNumAndDetectedAtBetweenOrderByDetectedAtDesc(
                eq(226L), eq("12가3456"), any(), any())).thenReturn(Optional.of(old));
        f.service.receivePlateLog(f.request, null);
        verify(f.logs, never()).save(any(PlateLog.class)); verifyNoInteractions(f.stream);
    }
}
