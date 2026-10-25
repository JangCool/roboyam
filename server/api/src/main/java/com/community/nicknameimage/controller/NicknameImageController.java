package com.community.nicknameimage.controller;

import com.community.auth.dto.AuthUser;
import com.community.nicknameimage.dto.response.MyNicknameImageView;
import com.community.nicknameimage.service.NicknameImageService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 내 닉네임 그림 API(공개 사이트) — user-web {@code useNicknameImageApi.ts} 머리말 그대로. 로그인 필요(401), 본인 것만, {@code Cache-Control: no-store}, 쓰기는 CSRF.
 * 규칙·오류 차례는 {@link NicknameImageService}.
 */
@RestController
@RequestMapping("/api/me/nickname-image")
public class NicknameImageController {

    private final NicknameImageService service;

    public NicknameImageController(NicknameImageService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MyNicknameImageView>> status(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.status(userId(authentication))));
    }

    /** 신청(multipart {@code file}) — 201 + 신청 직후의 상태 */
    @PostMapping
    public ResponseEntity<ApiResponse<MyNicknameImageView>> apply(@RequestParam(value = "file", required = false) MultipartFile file, Authentication authentication) {
        MyNicknameImageView view = service.apply(userId(authentication), file);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(view));
    }

    @DeleteMapping("/request")
    public ResponseEntity<Void> cancelRequest(Authentication authentication) {
        service.cancelRequest(userId(authentication));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping
    public ResponseEntity<Void> remove(Authentication authentication) {
        service.remove(userId(authentication));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static long userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser principal) {
            return principal.id();
        }
        throw new CommonException(ErrorCode.UNAUTHENTICATED);
    }
}
