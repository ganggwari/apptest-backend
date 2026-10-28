package com.mbc.puf.sinkhole;

import com.mbc.puf.common.enums.ManagerNotificationCategory;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.ScopeResolver;
import com.mbc.puf.notification.service.NotificationService;
import com.mbc.puf.stream.StreamPublisher;
import com.mbc.puf.stream.StreamPayloads;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SinkholeService {
    private final JdbcTemplate db;
    private final ScopeResolver scope;
    private final StreamPublisher stream;
    private final NotificationService notifications;
    private final Path imageDir;
    private final String underpassName;

    public SinkholeService(JdbcTemplate db, ScopeResolver scope, StreamPublisher stream,
            NotificationService notifications,
            @Value("${puf.sinkhole.image-dir:./sinkhole-images}") String imageDir,
            @Value("${puf.sinkhole.underpass-name:초량지하차도}") String underpassName) {
        this.db = db; this.scope = scope; this.stream = stream; this.notifications = notifications;
        this.imageDir = Path.of(imageDir).toAbsolutePath().normalize();
        this.underpassName = underpassName;
    }

    public record Result(long id, String jobId, Long eventId, Long cameraId, boolean detected,
            BigDecimal confidence, int detectionCount, Double frameSeconds, LocalDateTime analyzedAt,
            String modelVersion, String reviewStatus, String imageUrl, String eventStatus) {}
    public record Detail(long equipId, String name, String underpassName, String state,
            Result current, List<Result> history) {}

    @Transactional(rollbackFor = Exception.class)
    public Result receive(SinkholeResultRequest request, MultipartFile image) throws IOException {
        if (!underpassName.equals(request.underpassName())) bad("설정된 지하차도에만 결과를 전송할 수 있습니다.");
        validateEvidence(request, image);
        // 설비 행을 잠가 같은 노면의 동시 탐지/관리자 처리를 직렬화한다.
        List<Map<String,Object>> targets = db.queryForList("""
                SELECT e.id, e.name, u.id AS underpass_id, u.region_id
                FROM pm_equipment e JOIN underpass u ON u.id=e.underpass_id
                WHERE u.name=? AND NOT u.is_deleted AND NOT e.is_deleted AND e.type::text='ROAD'
                ORDER BY e.id FOR UPDATE OF e
                """, underpassName);
        if (targets.size()!=1) throw new BusinessException(HttpStatus.CONFLICT,
                "초량 노면이 정확히 1개 필요합니다. 5.sinkhole_road_v1.sql 적용 여부를 확인하세요.");
        Map<String,Object> target=targets.get(0);
        long equipId=number(target.get("id"));
        List<Map<String,Object>> existing=db.queryForList(RESULT_SQL+" WHERE r.job_id=?",request.jobId());
        if (!existing.isEmpty()) return result(existing.get(0)); // 재전송은 중복 저장/알림하지 않는다.
        if (request.cameraId()!=null) {
            Integer n=db.queryForObject("SELECT COUNT(*) FROM camera WHERE id=? AND underpass_id=? AND NOT is_deleted",
                    Integer.class,request.cameraId(),target.get("underpass_id"));
            if (n==null||n!=1) bad("초량지하차도에 속한 카메라만 연결할 수 있습니다.");
        }
        Long eventId=null;
        String verdict="PENDING";
        boolean newEvent=false;
        if (request.detected()) {
            List<Map<String,Object>> open=db.queryForList("""
                    SELECT id FROM pm_event WHERE equip_id=? AND trigger_type::text='싱크홀탐지'
                    AND status::text IN ('CHECK_NEEDED','IN_PROGRESS') ORDER BY id DESC LIMIT 1
                    """,equipId);
            if (open.isEmpty()) {
                eventId=db.queryForObject("""
                        INSERT INTO pm_event(equip_id,trigger_type,status)
                        VALUES (?, '싱크홀탐지'::pm_trigger_type,'CHECK_NEEDED'::pm_event_status) RETURNING id
                        """,Long.class,equipId);
                newEvent=true;
            } else {
                eventId=number(open.get(0).get("id"));
                List<String> reviews=db.queryForList("SELECT review_status FROM pm_road_result WHERE event_id=? ORDER BY id DESC LIMIT 1",String.class,eventId);
                if (!reviews.isEmpty()) verdict=reviews.get(0);
            }
        }
        String imageName=null;
        if (request.detected()) {
            Files.createDirectories(imageDir);
            imageName=UUID.randomUUID()+".jpg";
            Path photo=imageDir.resolve(imageName);
            Files.write(photo,image.getBytes(),StandardOpenOption.CREATE_NEW);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if(status!=STATUS_COMMITTED) try { Files.deleteIfExists(photo); } catch(IOException ignored) {}
                    }
                });
            }
        }
        LocalDateTime analyzed=request.analyzedAt().atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime();
        Long id=db.queryForObject("""
                INSERT INTO pm_road_result(job_id,equip_id,camera_id,event_id,analyzed_at,detected,confidence,
                    detection_count,frame_seconds,model_version,image_name,review_status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id
                """,Long.class,request.jobId(),equipId,request.cameraId(),eventId,Timestamp.valueOf(analyzed),
                request.detected(),request.confidence(),request.detectionCount(),request.frameSeconds(),
                request.modelVersion(),imageName,verdict);
        long regionId=number(target.get("region_id"));
        if(newEvent) notifications.notifyManagersUrgent(ManagerNotificationCategory.EQUIPMENT,regionId,
                "노면 싱크홀 의심",underpassName+" 노면에서 싱크홀 의심이 탐지되었습니다. 사진을 확인해 주세요.","PM_EVENT",eventId);
        stream.dataChangedToAdmins(regionId,"pm-equipment","UPDATE",equipId);
        if(newEvent) stream.dataChangedToAdmins(regionId,"pm-event","CREATE",eventId);
        Result saved = result(db.queryForList(RESULT_SQL+" WHERE r.id=?",id).get(0));
        publishResult(regionId, number(target.get("underpass_id")), equipId, saved);
        return saved;
    }

    private void publishResult(long regionId, long underpassId, long equipId, Result r) {
        stream.publishToAdmins(regionId,"sinkhole-update", new StreamPayloads.SinkholeUpdate(
                equipId,underpassId,r.id(),r.eventId(),r.detected(),r.confidence(),r.reviewStatus(),
                r.eventStatus(),r.imageUrl(),r.analyzedAt()));
    }

    static void validateEvidence(SinkholeResultRequest request, MultipartFile image) throws IOException {
        if(request.detected()==null || request.analyzedAt()==null) bad("분석 결과와 분석 시각이 필요합니다.");
        if(request.analyzedAt().isAfter(OffsetDateTime.now().plusMinutes(5))) bad("미래 분석 시각은 허용되지 않습니다.");
        if(request.frameSeconds()!=null && (!Double.isFinite(request.frameSeconds()) || request.frameSeconds()<0)) bad("영상 위치가 올바르지 않습니다.");
        if(Boolean.TRUE.equals(request.detected())) {
            if(request.confidence()==null || request.detectionCount()<1 || image==null || image.isEmpty())
                bad("탐지 결과에는 신뢰도, 탐지 수, JPEG 사진이 필요합니다.");
            if(image.getSize()>10*1024*1024) bad("탐지 사진은 10MB 이하여야 합니다.");
            try(var input=ImageIO.createImageInputStream(image.getInputStream())) {
                var readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext()) { bad("읽을 수 없는 탐지 사진입니다."); return; }
                var reader=readers.next();
                try {
                    reader.setInput(input);
                    if(!"JPEG".equalsIgnoreCase(reader.getFormatName()) || (long)reader.getWidth(0)*reader.getHeight(0)>24_000_000)
                        bad("24메가픽셀 이하 JPEG 사진만 허용합니다.");
                } finally {reader.dispose();}
            }
        } else if(request.detectionCount()!=0 || (image!=null && !image.isEmpty())) bad("미탐지 결과에는 탐지 수와 사진을 넣을 수 없습니다.");
    }

    @Transactional(readOnly=true)
    public Detail detail(LoginUser user,long equipId) {
        Map<String,Object> equipment=owned(user,equipId,false);
        List<Result> history=db.queryForList(RESULT_SQL+" WHERE r.equip_id=? ORDER BY r.analyzed_at DESC,r.id DESC LIMIT 20",equipId)
                .stream().map(this::result).toList();
        // 후속 미탐지 결과가 있어도 처리 중인 의심 건을 숨기지 않는다.
        List<Result> open=db.queryForList(RESULT_SQL+" WHERE r.equip_id=? AND ev.status::text IN ('CHECK_NEEDED','IN_PROGRESS') ORDER BY r.analyzed_at DESC,r.id DESC LIMIT 1",equipId)
                .stream().map(this::result).toList();
        Result current=!open.isEmpty()?open.get(0):(history.isEmpty()?null:history.get(0));
        return new Detail(equipId,(String)equipment.get("name"),(String)equipment.get("underpass_name"),state(current),current,history);
    }

    @Transactional
    public Detail review(LoginUser user,long equipId,SinkholeController.Review review) {
        Map<String,Object> equipment=owned(user,equipId,true);
        List<Map<String,Object>> events=db.queryForList("SELECT id,status::text AS status FROM pm_event WHERE id=? AND equip_id=? AND trigger_type::text='싱크홀탐지' FOR UPDATE",review.eventId(),equipId);
        if(events.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND,"해당 노면의 탐지 이벤트가 아닙니다.");
        String from=(String)events.get(0).get("status");
        if(!List.of("CHECK_NEEDED","IN_PROGRESS").contains(from)) throw new BusinessException(HttpStatus.CONFLICT,"이미 종료된 탐지 건입니다.");
        String to=switch(review.verdict()) {case "CONFIRMED" -> "IN_PROGRESS"; case "FALSE_POSITIVE" -> "ETC"; case "RESOLVED" -> "RESOLVED"; default -> throw new BusinessException(HttpStatus.BAD_REQUEST,"올바르지 않은 판정입니다.");};
        String label=switch(review.verdict()) {case "CONFIRMED" -> "싱크홀 확인";case "FALSE_POSITIVE" -> "오탐 확인";default -> "조치 완료";};
        db.update("UPDATE pm_road_result SET review_status=?,reviewed_by=?,reviewed_at=NOW() WHERE event_id=?",review.verdict(),user.id(),review.eventId());
        db.update("UPDATE pm_event SET status=CAST(? AS pm_event_status) WHERE id=?",to,review.eventId());
        String kind="CONFIRMED".equals(review.verdict())?"START":to;
        String content=label+(review.content()==null||review.content().isBlank()?"":" · "+review.content().trim());
        Long actionId = db.queryForObject("INSERT INTO pm_action(event_id,content,manager_id,kind) VALUES (?,?,?,CAST(? AS pm_action_kind)) RETURNING id",
                Long.class,review.eventId(),content,user.id(),kind);
        long regionId = number(equipment.get("region_id"));
        stream.dataChangedToAdmins(regionId,"pm-action","CREATE",actionId);
        stream.dataChangedToAdmins(number(equipment.get("region_id")),"pm-event","UPDATE",review.eventId());
        stream.dataChangedToAdmins(number(equipment.get("region_id")),"pm-equipment","UPDATE",equipId);
        Detail updated = detail(user,equipId);
        Result reviewed = result(db.queryForList(RESULT_SQL+" WHERE r.event_id=? ORDER BY r.analyzed_at DESC,r.id DESC LIMIT 1",review.eventId()).get(0));
        publishResult(regionId, number(equipment.get("underpass_id")), equipId, reviewed);
        return updated;
    }

    @Transactional(readOnly=true)
    public Resource image(LoginUser user,long resultId) {
        List<Map<String,Object>> rows=db.queryForList("SELECT equip_id,image_name FROM pm_road_result WHERE id=?",resultId);
        if(rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND,"탐지 사진이 없습니다.");
        owned(user,number(rows.get(0).get("equip_id")),false);
        Object name=rows.get(0).get("image_name");
        if(name==null) throw new BusinessException(HttpStatus.NOT_FOUND,"탐지 사진이 없습니다.");
        Path path=imageDir.resolve(name.toString()).normalize();
        if(!path.startsWith(imageDir)||!Files.isRegularFile(path)) throw new BusinessException(HttpStatus.NOT_FOUND,"탐지 사진 파일이 없습니다.");
        return new FileSystemResource(path);
    }

    private Map<String,Object> owned(LoginUser user,long equipId,boolean lock) {
        var rows=db.queryForList("SELECT e.name,u.id AS underpass_id,u.region_id,u.name AS underpass_name FROM pm_equipment e JOIN underpass u ON u.id=e.underpass_id WHERE e.id=? AND e.type::text='ROAD' AND NOT e.is_deleted AND NOT u.is_deleted"+(lock?" FOR UPDATE OF e":""),equipId);
        if(rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND,"노면 관리 대상이 없습니다.");
        scope.requireOwnable(user,number(rows.get(0).get("region_id")));
        return rows.get(0);
    }
    private static final String RESULT_SQL="SELECT r.*,ev.status::text AS event_status FROM pm_road_result r LEFT JOIN pm_event ev ON ev.id=r.event_id";
    private Result result(Map<String,Object> r) {
        return new Result(number(r.get("id")),(String)r.get("job_id"),nullableLong(r.get("event_id")),nullableLong(r.get("camera_id")),
                Boolean.TRUE.equals(r.get("detected")),(BigDecimal)r.get("confidence"),((Number)r.get("detection_count")).intValue(),
                r.get("frame_seconds")==null?null:((Number)r.get("frame_seconds")).doubleValue(),((Timestamp)r.get("analyzed_at")).toLocalDateTime(),
                (String)r.get("model_version"),(String)r.get("review_status"),r.get("image_name")==null?null:"/api/pm/road-results/"+r.get("id")+"/image",(String)r.get("event_status"));
    }
    public static String state(Result r) {
        if(r==null) return "미분석";
        if("RESOLVED".equals(r.eventStatus())) return "조치 완료";
        if("ETC".equals(r.eventStatus())) return "FALSE_POSITIVE".equals(r.reviewStatus())?"오탐 확인":"기타 종료";
        if(!r.detected()) return "이상 미탐지";
        return "CONFIRMED".equals(r.reviewStatus())?"싱크홀 확인":"싱크홀 의심";
    }
    private static long number(Object n) {return ((Number)n).longValue();}
    private static Long nullableLong(Object n) {return n==null?null:number(n);}
    private static void bad(String message) {throw new BusinessException(HttpStatus.BAD_REQUEST,message);}
}
