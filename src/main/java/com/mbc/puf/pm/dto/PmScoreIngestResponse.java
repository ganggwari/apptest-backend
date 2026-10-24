package com.mbc.puf.pm.dto;

/** 점수 묶음 수신 결과 */
public class PmScoreIngestResponse {

    private final int received;          // 받은 건수
    private final int saved;             // 새로 저장한 건수
    private final int duplicated;        // 같은 설비·같은 시각이 이미 있어서 건너뛴 건수
    private final int unknownEquipment;  // DB 에 없는 설비라서 건너뛴 건수
    private final int eventsCreated;     // 새로 만든 점검 이벤트 수
    private final int eventsRaised;      // 주의 → 위험으로 단계만 올린 이벤트 수

    public PmScoreIngestResponse(int received, int saved, int duplicated, int unknownEquipment,
                                 int eventsCreated, int eventsRaised) {
        this.received = received;
        this.saved = saved;
        this.duplicated = duplicated;
        this.unknownEquipment = unknownEquipment;
        this.eventsCreated = eventsCreated;
        this.eventsRaised = eventsRaised;
    }

    public int getReceived() { return received; }
    public int getSaved() { return saved; }
    public int getDuplicated() { return duplicated; }
    public int getUnknownEquipment() { return unknownEquipment; }
    public int getEventsCreated() { return eventsCreated; }
    public int getEventsRaised() { return eventsRaised; }
}
