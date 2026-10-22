package com.community.board.controller;

import com.community.board.dto.BoardDefaultsValues;
import com.community.board.dto.BoardNotificationValues;
import com.community.board.dto.BoardPrivacyValues;
import com.community.board.dto.BoardReportValues;
import com.community.board.dto.BoardSecurityValues;
import com.community.board.dto.request.BoardPrivacyRunRequest;
import com.community.board.dto.response.BoardDefaultsResponse;
import com.community.board.dto.response.BoardPrivacyResponse;
import com.community.board.dto.response.BoardPrivacyRunView;
import com.community.board.dto.response.BoardPrivacyTargetView;
import com.community.board.dto.response.BoardSettingsResponse;
import com.community.board.service.BoardDefaultsService;
import com.community.board.service.BoardPrivacyRunService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시판 기본 설정 API — 일반 화면과 하위 화면 4개(신고 정책·보안·알림·개인정보 보유기간). 읽기 {@code board.read}, 하위 화면 저장·지금 실행 {@code board.settings},
 * 일반 화면 저장은 바뀐 칸의 권한({@code board.settings} · {@code board.staff.limit} — 서비스가 본다).
 */
@RestController
@RequestMapping("/api/admin/boards/defaults")
public class BoardDefaultsController {

    private final BoardDefaultsService service;
    private final BoardPrivacyRunService privacyRun;

    public BoardDefaultsController(BoardDefaultsService service, BoardPrivacyRunService privacyRun) {
        this.service = service;
        this.privacyRun = privacyRun;
    }

    @RequirePermission(Permission.BOARD_READ)
    @GetMapping
    public ApiResponse<BoardDefaultsResponse> defaults() {
        return ApiResponse.success(service.defaults());
    }

    @RequirePermission(anyOf = {Permission.BOARD_SETTINGS, Permission.BOARD_STAFF_LIMIT})
    @PutMapping
    public ApiResponse<BoardDefaultsResponse> saveDefaults(@RequestBody BoardDefaultsValues body, AdminActor actor) {
        return ApiResponse.success(service.saveDefaults(actor, body));
    }

    @RequirePermission(Permission.BOARD_READ)
    @GetMapping("/report")
    public ApiResponse<BoardSettingsResponse<BoardReportValues>> report() {
        return ApiResponse.success(service.report());
    }

    @RequirePermission(Permission.BOARD_SETTINGS)
    @PutMapping("/report")
    public ApiResponse<BoardSettingsResponse<BoardReportValues>> saveReport(@RequestBody BoardReportValues body, AdminActor actor) {
        return ApiResponse.success(service.saveReport(actor, body));
    }

    @RequirePermission(Permission.BOARD_READ)
    @GetMapping("/security")
    public ApiResponse<BoardSettingsResponse<BoardSecurityValues>> security() {
        return ApiResponse.success(service.security());
    }

    @RequirePermission(Permission.BOARD_SETTINGS)
    @PutMapping("/security")
    public ApiResponse<BoardSettingsResponse<BoardSecurityValues>> saveSecurity(@RequestBody BoardSecurityValues body, AdminActor actor) {
        return ApiResponse.success(service.saveSecurity(actor, body));
    }

    @RequirePermission(Permission.BOARD_READ)
    @GetMapping("/notifications")
    public ApiResponse<BoardSettingsResponse<BoardNotificationValues>> notifications() {
        return ApiResponse.success(service.notifications());
    }

    @RequirePermission(Permission.BOARD_SETTINGS)
    @PutMapping("/notifications")
    public ApiResponse<BoardSettingsResponse<BoardNotificationValues>> saveNotifications(@RequestBody BoardNotificationValues body, AdminActor actor) {
        return ApiResponse.success(service.saveNotifications(actor, body));
    }

    @RequirePermission(Permission.BOARD_READ)
    @GetMapping("/privacy")
    public ApiResponse<BoardPrivacyResponse> privacy() {
        return ApiResponse.success(service.privacy());
    }

    @RequirePermission(Permission.BOARD_SETTINGS)
    @PutMapping("/privacy")
    public ApiResponse<BoardPrivacyResponse> savePrivacy(@RequestBody BoardPrivacyValues body, AdminActor actor) {
        return ApiResponse.success(service.savePrivacy(actor, body));
    }

    /** 축 하나의 파기 대상(저장된 설정 기준, 앞의 20줄) */
    @RequirePermission(Permission.BOARD_READ)
    @GetMapping("/privacy/targets")
    public ApiResponse<List<BoardPrivacyTargetView>> privacyTargets(@RequestParam(required = false) String axis) {
        return ApiResponse.success(service.privacyTargets(axis));
    }

    /** 축 하나를 저장된 값으로 지금 한 번 실행 — 꺼져 있으면 409 {@code BOARD_PRIVACY_DISABLED} */
    @RequirePermission(Permission.BOARD_SETTINGS)
    @PostMapping("/privacy/run")
    public ApiResponse<BoardPrivacyRunView> runPrivacy(@RequestBody BoardPrivacyRunRequest body, AdminActor actor) {
        return ApiResponse.success(privacyRun.run(actor, body));
    }
}
