package com.mbc.puf.member.dto;

import com.mbc.puf.common.enums.DevicePlatform;

/** 등록된 푸시 토큰 */
public record DeviceTokenResponse(Long id, DevicePlatform platform, boolean active) {
}
