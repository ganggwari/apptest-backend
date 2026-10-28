package com.mbc.puf.display.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 차단·해제·위험도 변경이 DB 에 커밋된 "뒤에" 전광판 문구를 다시 맞춘다.
 * 전광판 쪽에서 오류가 나도 차단/해제 자체는 이미 확정된 뒤라서 취소되지 않는다.
 * (PostgreSQL 은 SQL 오류가 한 번 나면 같은 트랜잭션 전체를 못 쓰게 되므로, 같은 트랜잭션 안에서 try/catch 로는 막을 수 없다)
 * 실패하면 로그만 남기고, 60초마다 도는 VmsDisplayScheduler 가 다음 주기에 다시 맞춰 준다.
 */
@Component
public class VmsReconcileListener {

    private static final Logger log = LoggerFactory.getLogger(VmsReconcileListener.class);

    private final VmsDisplayService vmsDisplayService;
    private final TransactionTemplate newTransaction;

    public VmsReconcileListener(VmsDisplayService vmsDisplayService, PlatformTransactionManager transactionManager) {
        this.vmsDisplayService = vmsDisplayService;
        // 커밋이 끝난 트랜잭션에는 더 쓸 수 없으므로 새 트랜잭션에서 처리한다
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // 트랜잭션 밖에서 불러도(fallbackExecution) 바로 처리한다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReconcileRequested(VmsReconcileRequested request) {
        try {
            newTransaction.executeWithoutResult(status ->
                    vmsDisplayService.reconcileForUnderpass(request.underpassId(), request.eventId(), request.operatorId()));
        } catch (RuntimeException e) {
            log.warn("[vms] 지하차도 {} 전광판 갱신 실패 (다음 주기에 다시 맞춘다): {}", request.underpassId(), e.toString());
        }
    }
}
