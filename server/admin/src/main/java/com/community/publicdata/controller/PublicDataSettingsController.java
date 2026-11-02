package com.community.publicdata.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.publicdata.dto.request.PublicDataSaveRequest;
import com.community.publicdata.dto.response.PublicDataSettingsView;
import com.community.publicdata.service.PublicDataSettingsService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공공데이터포털 설정 API(설정 › 시스템 › 공공데이터포털 {@code /settings/public-data}, business-contract 「국세청 진위확인 · 공공데이터 설정」) —
 * 조회·연결 시험 {@code site.read}, 저장·지우기 {@code site.secret}(포트원 설정과 같은 규칙). 키 값은 어떤 응답에도 없다. 응답은 {@code Cache-Control: no-store}.
 * 입력 검사는 서비스가 한다.
 */
@RestController
@RequestMapping("/api/admin/public-data-settings")
public class PublicDataSettingsController {

    private final PublicDataSettingsService service;

    public PublicDataSettingsController(PublicDataSettingsService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<PublicDataSettingsView>> view() {
        return noStore(service.view());
    }

    /** 서비스 키 저장 — 400 {@code fields.serviceKey}(빈 값·공백 포함·500자 초과) · 503 {@code CRYPTO_KEY_MISSING} */
    @RequirePermission(Permission.SITE_SECRET)
    @PutMapping
    public ResponseEntity<ApiResponse<PublicDataSettingsView>> save(@RequestBody(required = false) PublicDataSaveRequest body, AdminActor actor) {
        return noStore(service.save(body == null ? null : body.serviceKey(), actor.operatorId(), actor.ip()));
    }

    /** 화면에서 저장한 키 지우기 — 204 · 404 {@code PUBLIC_DATA_KEY_NOT_FOUND}. 환경 변수가 있으면 그 값으로 돌아간다 */
    @RequirePermission(Permission.SITE_SECRET)
    @DeleteMapping("/service-key")
    public ResponseEntity<Void> deleteServiceKey(AdminActor actor) {
        service.deleteServiceKey(actor.operatorId(), actor.ip());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    /** 연결 시험 — 지금 쓸 키로 실제 국세청 상태조회 한 번(구현이 가짜여도). {@code { result, testedAt, testedBy }} */
    @RequirePermission(Permission.SITE_READ)
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<PublicDataSettingsView.Test>> test(AdminActor actor) {
        return noStore(service.test(actor.operatorId()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
