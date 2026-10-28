package com.mbc.puf.control;

import com.mbc.puf.control.service.ControlServiceImpl;
import com.mbc.puf.control.repository.*;
import com.mbc.puf.control.entity.CtrlTransfer;
import com.mbc.puf.common.enums.*;
import com.mbc.puf.risk.RiskPolicy;
import com.mbc.puf.underpass.entity.Underpass;
import com.mbc.puf.underpass.repository.UnderpassRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ControlTransferFlowTest {
    @Test void automaticBlockCreatesBothNoticesWithoutSendingVehicleReport() throws Exception {
        Map<Class<?>,Object> deps = new HashMap<>();
        var constructor = ControlServiceImpl.class.getConstructors()[0];
        Object[] args = Arrays.stream(constructor.getParameterTypes()).map(type -> deps.computeIfAbsent(type, t -> mock(t))).toArray();
        var service = (ControlServiceImpl) constructor.newInstance(args);
        var facility = mock(Underpass.class);
        when(facility.getId()).thenReturn(1L);
        when(facility.getCurrentStatus()).thenReturn(UnderpassCurrentStatus.NORMAL);
        when(((UnderpassRepository)deps.get(UnderpassRepository.class)).findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(facility));
        when(((RiskPolicy)deps.get(RiskPolicy.class)).blockCm(facility)).thenReturn(BigDecimal.valueOf(15));
        var transfers = (CtrlTransferRepository) deps.get(CtrlTransferRepository.class);
        when(transfers.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        service.autoBlock(1L,BigDecimal.valueOf(15));
        var saved = ArgumentCaptor.forClass(CtrlTransfer.class);
        verify(transfers,times(2)).saveAndFlush(saved.capture());
        assertEquals(List.of(TransferAgency.POLICE,TransferAgency.FIRE),saved.getAllValues().stream().map(CtrlTransfer::getAgency).toList());
        assertTrue(saved.getAllValues().stream().allMatch(t -> t.getSnapshot().contains("auto")));
        verifyNoInteractions(deps.get(CtrlTransferCarRepository.class));
    }
    @Test void manualVehicleReportRejectsReleaseEvent() throws Exception {
        Map<Class<?>,Object> deps = new HashMap<>();
        var constructor = ControlServiceImpl.class.getConstructors()[0];
        Object[] args = Arrays.stream(constructor.getParameterTypes()).map(type -> deps.computeIfAbsent(type,t -> mock(t))).toArray();
        var service = (ControlServiceImpl)constructor.newInstance(args);
        var facility = mock(Underpass.class);
        when(facility.getRegionId()).thenReturn(1L);
        when(((UnderpassRepository)deps.get(UnderpassRepository.class)).findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(facility));
        var event = mock(com.mbc.puf.underpass.entity.UnderpassEvent.class);
        when(event.getUnderpassId()).thenReturn(1L);
        when(event.getEventType()).thenReturn(UnderpassEventType.RELEASE);
        when(((com.mbc.puf.underpass.repository.UnderpassEventRepository)deps.get(com.mbc.puf.underpass.repository.UnderpassEventRepository.class)).findById(2L)).thenReturn(Optional.of(event));
        var request = new com.mbc.puf.control.dto.FireTransferRequest();
        request.setEventId(2L); request.setCarNums(List.of("12가3456"));
        assertThrows(com.mbc.puf.common.exception.BusinessException.class, () -> service.fireTransfer(new com.mbc.puf.common.security.LoginUser(1L,"MANAGER","REGIONAL_ADMIN"),1L,request));
        verifyNoInteractions(deps.get(CtrlTransferRepository.class));
    }

}
