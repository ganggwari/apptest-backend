package com.mbc.puf.cctv.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.mbc.puf.common.exception.BusinessException;

/**
 * 제공처가 준 영상 주소를 검증하고 재생 가능한 주소로 바꾼다 (프런트 seoulTunnelCctv.ts 가 하던 일과 같다).
 *  - http/https 만, 아이디·비밀번호가 들어간 주소는 거부
 *  - 옛 스트리밍 서버(http://211.61.148.68:1935)는 운영사 HTTPS 주소(https://www.seoultunnel.co.kr)로 바꾼다
 *    (공식 지도 플레이어와 같은 방식이고, 교차 출처 HLS 재생이 허용되는 주소다)
 *  - 최종 주소는 https + seoultunnel.co.kr / www.seoultunnel.co.kr 만 허용
 */
public final class TunnelStreamUrl {

    private static final String LEGACY_HOST = "211.61.148.68";
    private static final int LEGACY_PORT = 1935;
    private static final String OFFICIAL_HOST = "www.seoultunnel.co.kr";
    private static final Set<String> ALLOWED_HOSTS = Set.of("seoultunnel.co.kr", OFFICIAL_HOST);

    private TunnelStreamUrl() {
    }

    public static String normalize(String raw) {
        URI uri;
        try {
            uri = new URI(raw.trim());
        } catch (URISyntaxException | NullPointerException e) {
            throw unsupported("지원하지 않는 CCTV 영상 주소입니다.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!(scheme.equals("http") || scheme.equals("https")) || host.isEmpty() || uri.getUserInfo() != null) {
            throw unsupported("지원하지 않는 CCTV 영상 주소입니다.");
        }
        if (scheme.equals("http") && host.equals(LEGACY_HOST) && uri.getPort() == LEGACY_PORT) {
            scheme = "https";
            host = OFFICIAL_HOST;
            uri = rebuild(uri, scheme, host);
        }
        if (!scheme.equals("https") || !ALLOWED_HOSTS.contains(host)) {
            throw unsupported("공식 CCTV 영상 주소를 확인하지 못했습니다. 공식 사이트에서 확인해 주세요.");
        }
        return uri.toString();
    }

    // 경로·쿼리는 그대로 두고 scheme/host 만 바꾼다 (포트는 기본 포트로)
    private static URI rebuild(URI uri, String scheme, String host) {
        try {
            return new URI(scheme, null, host, -1, uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw unsupported("지원하지 않는 CCTV 영상 주소입니다.");
        }
    }

    private static BusinessException unsupported(String message) {
        return new BusinessException(HttpStatus.BAD_GATEWAY, message);
    }
}
