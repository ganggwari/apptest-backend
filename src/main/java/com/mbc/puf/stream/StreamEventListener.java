package com.mbc.puf.stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋이 끝난 뒤에 이벤트를 StreamHub 로 넘긴다 (트랜잭션이 없으면 바로 넘김) */
@Component
public class StreamEventListener {

    private final StreamHub hub;

    public StreamEventListener(StreamHub hub) {
        this.hub = hub;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStreamEvent(StreamEvent event) {
        hub.dispatch(event);
    }
}
