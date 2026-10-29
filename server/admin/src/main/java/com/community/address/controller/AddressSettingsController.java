package com.community.address.controller;

import com.community.address.dto.request.AddressSettingsSaveRequest;
import com.community.address.dto.response.AddressDbLoadPage;
import com.community.address.dto.response.AddressImportProgressView;
import com.community.address.dto.response.AddressImportStartedResponse;
import com.community.address.dto.response.AddressSettingsView;
import com.community.address.service.AddressImportService;
import com.community.address.service.AddressSettingsService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 관리자 설정 › 주소 검색(docs/features/address-search-contract.md) — 조회 {@code site.read}, 저장·주소 DB 올리기 {@code site.manage}.
 * 입력 검사는 서비스가 한다. 파일은 한 개 300MB 까지 받는다(이 주소만 — {@code AddressUploadLimitFilter}).
 */
@RestController
@RequestMapping("/api/admin/address")
public class AddressSettingsController {

    private final AddressSettingsService settings;
    private final AddressImportService imports;

    public AddressSettingsController(AddressSettingsService settings, AddressImportService imports) {
        this.settings = settings;
        this.imports = imports;
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/settings")
    public ApiResponse<AddressSettingsView> view() {
        return ApiResponse.success(settings.view());
    }

    /** 400 {@code VALIDATION}({@code fields.provider}·{@code scriptUrl}·{@code staleWarnDays}) */
    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/settings")
    public ApiResponse<AddressSettingsView> save(@RequestBody(required = false) AddressSettingsSaveRequest body, AdminActor actor) {
        return ApiResponse.success(settings.save(actor, body));
    }

    /** 멀티파트 {@code files}(1~2개) + {@code charset} — 202, 400 {@code VALIDATION}({@code fields.files}·{@code charset}) · 409 {@code ADDRESS_IMPORT_RUNNING} · 413 */
    @RequirePermission(Permission.SITE_MANAGE)
    @PostMapping("/imports")
    public ResponseEntity<ApiResponse<AddressImportStartedResponse>> startImport(@RequestParam(value = "files", required = false) List<MultipartFile> files,
                                                                                 @RequestParam(value = "charset", required = false) String charset,
                                                                                 AdminActor actor) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(imports.start(actor, files, charset)));
    }

    /** 돌고 있는 적재 — 없으면 {@code data: null} */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/imports/current")
    public ApiResponse<AddressImportProgressView> currentImport() {
        return ApiResponse.success(imports.current());
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/imports/history")
    public ApiResponse<AddressDbLoadPage> history(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(imports.history(page, size));
    }

    /** 404 {@code ADDRESS_IMPORT_JOB_NOT_FOUND} */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/imports/{jobId}")
    public ApiResponse<AddressImportProgressView> importProgress(@PathVariable String jobId) {
        return ApiResponse.success(imports.progress(jobId));
    }
}
