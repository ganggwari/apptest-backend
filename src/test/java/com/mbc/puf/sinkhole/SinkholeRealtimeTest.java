package com.mbc.puf.sinkhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mbc.puf.common.enums.ManagerNotificationCategory;
import com.mbc.puf.common.security.*;
import com.mbc.puf.notification.service.NotificationService;
import com.mbc.puf.stream.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class SinkholeRealtimeTest {
    @TempDir Path images;
    Map<String,Object> row() {
        Map<String,Object> r=new HashMap<>();
        r.put("id",20L);r.put("job_id","job");r.put("event_id",30L);r.put("detected",true);
        r.put("confidence",new BigDecimal("0.87"));r.put("detection_count",2);
        r.put("analyzed_at",Timestamp.valueOf(LocalDateTime.now()));r.put("model_version","test");
        r.put("review_status","PENDING");r.put("event_status","CHECK_NEEDED");r.put("image_name","image.jpg");return r;
    }
    @Test void firstDetectionPublishesUrgentNotificationAndRegionalEvidenceButRetryPublishesNothing() throws Exception {
        var events=new ArrayList<StreamEvent>();var notifications=mock(NotificationService.class);
        var saved=row();boolean[] exists={false};
        JdbcTemplate db=mock(JdbcTemplate.class,call->{
            String sql=call.getArgument(0);
            if(call.getMethod().getName().equals("queryForList")) {
                if(sql.contains("FROM pm_equipment e"))return List.of(Map.of("id",7L,"underpass_id",1L,"region_id",2L));
                if(sql.contains("WHERE r.job_id"))return exists[0]?List.of(saved):List.of();
                if(sql.contains("SELECT id FROM pm_event"))return List.of();
                if(sql.contains("WHERE r.id"))return List.of(saved);
            }
            if(call.getMethod().getName().equals("queryForObject")) {
                if(sql.contains("INSERT INTO pm_event"))return 30L;
                if(sql.contains("INSERT INTO pm_road_result")){exists[0]=true;return 20L;}
            }
            throw new AssertionError(sql);
        });
        var service=new SinkholeService(db,mock(ScopeResolver.class),new StreamPublisher(event->events.add((StreamEvent)event)),notifications,images.toString(),"초량지하차도");
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(10,10,BufferedImage.TYPE_INT_RGB),"jpg",bytes);
        var image=new MockMultipartFile("image","a.jpg","image/jpeg",bytes.toByteArray());
        var request=new SinkholeResultRequest("job","초량지하차도",null,OffsetDateTime.now(),true,new BigDecimal("0.87"),2,1.0,"test");
        service.receive(request,image);
        verify(notifications).notifyManagersUrgent(eq(ManagerNotificationCategory.EQUIPMENT),eq(2L),anyString(),anyString(),eq("PM_EVENT"),eq(30L));
        assertEquals(3,events.size());
        var update=(StreamPayloads.SinkholeUpdate)events.stream().filter(e->e.name().equals("sinkhole-update")).findFirst().orElseThrow().data();
        assertEquals(7L,update.equipId());assertEquals(new BigDecimal("0.87"),update.confidence());
        assertTrue(events.stream().allMatch(e->e.regionId().equals(2L)&&e.scope()==StreamScope.ADMIN));
        service.receive(request,image);assertEquals(3,events.size());verifyNoMoreInteractions(notifications);
    }
    @Test void reviewPublishesActionHistoryAndUpdatedEvidence() {
        var events=new ArrayList<StreamEvent>();var r=row();var scope=mock(ScopeResolver.class);
        JdbcTemplate db=mock(JdbcTemplate.class,call->{
            String sql=call.getArgument(0);
            if(call.getMethod().getName().equals("queryForList")) {
                if(sql.contains("SELECT e.name"))return List.of(Map.of("name","R-1","underpass_name","초량지하차도","underpass_id",1L,"region_id",2L));
                if(sql.contains("SELECT id,status"))return List.of(Map.of("id",30L,"status","CHECK_NEEDED"));
                return List.of(r);
            }
            if(call.getMethod().getName().equals("update")) {r.put("review_status","CONFIRMED");r.put("event_status","IN_PROGRESS");return 1;}
            if(call.getMethod().getName().equals("queryForObject"))return 40L;
            throw new AssertionError(sql);
        });
        var service=new SinkholeService(db,scope,new StreamPublisher(event->events.add((StreamEvent)event)),mock(NotificationService.class),images.toString(),"초량지하차도");
        service.review(new LoginUser(9L,"MANAGER","REGIONAL_ADMIN"),7L,new SinkholeController.Review(30L,"CONFIRMED","현장 확인"));
        assertTrue(events.stream().map(StreamEvent::data).filter(StreamPayloads.DataChanged.class::isInstance)
            .map(StreamPayloads.DataChanged.class::cast).anyMatch(d->d.resource().equals("pm-action")&&d.id().equals(40L)));
        var payload=(StreamPayloads.SinkholeUpdate)events.get(events.size()-1).data();
        assertEquals("CONFIRMED",payload.reviewStatus());assertEquals("IN_PROGRESS",payload.eventStatus());
        verify(scope,atLeastOnce()).requireOwnable(any(),eq(2L));
    }
}
