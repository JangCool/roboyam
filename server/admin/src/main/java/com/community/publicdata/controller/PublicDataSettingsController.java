package com.community.publicdata.controller;

import com.community.holiday.service.HolidaySyncService;
import com.community.permission.annotation.RequirePermission;
import com.community.publicdata.dto.request.PublicDataEndpointRequest;
import com.community.publicdata.dto.response.PublicDataEndpointsView;
import com.community.publicdata.service.PublicDataEndpointService;
import com.community.publicdata.exception.PublicDataEndpointNotFoundException;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final PublicDataEndpointService endpoints;
    private final HolidaySyncService holidaySync;

    public PublicDataSettingsController(PublicDataSettingsService service, PublicDataEndpointService endpoints, HolidaySyncService holidaySync) {
        this.service = service;
        this.endpoints = endpoints;
        this.holidaySync = holidaySync;
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

    /**
     * API 주소 저장(사용자 지시 2026-11-05) — {@code api} = {@code nts}(국세청 기본 주소) · {@code holiday}(특일 정보 주소·요청 글자 틀·응답 칸 이름). 빈 칸은 기본값.
     * 400 {@code fields.url} 등 · 404 {@code PUBLIC_DATA_ENDPOINT_NOT_FOUND}(모르는 API). 다음 요청부터 그 주소를 쓴다.
     */
    @RequirePermission(Permission.SITE_SECRET)
    @PutMapping("/endpoints/{api}")
    public ResponseEntity<ApiResponse<PublicDataEndpointsView>> saveEndpoint(@PathVariable String api, @RequestBody(required = false) PublicDataEndpointRequest body,
                                                                            AdminActor actor) {
        PublicDataEndpointRequest b = body == null ? new PublicDataEndpointRequest(null, null, null, null) : body;
        return noStore(switch (api(api)) {
            case PublicDataEndpointService.NTS -> endpoints.saveNts(b.url(), actor.operatorId(), actor.ip());
            default -> endpoints.saveHoliday(b.url(), b.queryTemplate(), b.dateField(), b.nameField(), actor.operatorId(), actor.ip());
        });
    }

    /** API 주소 기본값으로 되돌리기 — 모든 칸 */
    @RequirePermission(Permission.SITE_SECRET)
    @DeleteMapping("/endpoints/{api}")
    public ResponseEntity<ApiResponse<PublicDataEndpointsView>> resetEndpoint(@PathVariable String api, AdminActor actor) {
        return noStore(endpoints.reset(api(api), actor.operatorId(), actor.ip()));
    }

    /** 특일 정보 연결 시험 — 지금 키·주소로 올해 1월 한 달 조회(표는 바꾸지 않는다). {@code { result, testedAt, testedBy }} */
    @RequirePermission(Permission.SITE_READ)
    @PostMapping("/holiday-test")
    public ResponseEntity<ApiResponse<PublicDataSettingsView.Test>> holidayTest(AdminActor actor) {
        return noStore(holidaySync.probe(actor.operatorId()));
    }

    private static String api(String path) {
        String value = path == null ? "" : path.strip().toUpperCase(java.util.Locale.ROOT);
        if (!PublicDataEndpointService.NTS.equals(value) && !PublicDataEndpointService.HOLIDAY.equals(value)) {
            throw new PublicDataEndpointNotFoundException();
        }
        return value;
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
