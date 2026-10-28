package com.mbc.puf.sinkhole;

import static org.junit.jupiter.api.Assertions.*;

import com.mbc.puf.common.security.*;
import com.mbc.puf.notification.service.NotificationService;
import com.mbc.puf.stream.StreamPublisher;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in real PostgreSQL contract test. All DB changes and evidence files roll back. */
@EnabledIfEnvironmentVariable(named="PUF_SINKHOLE_DB_TEST", matches="true")
class SinkholeDatabaseTest {
    @TempDir Path images;
    @Test void ingestRetryAndReviewShareTheEquipmentWithoutFakeRiskScores() throws Exception {
        Properties p=new Properties();
        try(var reader=Files.newBufferedReader(Path.of("src/main/resources/application-secret.properties"))) {p.load(reader);}
        var ds=new DriverManagerDataSource(p.getProperty("secret.db.url"),p.getProperty("secret.db.username"),p.getProperty("secret.db.password"));
        var db=new JdbcTemplate(ds);
        var scoped=new ScopeResolver(null,null) {
            @Override public void requireOwnable(LoginUser user, Long regionId) {}
        };
        var notificationCount=new java.util.concurrent.atomic.AtomicInteger();
        var notifications=(NotificationService)java.lang.reflect.Proxy.newProxyInstance(
                NotificationService.class.getClassLoader(),new Class<?>[]{NotificationService.class},
                (proxy,method,args)->{if(method.getName().equals("notifyManagersUrgent"))notificationCount.incrementAndGet();return null;});
        var service=new SinkholeService(db,scoped,new StreamPublisher(event->{}),notifications,images.toString(),"초량지하차도");
        var user=new LoginUser(db.queryForObject("SELECT MIN(id) FROM manager",Long.class),"MANAGER","REGIONAL_ADMIN");
        var output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(24,24,BufferedImage.TYPE_INT_RGB),"jpg",output);
        var image=new MockMultipartFile("image","evidence.jpg","image/jpeg",output.toByteArray());
        var now=OffsetDateTime.now();String job="test-"+UUID.randomUUID();
        new TransactionTemplate(new DataSourceTransactionManager(ds)).execute(status->{
            status.setRollbackOnly();
            try {
                var metadata=new SinkholeResultRequest(job,"초량지하차도",null,now,true,new BigDecimal("0.87"),3,12.0,"test-model");
                var first=service.receive(metadata,image);
                var retry=service.receive(metadata,image);
                assertEquals(first.id(),retry.id());assertNotNull(first.eventId());assertEquals(1,notificationCount.get());
                var interceptor=new InternalApiKeyInterceptor();
                org.springframework.test.util.ReflectionTestUtils.setField(interceptor,"expectedApiKey","test-key");
                var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new SinkholeController(service))
                        .addMappedInterceptors(new String[]{"/api/internal/**"},interceptor).build();
                String json="{\"jobId\":\""+job+"\",\"underpassName\":\"초량지하차도\",\"analyzedAt\":\""+now+"\",\"detected\":true,\"confidence\":0.87,\"detectionCount\":3,\"frameSeconds\":12.0,\"modelVersion\":\"test-model\"}";
                var part=new MockMultipartFile("metadata","metadata.json","application/json",json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/internal/sinkhole/results").file(part).file(image))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/internal/sinkhole/results").file(part).file(image).header("X-Internal-Api-Key","test-key"))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.id").value(first.id()));

                Long equip=db.queryForObject("SELECT equip_id FROM pm_road_result WHERE id=?",Long.class,first.id());
                var clean=new SinkholeResultRequest(job+"-clean","초량지하차도",null,now.plusSeconds(1),false,null,0,null,"test-model");
                service.receive(clean,null);
                assertEquals(first.id(),service.detail(user,equip).current().id(),"A later clean frame must not hide an open incident");
                var selectField=com.mbc.puf.pm.service.PmQueryServiceImpl.class.getDeclaredField("EQUIP_SELECT"); selectField.setAccessible(true);
                var fromField=com.mbc.puf.pm.service.PmQueryServiceImpl.class.getDeclaredField("EQUIP_FROM"); fromField.setAccessible(true);
                String listSql=(selectField.get(null).toString()+fromField.get(null).toString())
                        .replace(":openStatuses","'CHECK_NEEDED','IN_PROGRESS'")+" AND e.id=?";
                var listed=db.queryForMap(listSql,equip);
                assertEquals("싱크홀 의심",listed.get("road_status"));
                assertNull(listed.get("score"));

                service.review(user,equip,new SinkholeController.Review(first.eventId(),"CONFIRMED","현장 확인"));
                var again=service.receive(new SinkholeResultRequest(job+"-again","초량지하차도",null,now.plusSeconds(2),true,new BigDecimal("0.91"),2,15.0,"test-model"),image);
                assertEquals(first.eventId(),again.eventId());assertEquals("CONFIRMED",again.reviewStatus());
                service.review(user,equip,new SinkholeController.Review(first.eventId(),"FALSE_POSITIVE","노면 얼룩"));
                assertEquals("오탐 확인",service.detail(user,equip).state());
                assertThrows(RuntimeException.class,()->service.review(user,equip,new SinkholeController.Review(first.eventId(),"RESOLVED","")));
                assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM pm_score WHERE equip_id=?",Integer.class,equip));
                assertTrue(service.image(user,first.id()).exists());
            }catch(Exception ex){throw new RuntimeException(ex);}
            return null;
        });
        try(var files=Files.list(images)) {assertEquals(0,files.count(),"rolled back evidence must be removed");}
    }
}
