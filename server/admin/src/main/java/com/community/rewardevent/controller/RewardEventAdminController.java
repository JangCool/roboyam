package com.community.rewardevent.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.rewardevent.dto.RewardMemberRef;
import com.community.rewardevent.dto.request.RewardCancelRequest;
import com.community.rewardevent.dto.request.RewardCommentPreviewRequest;
import com.community.rewardevent.dto.request.RewardDeliveredRequest;
import com.community.rewardevent.dto.request.RewardDrawRequest;
import com.community.rewardevent.dto.request.RewardEventSaveRequest;
import com.community.rewardevent.dto.request.RewardGrantRequest;
import com.community.rewardevent.dto.request.RewardRecipientAddRequest;
import com.community.rewardevent.dto.request.RewardResolveRequest;
import com.community.rewardevent.dto.request.RewardRevokeRequest;
import com.community.rewardevent.dto.request.RewardTargetPreviewRequest;
import com.community.rewardevent.dto.response.RewardCommentPreviewView;
import com.community.rewardevent.dto.response.RewardDrawView;
import com.community.rewardevent.dto.response.RewardEventAuditView;
import com.community.rewardevent.dto.response.RewardEventCatalogView;
import com.community.rewardevent.dto.response.RewardEventDetailView;
import com.community.rewardevent.dto.response.RewardEventPageView;
import com.community.rewardevent.dto.response.RewardGrantPreviewView;
import com.community.rewardevent.dto.response.RewardRecipientPageView;
import com.community.rewardevent.dto.response.RewardRecipientResolveView;
import com.community.rewardevent.dto.response.RewardRecipientView;
import com.community.rewardevent.dto.response.RewardRunView;
import com.community.rewardevent.dto.response.RewardTargetPreviewView;
import com.community.rewardevent.service.RewardEventAdminService;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 보상 이벤트 API — admin-web {@code useRewardEventAdminApi.ts} 머리말 그대로(docs/features/event-admin-contract.md 「API」). 모두 {@code /api/admin/reward-events}.
 * 권한: 조회 {@code reward-event.read} · 만들기·고치기·시작·마감·취소·후보·당첨자·추첨 {@code reward-event.manage} · 지급·재시도·회수·전달 완료 {@code reward-event.grant}
 * (1인 고액·일괄 포인트는 서비스가 {@code point.adjust.large} 도 본다). 회원·돈 정보가 담긴 응답이라 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/reward-events")
public class RewardEventAdminController {

    private final RewardEventAdminService service;

    public RewardEventAdminController(RewardEventAdminService service) {
        this.service = service;
    }

    @RequirePermission(Permission.REWARD_EVENT_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<RewardEventPageView>> list(@RequestParam(required = false) String status, @RequestParam(required = false) String mode,
                                                                 @RequestParam(required = false) String q, @RequestParam(required = false) Integer page,
                                                                 @RequestParam(required = false) Integer size) {
        return noStore(service.list(status, mode, q, page, size));
    }

    @RequirePermission(Permission.REWARD_EVENT_READ)
    @GetMapping("/catalog")
    public ResponseEntity<ApiResponse<RewardEventCatalogView>> catalog() {
        return noStore(service.catalog());
    }

    @RequirePermission(Permission.REWARD_EVENT_READ)
    @GetMapping("/member-search")
    public ResponseEntity<ApiResponse<List<RewardMemberRef>>> memberSearch(@RequestParam(required = false) String q, AdminActor actor) {
        return noStore(service.searchMembers(actor, q));
    }

    @RequirePermission(Permission.REWARD_EVENT_MANAGE)
    @PostMapping("/preview/comments")
    public ResponseEntity<ApiResponse<RewardCommentPreviewView>> previewComments(@RequestBody(required = false) RewardCommentPreviewRequest body) {
        return noStore(service.previewComments(body));
    }

    @RequirePermission(Permission.REWARD_EVENT_MANAGE)
    @PostMapping("/preview/targets")
    public ResponseEntity<ApiResponse<RewardTargetPreviewView>> previewTargets(@RequestBody(required = false) RewardTargetPreviewRequest body) {
        return noStore(service.previewTargets(body));
    }

    @RequirePermission(Permission.REWARD_EVENT_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RewardEventDetailView>> detail(@PathVariable String id) {
        return noStore(service.detail(id));
    }

    /** 만들기(준비) — 201 */
    @RequirePermission(Permission.REWARD_EVENT_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<RewardEventDetailView>> create(@RequestBody(required = false) RewardEventSaveRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.create(actor, body)));
    }

    @RequirePermission(Permission.REWARD_EVENT_MANAGE)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RewardEventDetailView>> update(@PathVariable String id, @RequestBody(required = false) RewardEventSaveRequest body, AdminActor actor) {
        return noStore(service.update(actor, id, body));
    }

    @RequirePermission(Permission.REWARD_EVENT_MANAGE)
    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<RewardEventDetailView>> start(@PathVariable String id, AdminActor actor) {
        return noStore(service.start(actor, id));
    }

    @RequirePermission(Permission.REWARD_EVENT_MANAGE)
    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<RewardEventDetailView>> close(@PathVariable String id, AdminActor actor) {
        return noStore(service.close(actor, id));
    }

    @RequirePermission(Permission.REWARD_EVENT_MANAGE)
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<RewardEventDetailView>> cancel(@PathVariable String id, @RequestBody(required = false) RewardCancelRequest body, AdminActor actor) {
        return noStore(service.cancel(actor, id, body));
    }

    @RequirePermission(Permission.REWARD_EVENT_MANAGE)
    @PostMapping("/{id}/recipients/resolve")
    public ResponseEntity<ApiResponse<RewardRecipientResolveView>> resolve(@PathVariable String id, @RequestBody(required = false) RewardResolveRequest body,
                                                                           AdminActor actor) {
        return noStore(service.resolve(actor, id, body));
    }

    @RequirePermission(Permission.REWARD_EVENT_MANAGE)
    @PostMapping("/{id}/recipients")
    public ResponseEntity<ApiResponse<RewardEventDetailView>> addRecipients(@PathVariable String id, @RequestBody(required = false) RewardRecipientAddRequest body,
                                                                            AdminActor actor) {
        return noStore(service.addRecipients(actor, id, body));
    }

    /** 당첨자 빼기(지급 전만) — 상세를 돌려준다(화면 {@code removeRecipient} 가 상세를 받는다) */
    @RequirePermission(Permission.REWARD_EVENT_MANAGE)
    @DeleteMapping("/{id}/recipients/{recipientId}")
    public ResponseEntity<ApiResponse<RewardEventDetailView>> removeRecipient(@PathVariable String id, @PathVariable String recipientId, AdminActor actor) {
        return noStore(service.removeRecipient(actor, id, recipientId));
    }

    @RequirePermission(Permission.REWARD_EVENT_READ)
    @GetMapping("/{id}/recipients")
    public ResponseEntity<ApiResponse<RewardRecipientPageView>> recipients(@PathVariable String id, @RequestParam(required = false) String status,
                                                                           @RequestParam(required = false) String tierId, @RequestParam(required = false) String q,
                                                                           @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
                                                                           AdminActor actor) {
        return noStore(service.recipients(actor, id, status, tierId, q, page, size));
    }

    @RequirePermission(Permission.REWARD_EVENT_MANAGE)
    @PostMapping("/{id}/draws")
    public ResponseEntity<ApiResponse<RewardDrawView>> draw(@PathVariable String id, @RequestBody(required = false) RewardDrawRequest body, AdminActor actor) {
        return noStore(service.draw(actor, id, body));
    }

    @RequirePermission(Permission.REWARD_EVENT_READ)
    @GetMapping("/{id}/draws")
    public ResponseEntity<ApiResponse<List<RewardDrawView>>> draws(@PathVariable String id, AdminActor actor) {
        return noStore(service.draws(actor, id));
    }

    @RequirePermission(Permission.REWARD_EVENT_READ)
    @GetMapping("/{id}/grants/preview")
    public ResponseEntity<ApiResponse<RewardGrantPreviewView>> grantPreview(@PathVariable String id) {
        return noStore(service.grantPreview(id));
    }

    @RequirePermission(Permission.REWARD_EVENT_GRANT)
    @PostMapping("/{id}/grants")
    public ResponseEntity<ApiResponse<RewardRunView>> grant(@PathVariable String id, @RequestBody(required = false) RewardGrantRequest body, AdminActor actor) {
        return noStore(service.grant(actor, id, body));
    }

    @RequirePermission(Permission.REWARD_EVENT_GRANT)
    @PostMapping("/{id}/grants/retry")
    public ResponseEntity<ApiResponse<RewardRunView>> retry(@PathVariable String id, AdminActor actor) {
        return noStore(service.retry(actor, id));
    }

    @RequirePermission(Permission.REWARD_EVENT_GRANT)
    @PostMapping("/{id}/revocations")
    public ResponseEntity<ApiResponse<RewardRunView>> revoke(@PathVariable String id, @RequestBody(required = false) RewardRevokeRequest body, AdminActor actor) {
        return noStore(service.revoke(actor, id, body));
    }

    @RequirePermission(Permission.REWARD_EVENT_GRANT)
    @PostMapping("/{id}/recipients/{recipientId}/manual-delivered")
    public ResponseEntity<ApiResponse<RewardRecipientView>> markDelivered(@PathVariable String id, @PathVariable String recipientId,
                                                                          @RequestBody(required = false) RewardDeliveredRequest body, AdminActor actor) {
        return noStore(service.markDelivered(actor, id, recipientId, body));
    }

    @RequirePermission(Permission.REWARD_EVENT_READ)
    @GetMapping("/{id}/runs")
    public ResponseEntity<ApiResponse<List<RewardRunView>>> runs(@PathVariable String id) {
        return noStore(service.runs(id));
    }

    @RequirePermission(Permission.REWARD_EVENT_READ)
    @GetMapping("/{id}/audit")
    public ResponseEntity<ApiResponse<List<RewardEventAuditView>>> audit(@PathVariable String id) {
        return noStore(service.audit(id));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
