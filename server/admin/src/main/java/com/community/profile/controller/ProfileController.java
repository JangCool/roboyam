package com.community.profile.controller;

import com.community.permission.annotation.AdminAuthenticated;
import com.community.permission.dto.AdminActor;
import com.community.profile.dto.request.MyDisplayRequest;
import com.community.profile.dto.request.MyNotificationRequest;
import com.community.profile.dto.request.PasswordChangeRequest;
import com.community.profile.dto.request.ProfileUpdateRequest;
import com.community.profile.dto.response.LoginHistoryResponse;
import com.community.profile.dto.response.MyDisplayResponse;
import com.community.profile.dto.response.MyNotificationResponse;
import com.community.profile.dto.response.ProfileResponse;
import com.community.profile.service.LoginHistoryService;
import com.community.profile.service.MyDisplayService;
import com.community.profile.service.MyNotificationService;
import com.community.profile.service.PasswordChangeService;
import com.community.profile.service.ProfileService;
import com.community.zconfiguration.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 정보 API — 로그인한 운영자 <b>본인</b>의 것만 다룬다(경로에 ID 가 없고 대상은 늘 요청한 사람이다). 역할(권한)이 없어도 부를 수 있어 {@link AdminAuthenticated} 다.
 * 개인 정보라 응답은 {@code Cache-Control: no-store}. 칸별 검사 문구가 필요한 요청은 서비스가 검사한다.
 * (docs/features/roles-contract.md 「4단계 구현 결과」, admin-web {@code useProfileApi.types.ts})
 */
@RestController
@RequestMapping("/api/admin/profile")
@AdminAuthenticated
public class ProfileController {

    private final ProfileService profiles;
    private final LoginHistoryService loginHistory;
    private final MyDisplayService display;
    private final MyNotificationService notification;
    private final PasswordChangeService passwords;

    public ProfileController(ProfileService profiles, LoginHistoryService loginHistory, MyDisplayService display, MyNotificationService notification,
                             PasswordChangeService passwords) {
        this.profiles = profiles;
        this.loginHistory = loginHistory;
        this.display = display;
        this.notification = notification;
        this.passwords = passwords;
    }

    /** 내 정보 */
    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> get(AdminActor actor) {
        return noStore(profiles.get(actor));
    }

    /** 닉네임을 바꾼다 — 갱신된 내 정보를 돌려준다 */
    @PatchMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> update(@RequestBody ProfileUpdateRequest body, AdminActor actor) {
        return noStore(profiles.updateNickname(actor, body));
    }

    /** 내 로그인 이력 — 최근 20건 */
    @GetMapping("/login-history")
    public ResponseEntity<ApiResponse<LoginHistoryResponse>> loginHistory(AdminActor actor) {
        return noStore(loginHistory.recent(actor));
    }

    /** 내 표시 형식 — 내 값·사이트 표시 기본값·지금 적용되는 출처 */
    @GetMapping("/display")
    public ResponseEntity<ApiResponse<MyDisplayResponse>> display(AdminActor actor) {
        return noStore(display.get(actor));
    }

    /** 내 표시 형식을 저장한다(쓸지 + 값) — 저장된 모양을 돌려준다 */
    @PutMapping("/display")
    public ResponseEntity<ApiResponse<MyDisplayResponse>> saveDisplay(@RequestBody MyDisplayRequest body, AdminActor actor) {
        return noStore(display.save(actor, body));
    }

    /** 알림 수신 설정 */
    @GetMapping("/notification")
    public ResponseEntity<ApiResponse<MyNotificationResponse>> notification(AdminActor actor) {
        return noStore(notification.get(actor));
    }

    /** 알림 수신 설정을 바꾼다 */
    @PutMapping("/notification")
    public ResponseEntity<ApiResponse<MyNotificationResponse>> saveNotification(@RequestBody MyNotificationRequest body, AdminActor actor) {
        return noStore(notification.save(actor, body));
    }

    /** 비밀번호를 바꾼다 — 현재 비밀번호를 확인하고, 지금 세션은 유지한 채 다른 세션을 끝낸다. 현재 비밀번호가 틀리면 401 이 아니라 400 {@code INVALID_CURRENT_PASSWORD} */
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@RequestBody PasswordChangeRequest body, AdminActor actor, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        passwords.change(actor, body, session == null ? null : session.getId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
