package com.mbc.puf.internal.devstub;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.sms.StubSmsSender;
import com.mbc.puf.common.util.PhoneNumber;

/**
 * [개발 전용] 스텁 문자 발송기가 마지막으로 "보낸" 인증코드를 돌려준다. Postman·프런트 개발 때 콘솔을 안 봐도 되게.
 * puf.sms-stub.expose-code=true 일 때만 이 컨트롤러가 만들어진다(기본 false → 404). 시연·배포 환경에서는 절대 켜지 말 것.
 */
@RestController
@ConditionalOnProperty(name = "puf.sms-stub.expose-code", havingValue = "true")
public class DevSmsController {

    private static final Pattern CODE = Pattern.compile("\\[(\\d{6})]");

    private final StubSmsSender stubSmsSender;

    public DevSmsController(StubSmsSender stubSmsSender) {
        this.stubSmsSender = stubSmsSender;
    }

    // 응답 data = { "phone": "01012345678", "code": "123456", "message": "..." }
    @GetMapping("/api/dev/sms/last")
    public ApiResponse<java.util.Map<String, String>> last(@RequestParam("phone") String phone) {
        String target = PhoneNumber.normalize(phone);
        String message = stubSmsSender.lastMessage(target)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "이 번호로 보낸 문자가 없습니다."));
        Matcher m = CODE.matcher(message);
        return ApiResponse.ok(java.util.Map.of("phone", target, "code", m.find() ? m.group(1) : "", "message", message));
    }
}
