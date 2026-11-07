package com.community.avatarupload.controller;

import com.community.auth.dto.AuthUser;
import com.community.avatarupload.dto.response.MyAvatarUploadView;
import com.community.avatarupload.service.MyAvatarUploadService;
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
 * 내 아바타 올리기 API(공개 사이트 — commerce-motion-avatar-contract 「API 계약」 api). 로그인 필요(401), 본인 것만, {@code Cache-Control: no-store}, 쓰기는 CSRF.
 * 규칙·오류 차례는 {@link MyAvatarUploadService}·shared {@code AvatarUploadService}.
 *
 * <ul>
 *   <li>{@code GET} → {@code MyAvatarUploadView}</li>
 *   <li>{@code POST}(multipart {@code file}) → 201 같은 모양 · {@code 409 AVATAR_UPLOAD_TICKET_REQUIRED}(맨 위 {@code ticketCount}·{@code ticketPrice}) · {@code 400 fields.file} ·
 *       {@code 403 EMAIL_NOT_VERIFIED|ACCOUNT_RESTRICTED} · {@code 429 RATE_LIMITED}</li>
 *   <li>{@code DELETE /request} → 204(대기 신청 취소 — 변경권 반환) · {@code 404 AVATAR_UPLOAD_REQUEST_NOT_FOUND}</li>
 *   <li>{@code DELETE} → 204(내가 올린 아바타 지우기 — 반환 없음, 쓰는 중이면 마지막 정지 아바타로) · {@code 404 AVATAR_UPLOAD_NOT_FOUND}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/me/avatar-upload")
public class AvatarUploadController {

    private final MyAvatarUploadService service;

    public AvatarUploadController(MyAvatarUploadService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MyAvatarUploadView>> status(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.status(userId(authentication))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MyAvatarUploadView>> apply(@RequestParam(value = "file", required = false) MultipartFile file, Authentication authentication) {
        MyAvatarUploadView view = service.apply(userId(authentication), file);
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
