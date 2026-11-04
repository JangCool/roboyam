package com.community.withdrawal.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.withdrawal.dto.MemberWithdrawalAdminRequest;
import com.community.withdrawal.dto.MemberWithdrawalAdminView;
import com.community.withdrawal.service.MemberWithdrawalAdminService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 탈퇴 관리 API(admin-web {@code useMemberWithdrawalApi} — {@code /members/withdrawals} · 회원 상세 탈퇴 블록, member-withdrawal-contract 「관리자」).
 *
 * <ul>
 *   <li>{@code member.read}: 목록 {@code GET /api/admin/member-withdrawals} · 요약 {@code /summary} · 사유 분포 {@code /reasons} · 상세 {@code /{id}} · 회원별 최근 기록
 *       {@code GET /api/admin/members/{memberId}/withdrawal}</li>
 *   <li>{@code member.withdraw}(SENSITIVE): 강제 탈퇴 미리보기 {@code GET /api/admin/members/{memberId}/withdrawal/preview} · 강제 탈퇴 {@code POST /api/admin/members/{memberId}/withdrawal}
 *       (감사 {@code MEMBER_WITHDRAW_FORCE}) · 대리 취소 {@code POST /api/admin/member-withdrawals/{id}/cancel}(감사 {@code MEMBER_WITHDRAW_CANCEL})</li>
 * </ul>
 * 오류: 400 {@code VALIDATION}(fields) · 403 {@code PERMISSION_DENIED} · 404 {@code WITHDRAWAL_NOT_FOUND}·{@code MEMBER_NOT_FOUND} · 409 {@code WITHDRAWAL_ALREADY_SCHEDULED}·
 * {@code MEMBER_ALREADY_WITHDRAWN}·{@code WITHDRAWAL_NOT_SCHEDULED}·{@code WITHDRAWAL_NOT_MEMBER_REQUESTED}. 응답은 {@code Cache-Control: no-store}(닉네임·운영 메모).
 */
@RestController
public class MemberWithdrawalAdminController {

    private final MemberWithdrawalAdminService service;

    public MemberWithdrawalAdminController(MemberWithdrawalAdminService service) {
        this.service = service;
    }

    @RequirePermission(Permission.MEMBER_READ)
    @GetMapping("/api/admin/member-withdrawals")
    public ResponseEntity<ApiResponse<MemberWithdrawalAdminView.Page>> list(@RequestParam(required = false) String status, @RequestParam(required = false) String memberNo,
                                                                            @RequestParam(required = false) String requestedFrom, @RequestParam(required = false) String requestedTo,
                                                                            @RequestParam(required = false) String completedFrom, @RequestParam(required = false) String completedTo,
                                                                            @RequestParam(required = false) String sort, @RequestParam(required = false) String page,
                                                                            @RequestParam(required = false) String size) {
        return ok(service.list(status, memberNo, requestedFrom, requestedTo, completedFrom, completedTo, sort, page, size));
    }

    @RequirePermission(Permission.MEMBER_READ)
    @GetMapping("/api/admin/member-withdrawals/summary")
    public ResponseEntity<ApiResponse<MemberWithdrawalAdminView.Summary>> summary() {
        return ok(service.summary());
    }

    @RequirePermission(Permission.MEMBER_READ)
    @GetMapping("/api/admin/member-withdrawals/reasons")
    public ResponseEntity<ApiResponse<MemberWithdrawalAdminView.ReasonStats>> reasons(@RequestParam(required = false) String requestedFrom,
                                                                                      @RequestParam(required = false) String requestedTo) {
        return ok(service.reasons(requestedFrom, requestedTo));
    }

    @RequirePermission(Permission.MEMBER_READ)
    @GetMapping("/api/admin/member-withdrawals/{id}")
    public ResponseEntity<ApiResponse<MemberWithdrawalAdminView.Detail>> detail(@PathVariable String id) {
        return ok(service.detail(id));
    }

    @RequirePermission(Permission.MEMBER_WITHDRAW)
    @PostMapping("/api/admin/member-withdrawals/{id}/cancel")
    public ResponseEntity<ApiResponse<MemberWithdrawalAdminView.Detail>> cancel(@PathVariable String id, @RequestBody(required = false) MemberWithdrawalAdminRequest.Cancel body,
                                                                                AdminActor actor) {
        return ok(service.cancel(actor, id, body));
    }

    @RequirePermission(Permission.MEMBER_READ)
    @GetMapping("/api/admin/members/{memberId}/withdrawal")
    public ResponseEntity<ApiResponse<MemberWithdrawalAdminView.Detail>> forMember(@PathVariable String memberId) {
        return ok(service.forMember(memberId));
    }

    @RequirePermission(Permission.MEMBER_WITHDRAW)
    @GetMapping("/api/admin/members/{memberId}/withdrawal/preview")
    public ResponseEntity<ApiResponse<MemberWithdrawalAdminView.Preview>> preview(@PathVariable String memberId) {
        return ok(service.preview(memberId));
    }

    @RequirePermission(Permission.MEMBER_WITHDRAW)
    @PostMapping("/api/admin/members/{memberId}/withdrawal")
    public ResponseEntity<ApiResponse<MemberWithdrawalAdminView.ForceResult>> force(@PathVariable String memberId, @RequestBody(required = false) MemberWithdrawalAdminRequest.Force body,
                                                                                    AdminActor actor) {
        return ok(service.force(actor, memberId, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
