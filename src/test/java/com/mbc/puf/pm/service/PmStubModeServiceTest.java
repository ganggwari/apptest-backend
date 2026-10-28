package com.mbc.puf.pm.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import com.mbc.puf.common.security.*;
import com.mbc.puf.stream.StreamPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
class PmStubModeServiceTest {
 @Test void disabledModeDoesNotTouchDatabaseOrSensors(){
  var db=mock(JdbcTemplate.class);var sensors=mock(PmSensorBackfillServiceImpl.class);
  var service=new PmStubModeService(db,sensors,mock(ScopeResolver.class),mock(StreamPublisher.class),false);
  service.resolved(null,1);verifyNoInteractions(db,sensors);
  assertThrows(RuntimeException.class,()->service.change(null,1,"DANGER"));
 }
 @Test void roadIsRejectedAndCompletionEmitsNormalSensor(){
  var db=mock(JdbcTemplate.class);var sensors=mock(PmSensorBackfillServiceImpl.class);var scope=mock(ScopeResolver.class);
  when(db.queryForList(anyString(),eq(7L))).thenReturn(List.of(Map.of("type","ROAD","region_id",2L)));
  var service=new PmStubModeService(db,sensors,scope,mock(StreamPublisher.class),true);
  assertThrows(RuntimeException.class,()->service.change(null,7,"DANGER"));verifyNoInteractions(sensors);
  when(db.queryForList(anyString(),eq(7L))).thenReturn(List.of(Map.of("type","PUMP","region_id",2L)));
  when(sensors.emitNow(7,"PUMP",false)).thenReturn(1);
  service.resolved(null,7);verify(sensors).emitNow(7,"PUMP",false);verify(scope,atLeastOnce()).requireOwnable(null,2L);
 }
}
