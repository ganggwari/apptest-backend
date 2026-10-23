package com.mbc.puf.underpass.dto;

/**
 * 상행/하행 입·출구 카메라 지정 (MP-10-1). 네 칸을 한 번에 보낸다. null 이면 그 자리는 지정 해제.
 * 지정하는 카메라는 반드시 이 지하차도 소속이어야 하고, 한 카메라를 두 자리에 겹쳐 지정할 수 없다.
 */
public class GateCameraRequest {

    private Long upEntryCameraId;
    private Long upExitCameraId;
    private Long downEntryCameraId;
    private Long downExitCameraId;

    public GateCameraRequest() {
    }

    public Long getUpEntryCameraId() { return upEntryCameraId; }
    public void setUpEntryCameraId(Long upEntryCameraId) { this.upEntryCameraId = upEntryCameraId; }

    public Long getUpExitCameraId() { return upExitCameraId; }
    public void setUpExitCameraId(Long upExitCameraId) { this.upExitCameraId = upExitCameraId; }

    public Long getDownEntryCameraId() { return downEntryCameraId; }
    public void setDownEntryCameraId(Long downEntryCameraId) { this.downEntryCameraId = downEntryCameraId; }

    public Long getDownExitCameraId() { return downExitCameraId; }
    public void setDownExitCameraId(Long downExitCameraId) { this.downExitCameraId = downExitCameraId; }
}
