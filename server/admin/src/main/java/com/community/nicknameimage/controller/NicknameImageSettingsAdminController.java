package com.community.nicknameimage.controller;

import com.community.nicknameimage.dto.request.NicknameImageSettingsRequest;
import com.community.nicknameimage.dto.response.NicknameImageSettingsView;
import com.community.nicknameimage.service.NicknameImageSettingsService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 닉네임 이미지 설정 API(관리자 앱, 화면 {@code /settings/nickname-image}) — 회원의 닉네임 그림 신청 조건. 조회 {@code nickname-image.read}, 저장 {@code nickname-image.manage}(CSRF).
 * 등급 고르개 목록이 같이 오므로 {@code grade.read} 가 없어도 고를 수 있다. 규칙은 {@link NicknameImageSettingsService}.
 */
@RestController
@RequestMapping("/api/admin/nickname-image-settings")
public class NicknameImageSettingsAdminController {

    private final NicknameImageSettingsService service;

    public NicknameImageSettingsAdminController(NicknameImageSettingsService service) {
        this.service = service;
    }

    @RequirePermission(Permission.NICKNAME_IMAGE_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<NicknameImageSettingsView>> load() {
        return noStore(service.load());
    }

    /** 통째로 저장 — 저장된 값을 돌려준다 */
    @RequirePermission(Permission.NICKNAME_IMAGE_MANAGE)
    @PutMapping
    public ResponseEntity<ApiResponse<NicknameImageSettingsView>> save(@RequestBody(required = false) NicknameImageSettingsRequest body, AdminActor actor) {
        return noStore(service.save(actor, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
