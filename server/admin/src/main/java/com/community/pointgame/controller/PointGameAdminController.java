package com.community.pointgame.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.pointgame.dto.request.PointGameSettingsSaveRequest;
import com.community.pointgame.dto.response.PointGameSettingsView;
import com.community.pointgame.service.PointGameSettingsService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포인트 게임 설정(관리자 — docs/features/point-games-contract.md 「관리자」 · 화면은 포인트 상점 설정 안 「포인트 게임」 칸) — 로또 당첨 확인 링크(복권 사업자 이름 · 주소).
 * 권한은 포인트 상점과 같은 {@code pointshop.read}(조회)·{@code pointshop.manage}(저장). 저장은 감사(일반 운영). 규칙은 shared {@link PointGameSettingsService}.
 */
@RestController
@RequestMapping("/api/admin/point-games")
public class PointGameAdminController {

    private final PointGameSettingsService service;

    public PointGameAdminController(PointGameSettingsService service) {
        this.service = service;
    }

    @RequirePermission(Permission.POINTSHOP_READ)
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<PointGameSettingsView>> settings() {
        return noStore(service.settings());
    }

    /** 저장 — 400 {@code VALIDATION}({@code fields.lotteryProviderName}·{@code fields.lotteryCheckUrl}) */
    @RequirePermission(Permission.POINTSHOP_MANAGE)
    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<PointGameSettingsView>> save(@RequestBody(required = false) PointGameSettingsSaveRequest body, AdminActor actor) {
        return noStore(service.update(body == null ? null : body.lotteryProviderName(), body == null ? null : body.lotteryCheckUrl(), actor.operatorId(), actor.ip()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
