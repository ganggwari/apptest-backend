package com.mbc.puf.pm.service;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.security.*;
import com.mbc.puf.stream.StreamPublisher;

/** 로컬 센서 stub의 설비별 모드. 점수는 PM 서버가 새 센서값으로 계산한다. */
@Service
public class PmStubModeService {
    private final JdbcTemplate db;
    private final PmSensorBackfillServiceImpl sensors;
    private final ScopeResolver scope;
    private final StreamPublisher stream;
    private final boolean enabled;
    public PmStubModeService(JdbcTemplate db,PmSensorBackfillServiceImpl sensors,ScopeResolver scope,StreamPublisher stream,
            @Value("${puf.pm.sensor-stub.enabled:false}") boolean enabled) {
        this.db=db;this.sensors=sensors;this.scope=scope;this.stream=stream;this.enabled=enabled;
    }
    public record State(long equipId,String mode,int emittedSamples) {}
    @Transactional
    public State change(LoginUser user,long equipId,String mode) {
        if(!enabled)throw new BusinessException(HttpStatus.CONFLICT,"센서 stub가 비활성화되어 있습니다.");
        if(!mode.equals("NORMAL")&&!mode.equals("DANGER"))throw new BusinessException(HttpStatus.BAD_REQUEST,"NORMAL 또는 DANGER만 가능합니다.");
        var rows=db.queryForList("SELECT e.type::text AS type,u.region_id FROM pm_equipment e JOIN underpass u ON u.id=e.underpass_id WHERE e.id=? AND NOT e.is_deleted AND NOT u.is_deleted",equipId);
        if(rows.isEmpty())throw new BusinessException(HttpStatus.NOT_FOUND,"설비가 없습니다.");
        Map<String,Object> e=rows.get(0);long region=((Number)e.get("region_id")).longValue();
        scope.requireOwnable(user,region);
        String type=e.get("type").toString();
        if(type.equals("ROAD"))throw new BusinessException(HttpStatus.BAD_REQUEST,"노면은 영상 분석을 사용합니다.");
        db.queryForObject("SELECT TRUE FROM pg_advisory_xact_lock(731905210)",Boolean.class);
        db.update("INSERT INTO pm_stub_mode(equip_id,mode,changed_at) VALUES (?,?,LOCALTIMESTAMP) ON CONFLICT(equip_id) DO UPDATE SET mode=EXCLUDED.mode,changed_at=EXCLUDED.changed_at",equipId,mode);
        int emitted=sensors.emitNow(equipId,type,mode.equals("DANGER"));
        stream.dataChangedToAdmins(region,"pm-stub-mode","UPDATE",equipId);
        return new State(equipId,mode,emitted);
    }
    @Transactional
    public void resolved(LoginUser user,long equipId) {
        if(enabled)change(user,equipId,"NORMAL");
    }
}
