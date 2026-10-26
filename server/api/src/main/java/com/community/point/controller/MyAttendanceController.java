package com.community.point.controller;

import com.community.auth.dto.AuthUser;
import com.community.point.dto.response.AttendanceMonthView;
import com.community.point.dto.response.AttendanceResultView;
import com.community.point.service.AttendanceService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 출석체크(공개 사이트, 커머스 1-3 — commerce-stage1-contract 「1-3 출석체크」) — 로그인 필요(없으면 401), 본인 것만. 개인 정보라 {@code Cache-Control: no-store}.
 * 규칙은 shared {@link AttendanceService}.
 */
@RestController
@RequestMapping("/api/me/attendance")
public class MyAttendanceController {

    private final AttendanceService service;

    public MyAttendanceController(AttendanceService service) {
        this.service = service;
    }

    /** 한 달 달력 — {@code month}(YYYY-MM, 없으면 이번 달). 형식이 틀리면 400 {@code fields.month} */
    @GetMapping
    public ResponseEntity<ApiResponse<AttendanceMonthView>> month(@RequestParam(required = false) String month, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.month(userId(authentication), month)));
    }

    /** 오늘 출석 — 이미 했으면 409 {@code ATTENDANCE_ALREADY_DONE}, 정지 403 {@code ACCOUNT_RESTRICTED}, 이메일 인증 전 403 {@code EMAIL_NOT_VERIFIED} */
    @PostMapping
    public ResponseEntity<ApiResponse<AttendanceResultView>> attend(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.attend(userId(authentication))));
    }

    private static long userId(Authentication authentication) {
        if (!(authentication != null && authentication.getPrincipal() instanceof AuthUser principal)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return principal.id();
    }
}
