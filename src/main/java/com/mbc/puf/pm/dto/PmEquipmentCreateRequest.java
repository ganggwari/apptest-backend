package com.mbc.puf.pm.dto;

import java.time.LocalDate;

import com.mbc.puf.common.enums.PmEquipmentType;

import jakarta.validation.constraints.NotNull;

/**
 * 예지보전 설비 등록 요청 (POST /api/pm/equipments).
 * 이름(name)과 센서 주기는 보내지 않는다 — DB 가 종류글자-지하차도id-번호(예: P-12-1)와 종류별 기본 주기를 정한다.
 */
public class PmEquipmentCreateRequest {

    @NotNull(message = "underpassId는 필수입니다.")
    private Long underpassId;

    @NotNull(message = "type은 필수입니다.")
    private PmEquipmentType type;       // CCTV / DISPLAY / PUMP / WATER_LEVEL

    private Long camId;                 // CCTV 설비일 때만. 그 지하차도의 카메라 (선택)
    private Long itsVmsId;              // DISPLAY 설비일 때만. 다른 설비에 연결되지 않은 ITS 전광판 (선택)
    private LocalDate installedAt;      // 설치일 (선택, 오늘 이후는 불가)

    public PmEquipmentCreateRequest() {
    }

    public Long getUnderpassId() { return underpassId; }
    public void setUnderpassId(Long underpassId) { this.underpassId = underpassId; }

    public PmEquipmentType getType() { return type; }
    public void setType(PmEquipmentType type) { this.type = type; }

    public Long getCamId() { return camId; }
    public void setCamId(Long camId) { this.camId = camId; }

    public Long getItsVmsId() { return itsVmsId; }
    public void setItsVmsId(Long itsVmsId) { this.itsVmsId = itsVmsId; }

    public LocalDate getInstalledAt() { return installedAt; }
    public void setInstalledAt(LocalDate installedAt) { this.installedAt = installedAt; }
}
