package com.community.social.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.social.dto.request.SocialProviderSaveRequest;
import com.community.social.dto.request.SocialSettingHistoryRequest;
import com.community.social.dto.response.SocialProviderSettingView;
import com.community.social.dto.response.SocialProviderSettingsView;
import com.community.social.dto.response.SocialSettingHistoryPage;
import com.community.social.service.SocialProviderAdminService;
import com.community.social.service.SocialSettingHistoryService;
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
 * 소셜 로그인 공급자 설정 API(카카오·네이버·구글) — 조회·이력은 {@code site.read}, 등록/수정·삭제는 {@code site.secret}. 시크릿은 어떤 응답에도 없다.
 * 응답은 {@code Cache-Control: no-store}. 입력 검사는 서비스가 한다(시크릿 필수 여부가 저장된 값에 달려 있다).
 */
@RestController
@RequestMapping("/api/admin/social-providers")
public class SocialProviderSettingsController {

    private final SocialProviderAdminService service;
    private final SocialSettingHistoryService historyService;

    public SocialProviderSettingsController(SocialProviderAdminService service, SocialSettingHistoryService historyService) {
        this.service = service;
        this.historyService = historyService;
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<SocialProviderSettingsView>> list() {
        return noStore(service.list());
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<SocialSettingHistoryPage>> history(SocialSettingHistoryRequest request) {
        return noStore(historyService.search(request));
    }

    @RequirePermission(Permission.SITE_SECRET)
    @PutMapping("/{provider}")
    public ResponseEntity<ApiResponse<SocialProviderSettingView>> save(@PathVariable String provider, @RequestBody SocialProviderSaveRequest body, AdminActor actor) {
        return noStore(service.save(actor, provider, body));
    }

    @RequirePermission(Permission.SITE_SECRET)
    @DeleteMapping("/{provider}")
    public ResponseEntity<Void> delete(@PathVariable String provider, AdminActor actor) {
        service.delete(actor, provider);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
