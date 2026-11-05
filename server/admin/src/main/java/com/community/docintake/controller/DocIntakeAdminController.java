package com.community.docintake.controller;

import com.community.bizapplication.service.BizApplicationAdminService;
import com.community.docintake.dto.DocIntakeAdminRequest;
import com.community.docintake.dto.DocIntakeAdminView;
import com.community.docintake.service.DocIntakeAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사업자 서류 「접수 기록」(admin-web {@code useDocIntakeApi}, docs/features/biz-doc-intake-contract.md) — 계약서 사본은 신청 건 {@code /api/admin/biz-applications/{id}} 아래,
 * 사업자등록증 사본은 사업자 {@code /api/admin/businesses/{id}} 아래. 응답은 그 대상의 묶음 전체({@code DocIntakeBundle}), {@code Cache-Control: no-store}.
 * 권한: 계약서 조회 {@code ad.read}|{@code hotdeal.read} · 쓰기 {@code ad.manage}|{@code hotdeal.manage}(서비스가 건의 도메인으로 다시 본다) / 등록증 조회 {@code business.read} · 쓰기
 * {@code business.manage}. 오류: 400 {@code fields.*} · 404 {@code DOC_INTAKE_NOT_FOUND} · 409 {@code DOC_INTAKE_STATE_CHANGED}.
 */
@RestController
public class DocIntakeAdminController {

    private static final String CONTRACT = "/api/admin/biz-applications/{id}/doc-intakes";
    private static final String CERTIFICATE = "/api/admin/businesses/{id}/doc-intakes";

    private final DocIntakeAdminService service;

    public DocIntakeAdminController(DocIntakeAdminService service) {
        this.service = service;
    }

    // ── 계약서 사본(신청 건) ─────────────────────────────────────────────────

    @RequirePermission(anyOf = {Permission.AD_READ, Permission.HOTDEAL_READ})
    @GetMapping(CONTRACT)
    public ResponseEntity<ApiResponse<DocIntakeAdminView.Bundle>> contractList(@PathVariable String id, AdminActor actor) {
        return ok(service.contractBundle(actor, BizApplicationAdminService.parseId(id)));
    }

    /** 새 종이 접수 — 입금 대기 · 운영자 등록 건 심사 대기에서만(아니면 409) */
    @RequirePermission(anyOf = {Permission.AD_MANAGE, Permission.HOTDEAL_MANAGE})
    @PostMapping(CONTRACT)
    public ResponseEntity<ApiResponse<DocIntakeAdminView.Bundle>> contractCreate(@PathVariable String id,
                                                                                @RequestBody(required = false) DocIntakeAdminRequest.Create body, AdminActor actor) {
        return ok(service.createContract(actor, BizApplicationAdminService.parseId(id), body));
    }

    @RequirePermission(anyOf = {Permission.AD_MANAGE, Permission.HOTDEAL_MANAGE})
    @PostMapping(CONTRACT + "/{intakeId}/handling")
    public ResponseEntity<ApiResponse<DocIntakeAdminView.Bundle>> contractHandling(@PathVariable String id, @PathVariable String intakeId,
                                                                                  @RequestBody(required = false) DocIntakeAdminRequest.Handling body, AdminActor actor) {
        return ok(service.changeContractHandling(actor, BizApplicationAdminService.parseId(id), intakeId, body));
    }

    /** 폐기 — 스캔본(그 건의 대체되지 않은 계약서 파일) 필수 */
    @RequirePermission(anyOf = {Permission.AD_MANAGE, Permission.HOTDEAL_MANAGE})
    @PostMapping(CONTRACT + "/{intakeId}/dispose")
    public ResponseEntity<ApiResponse<DocIntakeAdminView.Bundle>> contractDispose(@PathVariable String id, @PathVariable String intakeId,
                                                                                 @RequestBody(required = false) DocIntakeAdminRequest.Dispose body, AdminActor actor) {
        return ok(service.disposeContract(actor, BizApplicationAdminService.parseId(id), intakeId, body));
    }

    /** 잘못 기록 — 입금 대기 · 운영자 등록 건 심사 대기에서만, 폐기하지 않은 줄만 */
    @RequirePermission(anyOf = {Permission.AD_MANAGE, Permission.HOTDEAL_MANAGE})
    @PostMapping(CONTRACT + "/{intakeId}/void")
    public ResponseEntity<ApiResponse<DocIntakeAdminView.Bundle>> contractVoid(@PathVariable String id, @PathVariable String intakeId,
                                                                              @RequestBody(required = false) DocIntakeAdminRequest.MarkVoid body, AdminActor actor) {
        return ok(service.voidContract(actor, BizApplicationAdminService.parseId(id), intakeId, body));
    }

    // ── 사업자등록증 사본(사업자 신원) ────────────────────────────────────────

    @RequirePermission(Permission.BUSINESS_READ)
    @GetMapping(CERTIFICATE)
    public ResponseEntity<ApiResponse<DocIntakeAdminView.Bundle>> certificateList(@PathVariable String id) {
        return ok(service.certificateBundle(id));
    }

    /** 새 종이 접수 — 탈퇴 상태가 아닐 때만(아니면 409) */
    @RequirePermission(Permission.BUSINESS_MANAGE)
    @PostMapping(CERTIFICATE)
    public ResponseEntity<ApiResponse<DocIntakeAdminView.Bundle>> certificateCreate(@PathVariable String id,
                                                                                   @RequestBody(required = false) DocIntakeAdminRequest.Create body, AdminActor actor) {
        return ok(service.createCertificate(actor, id, body));
    }

    @RequirePermission(Permission.BUSINESS_MANAGE)
    @PostMapping(CERTIFICATE + "/{intakeId}/handling")
    public ResponseEntity<ApiResponse<DocIntakeAdminView.Bundle>> certificateHandling(@PathVariable String id, @PathVariable String intakeId,
                                                                                     @RequestBody(required = false) DocIntakeAdminRequest.Handling body, AdminActor actor) {
        return ok(service.changeCertificateHandling(actor, id, intakeId, body));
    }

    /** 폐기 — 스캔본은 선택(그 사업자의 등록증 파일) */
    @RequirePermission(Permission.BUSINESS_MANAGE)
    @PostMapping(CERTIFICATE + "/{intakeId}/dispose")
    public ResponseEntity<ApiResponse<DocIntakeAdminView.Bundle>> certificateDispose(@PathVariable String id, @PathVariable String intakeId,
                                                                                    @RequestBody(required = false) DocIntakeAdminRequest.Dispose body, AdminActor actor) {
        return ok(service.disposeCertificate(actor, id, intakeId, body));
    }

    @RequirePermission(Permission.BUSINESS_MANAGE)
    @PostMapping(CERTIFICATE + "/{intakeId}/void")
    public ResponseEntity<ApiResponse<DocIntakeAdminView.Bundle>> certificateVoid(@PathVariable String id, @PathVariable String intakeId,
                                                                                 @RequestBody(required = false) DocIntakeAdminRequest.MarkVoid body, AdminActor actor) {
        return ok(service.voidCertificate(actor, id, intakeId, body));
    }

    /**
     * 운영자의 등록증 사본 올리기 — multipart {@code file}(PDF·JPG·PNG 10MB) + {@code channel}({@code UPLOAD} 기본 · {@code FAX} 전자 팩스). 그 사업자의 지금 등록증이 된다.
     * 응답은 등록증 접수 묶음(사업자 상세는 화면이 다시 읽는다). 탈퇴·파기된 신원 409 {@code BUSINESS_STATE_CHANGED}.
     */
    @RequirePermission(Permission.BUSINESS_MANAGE)
    @PostMapping("/api/admin/businesses/{id}/certificate")
    public ResponseEntity<ApiResponse<DocIntakeAdminView.Bundle>> certificateUpload(@PathVariable String id,
                                                                                   @RequestParam(value = "file", required = false) MultipartFile file,
                                                                                   @RequestParam(value = "channel", required = false) String channel, AdminActor actor) {
        return ok(service.uploadCertificate(actor, id, file, channel));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
