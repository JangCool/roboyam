package com.community.osslicense.controller;

import com.community.osslicense.dto.request.OssNoticeEntrySaveRequest;
import com.community.osslicense.dto.request.OssNoticeHiddenRequest;
import com.community.osslicense.dto.request.OssNoticeIntroRequest;
import com.community.osslicense.dto.response.OssNoticeAdminView;
import com.community.osslicense.dto.response.OssNoticeEntryView;
import com.community.osslicense.service.OssNoticeAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 오픈소스 라이선스 고지 관리 API(관리자 앱, docs/features/oss-licenses-contract.md) — 보기는 {@code site.read}, 안내 문구·직접 추가 항목 바꾸기는 {@code site.manage}(CSRF).
 * 규칙은 {@link OssNoticeAdminService}. 자동 수집 목록은 API 가 아니라 정적 파일({@code /oss-licenses.json})이다. 모든 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/oss-licenses")
public class OssNoticeAdminController {

    private final OssNoticeAdminService service;

    public OssNoticeAdminController(OssNoticeAdminService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<OssNoticeAdminView>> get() {
        return noStore(service.get());
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/intro")
    public ResponseEntity<ApiResponse<OssNoticeAdminView>> intro(@RequestBody(required = false) OssNoticeIntroRequest body, AdminActor actor) {
        return noStore(service.updateIntro(actor, body));
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PostMapping("/entries")
    public ResponseEntity<ApiResponse<OssNoticeEntryView>> create(@RequestBody(required = false) OssNoticeEntrySaveRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.create(actor, body)));
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/entries/{id}")
    public ResponseEntity<ApiResponse<OssNoticeEntryView>> update(@PathVariable String id, @RequestBody(required = false) OssNoticeEntrySaveRequest body,
                                                                  AdminActor actor) {
        return noStore(service.update(actor, id, body));
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/entries/{id}/hidden")
    public ResponseEntity<ApiResponse<OssNoticeEntryView>> hidden(@PathVariable String id, @RequestBody(required = false) OssNoticeHiddenRequest body,
                                                                  AdminActor actor) {
        return noStore(service.hidden(actor, id, body));
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @DeleteMapping("/entries/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, AdminActor actor) {
        service.delete(actor, id);
        return ResponseEntity.noContent().build();
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
