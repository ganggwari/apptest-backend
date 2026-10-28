package com.mbc.puf.chat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatServiceTest {
    @Test void privateDataNeverQueriesDatabase() {
        var em = mock(EntityManager.class);
        var answer = new ChatService(em).answer("내 차량번호와 연락처 알려줘");
        assertTrue(answer.sources().isEmpty());
        assertTrue(answer.answer().contains("조회하지 않습니다"));
        verifyNoInteractions(em);
    }
    @Test void facilityUsesBoundInputAndPublicColumns() {
        var em = mock(EntityManager.class);
        var query = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.<Object[]>of(new Object[]{1L,"초량1지하차도","BLOCKED","위험",15,"동구"}));
        var answer = new ChatService(em).answer("초량1지하차도 수위");
        assertTrue(answer.answer().contains("15cm"));
        assertTrue(answer.answer().contains("통제 중"));
        verify(query).setParameter("question", "초량1지하차도 수위");
        assertFalse(answer.sources().isEmpty());
    }
    @Test void unknownQuestionDoesNotReturnUnrelatedFaq() {
        var em = mock(EntityManager.class); var query = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(),any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.<Object[]>of(new Object[]{1L,"질문","관련 없는 답변",0}));
        assertTrue(new ChatService(em).answer("우주선").sources().isEmpty());
    }
    @Test void noticesReadOnlyPublicNonDeletedRows() {
        var em = mock(EntityManager.class); var query = mock(Query.class);
        when(em.createNativeQuery(contains("from notice where is_deleted=false"))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.<Object[]>of(new Object[]{1L,"점검 공지","점검합니다"}));
        assertTrue(new ChatService(em).answer("최근 공지").answer().contains("점검합니다"));
    }
}
