package com.community.groupbuy.controller;

import static com.community.groupbuy.controller.GroupBuyController.noStore;
import static com.community.groupbuy.controller.GroupBuyController.optionalUser;
import static com.community.groupbuy.controller.GroupBuyController.user;

import com.community.groupbuy.feedback.FeedbackView;
import com.community.groupbuy.feedback.GroupBuyQnaService;
import com.community.groupbuy.feedback.GroupBuyReviewService;
import com.community.zconfiguration.response.ApiResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 공구 Q&A·리뷰(user-web {@code useGroupBuyQnaApi}·{@code useGroupBuyReviewApi}, 2b-6 — 계약 「Q&A 규칙」·「리뷰 규칙」). 목록은 로그인 없이, 쓰기는 로그인(401).
 * 신고는 공용 {@code POST /api/reports}(대상 {@code GROUP_BUY_QUESTION}·{@code GROUP_BUY_REVIEW}). 응답은 {@code no-store}(내 글·할 수 있는 일이 실린다).
 */
@RestController
public class GroupBuyFeedbackController {

    /** 질문 쓰기·고치기 */
    public record QuestionBody(String body, Boolean secret) {
    }

    /** 리뷰 쓰기·고치기 */
    public record ReviewBody(Integer rating, String body, List<String> photoIds) {
    }

    private final GroupBuyQnaService qna;
    private final GroupBuyReviewService reviews;

    public GroupBuyFeedbackController(GroupBuyQnaService qna, GroupBuyReviewService reviews) {
        this.qna = qna;
        this.reviews = reviews;
    }

    // ── Q&A ──────────────────────────────────────────────────────────────

    @GetMapping("/api/group-buys/{id}/questions")
    public ResponseEntity<ApiResponse<FeedbackView.QnaPage>> questions(@PathVariable String id, @RequestParam(required = false) Boolean mine,
                                                                      @RequestParam(defaultValue = "0") int page, Authentication authentication) {
        return noStore(qna.list(id, optionalUser(authentication), Boolean.TRUE.equals(mine), page));
    }

    @PostMapping("/api/group-buys/{id}/questions")
    public ResponseEntity<ApiResponse<FeedbackView.Question>> ask(@PathVariable String id, @RequestBody(required = false) QuestionBody body, Authentication authentication) {
        long userId = user(authentication);
        QuestionBody b = body == null ? new QuestionBody(null, null) : body;
        return noStore(qna.ask(userId, id, b.body(), b.secret()));
    }

    @PutMapping("/api/group-buy-questions/{qid}")
    public ResponseEntity<ApiResponse<FeedbackView.Question>> updateQuestion(@PathVariable String qid, @RequestBody(required = false) QuestionBody body,
                                                                            Authentication authentication) {
        long userId = user(authentication);
        QuestionBody b = body == null ? new QuestionBody(null, null) : body;
        return noStore(qna.update(userId, qid, b.body(), b.secret()));
    }

    @PostMapping("/api/group-buy-questions/{qid}/secret")
    public ResponseEntity<ApiResponse<FeedbackView.Question>> makeSecret(@PathVariable String qid, Authentication authentication) {
        return noStore(qna.makeSecret(user(authentication), qid));
    }

    @DeleteMapping("/api/group-buy-questions/{qid}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable String qid, Authentication authentication) {
        qna.remove(user(authentication), qid);
        return ResponseEntity.noContent().build();
    }

    // ── 리뷰 ─────────────────────────────────────────────────────────────

    @GetMapping("/api/group-buys/{id}/reviews")
    public ResponseEntity<ApiResponse<FeedbackView.ReviewPage>> reviews(@PathVariable String id, @RequestParam(required = false) String sort,
                                                                       @RequestParam(required = false) Boolean photo, @RequestParam(defaultValue = "0") int page,
                                                                       Authentication authentication) {
        return noStore(reviews.list(id, optionalUser(authentication), sort, Boolean.TRUE.equals(photo), page));
    }

    @GetMapping("/api/me/orders/{orderNo}/reviews")
    public ResponseEntity<ApiResponse<List<FeedbackView.ReviewTarget>>> targets(@PathVariable String orderNo, Authentication authentication) {
        return noStore(reviews.targets(user(authentication), orderNo));
    }

    @PostMapping("/api/me/orders/{orderNo}/items/{itemId}/review")
    public ResponseEntity<ApiResponse<FeedbackView.Review>> create(@PathVariable String orderNo, @PathVariable String itemId, @RequestBody(required = false) ReviewBody body,
                                                                  Authentication authentication) {
        long userId = user(authentication);
        ReviewBody b = body == null ? new ReviewBody(null, null, null) : body;
        return noStore(reviews.create(userId, orderNo, itemId, b.rating(), b.body(), b.photoIds()));
    }

    @GetMapping("/api/me/group-buy-reviews")
    public ResponseEntity<ApiResponse<FeedbackView.MyReviewPage>> mine(@RequestParam(defaultValue = "0") int page, Authentication authentication) {
        return noStore(reviews.mine(user(authentication), page));
    }

    @PutMapping("/api/me/group-buy-reviews/{id}")
    public ResponseEntity<ApiResponse<FeedbackView.Review>> update(@PathVariable String id, @RequestBody(required = false) ReviewBody body, Authentication authentication) {
        long userId = user(authentication);
        ReviewBody b = body == null ? new ReviewBody(null, null, null) : body;
        return noStore(reviews.update(userId, id, b.rating(), b.body(), b.photoIds()));
    }

    @DeleteMapping("/api/me/group-buy-reviews/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, Authentication authentication) {
        reviews.remove(user(authentication), id);
        return ResponseEntity.noContent().build();
    }

    /** 사진(multipart {@code file}) — 리뷰에 붙이기 전 먼저 올린다 */
    @PostMapping("/api/me/group-buy-reviews/photos")
    public ResponseEntity<ApiResponse<FeedbackView.Photo>> photo(@RequestParam(value = "file", required = false) MultipartFile file, Authentication authentication) {
        long userId = user(authentication);
        byte[] data;
        try {
            data = file == null ? null : file.getBytes();
        } catch (IOException e) {
            data = null;
        }
        return noStore(reviews.uploadPhoto(userId, data));
    }
}
