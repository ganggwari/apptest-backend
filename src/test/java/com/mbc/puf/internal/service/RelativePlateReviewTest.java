package com.mbc.puf.internal.service;

import com.mbc.puf.plate.service.PlateReviewPolicy;
import com.mbc.puf.plate.repository.PlateLogRepository;
import com.mbc.puf.plate.entity.PlateLog;
import com.mbc.puf.underpass.repository.CameraRepository;
import com.mbc.puf.underpass.entity.Camera;
import com.mbc.puf.car.repository.CarRepository;
import com.mbc.puf.car.entity.Car;
import com.mbc.puf.internal.dto.PlateLogCreateRequest;
import com.mbc.puf.common.enums.ReviewStatus;
import com.mbc.puf.common.enums.RecognitionStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RelativePlateReviewTest {
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    @Test void comparesStrictlyBelowRecentMean() {
        assertTrue(PlateReviewPolicy.requiresReview("plate", n("0.950"), n("0.980")));
        assertFalse(PlateReviewPolicy.requiresReview("plate", n("0.980"), n("0.980")));
        assertFalse(PlateReviewPolicy.requiresReview("plate", n("0.990"), n("0.980")));
    }
    @Test void keepsOriginalFloorAndHandlesMissingData() {
        assertTrue(PlateReviewPolicy.requiresReview("plate", n("0.590"), null));
        assertFalse(PlateReviewPolicy.requiresReview("plate", n("0.600"), null));
        assertTrue(PlateReviewPolicy.requiresReview("plate", n("0.590"), n("0.400")));
        assertFalse(PlateReviewPolicy.requiresReview("plate", n("0.800"), n("2.000")));
        assertFalse(PlateReviewPolicy.requiresReview(null, n("0.500"), n("0.980")));
        assertFalse(PlateReviewPolicy.requiresReview("plate", null, n("0.980")));
    }
    record Fixture(InternalIngestServiceImpl service, PlateLogRepository logs, PlateLogCreateRequest request) {}
    private Fixture fixture(String confidence) throws Exception {
        Map<Class<?>,Object> deps = new HashMap<>();
        var ctor = InternalIngestServiceImpl.class.getConstructors()[0];
        Object[] args = Arrays.stream(ctor.getParameterTypes()).map(type -> deps.computeIfAbsent(type,t -> mock(t))).toArray();
        var service = (InternalIngestServiceImpl)ctor.newInstance(args);
        var camera = mock(Camera.class);
        when(camera.getUnderpassId()).thenReturn(9L);
        when(((CameraRepository)deps.get(CameraRepository.class)).findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(camera));
        var car = mock(Car.class);when(car.getId()).thenReturn(44L);
        when(((CarRepository)deps.get(CarRepository.class)).findActiveByCarNum(anyString())).thenReturn(List.of(car));
        var request = new PlateLogCreateRequest();
        request.setCameraId(7L);request.setDetectedNum("12\uac003456");request.setConfidence(n(confidence));
        request.setDetectedAt(LocalDateTime.of(2026,10,26,14,0));
        var logs = (PlateLogRepository)deps.get(PlateLogRepository.class);
        when(logs.recentConfidenceAverage(7L,request.getDetectedAt())).thenReturn(n("0.980"));
        return new Fixture(service,logs,request);
    }
    @Test void ingestionQueuesHighButBelowAverageScoreWithoutChangingOcrOutput() throws Exception {
        var f = fixture("0.950");f.service.receivePlateLog(f.request,null);
        var saved = ArgumentCaptor.forClass(PlateLog.class);verify(f.logs).save(saved.capture());
        var row = saved.getValue();
        assertEquals(ReviewStatus.PENDING,row.getReviewStatus());
        assertEquals(f.request.getConfidence(),row.getConfidence());
        assertEquals(f.request.getDetectedNum(),row.getDetectedNum());
        assertEquals(RecognitionStatus.SUCCESS,row.getRecognitionStatus());
        assertEquals(f.request.getDetectedAt().plusMinutes(1),row.getReviewDeadline());
        verify(f.logs).recentConfidenceAverage(7L,f.request.getDetectedAt());
    }
    @Test void equalAverageIsNotQueued() throws Exception {
        var f = fixture("0.980");f.service.receivePlateLog(f.request,null);
        var saved = ArgumentCaptor.forClass(PlateLog.class);verify(f.logs).save(saved.capture());
        assertEquals(ReviewStatus.NONE,saved.getValue().getReviewStatus());
        assertNull(saved.getValue().getReviewDeadline());
    }
    @Test void disablingRelativeModeRestoresOriginalRule() throws Exception {
        var f = fixture("0.950");
        var field = InternalIngestServiceImpl.class.getDeclaredField("reviewRelativeToAverage");
        field.setAccessible(true);field.set(f.service,false);
        f.service.receivePlateLog(f.request,null);
        var saved = ArgumentCaptor.forClass(PlateLog.class);verify(f.logs).save(saved.capture());
        assertEquals(ReviewStatus.NONE,saved.getValue().getReviewStatus());
        verify(f.logs,never()).recentConfidenceAverage(anyLong(),any());
    }
}
