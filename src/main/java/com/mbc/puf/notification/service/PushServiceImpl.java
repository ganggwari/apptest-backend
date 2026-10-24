package com.mbc.puf.notification.service;

import com.mbc.puf.notification.dto.PushMessage;
import com.mbc.puf.notification.dto.PushResult;
import com.mbc.puf.notification.client.PushSender;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mbc.puf.common.enums.DeliveryStatus;
import com.mbc.puf.member.entity.DeviceToken;
import com.mbc.puf.member.entity.ManagerDeviceToken;
import com.mbc.puf.member.repository.DeviceTokenRepository;
import com.mbc.puf.member.repository.ManagerDeviceTokenRepository;

import jakarta.annotation.PreDestroy;

@Service
public class PushServiceImpl implements PushService {

    private static final Logger log = LoggerFactory.getLogger(PushServiceImpl.class);

    private final PushSender sender;
    private final DeviceTokenRepository tokenRepository;
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "push-sender");
        t.setDaemon(true);
        return t;
    });

    private final ManagerDeviceTokenRepository managerTokenRepository;

    public PushServiceImpl(PushSender sender, DeviceTokenRepository tokenRepository,
                           ManagerDeviceTokenRepository managerTokenRepository) {
        this.sender = sender;
        this.tokenRepository = tokenRepository;
        this.managerTokenRepository = managerTokenRepository;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    @Override
    public List<DeviceToken> activeTokens(Long customerId) {
        return tokenRepository.findByCustomerIdAndActiveTrueOrderByIdAsc(customerId);
    }

    @Override
    public void sendAfterCommit(List<DeviceToken> tokens, PushMessage message, ResultListener listener) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        List<DeviceToken> copy = List.copyOf(tokens);
        Runnable job = () -> executor.execute(() -> deliver(copy, message, listener));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    job.run();
                }
            });
        } else {
            job.run();
        }
    }

    @Override
    public void sendToCustomerAfterCommit(Long customerId, PushMessage message) {
        sendAfterCommit(activeTokens(customerId), message, null);
    }

    @Override
    public void sendToAllAfterCommit(PushMessage message) {
        sendAfterCommit(tokenRepository.findByActiveTrue(), message, null);
    }

    @Override
    public void sendToManagersAfterCommit(Long regionId, PushMessage message) {
        Runnable job = () -> executor.execute(() -> {
            List<ManagerDeviceToken> tokens;
            try {
                tokens = managerTokenRepository.findActiveForRegion(regionId);
            } catch (RuntimeException e) {
                log.warn("[push] 관리자 토큰 조회 실패 regionId={}: {}", regionId, e.toString());
                return;
            }
            for (ManagerDeviceToken t : tokens) {
                try {
                    if (sender.send(t.getToken(), message) == PushResult.INVALID_TOKEN) {
                        managerTokenRepository.deactivateById(t.getId());
                    }
                } catch (RuntimeException e) {
                    log.warn("[push] 관리자 발송 중 오류 tokenId={}: {}", t.getId(), e.toString());
                }
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    job.run();
                }
            });
        } else {
            job.run();
        }
    }

    @Override
    public TestResult sendTest(Long customerId, PushMessage message) {
        List<DeviceToken> tokens = activeTokens(customerId);
        int ok = 0, invalid = 0, fail = 0;
        for (DeviceToken t : tokens) {
            PushResult r = sendOne(t, message);
            if (r == PushResult.SUCCESS) {
                ok++;
            } else if (r == PushResult.INVALID_TOKEN) {
                invalid++;
            } else {
                fail++;
            }
        }
        return new TestResult(tokens.size(), ok, invalid, fail);
    }

    // 별도 스레드에서: 토큰마다 보내고, 만료 토큰은 끄고, 결과를 알린다
    private void deliver(List<DeviceToken> tokens, PushMessage message, ResultListener listener) {
        for (DeviceToken t : tokens) {
            PushResult r;
            try {
                r = sendOne(t, message);
            } catch (RuntimeException e) {
                log.warn("[push] 발송 중 오류 tokenId={}: {}", t.getId(), e.toString());
                r = PushResult.FAIL;
            }
            if (listener != null) {
                try {
                    listener.onResult(t, r == PushResult.SUCCESS ? DeliveryStatus.SUCCESS : DeliveryStatus.FAIL);
                } catch (RuntimeException e) {
                    log.warn("[push] 결과 기록 실패 tokenId={}: {}", t.getId(), e.toString());
                }
            }
        }
    }

    private PushResult sendOne(DeviceToken t, PushMessage message) {
        PushResult r = sender.send(t.getToken(), message);
        if (r == PushResult.INVALID_TOKEN) {
            tokenRepository.deactivateById(t.getId());   // 다시 쓰면 안 되는 토큰은 끈다 (행은 보존)
        }
        return r;
    }
}
