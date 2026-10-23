package com.mbc.puf.cctv.service;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.mbc.puf.cctv.client.OcrServerClient;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.ScopeResolver;
import com.mbc.puf.underpass.entity.Camera;
import com.mbc.puf.underpass.entity.Underpass;
import com.mbc.puf.underpass.repository.CameraRepository;
import com.mbc.puf.underpass.repository.UnderpassRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// puf.ocr.mode=fastapi(기본)일 때만 쓴다. Render 시연(stub)에서는 OcrStubCctvService 가 대신한다
@Service
@ConditionalOnProperty(name = "puf.ocr.mode", havingValue = "fastapi", matchIfMissing = true)
public class CctvServiceImpl implements CctvService {

    private final OcrServerClient ocr;
    private final CameraRepository cameraRepository;
    private final UnderpassRepository underpassRepository;
    private final ScopeResolver scopeResolver;
    private final ObjectMapper objectMapper;

    public CctvServiceImpl(OcrServerClient ocr, CameraRepository cameraRepository, UnderpassRepository underpassRepository,
                           ScopeResolver scopeResolver, ObjectMapper objectMapper) {
        this.ocr = ocr;
        this.cameraRepository = cameraRepository;
        this.underpassRepository = underpassRepository;
        this.scopeResolver = scopeResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public HttpResponse<InputStream> openStream(Long cameraId) {
        requireCamera(cameraId);
        HttpResponse<InputStream> upstream = ocr.openStream("/stream/" + cameraId);
        if (upstream.statusCode() != 200) {
            String body;
            try (InputStream in = upstream.body()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                body = "";
            }
            throw upstreamError(upstream.statusCode(), body);
        }
        return upstream;
    }

    @Override
    public byte[] snapshot(Long cameraId) {
        requireCamera(cameraId);
        HttpResponse<byte[]> res = ocr.getBytes("/stream/" + cameraId + "/snapshot");
        if (res.statusCode() != 200) throw upstreamError(res.statusCode(), new String(res.body(), StandardCharsets.UTF_8));
        return res.body();
    }

    @Override
    public JsonNode plateState() {
        return json(ocr.getBytes("/control/plate"));
    }

    @Override
    public JsonNode startPlate(LoginUser loginUser, Long cameraId) {
        requireOwnable(loginUser, requireCamera(cameraId));
        return json(ocr.postBytes("/control/plate/start?cameraId=" + cameraId));
    }

    @Override
    public JsonNode stopPlate(LoginUser loginUser, Long cameraId) {
        requireOwnable(loginUser, requireCamera(cameraId));
        return json(ocr.postBytes("/control/plate/stop?cameraId=" + cameraId));
    }

    // ---------------------------------------------------------------

    private Camera requireCamera(Long cameraId) {
        return cameraRepository.findByIdAndDeletedFalse(cameraId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "카메라를 찾을 수 없습니다. cameraId=" + cameraId));
    }

    private void requireOwnable(LoginUser loginUser, Camera camera) {
        Underpass underpass = underpassRepository.findByIdAndDeletedFalse(camera.getUnderpassId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "카메라의 지하차도를 찾을 수 없습니다."));
        scopeResolver.requireOwnable(loginUser, underpass.getRegionId());
    }

    // OCR 서버의 {"detail": "..."} 오류 문구를 살려서, 404/503 은 같은 상태코드로 그 밖에는 502 로 돌려준다
    private BusinessException upstreamError(int status, String body) {
        String message = "OCR 서버 오류 (" + status + ")";
        try {
            JsonNode detail = objectMapper.readTree(body).get("detail");
            if (detail != null && detail.isTextual()) message = detail.asText();
        } catch (Exception ignored) {
            // JSON 이 아니면 기본 문구
        }
        HttpStatus mapped = status == 404 || status == 503 ? HttpStatus.valueOf(status) : HttpStatus.BAD_GATEWAY;
        return new BusinessException(mapped, message);
    }

    private JsonNode json(HttpResponse<byte[]> res) {
        String body = new String(res.body(), StandardCharsets.UTF_8);
        if (res.statusCode() != 200) throw upstreamError(res.statusCode(), body);
        return objectMapper.readTree(body);
    }
}
