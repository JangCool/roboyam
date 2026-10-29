package com.community.groupbuy.controller;

import static com.community.groupbuy.controller.GroupBuyAdminController.ok;

import com.community.groupbuy.feedback.FeedbackView;
import com.community.groupbuy.feedback.GroupBuyQnaService;
import com.community.groupbuy.feedback.GroupBuyReviewService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 공구 Q&A·리뷰(admin-web {@code useGroupBuyQnaAdminApi}·{@code useGroupBuyReviewAdminApi}, 2b-6 — 결정 35). 조회 {@code commerce.order.read},
 * 답변·답글·숨김·숨김 풀기 {@code commerce.order.manage}. Q&A 목록·상세 조회는 서비스가 개인정보 열람 감사({@code GROUPBUY_QNA_VIEW})를 남긴다.
 */
@RestController
public class GroupBuyFeedbackAdminController {

    public record TextBody(String body) {
    }

    public record ReasonBody(String reason) {
    }

    public record HideReviewBody(String reasonCode, String memo) {
    }

    private final GroupBuyQnaService qna;
    private final GroupBuyReviewService reviews;

    public GroupBuyFeedbackAdminController(GroupBuyQnaService qna, GroupBuyReviewService reviews) {
        this.qna = qna;
        this.reviews = reviews;
    }

    // ── Q&A ──────────────────────────────────────────────────────────────

    @RequirePermission(Permission.COMMERCE_ORDER_READ)
    @GetMapping("/api/admin/group-buy-qna")
    public ResponseEntity<ApiResponse<FeedbackView.QnaList>> qnaList(@RequestParam(required = false) String tab, @RequestParam(required = false) Boolean overdue,
                                                                     @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size, AdminActor actor) {
        return ok(qna.adminList(tab, Boolean.TRUE.equals(overdue), q, page, size, actor.operatorId(), actor.ip()));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_READ)
    @GetMapping("/api/admin/group-buy-qna/{id}")
    public ResponseEntity<ApiResponse<FeedbackView.QnaDetail>> qnaDetail(@PathVariable String id, AdminActor actor) {
        return ok(qna.adminDetail(id, actor.operatorId(), actor.ip()));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PutMapping("/api/admin/group-buy-qna/{id}/answer")
    public ResponseEntity<ApiResponse<FeedbackView.QnaDetail>> answer(@PathVariable String id, @RequestBody(required = false) TextBody body, AdminActor actor) {
        return ok(qna.answer(id, body == null ? null : body.body(), actor.operatorId(), actor.ip()));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PostMapping("/api/admin/group-buy-qna/{id}/hide")
    public ResponseEntity<ApiResponse<FeedbackView.QnaDetail>> hideQuestion(@PathVariable String id, @RequestBody(required = false) ReasonBody body, AdminActor actor) {
        return ok(qna.hide(id, body == null ? null : body.reason(), actor.operatorId(), actor.ip()));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PostMapping("/api/admin/group-buy-qna/{id}/unhide")
    public ResponseEntity<ApiResponse<FeedbackView.QnaDetail>> unhideQuestion(@PathVariable String id, @RequestBody(required = false) ReasonBody body, AdminActor actor) {
        return ok(qna.unhide(id, body == null ? null : body.reason(), actor.operatorId(), actor.ip()));
    }

    // ── 리뷰 ─────────────────────────────────────────────────────────────

    @RequirePermission(Permission.COMMERCE_ORDER_READ)
    @GetMapping("/api/admin/group-buy-reviews")
    public ResponseEntity<ApiResponse<FeedbackView.ReviewList>> reviewList(@RequestParam(required = false) String tab, @RequestParam(required = false) Integer rating,
                                                                           @RequestParam(required = false) Boolean photo, @RequestParam(required = false) Boolean noReply,
                                                                           @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
                                                                           @RequestParam(defaultValue = "20") int size) {
        return ok(reviews.adminList(tab, rating, Boolean.TRUE.equals(photo), Boolean.TRUE.equals(noReply), q, page, size));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_READ)
    @GetMapping("/api/admin/group-buy-reviews/{id}")
    public ResponseEntity<ApiResponse<FeedbackView.ReviewDetail>> reviewDetail(@PathVariable String id) {
        return ok(reviews.adminDetail(id));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PutMapping("/api/admin/group-buy-reviews/{id}/reply")
    public ResponseEntity<ApiResponse<FeedbackView.ReviewDetail>> reply(@PathVariable String id, @RequestBody(required = false) TextBody body, AdminActor actor) {
        return ok(reviews.reply(id, body == null ? null : body.body(), actor.operatorId(), actor.ip()));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PostMapping("/api/admin/group-buy-reviews/{id}/hide")
    public ResponseEntity<ApiResponse<FeedbackView.ReviewDetail>> hideReview(@PathVariable String id, @RequestBody(required = false) HideReviewBody body, AdminActor actor) {
        return ok(reviews.hide(id, body == null ? null : body.reasonCode(), body == null ? null : body.memo(), actor.operatorId(), actor.ip()));
    }

    @RequirePermission(Permission.COMMERCE_ORDER_MANAGE)
    @PostMapping("/api/admin/group-buy-reviews/{id}/unhide")
    public ResponseEntity<ApiResponse<FeedbackView.ReviewDetail>> unhideReview(@PathVariable String id, @RequestBody(required = false) ReasonBody body, AdminActor actor) {
        return ok(reviews.unhide(id, body == null ? null : body.reason(), actor.operatorId(), actor.ip()));
    }
}
