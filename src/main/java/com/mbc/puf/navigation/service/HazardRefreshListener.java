package com.mbc.puf.navigation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 위험도·차단 상태가 커밋된 직후에 위험지점을 바로 다시 계산한다 (예전에는 2초마다 DB 를 보고 있었다).
 * 새 트랜잭션(읽기 전용)에서 읽으므로 방금 커밋된 값이 보이고, 실패해도 원래 처리에는 영향이 없다.
 * 놓친 변화는 HazardServiceImpl 의 안전 점검(30초)이 잡아 준다.
 */
@Component
public class HazardRefreshListener {

    private static final Logger log = LoggerFactory.getLogger(HazardRefreshListener.class);

    private final HazardService hazardService;
    private final TransactionTemplate newReadOnlyTransaction;

    public HazardRefreshListener(HazardService hazardService, PlatformTransactionManager transactionManager) {
        this.hazardService = hazardService;
        this.newReadOnlyTransaction = new TransactionTemplate(transactionManager);
        this.newReadOnlyTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.newReadOnlyTransaction.setReadOnly(true);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRefreshRequested(HazardRefreshRequested request) {
        try {
            newReadOnlyTransaction.executeWithoutResult(status -> hazardService.refresh());
        } catch (RuntimeException e) {
            log.warn("[hazard] 위험지점 즉시 갱신 실패 (안전 점검이 다시 시도한다): {}", e.toString());
        }
    }
}
