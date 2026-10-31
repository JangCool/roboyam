package com.community.business.controller;

import com.community.auth.dto.AuthUser;
import com.community.business.dto.BusinessApplyRequest;
import com.community.business.dto.MyBusinessView;
import com.community.business.service.MyBusinessService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사업자 센터 — 내 신원(user-web {@code useBusinessApi} 의 신원 부분, docs/features/business-contract.md). 로그인 필요(401), 응답은 {@code Cache-Control: no-store}.
 * 신청 건(광고·핫딜)은 신청 건 백엔드가 같은 {@code /api/business/**} 아래에 더한다.
 */
@RestController
@RequestMapping("/api/business")
public class MyBusinessController {

    private final MyBusinessService service;

    public MyBusinessController(MyBusinessService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyBusinessView.Profile>> me(Authentication authentication) {
        return noStore(service.profile(user(authentication)));
    }

    /** 신청(처음·반려 뒤 재신청) — 심사 중이 된다. 이미 심사 중·승인·정지면 409 {@code BUSINESS_ALREADY_APPLIED} */
    @PostMapping("/me")
    public ResponseEntity<ApiResponse<MyBusinessView.Profile>> apply(@RequestBody(required = false) BusinessApplyRequest body, Authentication authentication) {
        return noStore(service.apply(user(authentication), body));
    }

    /** 승인 사업자의 정보 수정 — 상호·번호가 바뀌면 재심사(F7) */
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<MyBusinessView.Profile>> update(@RequestBody(required = false) BusinessApplyRequest body, Authentication authentication) {
        return noStore(service.update(user(authentication), body));
    }

    /** 사업자등록증 사본(multipart {@code file}, PDF·JPG·PNG 10MB) — 신청·수정 전에 먼저 올린다 */
    @PostMapping("/files")
    public ResponseEntity<ApiResponse<MyBusinessView.File>> upload(@RequestParam(value = "file", required = false) MultipartFile file, Authentication authentication) {
        return noStore(service.upload(user(authentication), file));
    }

    private static long user(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser principal) {
            return principal.id();
        }
        throw new CommonException(ErrorCode.UNAUTHENTICATED);
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
