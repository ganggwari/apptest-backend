package com.mbc.puf.stream;

import java.time.*;
import java.util.*;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.*;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.cctv.client.*;
import com.mbc.puf.cctv.service.AnalysisStateBroadcaster;
import com.mbc.puf.pm.service.*;
import com.mbc.puf.notification.service.*;
import com.mbc.puf.notification.repository.*;
import com.mbc.puf.notification.entity.CustomerNotification;
import com.mbc.puf.notification.dto.NotificationUnreadResponse;
import com.mbc.puf.member.repository.ManagerRegionRepository;
import tools.jackson.databind.ObjectMapper;

class RealtimeAdditionTest {
    @Test void engineDetectsSilenceAndRecoveryWithoutScoreEvents() {
        var monitor=new PmEngineMonitor(); var clock=mock(DbClock.class); var stream=mock(StreamPublisher.class);
        var broadcaster=new PmEngineStatusBroadcaster(monitor,clock,stream,180,600);
        var at=LocalDateTime.of(2026,10,27,15,0);
        broadcaster.poll(); broadcaster.poll();
        monitor.received(at,1,false); when(clock.now()).thenReturn(at.plusSeconds(180)); broadcaster.poll();
        when(clock.now()).thenReturn(at.plusSeconds(181)); broadcaster.poll();
        when(clock.now()).thenReturn(at.plusSeconds(601)); broadcaster.poll(); broadcaster.poll();
        monitor.received(at.plusSeconds(602),1,false); when(clock.now()).thenReturn(at.plusSeconds(602)); broadcaster.poll();
        var payload=ArgumentCaptor.forClass(RealtimePayloads.EngineStatus.class);
        verify(stream,times(5)).publishToAdmins(isNull(),eq("pm-engine-status"),payload.capture());
        assertEquals(List.of("WAITING","NORMAL","DELAYED","DOWN","NORMAL"),payload.getAllValues().stream().map(RealtimePayloads.EngineStatus::status).toList());
    }
    @SuppressWarnings("unchecked")
    @Test void analysisChangesAreRegionalAndDoNotRepeatOrSendRunningSeconds() {
        var db=mock(JdbcTemplate.class);var ocr=mock(OcrServerClient.class);var sink=mock(SinkholeServerClient.class);var stream=mock(StreamPublisher.class);
        when(db.queryForList(anyString())).thenReturn(List.of(Map.of("camera_id",226L,"underpass_id",1L,"region_id",4L)));
        HttpResponse<byte[]> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"cameras\":[{\"cameraId\":226,\"running\":true}]}".getBytes(StandardCharsets.UTF_8));
        when(ocr.getBytes("/control/plate")).thenReturn(response);
        var hub=mock(StreamHub.class);when(hub.hasAdminConnections()).thenReturn(true);
        var b=new AnalysisStateBroadcaster(db,ocr,sink,new ObjectMapper(),stream,hub,230);
        b.poll();b.poll();
        when(response.body()).thenReturn("{\"cameras\":[]}".getBytes(StandardCharsets.UTF_8));b.poll();
        when(ocr.getBytes("/control/plate")).thenThrow(new RuntimeException("down"));b.poll();b.poll();
        var payload=ArgumentCaptor.forClass(RealtimePayloads.AnalysisStateChange.class);
        verify(stream,times(3)).publishToAdmins(eq(4L),eq("analysis-state-change"),payload.capture());
        assertEquals(List.of("RUNNING","STOPPED","ERROR"),payload.getAllValues().stream().map(RealtimePayloads.AnalysisStateChange::state).toList());
        verifyNoInteractions(sink);
    }
    record Fixture(NotificationServiceImpl service, CustomerNotificationRepository customers, EntityManager em, StreamPublisher stream) {}
    Fixture notifications() {
        var customers=mock(CustomerNotificationRepository.class);var em=mock(EntityManager.class);var stream=mock(StreamPublisher.class);
        var service=spy(new NotificationServiceImpl(customers,mock(ManagerNotificationRepository.class),
            mock(ManagerRegionRepository.class),em,stream,mock(PushService.class),mock(DbClock.class,invocation->java.time.LocalDateTime.now()),120));
        return new Fixture(service,customers,em,stream);
    }
    @Test void customerReadPublishesOnlyToThatCustomerWithFreshCount() {
        var f=notifications();var n=new CustomerNotification();
        when(f.customers.findByIdAndCustomerId(7L,2L)).thenReturn(Optional.of(n));
        var unread=new NotificationUnreadResponse(3,Map.of("RISK",3L));doReturn(unread).when(f.service).getMyUnread(2L);
        f.service.markMyRead(2L,7L);
        assertTrue(n.isRead());verify(f.customers).flush();
        verify(f.stream).publishToCustomer(2L,"notification-read-change",new RealtimePayloads.NotificationReadChange(7L,false,null,unread));
        verifyNoMoreInteractions(f.stream);
    }
    @Test void customerReadAllIncludesZeroCount() {
        var f=notifications();var unread=new NotificationUnreadResponse(0,Map.of());doReturn(unread).when(f.service).getMyUnread(2L);
        f.service.markMyAllRead(2L,null);
        verify(f.stream).publishToCustomer(2L,"notification-read-change",new RealtimePayloads.NotificationReadChange(null,true,null,unread));
    }
    @SuppressWarnings("unchecked")
    @Test void managerReadIsTargetedToAccountRatherThanWholeRegion() {
        var f=notifications();var user=new LoginUser(4L,"MANAGER","REGIONAL_ADMIN");
        TypedQuery<Long> visible=mock(TypedQuery.class,RETURNS_SELF);when(f.em.createQuery(anyString(),eq(Long.class))).thenReturn(visible);when(visible.getSingleResult()).thenReturn(1L);
        Query insert=mock(Query.class,RETURNS_SELF);when(f.em.createNativeQuery(anyString())).thenReturn(insert);
        var unread=new NotificationUnreadResponse(2,Map.of());doReturn(unread).when(f.service).getManagerUnread(user);
        f.service.markManagerRead(user,7L);
        verify(f.stream).publishToManager(4L,"notification-read-change",new RealtimePayloads.NotificationReadChange(7L,false,null,unread));
        verifyNoMoreInteractions(f.stream);
    }
    @Test void managerReadAllBroadcastsOnlyToOwnTabs() {
        var f=notifications();var user=new LoginUser(4L,"MANAGER","REGIONAL_ADMIN");
        Query insert=mock(Query.class,RETURNS_SELF);when(f.em.createNativeQuery(anyString())).thenReturn(insert);
        var unread=new NotificationUnreadResponse(0,Map.of());doReturn(unread).when(f.service).getManagerUnread(user);
        f.service.markManagerAllRead(user,null);
        verify(f.stream).publishToManager(4L,"notification-read-change",new RealtimePayloads.NotificationReadChange(null,true,null,unread));
    }
}
