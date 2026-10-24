package com.mbc.puf.cctv.dto;

/** 신월여의지하도로 CCTV 한 대의 영상 정보. streamUrl 은 브라우저가 HLS(.m3u8)로 바로 재생하는 주소 */
public class TunnelCameraStreamResponse {

    private final String id;
    private final String name;
    private final String direction;
    private final String streamUrl;

    public TunnelCameraStreamResponse(String id, String name, String direction, String streamUrl) {
        this.id = id;
        this.name = name;
        this.direction = direction;
        this.streamUrl = streamUrl;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDirection() {
        return direction;
    }

    public String getStreamUrl() {
        return streamUrl;
    }
}
