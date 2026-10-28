package com.mbc.puf.stream;

import java.util.*;
import java.util.concurrent.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mbc.puf.board.service.InquiryServiceImpl;
import com.mbc.puf.board.repository.InquiryRepository;
import com.mbc.puf.board.entity.Inquiry;
import com.mbc.puf.board.dto.InquiryUpdateRequest;
import com.mbc.puf.common.enums.*;
import com.mbc.puf.notification.service.NotificationService;
import com.mbc.puf.member.repository.ManagerRegionRepository;
import com.mbc.puf.control.service.ControlServiceImpl;
import com.mbc.puf.control.repository.CtrlTransferRepository;
import com.mbc.puf.risk.RiskPolicy;
import com.mbc.puf.underpass.entity.Underpass;
import com.mbc.puf.underpass.repository.UnderpassRepository;
import jakarta.persistence.EntityManager;

class MutationSseTest {
    @Test void inquiryOwnerEditAndDeleteNotifyBothOwnerAndAdmins() {
        var repo=mock(InquiryRepository.class);var stream=mock(StreamPublisher.class);
        var service=new InquiryServiceImpl(repo,mock(NotificationService.class),mock(EntityManager.class),stream);
        var em=mock(EntityManager.class);jakarta.persistence.TypedQuery<String> query=mock(jakarta.persistence.TypedQuery.class,RETURNS_SELF);
        when(em.createQuery(anyString(),eq(String.class))).thenReturn(query);when(query.getSingleResult()).thenReturn("고객");
        service=new InquiryServiceImpl(repo,mock(NotificationService.class),em,stream);
        var row=new Inquiry();row.setCustomerId(2L);row.setStatus(InquiryStatus.PENDING);
        when(repo.findByIdAndParentIdIsNullAndDeletedFalse(7L)).thenReturn(Optional.of(row));
        var request=new InquiryUpdateRequest();request.setTitle("수정");request.setContent("내용");
        service.updateMyInquiry(2L,7L,request);service.deleteMyInquiry(2L,7L);
        verify(stream).dataChangedToCustomer(2L,"inquiry","UPDATE",row.getId());
        verify(stream).dataChangedToAdmins(null,"inquiry","UPDATE",row.getId());
        verify(stream).dataChangedToCustomer(2L,"inquiry","DELETE",row.getId());
        verify(stream).dataChangedToAdmins(null,"inquiry","DELETE",row.getId());
    }
    @Test void editingSomeoneElsesInquiryDoesNotEmitAnything() {
        var repo=mock(InquiryRepository.class);var stream=mock(StreamPublisher.class);
        var service=new InquiryServiceImpl(repo,mock(NotificationService.class),mock(EntityManager.class),stream);
        var row=new Inquiry();row.setCustomerId(99L);when(repo.findByIdAndParentIdIsNullAndDeletedFalse(7L)).thenReturn(Optional.of(row));
        assertThrows(RuntimeException.class,()->service.deleteMyInquiry(2L,7L));verifyNoInteractions(stream);
    }
    @Test void assignmentEventRefreshesExistingConnectionsRegionFilter() throws Exception {
        var repo=mock(ManagerRegionRepository.class);when(repo.findRegionIds(1L)).thenReturn(List.of(4L),List.of(9L));
        var hub=new StreamHub(repo);
        try {
            hub.connectManager(1L);
            hub.dispatch(new StreamEvent("manager-assignment-change",new RealtimePayloads.ManagerAssignmentChange(1L,"REGIONS"),StreamScope.MANAGER,null,1L));
            var sender=StreamHub.class.getDeclaredField("sender");sender.setAccessible(true);
            ((ExecutorService)sender.get(hub)).submit(()->{}).get(3,TimeUnit.SECONDS);
            var connections=StreamHub.class.getDeclaredField("connections");connections.setAccessible(true);
            Object connection=((List<?>)connections.get(hub)).get(0);
            var regions=connection.getClass().getDeclaredField("regionIds");regions.setAccessible(true);
            assertEquals(Set.of(9L),regions.get(connection));
        } finally {hub.shutdown();}
    }
    @Test void automaticBlockNotifiesForBothPoliceAndFireDocuments() throws Exception {
        Map<Class<?>,Object> deps=new HashMap<>();var ctor=ControlServiceImpl.class.getConstructors()[0];
        var args=Arrays.stream(ctor.getParameterTypes()).map(t->deps.computeIfAbsent(t,k->mock(k))).toArray();
        var service=(ControlServiceImpl)ctor.newInstance(args);var facility=mock(Underpass.class);
        when(facility.getId()).thenReturn(1L);when(facility.getRegionId()).thenReturn(4L);when(facility.getCurrentStatus()).thenReturn(UnderpassCurrentStatus.NORMAL);
        when(((UnderpassRepository)deps.get(UnderpassRepository.class)).findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(facility));
        when(((RiskPolicy)deps.get(RiskPolicy.class)).blockCm(facility)).thenReturn(BigDecimal.valueOf(15));
        var transfers=(CtrlTransferRepository)deps.get(CtrlTransferRepository.class);when(transfers.saveAndFlush(any())).thenAnswer(c->c.getArgument(0));
        service.autoBlock(1L,BigDecimal.valueOf(15));
        verify((StreamPublisher)deps.get(StreamPublisher.class),times(2)).dataChangedToAdmins(eq(4L),eq("control-transfer"),eq("CREATE"),any());
    }
}
