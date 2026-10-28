package com.mbc.puf.cctv.service;

import java.io.InputStream;
import java.net.http.HttpResponse;

import com.mbc.puf.common.security.LoginUser;

import tools.jackson.databind.JsonNode;

/** OCR 서버의 영상·스냅샷·실행 버튼을 대신 전달 (cameraId 는 DB camera.id) */
public interface CctvService {
    JsonNode analysisState(LoginUser loginUser, Long cameraId);


    /** 탐지 영상(MJPEG) 연결을 연다. 카메라가 없으면 404, OCR 쪽 오류는 같은 의미의 상태코드로. 반환된 body 는 호출한 쪽이 닫는다 */
    HttpResponse<InputStream> openStream(Long cameraId);

    /** 지금 화면 한 장(jpg) */
    byte[] snapshot(Long cameraId);

    /** 카메라별 인식 실행 상태 */
    JsonNode plateState();

    /** 영상 처음부터 재생 + 인식 시작. 담당 지역 카메라만 가능(403) */
    JsonNode startPlate(LoginUser loginUser, Long cameraId);

    /**
     * 시연용: 넣을 번호판을 직접 지정해서 시작한다 (carNum 이 비어 있으면 위와 같다).
     * 진짜 OCR 서버(fastapi 모드)는 영상에 나오는 번호만 읽으므로 carNum 을 무시한다. stub 모드에서만 쓰인다
     */
    default JsonNode startPlate(LoginUser loginUser, Long cameraId, String carNum) {
        return startPlate(loginUser, cameraId);
    }

    /** 영상 + 인식 정지. 담당 지역 카메라만 가능(403) */
    JsonNode stopPlate(LoginUser loginUser, Long cameraId);
}
