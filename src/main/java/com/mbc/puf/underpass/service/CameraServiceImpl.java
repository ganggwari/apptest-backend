package com.mbc.puf.underpass.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.ScopeResolver;
import com.mbc.puf.underpass.dto.CameraCreateRequest;
import com.mbc.puf.underpass.dto.CameraResponse;
import com.mbc.puf.underpass.dto.CameraUpdateRequest;
import com.mbc.puf.underpass.entity.Camera;
import com.mbc.puf.underpass.entity.Underpass;
import com.mbc.puf.underpass.repository.CameraRepository;
import com.mbc.puf.underpass.repository.UnderpassRepository;

import jakarta.persistence.EntityManager;

@Service
public class CameraServiceImpl implements CameraService {


    private final CameraRepository cameraRepository;
    private final UnderpassRepository underpassRepository;
    private final EntityManager em;

    private final ScopeResolver scopeResolver;
    private final com.mbc.puf.stream.StreamPublisher streamPublisher;   // SSE 실시간 이벤트

    public CameraServiceImpl(CameraRepository cameraRepository, UnderpassRepository underpassRepository,
                             EntityManager em, ScopeResolver scopeResolver,
                             com.mbc.puf.stream.StreamPublisher streamPublisher) {
        this.streamPublisher = streamPublisher;
        this.scopeResolver = scopeResolver;
        this.cameraRepository = cameraRepository;
        this.underpassRepository = underpassRepository;
        this.em = em;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CameraResponse> getCameras(LoginUser loginUser, Long underpassId) {
        requireExistingUnderpass(underpassId);   // 지역 제한 없이 존재만 확인 (조회는 관리자 모두 허용)
        return cameraRepository.findByUnderpassIdAndDeletedFalseOrderByCreatedAtAscIdAsc(underpassId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CameraResponse getCamera(LoginUser loginUser, Long underpassId, Long id) {
        return toResponse(requireCameraOfUnderpass(underpassId, id));
    }

    @Override
    @Transactional
    public CameraResponse createCamera(LoginUser loginUser, Long underpassId, CameraCreateRequest request) {
        Underpass underpass = requireExistingUnderpass(underpassId);
        requireOwnableRegion(loginUser, underpass.getRegionId());

        Camera camera = new Camera();
        camera.setUnderpassId(underpassId);
        camera.setCameraRole(request.getCameraRole());
        camera.setStreamUrl(request.getStreamUrl());
        camera.setDistanceM(request.getDistanceM());
        camera.setLatitude(request.getLatitude());
        camera.setLongitude(request.getLongitude());
        cameraRepository.saveAndFlush(camera);
        streamPublisher.dataChangedToAdmins(underpass.getRegionId(), "camera", "CREATE", camera.getId());
        return toResponse(reload(camera));
    }

    @Override
    @Transactional
    public CameraResponse updateCamera(LoginUser loginUser, Long underpassId, Long id, CameraUpdateRequest request) {
        Camera camera = requireCameraOfUnderpass(underpassId, id);   // 존재 + 해당 지하차도 소속 확인
        Underpass underpass = requireExistingUnderpass(underpassId);
        requireOwnableRegion(loginUser, underpass.getRegionId());

        // underpass_id 는 여기서 바꾸지 않는다
        camera.setCameraRole(request.getCameraRole());
        camera.setStreamUrl(request.getStreamUrl());
        camera.setDistanceM(request.getDistanceM());
        camera.setLatitude(request.getLatitude());
        camera.setLongitude(request.getLongitude());
        streamPublisher.dataChangedToAdmins(underpass.getRegionId(), "camera", "UPDATE", camera.getId());
        return toResponse(reload(camera));
    }

    @Override
    @Transactional
    public void deleteCamera(LoginUser loginUser, Long underpassId, Long id) {
        Camera camera = requireCameraOfUnderpass(underpassId, id);
        Underpass underpass = requireExistingUnderpass(underpassId);
        requireOwnableRegion(loginUser, underpass.getRegionId());
        camera.setDeleted(true);   // 소프트 삭제 (plate_log 참조 보존용)
        streamPublisher.dataChangedToAdmins(underpass.getRegionId(), "camera", "DELETE", camera.getId());
    }

    // ---- 내부 메서드 ----

    // 저장 직후 DB 값으로 다시 읽는다 (DECIMAL 자릿수를 예전 응답과 똑같이 맞추기 위함)
    private Camera reload(Camera camera) {
        em.flush();
        em.refresh(camera);
        return camera;
    }

    private Underpass requireExistingUnderpass(Long underpassId) {
        return underpassRepository.findByIdAndDeletedFalse(underpassId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 지하차도입니다. id=" + underpassId));
    }

    // 카메라가 존재하고, 지정된 underpassId 소속인지 확인. 아니면 404 (다른 지하차도의 카메라 id로 접근 차단)
    private Camera requireCameraOfUnderpass(Long underpassId, Long id) {
        Camera camera = cameraRepository.findByIdAndDeletedFalse(id).orElse(null);
        if (camera == null || !camera.getUnderpassId().equals(underpassId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 카메라입니다. id=" + id);
        }
        return camera;
    }

    // REGIONAL_ADMIN이 자기 담당 지역이 아닌 지하차도의 카메라를 등록/수정/삭제하려 하면 차단
    private void requireOwnableRegion(LoginUser loginUser, Long regionId) {
        scopeResolver.requireOwnable(loginUser, regionId);
    }

    private CameraResponse toResponse(Camera camera) {
        return new CameraResponse(camera.getId(), camera.getUnderpassId(), camera.getCameraRole(),
                camera.getStreamUrl(), camera.getDistanceM(), camera.getLatitude(), camera.getLongitude(),
                camera.getCreatedAt());
    }
}
