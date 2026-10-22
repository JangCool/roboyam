package com.community.membernotification.controller;

import com.community.auth.dto.AuthUser;
import com.community.membernotification.dto.response.MemberNotificationPageView;
import com.community.membernotification.dto.response.ReadAllResultView;
import com.community.membernotification.dto.response.UnreadCountView;
import com.community.membernotification.service.MemberNotificationService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 알림 API(공개 사이트) — 로그인 필요(없으면 401, {@code SecurityConfig} 의 {@code /api/**} 규칙), <b>내 알림만</b>(경로에 회원 ID 가 없다).
 * 개인 정보라 {@code Cache-Control: no-store}. 쓰기(읽음)는 CSRF 헤더가 필요하다. 모양은 docs/features/member-notification-contract.md.
 */
@RestController
@RequestMapping("/api/notifications")
public class MemberNotificationController {

    private final MemberNotificationService service;

    public MemberNotificationController(MemberNotificationService service) {
        this.service = service;
    }

    /** 내 알림 한 쪽 — {@code filter}(all·comment·system) · {@code size}(기본 20, 1~50) · {@code cursor} */
    @GetMapping
    public ResponseEntity<ApiResponse<MemberNotificationPageView>> list(@RequestParam(required = false) String filter, @RequestParam(required = false) Integer size,
                                                                        @RequestParam(required = false) String cursor, Authentication authentication) {
        return noStore(service.list(userId(authentication), filter, size, cursor));
    }

    /** 안 읽은 알림 수(상한 100) */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountView>> unreadCount(Authentication authentication) {
        return noStore(new UnreadCountView(service.unreadCount(userId(authentication))));
    }

    /** 알림 하나를 읽음으로 — 이미 읽었어도 204, 내 알림이 아니면 404 */
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@PathVariable String id, Authentication authentication) {
        service.markRead(userId(authentication), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    /** 모두 읽음 — {@code filter} 를 주면 그 거르기의 알림만 */
    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<ReadAllResultView>> readAll(@RequestParam(required = false) String filter, Authentication authentication) {
        return noStore(service.markAllRead(userId(authentication), filter));
    }

    /** 로그인한 회원 — 보안 설정이 막지만 혹시 빠져도 401 로 닫는다 */
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
