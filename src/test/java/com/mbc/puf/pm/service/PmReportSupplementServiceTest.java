package com.mbc.puf.pm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PmReportSupplementServiceTest {
    final LocalDateTime from=LocalDateTime.of(2026,10,26,0,0), to=from.plusDays(1);
    @Test void controlsKeepFullCountsAndScopeAndMapManualAndAutomaticRows() {
        EntityManager em=mock(EntityManager.class);List<String> sqls=new ArrayList<>();List<Map<String,Object>> params=new ArrayList<>();
        when(em.createNativeQuery(anyString())).thenAnswer(call->{
            String sql=call.getArgument(0);sqls.add(sql);Map<String,Object> p=new HashMap<>();params.add(p);
            Query q=mock(Query.class);when(q.setParameter(anyString(),any())).thenAnswer(c->{p.put(c.getArgument(0),c.getArgument(1));return q;});
            List<Object[]> rows=sql.startsWith("SELECT COUNT")?Collections.singletonList(new Object[]{120L,10L}):List.of(
                new Object[]{1L,2L,"초량지하차도","BLOCK",Timestamp.valueOf(from.plusHours(1)),true,"수위 상승",null,null},
                new Object[]{2L,2L,"초량지하차도","RELEASE",Timestamp.valueOf(from.plusHours(2)),false,"안전 확인","현장 점검","홍길동"});
            when(q.getResultList()).thenReturn(rows);return q;
        });
        var service=new PmReportSupplementService();ReflectionTestUtils.setField(service,"em",em);
        var result=service.control(List.of(7L),2L,from,to);
        assertEquals(120,result.blockCount());assertEquals(10,result.releaseCount());assertEquals(2,result.events().size());
        assertTrue(result.events().get(0).automatic());assertEquals("홍길동",result.events().get(1).operatorName());
        for(int i=0;i<sqls.size();i++) {
            assertTrue(sqls.get(i).contains("u.region_id IN (:regions)"));assertTrue(sqls.get(i).contains("u.id=:underpass"));
            assertTrue(sqls.get(i).contains("ev.created_at>=:from AND ev.created_at<:to"));
            assertEquals(List.of(7L),params.get(i).get("regions"));assertEquals(to,params.get(i).get("to"));
        }
    }
    @Test void sinkholeKeepsConfidenceSeparateAndMapsProtectedEvidenceUrlAndNoDetection() {
        EntityManager em=mock(EntityManager.class);
        when(em.createNativeQuery(anyString())).thenAnswer(call->{
            String sql=call.getArgument(0);Query q=mock(Query.class);when(q.setParameter(anyString(),any())).thenReturn(q);
            List<Object[]> rows;
            if(sql.startsWith("SELECT COUNT"))rows=Collections.singletonList(new Object[]{150L,20L});
            else rows=Collections.singletonList(new Object[]{3L,4L,"R-1",2L,"초량지하차도",8L,Timestamp.valueOf(from),true,new BigDecimal("0.87"),2,"CONFIRMED","IN_PROGRESS","photo.jpg",Timestamp.valueOf(from.plusHours(1)),"홍길동"});
            when(q.getResultList()).thenReturn(rows);return q;
        });
        when(em.createNativeQuery(anyString(),eq(Tuple.class))).thenAnswer(call->{
            Query q=mock(Query.class);when(q.setParameter(anyString(),any())).thenReturn(q);
            Tuple tuple=mock(Tuple.class);TupleElement<?> el=mock(TupleElement.class);
            when(el.getAlias()).thenReturn("total");doReturn(List.of(el)).when(tuple).getElements();doReturn(2L).when(tuple).get(el);
            when(q.getResultList()).thenReturn(List.of(tuple));return q;
        });
        var service=new PmReportSupplementService();ReflectionTestUtils.setField(service,"em",em);
        var result=service.sinkhole(List.of(7L),2L,from,to);
        assertEquals(150,result.analysisCount());assertEquals(20,result.detectedCount());assertEquals(2,result.newEventCount());
        assertEquals(2,result.openEventCount());assertEquals(new BigDecimal("0.87"),result.results().get(0).confidence());
        assertEquals("/api/pm/road-results/3/image",result.results().get(0).imageUrl());
        assertEquals("CONFIRMED",result.openIncidents().get(0).reviewStatus());
    }
}
