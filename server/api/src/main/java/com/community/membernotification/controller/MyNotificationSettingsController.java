package com.community.membernotification.controller;

import com.community.auth.dto.AuthUser;
import com.community.membernotification.dto.request.MyNotificationSettingsRequest;
import com.community.membernotification.dto.response.MyNotificationSettingsView;
import com.community.membernotification.service.MyNotificationSettingsService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 알림 설정(공개 사이트) — 로그인 필요(없으면 401), 본인 것만(경로에 회원 ID 가 없다), 쓰기는 CSRF, {@code Cache-Control: no-store}.
 * docs/features/member-mypage-contract.md 「알림 설정」.
 */
@RestController
@RequestMapping("/api/me/notification-settings")
public class MyNotificationSettingsController {

    private final MyNotificationSettingsService service;

    public MyNotificationSettingsController(MyNotificationSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MyNotificationSettingsView>> get(Authentication authentication) {
        return noStore(service.get(userId(authentication)));
    }

    /** 회원이 끌 수 있는 이벤트의 받기를 저장 — 응답은 저장 뒤 {@code GET} 과 같은 모양 */
    @PutMapping
    public ResponseEntity<ApiResponse<MyNotificationSettingsView>> update(@RequestBody(required = false) MyNotificationSettingsRequest body,
                                                                          Authentication authentication) {
        return noStore(service.update(userId(authentication), body));
    }

    private static long userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser principal) {
            return principal.id();
        }
        throw new CommonException(ErrorCode.UNAUTHENTICATED);
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
