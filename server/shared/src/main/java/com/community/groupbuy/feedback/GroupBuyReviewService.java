package com.community.groupbuy.feedback;

import static com.community.groupbuy.feedback.GroupBuyQnaService.nz;
import static com.community.groupbuy.feedback.GroupBuyQnaService.operator;
import static com.community.groupbuy.feedback.GroupBuyQnaService.parse;
import static com.community.groupbuy.feedback.GroupBuyQnaService.parseOrNull;
import static com.community.groupbuy.feedback.GroupBuyQnaService.preview;

import com.community.audit.dto.AuditActions;
import com.community.commerce.image.GroupBuyImageUploader;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyMemberStore;
import com.community.groupbuy.repository.master.GroupBuyReviewStore;
import com.community.groupbuy.repository.master.GroupBuyReviewStore.AdminReviewRow;
import com.community.groupbuy.repository.master.GroupBuyReviewStore.ReviewRow;
import com.community.groupbuy.repository.master.GroupBuyRowNames;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.service.GroupBuyAudit;
import com.community.groupbuy.service.GroupBuyNotifications;
import com.community.payment.dto.response.PaymentMemberView;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공구 리뷰(2b-6 — 계약 「리뷰 규칙」, 사용자 확정 2026-10-29). 읽기·쓰기는 {@link GroupBuyReviewStore}.
 *
 * <ul>
 *   <li><b>쓰기</b>: 그 단품을 산 회원. 주문이 배송완료(DELIVERED)이고 배송완료일 + {@value #WRITE_DAYS}일 안, 주문 줄(단품)마다 하나 — 지워도 다시 쓰지 못한다.
 *       전부 취소·반품된 줄은 쓰지 못한다(409 {@code GROUP_BUY_REVIEW_NOT_WRITABLE}). 정지·탈퇴 회원은 403.</li>
 *   <li><b>내용</b>: 별점 1~5 · 글 10~1,000자 · 사진 {@value #PHOTO_MAX}장까지(먼저 올리고 ID 로 붙인다) · 산 단품 이름은 주문 때 단품 이름 사본.</li>
 *   <li><b>작성자</b>: 작성 {@value #EDIT_DAYS}일 안에 고치기(이전 내용은 이력), 언제든 지우기.</li>
 *   <li><b>노출</b>: 상품(상품 마스터)마다 — 같은 상품의 다른 차수 리뷰 포함, 카드마다 차수. 숨긴 리뷰는 작성자에게만(사유와 함께), 평균·수·분포에서 빠진다.</li>
 *   <li><b>운영자</b>: 공식 답글 하나(첫 답글이면 작성자에게 앱 알림, 고치면 감사) · 숨김은 사유 코드로만({@link #HIDE_REASONS} — 낮은 별점은 사유가 아니다,
 *       표시광고법·공정위 소비자 리뷰 지침) + 메모(기타는 필수 5~200자), 숨기면 열린 신고 처리 · 숨김 풀기는 사유 5~200자. 감사 {@code GROUPBUY_REVIEW_HIDE}.</li>
 *   <li><b>보상</b>: 없음(정책 미확정). 보상을 넣으면 「대가를 받은 리뷰」 표시 의무가 생긴다(계약).</li>
 * </ul>
 */
@Service
public class GroupBuyReviewService {

    public static final int PAGE_SIZE = 10;
    public static final int WRITE_DAYS = 90;
    public static final int EDIT_DAYS = 30;
    public static final int BODY_MIN = 10;
    public static final int BODY_MAX = 1000;
    public static final int REPLY_MAX = 2000;
    public static final int PHOTO_MAX = 5;
    public static final Set<String> SORTS = Set.of("LATEST", "RATING_HIGH", "RATING_LOW", "PHOTO");
    public static final Set<String> HIDE_REASONS = Set.of("ABUSE", "PRIVACY", "AD", "IRRELEVANT", "OTHER");

    private final GroupBuyReviewStore store;
    private final GroupBuyStore groups;
    private final GroupBuyMemberStore members;
    private final GroupBuyUgcReportService reports;
    private final GroupBuyNotifications notifications;
    private final GroupBuyAudit audit;
    private final GroupBuyImageUploader images;
    private final SnowflakeIdGenerator ids;
    private final ObjectProvider<Clock> clocks;

    public GroupBuyReviewService(GroupBuyReviewStore store, GroupBuyStore groups, GroupBuyMemberStore members, GroupBuyUgcReportService reports,
                                 GroupBuyNotifications notifications, GroupBuyAudit audit, GroupBuyImageUploader images, SnowflakeIdGenerator ids,
                                 ObjectProvider<Clock> clocks) {
        this.store = store;
        this.groups = groups;
        this.members = members;
        this.reports = reports;
        this.notifications = notifications;
        this.audit = audit;
        this.images = images;
        this.ids = ids;
        this.clocks = clocks;
    }

    // ── 회원: 보기 ──────────────────────────────────────────────────────────

    /** 공구 상세의 리뷰 — 같은 상품의 모든 차수. 숨긴 리뷰는 작성자에게만 */
    @Transactional(readOnly = true)
    public FeedbackView.ReviewPage list(String groupBuyId, Long viewerId, String sort, boolean photoOnly, int page) {
        GroupBuyRow g = visible(groupBuyId);
        String s = sort == null || sort.isBlank() ? "LATEST" : sort;
        if (!SORTS.contains(s)) {
            throw ValidationException.of("sort", new MessageRef("groupbuy.review.sort.invalid"));
        }
        int p = Math.max(0, page);
        long viewer = viewerId == null ? -1 : viewerId;
        long total = store.productCount(g.productId(), viewer, photoOnly);
        List<ReviewRow> rows = store.productPage(g.productId(), viewer, photoOnly, GroupBuyReviewStore.Sort.valueOf(s), (long) p * PAGE_SIZE, PAGE_SIZE);
        Map<Long, List<FeedbackView.Photo>> photos = photosOf(rows.stream().map(ReviewRow::id).toList());
        Instant now = now();
        List<FeedbackView.Review> items = rows.stream().map(r -> view(r, viewerId, photos.getOrDefault(r.id(), List.of()), now, false)).toList();
        return new FeedbackView.ReviewPage(summary(g.productId()), items, total, p, (long) (p + 1) * PAGE_SIZE < total);
    }

    /** 평균(소수 둘째 자리)·수·분포·사진 리뷰 수·사진 모아보기(최근 12장) — 숨긴·지운 리뷰는 빼고 */
    FeedbackView.Summary summary(long productId) {
        Map<String, Long> dist = new LinkedHashMap<>();
        for (int star = 1; star <= 5; star++) {
            dist.put(Integer.toString(star), 0L);
        }
        store.ratingCounts(productId).forEach((rating, n) -> dist.put(Integer.toString(rating), n));
        long count = dist.values().stream().mapToLong(Long::longValue).sum();
        long sum = dist.entrySet().stream().mapToLong(e -> Integer.parseInt(e.getKey()) * e.getValue()).sum();
        Double average = count == 0 ? null : Math.round(sum * 100.0 / count) / 100.0;
        long photoCount = store.photoReviewCount(productId);
        List<FeedbackView.SummaryPhoto> strip = store.photoStrip(productId, 12).stream()
                .map(ph -> new FeedbackView.SummaryPhoto(Long.toString(ph.reviewId()), images.url(ph.imageKey()))).toList();
        return new FeedbackView.Summary(average, count, dist, photoCount, strip);
    }

    /** 목록 카드의 평균(소수 첫째 자리)·수 — 상품마다(공구 목록이 한 번에 부른다) */
    @Transactional(readOnly = true)
    public Map<Long, FeedbackView.Stats> stats(List<Long> productIds) {
        Map<Long, FeedbackView.Stats> out = new HashMap<>();
        if (productIds.isEmpty()) {
            return out;
        }
        store.stats(productIds.stream().distinct().toList()).forEach((productId, v) -> {
            double avg = v[0] == null ? 0 : (Double) v[0];
            out.put(productId, new FeedbackView.Stats(Math.round(avg * 10) / 10.0, (Long) v[1]));
        });
        return out;
    }

    /** 내 주문의 줄마다 리뷰 상태 — 쓸 수 있음 · 썼음 · 기한 지남 · 대상 아님 */
    @Transactional(readOnly = true)
    public List<FeedbackView.ReviewTarget> targets(long userId, String orderNo) {
        List<GroupBuyReviewStore.TargetItem> items = store.targetItems(orderNo, userId);
        if (items.isEmpty()) {
            throw GroupBuyException.orderNotFound();
        }
        Set<Long> deletedOnce = new LinkedHashSet<>(store.deletedItems(orderNo, userId));
        LocalDate today = ServiceTime.today(clock());
        return items.stream().map(it -> {
            LocalDate until = it.deliveredAt() == null ? null : ServiceTime.dateOf(it.deliveredAt()).plusDays(WRITE_DAYS);
            if (it.reviewId() != null) {
                return new FeedbackView.ReviewTarget(Long.toString(it.itemId()), "WRITTEN", until, Long.toString(it.reviewId()));
            }
            if (it.status() != ParticipationStatus.DELIVERED || until == null || it.live() <= 0 || deletedOnce.contains(it.itemId())) {
                return new FeedbackView.ReviewTarget(Long.toString(it.itemId()), "NONE", null, null);
            }
            return new FeedbackView.ReviewTarget(Long.toString(it.itemId()), today.isAfter(until) ? "EXPIRED" : "WRITABLE", until, null);
        }).toList();
    }

    /** 내 리뷰 — 새 것 먼저, 숨긴 것도(사유와 함께) */
    @Transactional(readOnly = true)
    public FeedbackView.MyReviewPage mine(long userId, int page) {
        int p = Math.max(0, page);
        long total = store.mineCount(userId);
        List<ReviewRow> rows = store.minePage(userId, (long) p * PAGE_SIZE, PAGE_SIZE);
        Map<Long, List<FeedbackView.Photo>> photos = photosOf(rows.stream().map(ReviewRow::id).toList());
        Instant now = now();
        return new FeedbackView.MyReviewPage(rows.stream().map(r -> view(r, userId, photos.getOrDefault(r.id(), List.of()), now, true)).toList(), total, p,
                (long) (p + 1) * PAGE_SIZE < total);
    }

    static FeedbackView.Review view(ReviewRow r, Long viewerId, List<FeedbackView.Photo> photos, Instant now, boolean withOrder) {
        boolean own = viewerId != null && viewerId == r.userId();
        Instant editableUntil = r.createdAt().plus(Duration.ofDays(EDIT_DAYS));
        FeedbackView.Hidden hidden = own && r.hiddenCode() != null ? new FeedbackView.Hidden(r.hiddenCode(), r.hiddenMemo()) : null;
        FeedbackView.Reply reply = r.reply() == null ? null : new FeedbackView.Reply(r.reply(), r.repliedAt(), r.replyEdited());
        FeedbackView.ReviewActions actions = new FeedbackView.ReviewActions(own && now.isBefore(editableUntil), own, viewerId != null && !own);
        return new FeedbackView.Review(Long.toString(r.id()), PersonalInfo.maskNickname(r.nickname()), own, r.rating(), r.body(), photos, r.skuName(), r.roundNo(),
                r.createdAt(), r.updatedAt(), hidden, reply, actions, withOrder ? new FeedbackView.GroupBuyTitle(Long.toString(r.groupBuyId()), r.title()) : null,
                withOrder ? r.orderNo() : null, withOrder ? editableUntil : null);
    }

    // ── 회원: 쓰기 ──────────────────────────────────────────────────────────

    /** 사진 올리기 — 리뷰에 붙기 전 먼저(붙지 않은 사진은 정리 대상) */
    @Transactional
    public FeedbackView.Photo uploadPhoto(long userId, byte[] data) {
        requireWriter(userId);
        GroupBuyImageUploader.Uploaded up = images.upload("review", data);
        long id = ids.nextId();
        store.insertPhoto(id, userId, up.key(), now());
        return new FeedbackView.Photo(Long.toString(id), up.url());
    }

    @Transactional
    public FeedbackView.Review create(long userId, String orderNo, String itemId, Integer rating, String body, List<String> photoIds) {
        Draft d = draft(rating, body, photoIds);
        requireWriter(userId);
        long item = parse(itemId, GroupBuyException::orderNotFound);
        GroupBuyReviewStore.WritableLine line = store.lockWritableLine(item, orderNo, userId).orElseThrow(GroupBuyException::orderNotFound);
        LocalDate today = ServiceTime.today(clock());
        if (line.status() != ParticipationStatus.DELIVERED || line.deliveredAt() == null || line.live() <= 0
                || today.isAfter(ServiceTime.dateOf(line.deliveredAt()).plusDays(WRITE_DAYS))) {
            throw FeedbackException.reviewNotWritable();
        }
        long id = ids.nextId();
        Instant now = now();
        try {
            store.insertReview(id, line.productId(), line.groupBuyId(), line.participationId(), item, userId, line.skuName(), d.rating(), d.body(), now);
        } catch (DuplicateKeyException e) {
            throw FeedbackException.reviewExists();
        }
        attach(userId, id, d.photoIds(), now);
        return myView(id, userId);
    }

    /** 고치기 — 작성 30일 안. 이전 별점·글·사진 수는 이력으로 */
    @Transactional
    public FeedbackView.Review update(long userId, String reviewId, Integer rating, String body, List<String> photoIds) {
        Draft d = draft(rating, body, photoIds);
        requireWriter(userId);
        long id = parse(reviewId, FeedbackException::reviewNotFound);
        GroupBuyReviewStore.CurrentReview c = store.lockOwn(id, userId).orElseThrow(FeedbackException::reviewNotFound);
        Instant now = now();
        if (!now.isBefore(c.createdAt().plus(Duration.ofDays(EDIT_DAYS)))) {
            throw FeedbackException.reviewEditExpired();
        }
        int photoCount = store.photoCount(id);
        store.insertRevision(ids.nextId(), id, c.rating(), c.body(), photoCount, c.updatedAt() == null ? c.createdAt() : c.updatedAt(), now);
        store.updateReview(id, d.rating(), d.body(), now);
        // 사진: 빠진 것은 떼어 정리 대상으로, 새로 붙인 것은 붙인다
        List<Long> keep = d.photoIds();
        store.deletePhotosExcept(id, keep);
        attach(userId, id, keep, now);
        return myView(id, userId);
    }

    /** 지우기 — 같은 줄에 다시 쓸 수 없다(줄은 남는다) */
    @Transactional
    public void remove(long userId, String reviewId) {
        long id = parse(reviewId, FeedbackException::reviewNotFound);
        if (!store.softDelete(id, userId, now())) {
            throw FeedbackException.reviewNotFound();
        }
    }

    private record Draft(int rating, String body, List<Long> photoIds) {
    }

    private static Draft draft(Integer rating, String body, List<String> photoIds) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        if (rating == null || rating < 1 || rating > 5) {
            fields.put("rating", new MessageRef("groupbuy.review.rating.required"));
        }
        String text = body == null ? "" : body.strip();
        int n = text.codePointCount(0, text.length());
        if (n < BODY_MIN || n > BODY_MAX) {
            fields.put("body", new MessageRef("groupbuy.review.body.length", BODY_MIN, BODY_MAX));
        }
        List<Long> photos = new ArrayList<>();
        for (String p : photoIds == null ? List.<String>of() : photoIds) {
            Long id = parseOrNull(p);
            if (id == null) {
                fields.put("photoIds", new MessageRef("groupbuy.review.photo.invalid"));
                break;
            }
            if (!photos.contains(id)) {
                photos.add(id);
            }
        }
        if (photos.size() > PHOTO_MAX) {
            fields.put("photoIds", new MessageRef("groupbuy.review.photo.max", PHOTO_MAX));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return new Draft(rating, text, photos);
    }

    /** 내가 올린 사진을 차례대로 붙인다 — 남의 사진·다른 리뷰의 사진은 400 */
    private void attach(long userId, long reviewId, List<Long> photoIds, Instant now) {
        for (int i = 0; i < photoIds.size(); i++) {
            if (!store.attachPhoto(photoIds.get(i), userId, reviewId, i, now)) {
                throw ValidationException.of("photoIds", new MessageRef("groupbuy.review.photo.invalid"));
            }
        }
    }

    private FeedbackView.Review myView(long id, long userId) {
        ReviewRow r = store.memberRow(id).orElseThrow(FeedbackException::reviewNotFound);
        return view(r, userId, photosOf(List.of(id)).getOrDefault(id, List.of()), now(), true);
    }

    private Map<Long, List<FeedbackView.Photo>> photosOf(List<Long> reviewIds) {
        Map<Long, List<FeedbackView.Photo>> out = new HashMap<>();
        if (reviewIds.isEmpty()) {
            return out;
        }
        store.photosOf(reviewIds).forEach(ph -> out.computeIfAbsent(ph.reviewId(), k -> new ArrayList<>())
                .add(new FeedbackView.Photo(Long.toString(ph.id()), images.url(ph.imageKey()))));
        return out;
    }

    private void requireWriter(long userId) {
        if (members.state(userId).map(s -> s.suspended() || s.gone()).orElse(true)) {
            throw FeedbackException.restricted();
        }
    }

    // ── 운영자 ──────────────────────────────────────────────────────────────

    private static FeedbackView.ReviewRow adminRow(AdminReviewRow a) {
        return new FeedbackView.ReviewRow(Long.toString(a.id()), new FeedbackView.ProductRef(Long.toString(a.productId()), a.productName()),
                new FeedbackView.GroupBuyRef(Long.toString(a.groupBuyId()), a.title(), a.roundNo()), Long.toString(a.participationId()), a.orderNo(), a.skuName(),
                new PaymentMemberView(a.gone() ? null : Long.toString(a.userId()), a.gone() ? "" : nz(a.nickname()), a.gone()), a.rating(), preview(a.body()), a.photoCount(),
                a.hidden(), a.deleted(), a.reportCount(), a.hasReply(), a.createdAt(), a.updatedAt());
    }

    /** 관리자 목록 — 탭 VISIBLE(기본)·REPORTED(공개 중 열린 신고)·HIDDEN·ALL(지운 것 포함) · 별점 · 사진 있음 · 답글 없음 · 찾기. 새 것 먼저 */
    @Transactional(readOnly = true)
    public FeedbackView.ReviewList adminList(String tab, Integer rating, boolean photo, boolean noReply, String q, int page, int size) {
        String t = tab == null || tab.isBlank() ? "VISIBLE" : tab;
        if (!List.of("VISIBLE", "REPORTED", "HIDDEN", "ALL").contains(t)) {
            throw ValidationException.of("tab", new MessageRef("groupbuy.review.tab.invalid"));
        }
        String needle = q == null ? "" : q.trim();
        GroupBuyReviewStore.AdminFilter filter = new GroupBuyReviewStore.AdminFilter(t, rating, photo, noReply, needle.isEmpty() ? null : GroupBuyRowNames.like(needle),
                needle.isEmpty() ? null : parseOrNull(needle));
        int s = Math.min(Math.max(size, 1), 100);
        int p = Math.max(0, page);
        long total = store.adminCount(filter);
        List<FeedbackView.ReviewRow> items = store.adminPage(filter, (long) p * s, s).stream().map(GroupBuyReviewService::adminRow).toList();
        long[] tabs = store.tabCounts();
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("VISIBLE", tabs[0]);
        counts.put("REPORTED", tabs[1]);
        counts.put("HIDDEN", tabs[2]);
        return new FeedbackView.ReviewList(items, total, counts);
    }

    @Transactional(readOnly = true)
    public FeedbackView.ReviewDetail adminDetail(String reviewId) {
        return detail(parse(reviewId, FeedbackException::reviewNotFound));
    }

    private FeedbackView.ReviewDetail detail(long id) {
        AdminReviewRow a = store.adminRow(id).orElseThrow(FeedbackException::reviewNotFound);
        GroupBuyReviewStore.ReviewExtra x = store.extra(id).orElseThrow(FeedbackException::reviewNotFound);
        List<FeedbackView.ReviewRevision> revisions = store.revisions(id).stream().map(v -> new FeedbackView.ReviewRevision(Long.toString(v.id()), v.rating(), v.body(),
                v.photoCount(), v.writtenAt(), v.replacedAt())).toList();
        FeedbackView.AdminAnswer reply = store.reply(id).map(v -> new FeedbackView.AdminAnswer(v.body(), v.repliedAt(), operator(v.repliedBy(), v.repliedByName()),
                v.updatedAt(), v.updatedBy() == null ? null : operator(v.updatedBy(), v.updatedByName()))).orElse(null);
        FeedbackView.ReviewHiddenInfo hidden = x.hiddenAt() == null ? null : new FeedbackView.ReviewHiddenInfo(x.code(), x.memo(), x.hiddenAt(), operator(x.hiddenBy(),
                x.hiddenByName()));
        FeedbackView.ReviewRow r = adminRow(a);
        return new FeedbackView.ReviewDetail(r.id(), r.product(), r.groupBuy(), r.orderId(), r.orderNo(), r.skuName(), r.member(), r.rating(), r.preview(), r.photoCount(),
                r.hidden(), r.deleted(), r.reportCount(), r.hasReply(), r.createdAt(), r.updatedAt(), a.body(), photosOf(List.of(id)).getOrDefault(id, List.of()), x.deletedAt(),
                revisions, reply, hidden, reports.openReasons(GroupBuyUgcReportService.REVIEW, id));
    }

    /** 공식 답글(처음이면 작성자에게 앱 알림, 고치면 감사) — 지운 리뷰는 409 */
    @Transactional
    public FeedbackView.ReviewDetail reply(String reviewId, String body, long operatorId, String ip) {
        long id = parse(reviewId, FeedbackException::reviewNotFound);
        String text = body == null ? "" : body.strip();
        if (text.isEmpty() || text.codePointCount(0, text.length()) > REPLY_MAX) {
            throw ValidationException.of("body", new MessageRef("groupbuy.review.reply.length", REPLY_MAX));
        }
        GroupBuyReviewStore.LockedReview r = store.lock(id).orElseThrow(FeedbackException::reviewNotFound);
        if (r.deleted()) {
            throw FeedbackException.reviewDeleted();
        }
        Instant now = now();
        Optional<String> old = store.replyBody(id);
        if (old.isEmpty()) {
            store.insertReply(id, text, operatorId, now);
            notifications.reviewReplied(r.userId(), r.groupBuyId());
        } else if (!old.get().equals(text)) {
            store.updateReply(id, text, operatorId, now);
            audit.record(operatorId, AuditActions.GROUPBUY_REVIEW_REPLY_EDIT, GroupBuyAudit.TARGET_REVIEW, id, ip, Map.of("length", text.length()));
        }
        return detail(id);
    }

    /** 숨김 — 사유 코드로만(낮은 별점은 사유에 없다). 기타는 메모 필수 5~200자, 그 밖은 메모 선택 200자까지. 열린 신고 처리. 감사 */
    @Transactional
    public FeedbackView.ReviewDetail hide(String reviewId, String reasonCode, String memo, long operatorId, String ip) {
        long id = parse(reviewId, FeedbackException::reviewNotFound);
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        if (reasonCode == null || !HIDE_REASONS.contains(reasonCode)) {
            fields.put("reasonCode", new MessageRef("groupbuy.review.hide.reason"));
        }
        String m = memo == null ? null : memo.strip();
        if (m != null && m.isEmpty()) {
            m = null;
        }
        int n = m == null ? 0 : m.codePointCount(0, m.length());
        if (n > 200 || ("OTHER".equals(reasonCode) && n < 5)) {
            fields.put("memo", new MessageRef("groupbuy.review.hide.memo", 5, 200));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        lockLive(id);
        store.hide(id, reasonCode, m, operatorId, now());
        int resolved = reports.resolve(GroupBuyUgcReportService.REVIEW, id, operatorId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("action", "HIDE");
        detail.put("reasonCode", reasonCode);
        if (m != null) {
            detail.put("memo", m);
        }
        detail.put("resolvedReports", resolved);
        audit.record(operatorId, AuditActions.GROUPBUY_REVIEW_HIDE, GroupBuyAudit.TARGET_REVIEW, id, ip, detail);
        return detail(id);
    }

    @Transactional
    public FeedbackView.ReviewDetail unhide(String reviewId, String reason, long operatorId, String ip) {
        long id = parse(reviewId, FeedbackException::reviewNotFound);
        String why = GroupBuyQnaService.reason(reason);
        lockLive(id);
        store.unhide(id);
        audit.record(operatorId, AuditActions.GROUPBUY_REVIEW_HIDE, GroupBuyAudit.TARGET_REVIEW, id, ip, Map.of("action", "UNHIDE", "reason", why));
        return detail(id);
    }

    private void lockLive(long id) {
        if (store.lock(id).orElseThrow(FeedbackException::reviewNotFound).deleted()) {
            throw FeedbackException.reviewDeleted();
        }
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    private GroupBuyRow visible(String groupBuyId) {
        long id = parse(groupBuyId, GroupBuyException::notFound);
        return groups.find(id).filter(r -> r.publishedAt() != null).orElseThrow(GroupBuyException::notFound);
    }

    private Clock clock() {
        return clocks.getIfUnique(Clock::systemUTC);
    }

    private Instant now() {
        return clock().instant();
    }
}
