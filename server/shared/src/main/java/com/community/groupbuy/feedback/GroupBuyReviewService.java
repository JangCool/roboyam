package com.community.groupbuy.feedback;

import static com.community.groupbuy.feedback.GroupBuyQnaService.instant;
import static com.community.groupbuy.feedback.GroupBuyQnaService.nz;
import static com.community.groupbuy.feedback.GroupBuyQnaService.operator;
import static com.community.groupbuy.feedback.GroupBuyQnaService.parse;
import static com.community.groupbuy.feedback.GroupBuyQnaService.parseOrNull;
import static com.community.groupbuy.feedback.GroupBuyQnaService.preview;

import com.community.audit.dto.AuditActions;
import com.community.commerce.image.GroupBuyImageUploader;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyMemberStore;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.service.GroupBuyAudit;
import com.community.groupbuy.service.GroupBuyNotifications;
import com.community.payment.dto.response.PaymentMemberView;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공구 리뷰(2b-6 — 계약 「리뷰 규칙」, 사용자 확정 2026-10-29).
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

    private final JdbcTemplate jdbc;
    private final GroupBuyStore groups;
    private final GroupBuyMemberStore members;
    private final GroupBuyUgcReportService reports;
    private final GroupBuyNotifications notifications;
    private final GroupBuyAudit audit;
    private final GroupBuyImageUploader images;
    private final SnowflakeIdGenerator ids;
    private final ObjectProvider<Clock> clocks;

    public GroupBuyReviewService(DataSource dataSource, GroupBuyStore groups, GroupBuyMemberStore members, GroupBuyUgcReportService reports,
                                 GroupBuyNotifications notifications, GroupBuyAudit audit, GroupBuyImageUploader images, SnowflakeIdGenerator ids,
                                 ObjectProvider<Clock> clocks) {
        this.jdbc = new JdbcTemplate(dataSource);
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

    private record Row(long id, long userId, String nickname, int rating, String body, String skuName, int roundNo, long groupBuyId, String title, String orderNo,
                       Instant createdAt, Instant updatedAt, String hiddenCode, String hiddenMemo, String reply, Instant repliedAt, boolean replyEdited) {
    }

    private static final String MEMBER_SELECT = "SELECT r.id, r.user_id, CASE WHEN u.id IS NULL OR u.status = 'WITHDRAWN' OR u.deleted_at IS NOT NULL THEN NULL ELSE pr.nickname END, "
            + "r.rating, r.body, r.sku_name, g.round_no, r.group_buy_id, COALESCE(NULLIF(g.title, ''), p.name), pa.order_no, r.created_at, r.updated_at, r.hidden_reason_code, "
            + "r.hidden_memo, rp.body, rp.replied_at, rp.updated_at IS NOT NULL FROM group_buy_reviews r JOIN group_buys g ON g.id = r.group_buy_id "
            + "JOIN group_buy_products p ON p.id = r.product_id JOIN group_buy_participations pa ON pa.id = r.participation_id LEFT JOIN users u ON u.id = r.user_id "
            + "LEFT JOIN profiles pr ON pr.user_id = r.user_id LEFT JOIN group_buy_review_replies rp ON rp.review_id = r.id ";

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4), rs.getString(5), rs.getString(6), rs.getInt(7), rs.getLong(8), rs.getString(9),
                rs.getString(10), instant(rs, 11), instant(rs, 12), rs.getString(13), rs.getString(14), rs.getString(15), instant(rs, 16), rs.getBoolean(17));
    }

    /** 공구 상세의 리뷰 — 같은 상품의 모든 차수. 숨긴 리뷰는 작성자에게만 */
    @Transactional(readOnly = true)
    public FeedbackView.ReviewPage list(String groupBuyId, Long viewerId, String sort, boolean photoOnly, int page) {
        GroupBuyRow g = visible(groupBuyId);
        String s = sort == null || sort.isBlank() ? "LATEST" : sort;
        if (!SORTS.contains(s)) {
            throw ValidationException.of("sort", new MessageRef("groupbuy.review.sort.invalid"));
        }
        int p = Math.max(0, page);
        String photo = "EXISTS (SELECT 1 FROM group_buy_review_photos ph WHERE ph.review_id = r.id)";
        StringBuilder where = new StringBuilder("WHERE r.product_id = ? AND r.deleted_at IS NULL AND (r.hidden_at IS NULL OR r.user_id = ?)");
        if (photoOnly) {
            where.append(" AND ").append(photo);
        }
        long viewer = viewerId == null ? -1 : viewerId;
        long total = jdbc.queryForObject("SELECT count(*) FROM group_buy_reviews r " + where, Long.class, g.productId(), viewer);
        String order = switch (s) {
            case "RATING_HIGH" -> " ORDER BY r.rating DESC, r.created_at DESC, r.id DESC";
            case "RATING_LOW" -> " ORDER BY r.rating, r.created_at DESC, r.id DESC";
            case "PHOTO" -> " ORDER BY " + photo + " DESC, r.created_at DESC, r.id DESC";
            default -> " ORDER BY r.created_at DESC, r.id DESC";
        };
        List<Row> rows = jdbc.query(MEMBER_SELECT + where + order + " LIMIT ? OFFSET ?", (rs, i) -> row(rs), g.productId(), viewer, PAGE_SIZE, (long) p * PAGE_SIZE);
        Map<Long, List<FeedbackView.Photo>> photos = photosOf(rows.stream().map(Row::id).toList());
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
        jdbc.query("SELECT rating, count(*) FROM group_buy_reviews WHERE product_id = ? AND deleted_at IS NULL AND hidden_at IS NULL GROUP BY rating",
                rs -> {
                    dist.put(Integer.toString(rs.getInt(1)), rs.getLong(2));
                }, productId);
        long count = dist.values().stream().mapToLong(Long::longValue).sum();
        long sum = dist.entrySet().stream().mapToLong(e -> Integer.parseInt(e.getKey()) * e.getValue()).sum();
        Double average = count == 0 ? null : Math.round(sum * 100.0 / count) / 100.0;
        long photoCount = jdbc.queryForObject("SELECT count(*) FROM group_buy_reviews r WHERE r.product_id = ? AND r.deleted_at IS NULL AND r.hidden_at IS NULL "
                + "AND EXISTS (SELECT 1 FROM group_buy_review_photos ph WHERE ph.review_id = r.id)", Long.class, productId);
        List<FeedbackView.SummaryPhoto> strip = jdbc.query("SELECT ph.review_id, ph.image_key FROM group_buy_review_photos ph JOIN group_buy_reviews r ON r.id = ph.review_id "
                + "WHERE r.product_id = ? AND r.deleted_at IS NULL AND r.hidden_at IS NULL ORDER BY r.created_at DESC, ph.position LIMIT 12",
                (rs, i) -> new FeedbackView.SummaryPhoto(Long.toString(rs.getLong(1)), images.url(rs.getString(2))), productId);
        return new FeedbackView.Summary(average, count, dist, photoCount, strip);
    }

    /** 목록 카드의 평균(소수 첫째 자리)·수 — 상품마다(공구 목록이 한 번에 부른다) */
    @Transactional(readOnly = true)
    public Map<Long, FeedbackView.Stats> stats(List<Long> productIds) {
        Map<Long, FeedbackView.Stats> out = new HashMap<>();
        if (productIds.isEmpty()) {
            return out;
        }
        String in = String.join(",", productIds.stream().distinct().map(id -> "?").toList());
        jdbc.query("SELECT product_id, avg(rating), count(*) FROM group_buy_reviews WHERE product_id IN (" + in + ") AND deleted_at IS NULL AND hidden_at IS NULL "
                + "GROUP BY product_id", rs -> {
                    out.put(rs.getLong(1), new FeedbackView.Stats(Math.round(rs.getDouble(2) * 10) / 10.0, rs.getLong(3)));
                }, productIds.stream().distinct().toArray());
        return out;
    }

    /** 내 주문의 줄마다 리뷰 상태 — 쓸 수 있음 · 썼음 · 기한 지남 · 대상 아님 */
    @Transactional(readOnly = true)
    public List<FeedbackView.ReviewTarget> targets(long userId, String orderNo) {
        record Item(long itemId, int live, String status, Instant deliveredAt, Long reviewId) {
        }
        List<Item> items = jdbc.query("SELECT i.id, i.qty - i.canceled_qty - i.returned_qty, pa.status, s.delivered_at, r.id FROM group_buy_participations pa "
                + "JOIN group_buy_participation_items i ON i.participation_id = pa.id LEFT JOIN group_buy_shipments s ON s.participation_id = pa.id AND s.kind = 'ORIGINAL' "
                + "LEFT JOIN group_buy_reviews r ON r.item_id = i.id AND r.deleted_at IS NULL WHERE pa.order_no = ? AND pa.user_id = ? ORDER BY i.id",
                (rs, i) -> new Item(rs.getLong(1), rs.getInt(2), rs.getString(3), instant(rs, 4), (Long) rs.getObject(5)), orderNo, userId);
        if (items.isEmpty()) {
            throw GroupBuyException.orderNotFound();
        }
        Set<Long> deletedOnce = new LinkedHashSet<>(jdbc.queryForList("SELECT r.item_id FROM group_buy_reviews r JOIN group_buy_participations pa ON pa.id = r.participation_id "
                + "WHERE pa.order_no = ? AND pa.user_id = ? AND r.deleted_at IS NOT NULL", Long.class, orderNo, userId));
        LocalDate today = ServiceTime.today(clock());
        return items.stream().map(it -> {
            LocalDate until = it.deliveredAt() == null ? null : ServiceTime.dateOf(it.deliveredAt()).plusDays(WRITE_DAYS);
            if (it.reviewId() != null) {
                return new FeedbackView.ReviewTarget(Long.toString(it.itemId()), "WRITTEN", until, Long.toString(it.reviewId()));
            }
            if (!"DELIVERED".equals(it.status()) || until == null || it.live() <= 0 || deletedOnce.contains(it.itemId())) {
                return new FeedbackView.ReviewTarget(Long.toString(it.itemId()), "NONE", null, null);
            }
            return new FeedbackView.ReviewTarget(Long.toString(it.itemId()), today.isAfter(until) ? "EXPIRED" : "WRITABLE", until, null);
        }).toList();
    }

    /** 내 리뷰 — 새 것 먼저, 숨긴 것도(사유와 함께) */
    @Transactional(readOnly = true)
    public FeedbackView.MyReviewPage mine(long userId, int page) {
        int p = Math.max(0, page);
        long total = jdbc.queryForObject("SELECT count(*) FROM group_buy_reviews WHERE user_id = ? AND deleted_at IS NULL", Long.class, userId);
        List<Row> rows = jdbc.query(MEMBER_SELECT + "WHERE r.user_id = ? AND r.deleted_at IS NULL ORDER BY r.created_at DESC, r.id DESC LIMIT ? OFFSET ?", (rs, i) -> row(rs),
                userId, PAGE_SIZE, (long) p * PAGE_SIZE);
        Map<Long, List<FeedbackView.Photo>> photos = photosOf(rows.stream().map(Row::id).toList());
        Instant now = now();
        return new FeedbackView.MyReviewPage(rows.stream().map(r -> view(r, userId, photos.getOrDefault(r.id(), List.of()), now, true)).toList(), total, p,
                (long) (p + 1) * PAGE_SIZE < total);
    }

    static FeedbackView.Review view(Row r, Long viewerId, List<FeedbackView.Photo> photos, Instant now, boolean withOrder) {
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
        jdbc.update("INSERT INTO group_buy_review_photos (id, user_id, image_key, created_at) VALUES (?, ?, ?, ?)", id, userId, up.key(), Timestamp.from(now()));
        return new FeedbackView.Photo(Long.toString(id), up.url());
    }

    @Transactional
    public FeedbackView.Review create(long userId, String orderNo, String itemId, Integer rating, String body, List<String> photoIds) {
        Draft d = draft(rating, body, photoIds);
        requireWriter(userId);
        long item = parse(itemId, GroupBuyException::orderNotFound);
        record Line(long participationId, long groupBuyId, long productId, String skuName, int live, String status, Instant deliveredAt) {
        }
        Line line = jdbc.query("SELECT pa.id, pa.group_buy_id, g.product_id, s.name, i.qty - i.canceled_qty - i.returned_qty, pa.status, sh.delivered_at "
                + "FROM group_buy_participation_items i JOIN group_buy_participations pa ON pa.id = i.participation_id JOIN group_buys g ON g.id = pa.group_buy_id "
                + "JOIN group_buy_skus s ON s.id = i.sku_id LEFT JOIN group_buy_shipments sh ON sh.participation_id = pa.id AND sh.kind = 'ORIGINAL' "
                + "WHERE i.id = ? AND pa.order_no = ? AND pa.user_id = ? FOR UPDATE OF pa", (rs, i) -> new Line(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                        rs.getInt(5), rs.getString(6), instant(rs, 7)), item, orderNo, userId).stream().findFirst().orElseThrow(GroupBuyException::orderNotFound);
        LocalDate today = ServiceTime.today(clock());
        if (!"DELIVERED".equals(line.status()) || line.deliveredAt() == null || line.live() <= 0
                || today.isAfter(ServiceTime.dateOf(line.deliveredAt()).plusDays(WRITE_DAYS))) {
            throw FeedbackException.reviewNotWritable();
        }
        long id = ids.nextId();
        Instant now = now();
        try {
            jdbc.update("INSERT INTO group_buy_reviews (id, product_id, group_buy_id, participation_id, item_id, user_id, sku_name, rating, body, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, line.productId(), line.groupBuyId(), line.participationId(), item, userId, line.skuName(), d.rating(), d.body(),
                    Timestamp.from(now));
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
        record Cur(int rating, String body, Instant createdAt, Instant updatedAt) {
        }
        Cur c = jdbc.query("SELECT rating, body, created_at, updated_at FROM group_buy_reviews WHERE id = ? AND user_id = ? AND deleted_at IS NULL FOR UPDATE",
                (rs, i) -> new Cur(rs.getInt(1), rs.getString(2), instant(rs, 3), instant(rs, 4)), id, userId).stream().findFirst()
                .orElseThrow(FeedbackException::reviewNotFound);
        Instant now = now();
        if (!now.isBefore(c.createdAt().plus(Duration.ofDays(EDIT_DAYS)))) {
            throw FeedbackException.reviewEditExpired();
        }
        int photoCount = jdbc.queryForObject("SELECT count(*) FROM group_buy_review_photos WHERE review_id = ?", Integer.class, id);
        jdbc.update("INSERT INTO group_buy_review_revisions (id, review_id, rating, body, photo_count, written_at, replaced_at) VALUES (?, ?, ?, ?, ?, ?, ?)", ids.nextId(), id,
                c.rating(), c.body(), photoCount, Timestamp.from(c.updatedAt() == null ? c.createdAt() : c.updatedAt()), Timestamp.from(now));
        jdbc.update("UPDATE group_buy_reviews SET rating = ?, body = ?, updated_at = ? WHERE id = ?", d.rating(), d.body(), Timestamp.from(now), id);
        // 사진: 빠진 것은 떼어 정리 대상으로, 새로 붙인 것은 붙인다
        List<Long> keep = d.photoIds();
        jdbc.update("DELETE FROM group_buy_review_photos WHERE review_id = ?" + (keep.isEmpty() ? "" : " AND id NOT IN (" + placeholders(keep.size()) + ")"),
                concat(id, keep));
        attach(userId, id, keep, now);
        return myView(id, userId);
    }

    /** 지우기 — 같은 줄에 다시 쓸 수 없다(줄은 남는다) */
    @Transactional
    public void remove(long userId, String reviewId) {
        long id = parse(reviewId, FeedbackException::reviewNotFound);
        int n = jdbc.update("UPDATE group_buy_reviews SET deleted_at = ? WHERE id = ? AND user_id = ? AND deleted_at IS NULL", Timestamp.from(now()), id, userId);
        if (n == 0) {
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
            int n = jdbc.update("UPDATE group_buy_review_photos SET review_id = ?, attached_at = COALESCE(attached_at, ?), position = ? WHERE id = ? AND user_id = ? "
                    + "AND (review_id IS NULL OR review_id = ?)", reviewId, Timestamp.from(now), i, photoIds.get(i), userId, reviewId);
            if (n == 0) {
                throw ValidationException.of("photoIds", new MessageRef("groupbuy.review.photo.invalid"));
            }
        }
    }

    private FeedbackView.Review myView(long id, long userId) {
        Row r = jdbc.query(MEMBER_SELECT + "WHERE r.id = ?", (rs, i) -> row(rs), id).stream().findFirst().orElseThrow(FeedbackException::reviewNotFound);
        return view(r, userId, photosOf(List.of(id)).getOrDefault(id, List.of()), now(), true);
    }

    private Map<Long, List<FeedbackView.Photo>> photosOf(List<Long> reviewIds) {
        Map<Long, List<FeedbackView.Photo>> out = new HashMap<>();
        if (reviewIds.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT review_id, id, image_key FROM group_buy_review_photos WHERE review_id IN (" + placeholders(reviewIds.size()) + ") ORDER BY review_id, position, id",
                rs -> {
                    out.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new FeedbackView.Photo(Long.toString(rs.getLong(2)), images.url(rs.getString(3))));
                }, reviewIds.toArray());
        return out;
    }

    private void requireWriter(long userId) {
        if (members.state(userId).map(s -> s.suspended() || s.gone()).orElse(true)) {
            throw FeedbackException.restricted();
        }
    }

    // ── 운영자 ──────────────────────────────────────────────────────────────

    private static final String ADMIN_SELECT = "SELECT r.id, r.product_id, p.name, r.group_buy_id, COALESCE(NULLIF(g.title, ''), p.name), g.round_no, r.participation_id, pa.order_no, "
            + "r.sku_name, r.user_id, pr.nickname, (u.id IS NULL OR u.status = 'WITHDRAWN' OR u.deleted_at IS NOT NULL), r.rating, r.body, "
            + "(SELECT count(*) FROM group_buy_review_photos ph WHERE ph.review_id = r.id), r.hidden_at IS NOT NULL, r.deleted_at IS NOT NULL, "
            + "(SELECT count(*) FROM group_buy_ugc_reports x WHERE x.target_type = 'GROUP_BUY_REVIEW' AND x.target_id = r.id AND x.status = 'OPEN'), rp.review_id IS NOT NULL, "
            + "r.created_at, r.updated_at FROM group_buy_reviews r JOIN group_buys g ON g.id = r.group_buy_id JOIN group_buy_products p ON p.id = r.product_id "
            + "JOIN group_buy_participations pa ON pa.id = r.participation_id LEFT JOIN users u ON u.id = r.user_id LEFT JOIN profiles pr ON pr.user_id = r.user_id "
            + "LEFT JOIN group_buy_review_replies rp ON rp.review_id = r.id ";

    private record AdminRow(FeedbackView.ReviewRow row, String body) {
    }

    private static AdminRow adminRow(ResultSet rs) throws SQLException {
        boolean gone = rs.getBoolean(12);
        String body = rs.getString(14);
        FeedbackView.ReviewRow row = new FeedbackView.ReviewRow(Long.toString(rs.getLong(1)), new FeedbackView.ProductRef(Long.toString(rs.getLong(2)), rs.getString(3)),
                new FeedbackView.GroupBuyRef(Long.toString(rs.getLong(4)), rs.getString(5), rs.getInt(6)), Long.toString(rs.getLong(7)), rs.getString(8), rs.getString(9),
                new PaymentMemberView(gone ? null : Long.toString(rs.getLong(10)), gone ? "" : nz(rs.getString(11)), gone), rs.getInt(13), preview(body), rs.getInt(15),
                rs.getBoolean(16), rs.getBoolean(17), rs.getInt(18), rs.getBoolean(19), instant(rs, 20), instant(rs, 21));
        return new AdminRow(row, body);
    }

    /** 관리자 목록 — 탭 VISIBLE(기본)·REPORTED(공개 중 열린 신고)·HIDDEN·ALL(지운 것 포함) · 별점 · 사진 있음 · 답글 없음 · 찾기. 새 것 먼저 */
    @Transactional(readOnly = true)
    public FeedbackView.ReviewList adminList(String tab, Integer rating, boolean photo, boolean noReply, String q, int page, int size) {
        String t = tab == null || tab.isBlank() ? "VISIBLE" : tab;
        String reported = "EXISTS (SELECT 1 FROM group_buy_ugc_reports x WHERE x.target_type = 'GROUP_BUY_REVIEW' AND x.target_id = r.id AND x.status = 'OPEN')";
        StringBuilder where = new StringBuilder("WHERE TRUE");
        List<Object> args = new ArrayList<>();
        switch (t) {
            case "VISIBLE" -> where.append(" AND r.hidden_at IS NULL AND r.deleted_at IS NULL");
            case "REPORTED" -> where.append(" AND r.hidden_at IS NULL AND r.deleted_at IS NULL AND ").append(reported);
            case "HIDDEN" -> where.append(" AND r.hidden_at IS NOT NULL AND r.deleted_at IS NULL");
            case "ALL" -> {
            }
            default -> throw ValidationException.of("tab", new MessageRef("groupbuy.review.tab.invalid"));
        }
        if (rating != null) {
            where.append(" AND r.rating = ?");
            args.add(rating);
        }
        if (photo) {
            where.append(" AND EXISTS (SELECT 1 FROM group_buy_review_photos ph WHERE ph.review_id = r.id)");
        }
        if (noReply) {
            where.append(" AND rp.review_id IS NULL");
        }
        String needle = q == null ? "" : q.trim();
        if (!needle.isEmpty()) {
            Long n = parseOrNull(needle);
            String like = "%" + needle.replace("%", "\\%").replace("_", "\\_") + "%";
            where.append(" AND (p.name ILIKE ? OR g.title ILIKE ? OR pr.nickname ILIKE ? OR pa.order_no ILIKE ?").append(n != null ? " OR r.user_id = ?" : "").append(")");
            args.addAll(List.of(like, like, like, like));
            if (n != null) {
                args.add(n);
            }
        }
        int s = Math.min(Math.max(size, 1), 100);
        int p = Math.max(0, page);
        long total = jdbc.queryForObject("SELECT count(*) FROM group_buy_reviews r JOIN group_buys g ON g.id = r.group_buy_id JOIN group_buy_products p ON p.id = r.product_id "
                + "JOIN group_buy_participations pa ON pa.id = r.participation_id LEFT JOIN profiles pr ON pr.user_id = r.user_id "
                + "LEFT JOIN group_buy_review_replies rp ON rp.review_id = r.id " + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(s);
        pageArgs.add((long) p * s);
        List<FeedbackView.ReviewRow> items = jdbc.query(ADMIN_SELECT + where + " ORDER BY r.created_at DESC, r.id DESC LIMIT ? OFFSET ?", (rs, i) -> adminRow(rs).row(),
                pageArgs.toArray());
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("VISIBLE", jdbc.queryForObject("SELECT count(*) FROM group_buy_reviews r WHERE r.hidden_at IS NULL AND r.deleted_at IS NULL", Long.class));
        counts.put("REPORTED", jdbc.queryForObject("SELECT count(*) FROM group_buy_reviews r WHERE r.hidden_at IS NULL AND r.deleted_at IS NULL AND " + reported, Long.class));
        counts.put("HIDDEN", jdbc.queryForObject("SELECT count(*) FROM group_buy_reviews r WHERE r.hidden_at IS NOT NULL AND r.deleted_at IS NULL", Long.class));
        return new FeedbackView.ReviewList(items, total, counts);
    }

    @Transactional(readOnly = true)
    public FeedbackView.ReviewDetail adminDetail(String reviewId) {
        return detail(parse(reviewId, FeedbackException::reviewNotFound));
    }

    private FeedbackView.ReviewDetail detail(long id) {
        AdminRow a = jdbc.query(ADMIN_SELECT + "WHERE r.id = ?", (rs, i) -> adminRow(rs), id).stream().findFirst().orElseThrow(FeedbackException::reviewNotFound);
        record Extra(Instant deletedAt, String code, String memo, Instant hiddenAt, Long hiddenBy, String hiddenByName) {
        }
        Extra x = jdbc.queryForObject("SELECT r.deleted_at, r.hidden_reason_code, r.hidden_memo, r.hidden_at, r.hidden_by, o.operator_name FROM group_buy_reviews r "
                + "LEFT JOIN operators o ON o.id = r.hidden_by WHERE r.id = ?", (rs, i) -> new Extra(instant(rs, 1), rs.getString(2), rs.getString(3), instant(rs, 4),
                        (Long) rs.getObject(5), rs.getString(6)), id);
        List<FeedbackView.ReviewRevision> revisions = jdbc.query("SELECT id, rating, body, photo_count, written_at, replaced_at FROM group_buy_review_revisions "
                + "WHERE review_id = ? ORDER BY replaced_at DESC, id DESC", (rs, i) -> new FeedbackView.ReviewRevision(Long.toString(rs.getLong(1)), rs.getInt(2), rs.getString(3),
                        rs.getInt(4), instant(rs, 5), instant(rs, 6)), id);
        FeedbackView.AdminAnswer reply = jdbc.query("SELECT rp.body, rp.replied_at, rp.replied_by, o1.operator_name, rp.updated_at, rp.updated_by, o2.operator_name "
                + "FROM group_buy_review_replies rp LEFT JOIN operators o1 ON o1.id = rp.replied_by LEFT JOIN operators o2 ON o2.id = rp.updated_by WHERE rp.review_id = ?",
                (rs, i) -> new FeedbackView.AdminAnswer(rs.getString(1), instant(rs, 2), operator(rs.getLong(3), rs.getString(4)), instant(rs, 5),
                        rs.getObject(6) == null ? null : operator(rs.getLong(6), rs.getString(7))), id).stream().findFirst().orElse(null);
        FeedbackView.ReviewHiddenInfo hidden = x.hiddenAt() == null ? null : new FeedbackView.ReviewHiddenInfo(x.code(), x.memo(), x.hiddenAt(), operator(x.hiddenBy(),
                x.hiddenByName()));
        FeedbackView.ReviewRow r = a.row();
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
        record R(long userId, long groupBuyId, boolean deleted) {
        }
        R r = jdbc.query("SELECT user_id, group_buy_id, deleted_at IS NOT NULL FROM group_buy_reviews WHERE id = ? FOR UPDATE", (rs, i) -> new R(rs.getLong(1), rs.getLong(2),
                rs.getBoolean(3)), id).stream().findFirst().orElseThrow(FeedbackException::reviewNotFound);
        if (r.deleted()) {
            throw FeedbackException.reviewDeleted();
        }
        Instant now = now();
        Optional<String> old = jdbc.queryForList("SELECT body FROM group_buy_review_replies WHERE review_id = ?", String.class, id).stream().findFirst();
        if (old.isEmpty()) {
            jdbc.update("INSERT INTO group_buy_review_replies (review_id, body, replied_by, replied_at) VALUES (?, ?, ?, ?)", id, text, operatorId, Timestamp.from(now));
            notifications.reviewReplied(r.userId(), r.groupBuyId());
        } else if (!old.get().equals(text)) {
            jdbc.update("UPDATE group_buy_review_replies SET body = ?, updated_by = ?, updated_at = ? WHERE review_id = ?", text, operatorId, Timestamp.from(now), id);
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
        jdbc.update("UPDATE group_buy_reviews SET hidden_reason_code = ?, hidden_memo = ?, hidden_at = ?, hidden_by = ? WHERE id = ?", reasonCode, m, Timestamp.from(now()),
                operatorId, id);
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
        jdbc.update("UPDATE group_buy_reviews SET hidden_reason_code = NULL, hidden_memo = NULL, hidden_at = NULL, hidden_by = NULL WHERE id = ?", id);
        audit.record(operatorId, AuditActions.GROUPBUY_REVIEW_HIDE, GroupBuyAudit.TARGET_REVIEW, id, ip, Map.of("action", "UNHIDE", "reason", why));
        return detail(id);
    }

    private void lockLive(long id) {
        Boolean deleted = jdbc.query("SELECT deleted_at IS NOT NULL FROM group_buy_reviews WHERE id = ? FOR UPDATE", (rs, i) -> rs.getBoolean(1), id).stream().findFirst()
                .orElseThrow(FeedbackException::reviewNotFound);
        if (deleted) {
            throw FeedbackException.reviewDeleted();
        }
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    private GroupBuyRow visible(String groupBuyId) {
        long id = parse(groupBuyId, GroupBuyException::notFound);
        return groups.find(id).filter(r -> r.publishedAt() != null).orElseThrow(GroupBuyException::notFound);
    }

    private static String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    private static Object[] concat(long first, List<Long> rest) {
        List<Object> out = new ArrayList<>();
        out.add(first);
        out.addAll(rest);
        return out.toArray();
    }

    private Clock clock() {
        return clocks.getIfUnique(Clock::systemUTC);
    }

    private Instant now() {
        return clock().instant();
    }
}
