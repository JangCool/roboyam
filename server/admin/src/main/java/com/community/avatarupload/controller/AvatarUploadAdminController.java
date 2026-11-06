package com.community.avatarupload.controller;

import com.community.avatarupload.dto.response.AdminAvatarUploadPage;
import com.community.avatarupload.dto.response.AdminAvatarUploadView;
import com.community.avatarupload.service.AvatarUploadReviewService;
import com.community.nicknameimage.dto.request.NicknameImageReasonRequest;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 아바타 올리기 심사 API(관리자 앱, 화면 {@code /members/avatar-uploads} — commerce-motion-avatar-contract 「API 계약」 admin). 권한은 닉네임 이미지 심사와 같다:
 * 목록 {@code nickname-image.read}, 승인·반려·해제 {@code nickname-image.manage}. 회원 정보가 담긴 응답이라 {@code Cache-Control: no-store}, 쓰기는 CSRF.
 * 규칙은 shared {@link AvatarUploadReviewService}. 반려·해제 몸은 닉네임 이미지와 같은 {@code { reason }}(1~200자).
 */
@RestController
@RequestMapping("/api/admin/avatar-uploads")
public class AvatarUploadAdminController {

    private final AvatarUploadReviewService service;

    public AvatarUploadAdminController(AvatarUploadReviewService service) {
        this.service = service;
    }

    /** {@code status}(PENDING 기본 · APPROVED · REJECTED · REVOKED) · {@code keyword}(닉네임 부분 일치) · {@code page}(1부터) · {@code size}(기본 20, 1~100) */
    @RequirePermission(Permission.NICKNAME_IMAGE_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<AdminAvatarUploadPage>> list(@RequestParam(required = false) String status, @RequestParam(required = false) String keyword,
                                                                   @RequestParam(required = false) String page, @RequestParam(required = false) String size) {
        return noStore(service.list(status, keyword, page, size));
    }

    @RequirePermission(Permission.NICKNAME_IMAGE_MANAGE)
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<AdminAvatarUploadView>> approve(@PathVariable String id, AdminActor actor) {
        return noStore(service.approve(actor.operatorId(), actor.ip(), id));
    }

    @RequirePermission(Permission.NICKNAME_IMAGE_MANAGE)
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<AdminAvatarUploadView>> reject(@PathVariable String id, @RequestBody(required = false) NicknameImageReasonRequest body,
                                                                     AdminActor actor) {
        return noStore(service.reject(actor.operatorId(), actor.ip(), id, body == null ? null : body.reason()));
    }

    @RequirePermission(Permission.NICKNAME_IMAGE_MANAGE)
    @PostMapping("/{id}/revoke")
    public ResponseEntity<ApiResponse<AdminAvatarUploadView>> revoke(@PathVariable String id, @RequestBody(required = false) NicknameImageReasonRequest body,
                                                                     AdminActor actor) {
        return noStore(service.revoke(actor.operatorId(), actor.ip(), id, body == null ? null : body.reason()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
