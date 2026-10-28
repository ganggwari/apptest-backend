package com.mbc.puf.cctv.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.mbc.puf.cctv.client.*;
import com.mbc.puf.stream.StreamHub;
import com.mbc.puf.stream.StreamPublisher;
import com.mbc.puf.stream.RealtimePayloads.AnalysisStateChange;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 분석 상태만 중계한다. 프레임·진행 초·탐지 결과는 기존 API/스트림을 사용한다. */
@Component
public class AnalysisStateBroadcaster {
    private final JdbcTemplate db;
    private final OcrServerClient ocr;
    private final SinkholeServerClient sinkhole;
    private final ObjectMapper mapper;
    private final StreamPublisher stream;
    private final StreamHub hub;   // 관리자 연결이 있는지 확인용
    private final long sinkholeCamera;
    private final Map<Long, State> previous = new HashMap<>();
    private record State(String state, boolean running, String error) {}
    public AnalysisStateBroadcaster(JdbcTemplate db, OcrServerClient ocr, SinkholeServerClient sinkhole,
            ObjectMapper mapper, StreamPublisher stream, StreamHub hub, @Value("${puf.sinkhole.camera-id:230}") long sinkholeCamera) {
        this.db=db; this.ocr=ocr; this.sinkhole=sinkhole; this.mapper=mapper; this.stream=stream; this.hub=hub; this.sinkholeCamera=sinkholeCamera;
    }
    @Scheduled(initialDelay=5000, fixedDelay=3000, scheduler="realtimeScheduler")
    public synchronized void poll() {
        if (!hub.hasAdminConnections()) {
            return;   // 보는 관리자가 없으면 DB 와 분석 서버를 조회하지 않는다 (관리자가 접속하면 다음 주기부터 다시 확인)
        }
        List<Map<String,Object>> cameras=db.queryForList("""
                SELECT c.id AS camera_id,c.underpass_id,u.region_id
                  FROM camera c JOIN underpass u ON u.id=c.underpass_id
                 WHERE c.is_deleted=FALSE AND u.is_deleted=FALSE
                   AND (c.role::text IN ('APPROACH_LPR','FINAL_LPR') OR c.id=""" + sinkholeCamera + ")");
        Set<Long> alive=new HashSet<>();
        for (boolean road : List.of(false,true)) {
            var group=cameras.stream().filter(c -> (id(c,"camera_id")==sinkholeCamera)==road).toList();
            if (group.isEmpty()) continue;
            JsonNode snapshot=null;
            try {
                var response=(road?sinkhole:ocr).getBytes(road?"/control/sinkhole?cameraId="+sinkholeCamera:"/control/plate");
                if (response.statusCode()!=200) throw new IllegalStateException("upstream");
                snapshot=mapper.readTree(new String(response.body(),StandardCharsets.UTF_8));
                if (!snapshot.path("cameras").isArray()) throw new IllegalStateException("invalid state");
            } catch (RuntimeException error) { snapshot=null; }
            for (var camera:group) {
                long cameraId=id(camera,"camera_id"); alive.add(cameraId);
                State next=snapshot==null?new State("ERROR",false,"분석 서버 상태를 조회할 수 없습니다."):state(snapshot,cameraId);
                if (!next.equals(previous.get(cameraId))) {
                    stream.publishToAdmins(id(camera,"region_id"),"analysis-state-change",
                            new AnalysisStateChange(cameraId,id(camera,"underpass_id"),road?"SINKHOLE":"OCR",
                                    next.state(),next.running(),next.error(),LocalDateTime.now()));
                    previous.put(cameraId,next);
                }
            }
        }
        previous.keySet().retainAll(alive);
    }
    private State state(JsonNode snapshot,long cameraId) {
        for (JsonNode camera:snapshot.path("cameras")) {
            if (camera.path("cameraId").asLong(-1)!=cameraId) continue;
            String error=camera.path("error").isTextual()?camera.path("error").asText():null;
            if (error!=null && error.isBlank()) error=null;
            boolean running=camera.path("running").asBoolean(false);
            String state=camera.path("state").asText("");
            if (error!=null) state="ERROR";
            else if (!Set.of("STARTING","RUNNING","STOPPING","STOPPED","COMPLETED","ERROR").contains(state))
                state=running?"RUNNING":"COMPLETED";
            return new State(state,running,error);
        }
        return new State("STOPPED",false,null);
    }
    private static long id(Map<String,Object> row,String key) {return ((Number)row.get(key)).longValue();}
}
