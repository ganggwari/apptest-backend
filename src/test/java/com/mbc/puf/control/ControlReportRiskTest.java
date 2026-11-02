package com.mbc.puf.control;

import com.mbc.puf.common.enums.*;
import com.mbc.puf.common.pdf.*;
import com.mbc.puf.control.dto.CtrlTransferItem;
import com.mbc.puf.control.service.ControlServiceImpl;
import com.mbc.puf.control.entity.CtrlTransfer;
import com.mbc.puf.underpass.entity.*;
import com.mbc.puf.underpass.repository.UnderpassEventRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ControlReportRiskTest {
    private final Map<Class<?>,Object> dependencies = new HashMap<>();
    private ControlServiceImpl service() throws Exception {
        var constructor = ControlServiceImpl.class.getConstructors()[0];
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> dependencies.computeIfAbsent(type, t -> mock(t))).toArray();
        return (ControlServiceImpl)constructor.newInstance(args);
    }
    private Object call(ControlServiceImpl service, String name, Class<?>[] types, Object... args) throws Exception {
        var method = ControlServiceImpl.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(service,args);
    }
    private ControlNoticeData notice(RiskLevel level, Double score, LocalDateTime at) {
        return new ControlNoticeData("지하차도", "부산", true, at, "침수 위험", null,
                score, null, null, null, null, null, null, null, null, null, level, at);
    }
    @Test void snapshotKeepsOriginalAssessmentWhenNewAssessmentDiffers() throws Exception {
        var service = service();
        var at = LocalDateTime.of(2026,10,30,14,30);
        String snapshot = (String)call(service,"snapshotOf",new Class<?>[]{ControlNoticeData.class,BigDecimal.class,boolean.class},
                notice(RiskLevel.ALERT,76.4,at),null,false);
        var restored = (ControlNoticeData)call(service,"applySnapshot",new Class<?>[]{ControlNoticeData.class,String.class},
                notice(RiskLevel.SAFE,12.0,at.plusDays(1)),snapshot);
        assertEquals(RiskLevel.ALERT,restored.riskLevel());
        assertEquals(76.4,restored.riskScore());
        assertEquals(at,restored.riskAssessedAt());
    }
    @Test void ExplicitlyMissingAssessmentStaysMissing() throws Exception {
        var service = service();
        String snapshot = (String)call(service,"snapshotOf",new Class<?>[]{ControlNoticeData.class,BigDecimal.class,boolean.class},
                notice(null,null,null),null,false);
        var restored = (ControlNoticeData)call(service,"applySnapshot",new Class<?>[]{ControlNoticeData.class,String.class},
                notice(RiskLevel.SEVERE,99.0,LocalDateTime.now()),snapshot);
        assertNull(restored.riskLevel());
        assertNull(restored.riskScore());
        assertNull(restored.riskAssessedAt());
    }
    @Test void oldEventDoesNotBorrowCurrentFacilityStage() throws Exception {
        var service = service();
        var facility = mock(Underpass.class);
        when(facility.getCurrentRiskLevel()).thenReturn(RiskLevel.SEVERE);
        var event = new UnderpassEvent();
        event.setEventType(UnderpassEventType.BLOCK);
        var notice = (ControlNoticeData)call(service,"buildNotice",
                new Class<?>[]{Underpass.class,UnderpassEvent.class,UnderpassEvent.class,Long.class,String.class,String.class},
                facility,event,null,null,null,null);
        assertNull(notice.riskLevel());
        assertNull(notice.riskAssessedAt());
        verify(facility,never()).getCurrentRiskLevel();
    }
    @Test void historyAndPdfUseSameHistoricalSnapshot() throws Exception {
        var service = service();
        var at = LocalDateTime.of(2026,10,30,14,30);
        String snapshot = (String)call(service,"snapshotOf",new Class<?>[]{ControlNoticeData.class,BigDecimal.class,boolean.class},
                notice(RiskLevel.ALERT,76.4,at),null,false);
        var transfer = new CtrlTransfer();
        transfer.setAgency(TransferAgency.POLICE);
        transfer.setEventId(42L);
        transfer.setSnapshot(snapshot);
        var event = new UnderpassEvent();
        event.setEventType(UnderpassEventType.BLOCK);
        event.setRiskLevel(RiskLevel.SAFE);
        event.setRiskScoreAtEvent(BigDecimal.TEN);
        when(((UnderpassEventRepository)dependencies.get(UnderpassEventRepository.class)).findById(42L)).thenReturn(Optional.of(event));
        var item = (CtrlTransferItem)call(service,"toItem",new Class<?>[]{CtrlTransfer.class},transfer);
        assertEquals(RiskLevel.ALERT,item.getRiskLevel());
        assertEquals(76.4,item.getRiskScore());
        assertEquals(at,item.getRiskAssessedAt());
        assertTrue(item.getRiskAssessment().contains("경계"));
        assertTrue(item.getRiskAssessment().contains("2026-10-30 14:30"));
        byte[] bytes = new TransferPdfGenerator().generatePolicePdf(notice(RiskLevel.ALERT,76.4,at));
        try(var pdf = new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(new java.io.ByteArrayInputStream(bytes)))) {
            String text = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(pdf.getPage(1));
            assertTrue(text.contains("AI 위험단계"));
            assertTrue(text.contains("경계"));
            assertTrue(text.contains("경계 · 76.4점 · 판정 2026.10.30 14:30"));
            assertFalse(text.contains("발생 당시 판정 결과"));
        }
    }
}
