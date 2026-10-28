package com.mbc.puf.stream;

import java.time.LocalDateTime;
import com.mbc.puf.notification.dto.NotificationUnreadResponse;

public final class RealtimePayloads {
    private RealtimePayloads() {}
    /** category가 null인 all=true는 전체 읽음. unread는 해당 요청 처리 직후 스냅샷. */
    public record NotificationReadChange(Long id, boolean all, String category, NotificationUnreadResponse unread) {}
    /** changed=REGIONS/SHIFT. 계정 정보 재조회 후 필요 시 EventSource 재연결. */
    public record ManagerAssignmentChange(Long managerId, String changed) {}
    public record AnalysisStateChange(Long cameraId, Long underpassId, String analysisType,
            String state, boolean running, String error, LocalDateTime observedAt) {}
    public record EngineStatus(String status, LocalDateTime lastReceivedAt, Long secondsSinceReceived) {}
}
