package com.mbc.puf.stream;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 서비스 코드에서 SSE 이벤트를 보낼 때 쓰는 창구.
 * 이벤트는 바로 보내지 않고 스프링 이벤트로 발행하며, DB 트랜잭션이 커밋된 뒤에(AFTER_COMMIT)
 * StreamEventListener 가 받아서 실제로 전송한다. (롤백된 변경은 화면에 나가지 않게 하기 위함)
 * 트랜잭션 밖에서 호출하면 즉시 전송된다.
 */
@Component
public class StreamPublisher {

    private final ApplicationEventPublisher publisher;

    public StreamPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /** 이미 만들어진 이벤트를 그대로 발행 (테스트용 컨트롤러 등에서 사용) */
    public void publish(StreamEvent event) {
        publisher.publishEvent(event);
    }

    /** 공개 이벤트. regionId 는 관리자 필터용(고객에게는 지역과 상관없이 전달됨). 지역 구분이 없으면 null */
    public void publishPublic(String name, Long regionId, Object data) {
        publisher.publishEvent(new StreamEvent(name, data, StreamScope.PUBLIC, regionId, null));
    }

    // ---- data-changed: "다시 조회하라"는 신호 (본문 없음). 이벤트 이름은 모두 data-changed ----

    /** 관리자에게만. regionId 가 있으면 그 지역 담당 관리자에게만 */
    public void dataChangedToAdmins(Long regionId, String resource, String action, Long id) {
        publishToAdmins(regionId, "data-changed", new StreamPayloads.DataChanged(resource, action, id, regionId));
    }

    /** 고객·관리자 모두에게 (공개 데이터: 지하차도, FAQ, 공지) */
    public void dataChangedPublic(Long regionId, String resource, String action, Long id) {
        publishPublic("data-changed", regionId, new StreamPayloads.DataChanged(resource, action, id, regionId));
    }

    /** 특정 고객 1명에게 */
    public void dataChangedToCustomer(Long customerId, String resource, String action, Long id) {
        publishToCustomer(customerId, "data-changed", new StreamPayloads.DataChanged(resource, action, id, null));
    }

    /** 특정 고객 1명에게 */
    public void publishToCustomer(Long customerId, String name, Object data) {
        publisher.publishEvent(new StreamEvent(name, data, StreamScope.CUSTOMER, null, customerId));
    }

    /** 특정 관리자 1명에게 */
    public void publishToManager(Long managerId, String name, Object data) {
        publisher.publishEvent(new StreamEvent(name, data, StreamScope.MANAGER, null, managerId));
    }

    /** 관리자 전용. regionId 가 null 이면 모든 관리자, 값이 있으면 그 지역 담당 관리자에게만 */
    public void publishToAdmins(Long regionId, String name, Object data) {
        publisher.publishEvent(new StreamEvent(name, data, StreamScope.ADMIN, regionId, null));
    }
}
