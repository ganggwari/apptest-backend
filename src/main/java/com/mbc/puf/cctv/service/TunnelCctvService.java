package com.mbc.puf.cctv.service;

import java.util.List;

import com.mbc.puf.cctv.dto.TunnelCameraItem;
import com.mbc.puf.cctv.dto.TunnelCameraStreamResponse;

public interface TunnelCctvService {

    /** 신월여의지하도로 공개 CCTV 목록 */
    List<TunnelCameraItem> getCameras();

    /** 한 대의 영상 정보 (검증·변환을 마친 재생 주소 포함) */
    TunnelCameraStreamResponse getStream(String cctvId);
}
