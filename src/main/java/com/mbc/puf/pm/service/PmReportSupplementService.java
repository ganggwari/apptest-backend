package com.mbc.puf.pm.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mbc.puf.common.jpa.NativeSql;
import com.mbc.puf.pm.dto.PmSinkholeSummary;
import com.mbc.puf.pm.dto.PmControlSummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** 데일리/교대 인수인계가 같은 범위와 반개구간 [from,to)으로 집계한다. */
@Service
@Transactional(readOnly = true)
public class PmReportSupplementService {
    @PersistenceContext private EntityManager em;
    private static final String ROAD_FROM = """
        FROM pm_road_result r JOIN pm_equipment e ON e.id=r.equip_id
        JOIN underpass u ON u.id=e.underpass_id
        LEFT JOIN pm_event ev ON ev.id=r.event_id
        LEFT JOIN manager m ON m.id=r.reviewed_by LEFT JOIN employee emp ON emp.id=m.employee_id
        WHERE NOT e.is_deleted AND NOT u.is_deleted AND e.type::text='ROAD'
        """;
    private static final String ROAD_COLUMNS = """
        SELECT r.id,e.id,e.name,u.id,u.name,r.event_id,r.analyzed_at,r.detected,r.confidence,
          r.detection_count,r.review_status,ev.status::text,r.image_name,r.reviewed_at,emp.name
        """;
    private NativeSql scoped(String sql,List<Long> regions,Long underpass) {
        return new NativeSql(sql)
            .andIf(regions!=null," AND u.region_id IN (:regions)","regions",regions)
            .andIf(underpass!=null," AND u.id=:underpass","underpass",underpass);
    }
    public PmSinkholeSummary sinkhole(List<Long> regions,Long underpass,LocalDateTime from,LocalDateTime to) {
        Object[] counts=scoped("SELECT COUNT(*), COUNT(*) FILTER (WHERE r.detected) "+ROAD_FROM,regions,underpass)
            .append(" AND r.analyzed_at>=:from AND r.analyzed_at<:to").param("from",from).param("to",to).rows(em).get(0);
        String eventFrom="""
            FROM pm_event ev JOIN pm_equipment e ON e.id=ev.equip_id JOIN underpass u ON u.id=e.underpass_id
            WHERE NOT e.is_deleted AND NOT u.is_deleted AND ev.trigger_type::text='싱크홀탐지'
            """;
        long newEvents=scoped("SELECT COUNT(*) AS total "+eventFrom,regions,underpass)
            .append(" AND ev.created_at>=:from AND ev.created_at<:to").param("from",from).param("to",to).single(em,PmQueryServiceImpl.Total.class).getTotal();
        long open=scoped("SELECT COUNT(*) AS total "+eventFrom,regions,underpass)
            .append(" AND ev.status::text IN ('CHECK_NEEDED','IN_PROGRESS')").single(em,PmQueryServiceImpl.Total.class).getTotal();
        List<PmSinkholeSummary.Item> results=scoped(ROAD_COLUMNS+ROAD_FROM,regions,underpass)
            .append(" AND r.analyzed_at>=:from AND r.analyzed_at<:to ORDER BY r.analyzed_at DESC,r.id DESC LIMIT 100")
            .param("from",from).param("to",to).rows(em).stream().map(this::road).toList();
        // 이벤트마다 가장 최근 탐지 사진 1개. 후속 미탐지가 미처리 의심 건을 숨기지 않는다.
        List<PmSinkholeSummary.Item> incidents=scoped(ROAD_COLUMNS+ROAD_FROM,regions,underpass)
            .append("""
                 AND ev.status::text IN ('CHECK_NEEDED','IN_PROGRESS') AND r.detected
                 AND NOT EXISTS (SELECT 1 FROM pm_road_result newer WHERE newer.event_id=r.event_id
                   AND newer.detected AND (newer.analyzed_at,newer.id)>(r.analyzed_at,r.id))
                 ORDER BY r.analyzed_at DESC,r.id DESC LIMIT 100
                """).rows(em).stream().map(this::road).toList();
        return new PmSinkholeSummary(n(counts[0]),n(counts[1]),newEvents,open,results,incidents);
    }
    public PmControlSummary control(List<Long> regions,Long underpass,LocalDateTime from,LocalDateTime to) {
        String base="""
            FROM event ev JOIN underpass u ON u.id=ev.underpass_id
            LEFT JOIN manager m ON m.id=ev.operator_id LEFT JOIN employee emp ON emp.id=m.employee_id
            WHERE NOT u.is_deleted AND ev.type::text IN ('BLOCK','RELEASE')
              AND ev.created_at>=:from AND ev.created_at<:to
            """;
        Object[] counts=scoped("SELECT COUNT(*) FILTER (WHERE ev.type::text='BLOCK'), COUNT(*) FILTER (WHERE ev.type::text='RELEASE') "+base,regions,underpass)
            .param("from",from).param("to",to).rows(em).get(0);
        List<PmControlSummary.Item> events=scoped("SELECT ev.id,u.id,u.name,ev.type::text,ev.created_at,ev.is_auto,ev.reason,ev.reason_note,emp.name "+base,regions,underpass)
            .append(" ORDER BY ev.created_at DESC,ev.id DESC LIMIT 100").param("from",from).param("to",to).rows(em)
            .stream().map(r->new PmControlSummary.Item(n(r[0]),n(r[1]),str(r[2]),str(r[3]),dt(r[4]),Boolean.TRUE.equals(r[5]),str(r[6]),str(r[7]),str(r[8]))).toList();
        return new PmControlSummary(n(counts[0]),n(counts[1]),events);
    }
    private PmSinkholeSummary.Item road(Object[] r) {
        return new PmSinkholeSummary.Item(n(r[0]),n(r[1]),str(r[2]),n(r[3]),str(r[4]),r[5]==null?null:n(r[5]),dt(r[6]),Boolean.TRUE.equals(r[7]),
            (BigDecimal)r[8],((Number)r[9]).intValue(),str(r[10]),str(r[11]),r[12]==null?null:"/api/pm/road-results/"+n(r[0])+"/image",dt(r[13]),str(r[14]));
    }
    private static long n(Object o) {return ((Number)o).longValue();}
    private static String str(Object o) {return o==null?null:o.toString();}
    private static LocalDateTime dt(Object o) {return o==null?null:o instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)o;}
}
