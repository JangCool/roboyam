package com.community.point.controller;

import com.community.auth.dto.AuthUser;
import com.community.point.dto.response.MyPointLedgerPageView;
import com.community.point.dto.response.PointSummaryView;
import com.community.point.service.MyPointService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 포인트(공개 사이트) — 로그인 필요(없으면 401), 본인 것만(경로에 회원 ID 가 없다). 개인 정보라 {@code Cache-Control: no-store}. docs/features/point-contract.md */
@RestController
@RequestMapping("/api/me/points")
public class MyPointController {

    private final MyPointService service;

    public MyPointController(MyPointService service) {
        this.service = service;
    }

    /** 잔액·오늘 현황·규칙 */
    @GetMapping
    public ResponseEntity<ApiResponse<PointSummaryView>> summary(Authentication authentication) {
        if (!(authentication != null && authentication.getPrincipal() instanceof AuthUser principal)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.summary(principal.id())));
    }

    /** 내 포인트 내역 — {@code kind}(EARN·USE·REVOKE, 없으면 전체) · {@code size}(1~50, 기본 20) · {@code cursor}. 최신이 위 */
    @GetMapping("/ledger")
    public ResponseEntity<ApiResponse<MyPointLedgerPageView>> ledger(@RequestParam(required = false) String kind, @RequestParam(required = false) String size,
                                                                     @RequestParam(required = false) String cursor, Authentication authentication) {
        if (!(authentication != null && authentication.getPrincipal() instanceof AuthUser principal)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.ledger(principal.id(), kind, size, cursor)));
    }
}
