package com.community.groupbuy.feedback;

import com.community.payment.dto.response.PaymentMemberView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 공구 Q&A·리뷰 응답 모양(2b-6) — user-web {@code useGroupBuyQnaApi.types.ts}·{@code useGroupBuyReviewApi.types.ts}, admin-web {@code useGroupBuyQnaAdminApi.types.ts}·
 * {@code useGroupBuyReviewAdminApi.types.ts} 와 같다. ID 는 문자열(큰 수), 시각은 ISO 순간, 날짜는 {@code YYYY-MM-DD}.
 */
public final class FeedbackView {

    private FeedbackView() {
    }

    // ── 회원 Q&A ────────────────────────────────────────────────────────────

    public record Answer(String body, Instant answeredAt, boolean edited) {
    }

    public record QuestionActions(boolean edit, boolean delete, boolean makeSecret, boolean report) {
    }

    /** 회원이 보는 질문 — 볼 수 없으면(남의 비밀글·숨김) 본문·답변이 null */
    public record Question(String id, String nickname, boolean mine, String body, boolean secret, boolean forcedSecret, String status, boolean hidden, Instant createdAt,
                           Instant updatedAt, Answer answer, QuestionActions actions) {
    }

    public record Round(String id, int roundNo) {
    }

    public record QnaPage(List<Question> items, long total, int page, boolean hasMore, boolean canAsk, String askBlock, Round previousRound) {
    }

    // ── 관리자 Q&A ──────────────────────────────────────────────────────────

    public record GroupBuyRef(String id, String title, int roundNo) {
    }

    public record Operator(String id, String name) {
    }

    public record QnaRow(String id, GroupBuyRef groupBuy, PaymentMemberView member, String preview, boolean secret, boolean forcedSecret, String status, boolean hidden,
                         boolean deleted, int reportCount, Instant createdAt, Instant answeredAt, int elapsedBusinessDays, boolean overdue) {
    }

    public record QnaList(List<QnaRow> items, long total, Map<String, Long> counts, long overdueCount) {
    }

    public record AdminAnswer(String body, Instant answeredAt, Operator answeredBy, Instant updatedAt, Operator updatedBy) {
    }

    public record AnswerRevision(String id, String body, Instant writtenAt, Operator writtenBy, Instant replacedAt, Operator replacedBy) {
    }

    public record HiddenInfo(String reason, Instant hiddenAt, Operator hiddenBy) {
    }

    public record ReportCount(String reason, long count) {
    }

    public record QnaDetail(String id, GroupBuyRef groupBuy, PaymentMemberView member, String preview, boolean secret, boolean forcedSecret, String status,
                            boolean hidden, boolean deleted, int reportCount, Instant createdAt, Instant answeredAt, int elapsedBusinessDays, boolean overdue, String body,
                            Instant updatedAt, Instant deletedAt, LocalDate retainUntil, AdminAnswer answer, List<AnswerRevision> revisions, HiddenInfo hiddenInfo,
                            List<ReportCount> reports) {
    }

    // ── 회원 리뷰 ───────────────────────────────────────────────────────────

    public record Photo(String id, String url) {
    }

    public record Stats(Double average, long count) {
    }

    public record SummaryPhoto(String reviewId, String url) {
    }

    public record Summary(Double average, long count, Map<String, Long> distribution, long photoCount, List<SummaryPhoto> photos) {
    }

    public record Reply(String body, Instant repliedAt, boolean edited) {
    }

    public record Hidden(String reasonCode, String memo) {
    }

    public record ReviewActions(boolean edit, boolean delete, boolean report) {
    }

    public record GroupBuyTitle(String id, String title) {
    }

    /** 회원이 보는 리뷰 — 내 리뷰 목록이면 {@code groupBuy}·{@code orderNo}·{@code editableUntil} 도 채운다 */
    public record Review(String id, String nickname, boolean mine, int rating, String body, List<Photo> photos, String skuName, int roundNo, Instant createdAt,
                         Instant updatedAt, Hidden hidden, Reply reply, ReviewActions actions, GroupBuyTitle groupBuy, String orderNo, Instant editableUntil) {
    }

    public record ReviewPage(Summary summary, List<Review> items, long total, int page, boolean hasMore) {
    }

    public record MyReviewPage(List<Review> items, long total, int page, boolean hasMore) {
    }

    public record ReviewTarget(String itemId, String state, LocalDate writableUntil, String reviewId) {
    }

    // ── 관리자 리뷰 ─────────────────────────────────────────────────────────

    public record ProductRef(String id, String name) {
    }

    public record ReviewRow(String id, ProductRef product, GroupBuyRef groupBuy, String orderId, String orderNo, String skuName, PaymentMemberView member, int rating,
                            String preview, int photoCount, boolean hidden, boolean deleted, int reportCount, boolean hasReply, Instant createdAt, Instant updatedAt) {
    }

    public record ReviewList(List<ReviewRow> items, long total, Map<String, Long> counts) {
    }

    public record ReviewRevision(String id, int rating, String body, int photoCount, Instant writtenAt, Instant replacedAt) {
    }

    public record ReviewHiddenInfo(String reasonCode, String memo, Instant hiddenAt, Operator hiddenBy) {
    }

    public record ReviewDetail(String id, ProductRef product, GroupBuyRef groupBuy, String orderId, String orderNo, String skuName, PaymentMemberView member, int rating,
                               String preview, int photoCount, boolean hidden, boolean deleted, int reportCount, boolean hasReply, Instant createdAt, Instant updatedAt,
                               String body, List<Photo> photos, Instant deletedAt, List<ReviewRevision> revisions, AdminAnswer answer, ReviewHiddenInfo hiddenInfo,
                               List<ReportCount> reports) {
    }
}
