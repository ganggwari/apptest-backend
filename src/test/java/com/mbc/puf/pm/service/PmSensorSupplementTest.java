package com.mbc.puf.pm.service;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.mbc.puf.pm.dto.PmScoreIngestItem;
import com.mbc.puf.pm.repository.PmScoreRepository;
import tools.jackson.databind.json.JsonMapper;
class PmSensorSupplementTest {
 @Test void supplementsOnlyNonEmptyComparisonPayloads() throws Exception {
  var mapper=JsonMapper.builder().build();
  var repository=mock(PmScoreRepository.class);
  var service=mock(PmIngestServiceImpl.class,CALLS_REAL_METHODS);
  ReflectionTestUtils.setField(service,"scoreRepository",repository);
  ReflectionTestUtils.setField(service,"objectMapper",mapper);
  var item=new PmScoreIngestItem(); item.setEquipId(1L); item.setPredictedAt(LocalDateTime.of(2026,10,28,12,0));
  item.setDetail(mapper.readTree("{\"extra\":{\"sensors\":[{\"value\":0}]}}"));
  service.supplementComparisonSensors(item);
  verify(repository).supplementSensors(eq(1L),eq(item.getPredictedAt()),eq("[{\"value\":0}]"));
  verifyNoMoreInteractions(repository); clearInvocations(repository);
  for(String json:new String[]{"{}","{\"extra\":{\"sensors\":[]}}","{\"extra\":{\"sensors\":null}}"}) {
   item.setDetail(mapper.readTree(json)); service.supplementComparisonSensors(item);
  }
  item.setDetail(null); service.supplementComparisonSensors(item); verifyNoInteractions(repository);
 }
}
