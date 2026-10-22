package com.community.mail.controller;

import com.community.mail.dto.request.MailSettingsSaveRequest;
import com.community.mail.dto.request.MailTestRequest;
import com.community.mail.dto.response.MailSettingHistoryPage;
import com.community.mail.dto.response.MailSettingsView;
import com.community.mail.dto.response.MailTestResultView;
import com.community.mail.service.MailSettingsAdminService;
import com.community.mail.service.MailTestService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 메일 서버 설정 API (docs/features/mail-settings-contract.md). 조회·이력 {@code site.read} 또는 {@code site.secret}, 저장·시험 발송 {@code site.secret}.
 * 비밀번호는 요청으로만 들어오고 응답에는 「입력됨」({@code passwordSet})만 나간다.
 */
@RestController
@RequestMapping("/api/admin/mail-settings")
public class MailSettingsController {

    private final MailSettingsAdminService service;
    private final MailTestService tests;

    public MailSettingsController(MailSettingsAdminService service, MailTestService tests) {
        this.service = service;
        this.tests = tests;
    }

    @RequirePermission(anyOf = {Permission.SITE_READ, Permission.SITE_SECRET})
    @GetMapping
    public ApiResponse<MailSettingsView> load() {
        return ApiResponse.success(service.load());
    }

    @RequirePermission(Permission.SITE_SECRET)
    @PutMapping
    public ApiResponse<MailSettingsView> save(@RequestBody MailSettingsSaveRequest body, AdminActor actor) {
        return ApiResponse.success(service.save(actor, body));
    }

    /** 저장된 설정으로 시험 메일을 한 통 보낸다 — 실패한 시험도 200 {@code succeeded:false} */
    @RequirePermission(Permission.SITE_SECRET)
    @PostMapping("/test")
    public ApiResponse<MailTestResultView> test(@RequestBody MailTestRequest body, AdminActor actor) {
        return ApiResponse.success(tests.test(actor, body));
    }

    @RequirePermission(anyOf = {Permission.SITE_READ, Permission.SITE_SECRET})
    @GetMapping("/history")
    public ApiResponse<MailSettingHistoryPage> history(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.success(service.history(page, size));
    }
}
