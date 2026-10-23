package com.mbc.puf.member.dto;

import com.mbc.puf.common.enums.DevicePlatform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 푸시 토큰 등록/해제 요청. 등록은 로그인 후 웹(WEB)/앱(APP)이 발급받은 토큰을 보낸다 */
public class DeviceTokenRequest {

    @NotBlank(message = "토큰은 필수입니다.")
    @Size(max = 255, message = "토큰은 255자 이하여야 합니다.")
    private String token;

    @NotNull(message = "platform(WEB/APP)은 필수입니다.")
    private DevicePlatform platform;

    public DeviceTokenRequest() {
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public DevicePlatform getPlatform() { return platform; }
    public void setPlatform(DevicePlatform platform) { this.platform = platform; }
}
