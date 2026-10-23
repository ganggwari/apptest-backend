package com.mbc.puf.underpass.service;

import java.util.List;

import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.underpass.dto.CameraCreateRequest;
import com.mbc.puf.underpass.dto.CameraResponse;
import com.mbc.puf.underpass.dto.CameraUpdateRequest;

/** 지하차도 하위 카메라 관리. 조회는 관리자면 지역 상관없이 가능, 등록/수정/삭제는 해당 지하차도 담당 지역 관리자만 */
public interface CameraService {

    // 특정 지하차도의 카메라 전체 (underpassId가 존재하지 않으면 404)
    List<CameraResponse> getCameras(LoginUser loginUser, Long underpassId);

    // 카메라 단건 (해당 underpassId 소속이 아니면 404)
    CameraResponse getCamera(LoginUser loginUser, Long underpassId, Long id);

    // 등록. REGIONAL_ADMIN은 해당 지하차도가 본인 담당 지역이 아니면 403
    CameraResponse createCamera(LoginUser loginUser, Long underpassId, CameraCreateRequest request);

    // 수정
    CameraResponse updateCamera(LoginUser loginUser, Long underpassId, Long id, CameraUpdateRequest request);

    // 삭제 (소프트 삭제)
    void deleteCamera(LoginUser loginUser, Long underpassId, Long id);
}
