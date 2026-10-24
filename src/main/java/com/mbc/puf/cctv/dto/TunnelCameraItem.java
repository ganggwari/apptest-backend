package com.mbc.puf.cctv.dto;

/** 신월여의지하도로 공개 CCTV 한 대 (목록용) */
public class TunnelCameraItem {

    private final String id;

    public TunnelCameraItem(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
