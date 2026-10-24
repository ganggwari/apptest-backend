package com.mbc.puf.cctv.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.cctv.client.SeoulTunnelClient;
import com.mbc.puf.cctv.dto.TunnelCameraItem;
import com.mbc.puf.cctv.dto.TunnelCameraStreamResponse;

/**
 * 서울터널 제공처를 매번 부르지 않도록 짧게 캐시한다 (로그인 없이 열린 API라 호출이 많아도 제공처엔 가지 않게).
 * 제공처가 응답하지 않으면 예전에 받아 둔 값이 있을 때 그 값을 그대로 쓴다 (시연 중 끊김 방지).
 */
@Service
public class TunnelCctvServiceImpl implements TunnelCctvService {

    private static final Logger log = LoggerFactory.getLogger(TunnelCctvServiceImpl.class);
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
    private static final String LIST_KEY = "list";

    private record Cached(Object value, long expiresAtMillis) {}

    private final SeoulTunnelClient client;
    private final long listTtlMillis;
    private final long streamTtlMillis;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public TunnelCctvServiceImpl(SeoulTunnelClient client,
                                 @Value("${puf.tunnel-cctv.list-cache-seconds:300}") int listCacheSeconds,
                                 @Value("${puf.tunnel-cctv.stream-cache-seconds:60}") int streamCacheSeconds) {
        this.client = client;
        this.listTtlMillis = listCacheSeconds * 1000L;
        this.streamTtlMillis = streamCacheSeconds * 1000L;
    }

    @Override
    public List<TunnelCameraItem> getCameras() {
        return cameraIds().stream().map(TunnelCameraItem::new).toList();
    }

    @Override
    public TunnelCameraStreamResponse getStream(String cctvId) {
        String id = cctvId == null ? "" : cctvId.trim();
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CCTV 아이디 형식이 올바르지 않습니다.");
        }
        // 목록에 없는 아이디로 제공처를 마구 부르지 못하게 막는다
        if (!cameraIds().contains(id)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "공개 CCTV 목록에 없는 카메라입니다.");
        }
        return cached("stream:" + id, streamTtlMillis, () -> {
            SeoulTunnelClient.CameraInfo info = client.fetchCameraInfo(id);
            if (info == null) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "선택한 CCTV 영상 정보를 확인하지 못했습니다.");
            }
            String name = info.name().isEmpty() ? "공개 CCTV" : info.name();
            return new TunnelCameraStreamResponse(id, name, info.direction(), TunnelStreamUrl.normalize(info.rawStreamUrl()));
        });
    }

    private List<String> cameraIds() {
        return cached(LIST_KEY, listTtlMillis, client::fetchCameraIds);
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(String key, long ttlMillis, Supplier<T> loader) {
        long now = System.currentTimeMillis();
        Cached hit = cache.get(key);
        if (hit != null && hit.expiresAtMillis() > now) {
            return (T) hit.value();
        }
        try {
            T value = loader.get();
            cache.put(key, new Cached(value, now + ttlMillis));
            if (cache.size() > 500) {
                cache.entrySet().removeIf(e -> e.getValue().expiresAtMillis() < now);   // 만료된 것 정리
            }
            return value;
        } catch (BusinessException e) {
            if (hit != null) {
                log.warn("[tunnel-cctv] 제공처 실패, 이전 값 사용: key={} ({})", key, e.getMessage());
                return (T) hit.value();
            }
            throw e;
        }
    }
}
