package com.mbc.puf.cctv;
import static org.junit.jupiter.api.Assertions.*;
import com.mbc.puf.cctv.client.*;
import com.mbc.puf.cctv.service.CctvServiceImpl;
import com.mbc.puf.common.security.*;
import com.mbc.puf.underpass.entity.*;
import com.mbc.puf.underpass.repository.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class CameraAnalysisRoutingTest {
    @Test void camera230UsesSinkholeAndOtherCamerasKeepOcr() throws Exception {
        List<String> ocrCalls=new ArrayList<>(),sinkholeCalls=new ArrayList<>();
        HttpServer ocr=server(ocrCalls),sinkhole=server(sinkholeCalls);
        try {
            Underpass facility=new Underpass();facility.setName("초량지하차도");facility.setRegionId(4L);
            CameraRepository cameras=(CameraRepository)java.lang.reflect.Proxy.newProxyInstance(CameraRepository.class.getClassLoader(),new Class[]{CameraRepository.class},(p,m,a)->{
                Camera camera=new Camera();ReflectionTestUtils.setField(camera,"id",a[0]);camera.setUnderpassId(1L);return Optional.of(camera);
            });
            UnderpassRepository facilities=(UnderpassRepository)java.lang.reflect.Proxy.newProxyInstance(UnderpassRepository.class.getClassLoader(),new Class[]{UnderpassRepository.class},(p,m,a)->Optional.of(facility));
            ScopeResolver scope=new ScopeResolver(null,null){@Override public void requireOwnable(LoginUser user,Long region){assertEquals(4L,region);}};
            var service=new CctvServiceImpl(new OcrServerClient("http://127.0.0.1:"+ocr.getAddress().getPort(),"test-key",2),cameras,facilities,scope,new ObjectMapper(),
                    new SinkholeServerClient("http://127.0.0.1:"+sinkhole.getAddress().getPort(),"test-key",2),230,"초량지하차도");
            var user=new LoginUser(1L,"MANAGER","REGIONAL_ADMIN");
            service.startPlate(user,226L);service.startPlate(user,229L);
            service.startPlate(user,230L);service.stopPlate(user,230L);service.analysisState(user,230L);
            service.snapshot(230L);
            try(var stream=service.openStream(230L).body()){stream.readAllBytes();}
            assertEquals(List.of("/control/plate/start?cameraId=226","/control/plate/start?cameraId=229"),ocrCalls);
            assertEquals(List.of("/control/sinkhole/start?cameraId=230","/control/sinkhole/stop?cameraId=230","/control/sinkhole?cameraId=230","/stream/230/snapshot","/stream/230"),sinkholeCalls);
            facility.setName("다른 지하차도");
            assertThrows(RuntimeException.class,()->service.startPlate(user,230L));
            assertEquals(5,sinkholeCalls.size());
        } finally {ocr.stop(0);sinkhole.stop(0);}
    }
    private HttpServer server(List<String> calls) throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            assertEquals("test-key",exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"));
            calls.add(exchange.getRequestURI().toString());
            byte[] body="{\"running\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();return server;
    }
}
