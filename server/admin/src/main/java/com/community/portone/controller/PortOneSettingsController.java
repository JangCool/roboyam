package com.community.portone.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.portone.dto.request.PortOneIdentitySaveRequest;
import com.community.portone.dto.response.PortOneSettingsView;
import com.community.portone.service.PortOneSettingsService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포트원 설정 API(설정 › 시스템 › 포트원 — 계약 「0-4」, 세션 정리 결정 9) — 조회 {@code site.read}, 저장·지우기 {@code site.secret}. 값(상점 ID·API 비밀키·채널 키)은
 * 어떤 응답에도 없다 — 설정 여부·바꾼 시각·바꾼 사람만. 지금 본인인증 구현({@code identityProvider})은 배포 설정 {@code roboyam.identity.provider} 다(화면에서 바꾸지 않는다).
 * 소셜 로그인 설정처럼 응답은 {@code Cache-Control: no-store}. 입력 검사는 서비스가 한다.
 */
@RestController
@RequestMapping("/api/admin/portone-settings")
public class PortOneSettingsController {

    private final PortOneSettingsService service;

    public PortOneSettingsController(PortOneSettingsService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<PortOneSettingsView>> view() {
        return noStore(service.view());
    }

    /** 보낸 칸만 저장 — 400 {@code VALIDATION} · 503 {@code CRYPTO_KEY_MISSING} */
    @RequirePermission(Permission.SITE_SECRET)
    @PutMapping("/identity")
    public ResponseEntity<ApiResponse<PortOneSettingsView>> saveIdentity(@RequestBody PortOneIdentitySaveRequest body, AdminActor actor) {
        return noStore(service.saveIdentity(body.storeId(), body.apiSecret(), body.identityChannelKey(), actor.operatorId(), actor.ip()));
    }

    /** 칸 하나 지우기 — 204 · 404 {@code PORTONE_FIELD_NOT_FOUND}(모르는 칸·설정돼 있지 않은 칸) */
    @RequirePermission(Permission.SITE_SECRET)
    @DeleteMapping("/identity/{key}")
    public ResponseEntity<Void> deleteField(@PathVariable String key, AdminActor actor) {
        service.deleteField(key, actor.operatorId(), actor.ip());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
