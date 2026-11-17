package com.community.trial.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.trial.dto.TrialViews;
import com.community.trial.service.ReviewQueueService;
import com.community.trial.service.SponsorReviewService;
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
 *   <li>{@code POST /api/admin/review-queue/sponsor-hidden/{postId}/confirm} {@code { note? }} → 204(글을 협찬으로 바꿈) · {@code …/dismiss} → 204 ·
 *       404 {@code SPONSOR_REVIEW_NOT_FOUND} · 409 {@code SPONSOR_REVIEW_CLOSED}</li>
 * </ul>
 */
@RestController
public class AdminReviewQueueController {

    private final ReviewQueueService queue;
    private final SponsorReviewService sponsors;

    public AdminReviewQueueController(ReviewQueueService queue, SponsorReviewService sponsors) {
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
    public ResponseEntity<Void> confirm(@PathVariable String postId, @RequestBody(required = false) TrialViews.NoteRequest body, AdminActor actor) {
        sponsors.confirm(actor.operatorId(), actor.ip(), postId, body == null ? null : body.note());
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.TRIAL_REVIEW)
    @PostMapping("/api/admin/review-queue/sponsor-hidden/{postId}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable String postId, @RequestBody(required = false) TrialViews.NoteRequest body, AdminActor actor) {
        sponsors.dismiss(actor.operatorId(), actor.ip(), postId, body == null ? null : body.note());
        return ResponseEntity.noContent().build();
    }
}
