package com.mbc.puf.chat;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/** 공개 DB 전용 검색형 챗봇. 사용자 입력으로 SQL을 생성하거나 개인정보를 조회하지 않는다. */
@Service
@Transactional(readOnly = true)
public class ChatService {
    private final EntityManager em;
    public ChatService(EntityManager em) { this.em = em; }
    public record Source(String title, String url) {}
    public record Answer(String answer, List<Source> sources, LocalDateTime queriedAt) {}
    private Answer result(String text, List<Source> sources) { return new Answer(text, sources, LocalDateTime.now()); }
    private static final Pattern PRIVATE = Pattern.compile("개인정보|전화번호|연락처|차량번호|번호판|잔류차량|잔류 차량|내\\s*(차량|문의|계정|정보)");

    public Answer answer(String question) {
        String q = question.trim();
        if (PRIVATE.matcher(q).find()) return result("개인정보와 차량별 기록은 챗봇에서 조회하지 않습니다. 로그인 후 마이페이지 또는 담당 관리자 화면에서 확인해 주세요.", List.of());
        if (q.matches(".*(공지|안내문|새 소식).*")) return notices();
        if (q.matches(".*(지하차도|통제|차단|수위|위험|현황|시설).*")) {
            Answer facilities = facilities(q);
            if (facilities != null) return facilities;
        }
        return faq(q);
    }

    @SuppressWarnings("unchecked")
    private Answer facilities(String question) {
        // 전체 현황 또는 시설명/지역명을 포함한 질문. 입력은 항상 바인딩 값으로 전달한다.
        List<Object[]> rows = em.createNativeQuery("select u.id,u.name,u.status,u.risk_level,u.water_cm,r.name from underpass u join region r on r.id=u.region_id where u.is_deleted=false and (position(u.name in :question)>0 or position(r.name in :question)>0) order by u.name limit 10")
                .setParameter("question", question).getResultList();
        if (rows.isEmpty() && question.matches(".*(전체|현황|목록|몇|통제 중|차단 중).*")) {
            boolean blocked = question.contains("통제") || question.contains("차단");
            rows = em.createNativeQuery("select u.id,u.name,u.status,u.risk_level,u.water_cm,r.name from underpass u join region r on r.id=u.region_id where u.is_deleted=false and (:blocked=false or cast(u.status as text)='BLOCKED') order by u.name limit 10")
                    .setParameter("blocked", blocked).getResultList();
            if (rows.isEmpty()) return result(blocked ? "현재 DB에 통제 중인 지하차도가 없습니다." : "현재 DB에 등록된 시설이 없습니다.", List.of());
        }
        if (rows.isEmpty()) return null;
        List<Source> sources = new ArrayList<>();
        StringBuilder text = new StringBuilder("DB에 저장된 현재 시설 현황입니다 (최대 10곳).\n");
        for (Object[] r : rows) {
            text.append(r[1]).append(" · ").append(r[5]).append(" · ").append("BLOCKED".equals(String.valueOf(r[2])) ? "통제 중" : "정상 통행")
                .append(" · 위험 단계 ").append(r[3]).append(" · 수위 ").append(r[4]).append("cm\n");
            sources.add(new Source(String.valueOf(r[1]), "/c-map"));
        }
        return result(text.toString().trim(), sources);
    }

    @SuppressWarnings("unchecked")
    private Answer notices() {
        List<Object[]> rows = em.createNativeQuery("select id,title,content from notice where is_deleted=false order by is_important desc,created_at desc,id desc limit 3").getResultList();
        if (rows.isEmpty()) return result("현재 등록된 공지가 없습니다.", List.of());
        List<Source> sources = new ArrayList<>();
        StringBuilder text = new StringBuilder("최근 공지 (중요 공지 우선)\n");
        for (Object[] r : rows) {
            text.append(r[1]).append("\n").append(plain(r[2], 700)).append("\n\n");
            sources.add(new Source(String.valueOf(r[1]), "/c-notice"));
        }
        return result(text.toString().trim(), sources);
    }

    @SuppressWarnings("unchecked")
    private Answer faq(String question) {
        List<String> tokens = Arrays.stream(question.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .map(t -> t.replaceFirst("(은|는|이|가|을|를|에서|어떻게|하나요|인가요|알려줘)$", ""))
                .filter(t -> t.length() >= 2 && !Set.of("어떻게", "하나요", "알려줘", "주세요", "있나요").contains(t))
                .distinct().limit(12).toList();
        if (tokens.isEmpty()) return fallback();
        StringBuilder score = new StringBuilder("case when lower(question)=:exact then 1000 else 0 end");
        for (int i=0; i<tokens.size(); i++) score.append(" + case when position(:t").append(i).append(" in lower(question))>0 then 3 else 0 end + case when position(:t").append(i).append(" in lower(answer))>0 then 1 else 0 end");
        var query = em.createNativeQuery("select id,question,answer,(" + score + ") as relevance from faq where is_deleted=false order by relevance desc,sort_order,id limit 3");
        query.setParameter("exact", question.toLowerCase(Locale.ROOT));
        for (int i=0; i<tokens.size(); i++) query.setParameter("t"+i,tokens.get(i));
        List<Object[]> rows = query.getResultList();
        if (rows.isEmpty() || ((Number) rows.get(0)[3]).intValue() == 0) return fallback();
        Object[] best = rows.get(0);
        return result(plain(best[2], 3000), List.of(new Source(String.valueOf(best[1]), "/info/faq")));
    }
    private Answer fallback() { return result("DB에서 관련 답변을 찾지 못했습니다. 시설명을 포함해 ‘초량1지하차도 수위’, ‘전체 통제 현황’, ‘최근 공지’, ‘알림 설정’처럼 질문해 주세요.", List.of()); }
    private String plain(Object value, int max) {
        String text = String.valueOf(value).replaceAll("<[^>]*>", " ").trim();
        return text.length() > max ? text.substring(0,max) + "…" : text;
    }
}
