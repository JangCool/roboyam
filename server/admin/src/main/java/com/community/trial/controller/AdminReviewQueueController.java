package com.community.trial.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.sanction.dto.response.SanctionLineView;
import com.community.trial.service.ReviewQueueService;
import com.community.trial.service.SponsorHiddenActionService;
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
 * 관리자 「확인할 일」(trial-group-contract 「구현 결정」 7) — 칭호 관리의 「확인할 일」 탭과 체험단 메뉴가 같은 목록을 쓴다.
 *
 * <ul>
 *   <li>{@code GET /api/admin/review-queue?type=TITLE_APPLICATION|PURCHASE_PROOF|RETURN_CHECK|DAMAGE_REPORT|SPONSOR_HIDDEN&status=PENDING|DONE|ALL} →
 *       {@code { items: [{ type, id, status, title, member, at, note, refs }], pending: { 종류: 대기 수 } }} · 볼 수 없는 종류 403</li>
 *   <li>{@code POST /api/admin/review-queue/sponsor-hidden/{postId}/confirm} {@code { note?, sanction? }} → 200 {@code data} = 곁들인 제재 줄(없으면 null) — 글을 협찬으로
 *       고정(글쓴이가 못 바꾼다) · 그 글의 협찬 숨김 신고 사건도 처리 완료로 닫는다 · {@code sanction}({@code { kind, skipStep, confirmNickname, reason }} — 신고 처리와 같은 모양)이 있으면
 *       같은 트랜잭션에서 글쓴이 제재(제재 권한 403·칸 오류 400 {@code fields.sanction.*} — 확인도 롤백) (점검 D2)</li>
 *   <li>{@code …/dismiss} {@code { note? }} → 204(사건은 문제 없음으로) · 404 {@code SPONSOR_REVIEW_NOT_FOUND} · 409 {@code SPONSOR_REVIEW_CLOSED}</li>
 * </ul>
 */
@RestController
public class AdminReviewQueueController {

    private final ReviewQueueService queue;
    private final SponsorHiddenActionService sponsors;

    public AdminReviewQueueController(ReviewQueueService queue, SponsorHiddenActionService sponsors) {
        this.queue = queue;
        this.sponsors = sponsors;
    }

    @RequirePermission(anyOf = {Permission.TITLE_REVIEW, Permission.TITLE_READ, Permission.TRIAL_REVIEW})
    @GetMapping("/api/admin/review-queue")
    public ResponseEntity<ApiResponse<ReviewQueueService.Page>> list(@RequestParam(required = false) String type, @RequestParam(required = false) String status,
                                                                    AdminActor actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(queue.list(actor, type, status)));
    }

    @RequirePermission(Permission.TRIAL_REVIEW)
    @PostMapping("/api/admin/review-queue/sponsor-hidden/{postId}/confirm")
    public ResponseEntity<ApiResponse<SanctionLineView>> confirm(@PathVariable String postId, @RequestBody(required = false) SponsorHiddenActionService.Request body,
                                                                AdminActor actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(sponsors.confirm(actor, postId, body)));
    }

    @RequirePermission(Permission.TRIAL_REVIEW)
    @PostMapping("/api/admin/review-queue/sponsor-hidden/{postId}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable String postId, @RequestBody(required = false) SponsorHiddenActionService.Request body, AdminActor actor) {
        sponsors.dismiss(actor, postId, body);
        return ResponseEntity.noContent().build();
    }
}
