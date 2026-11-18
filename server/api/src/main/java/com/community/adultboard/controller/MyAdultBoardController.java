package com.community.adultboard.controller;

import com.community.adultboard.dto.request.AdultBoardEnabledRequest;
import com.community.adultboard.dto.request.AdultBoardPinRequest;
import com.community.adultboard.dto.request.AdultBoardRelockModeRequest;
import com.community.adultboard.dto.request.AdultBoardUnlockRequest;
import com.community.adultboard.dto.response.AdultBoardStatusResponse;
import com.community.adultboard.service.AdultBoardAccessService;
import com.community.auth.dto.AuthUser;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 19금 게시판 접근 설정·핀번호(docs/features/adult-board-pin-contract.md) — 로그인한 본인 것만(경로·본문에 회원 id 를 받지 않는다). 로그인 필요·CSRF 는
 * {@code SecurityConfig} 가 건다({@code /api/**} 기본 규칙). 여섯 요청 모두 같은 응답({@link AdultBoardStatusResponse}, 200, {@code Cache-Control: no-store}).
 * 입력 검사는 서비스가 한다(칸 경로 {@code fields.password}·{@code fields.pin}·{@code fields.relockMode}·{@code fields.enabled}).
 */
@RestController
@RequestMapping("/api/me/adult-board")
public class MyAdultBoardController {

    private final AdultBoardAccessService service;

    public MyAdultBoardController(AdultBoardAccessService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AdultBoardStatusResponse>> status(Authentication authentication) {
        return noStore(service.status(userId(authentication)));
    }

    /** 켜기 {@code { enabled: true, password, pin }} · 끄기 {@code { enabled: false }} */
    @PutMapping
    public ResponseEntity<ApiResponse<AdultBoardStatusResponse>> setEnabled(@RequestBody(required = false) AdultBoardEnabledRequest body,
                                                                            Authentication authentication) {
        return noStore(service.setEnabled(userId(authentication), body));
    }

    /** 핀번호 바꾸기·다시 정하기 {@code { password, pin }} */
    @PutMapping("/pin")
    public ResponseEntity<ApiResponse<AdultBoardStatusResponse>> changePin(@RequestBody(required = false) AdultBoardPinRequest body, Authentication authentication) {
        return noStore(service.changePin(userId(authentication), body));
    }

    @PutMapping("/relock-mode")
    public ResponseEntity<ApiResponse<AdultBoardStatusResponse>> setRelockMode(@RequestBody(required = false) AdultBoardRelockModeRequest body,
                                                                               Authentication authentication) {
        return noStore(service.setRelockMode(userId(authentication), body));
    }

    /** 핀번호 확인 {@code { pin }} — 맞으면 이 기기 1시간 */
    @PostMapping("/unlock")
    public ResponseEntity<ApiResponse<AdultBoardStatusResponse>> unlock(@RequestBody(required = false) AdultBoardUnlockRequest body, Authentication authentication) {
        return noStore(service.unlock(userId(authentication), body));
    }

    /** 이 기기 지금 잠그기 */
    @PostMapping("/lock")
    public ResponseEntity<ApiResponse<AdultBoardStatusResponse>> lock(Authentication authentication) {
        return noStore(service.lock(userId(authentication)));
    }

    private static ResponseEntity<ApiResponse<AdultBoardStatusResponse>> noStore(AdultBoardStatusResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }

    private static long userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser principal) {
            return principal.id();
        }
        throw new CommonException(ErrorCode.UNAUTHENTICATED);
    }
}
