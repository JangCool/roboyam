package com.community.hotdeal.entity;

import com.community.hotdeal.dto.DealValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 글에 붙은 딜 — V122 {@code post_deals}(글 하나에 하나, post_id PK, 외래 키 없음 — 글 표가 파티션이다). docs/features/hotdeal-contract.md.
 *
 * <ul>
 *   <li><b>저장 상태</b>는 {@link #ACTIVE}·{@link #ENDED} 둘. 「의심」({@code flaggedAt})·「기간 종료」(마감 또는 등록 + {@code autoExpireDays})는 읽을 때 계산한다({@link #view}).</li>
 *   <li>상태 전이는 도메인 메서드로만 한다 — 작성자 종료({@link #endByAuthor}) · 운영진 종료({@link #endByManager}) · 자동 종료({@link #autoEnd}) · 작성자 되돌리기({@link #reopenByAuthor},
 *       D2) · 운영진 재개({@link #reopenByManager}) · 의심({@link #flag}) · 「아직 진행 중」({@link #keep}). 2·3단계 서비스가 이 자리를 쓴다(검사·예외는 서비스 몫, 여기서는 상태만).</li>
 *   <li>클릭 수는 동시에 오르므로 이 엔티티로 고치지 않는다({@code updatable = false} — 원자적 UPDATE 만).</li>
 * </ul>
 */
@Entity
@Table(name = "post_deals")
@DynamicUpdate
public class PostDealEntity {

    public static final String ACTIVE = "ACTIVE";
    public static final String ENDED = "ENDED";

    public static final String BY_AUTHOR = "AUTHOR";
    public static final String BY_MANAGER = "MANAGER";
    public static final String BY_AUTO = "AUTO";

    /** 운영진 종료(MANAGER)를 한 사람 종류 — {@code ended_by_role}. 관리자 상세 {@code endedByRole} 은 여기에 작성자 종료 {@link #ROLE_AUTHOR} 를 더한다 */
    public static final String ROLE_OPERATOR = "OPERATOR";
    public static final String ROLE_BOARD_MANAGER = "BOARD_MANAGER";
    public static final String ROLE_BOARD_STAFF = "BOARD_STAFF";
    public static final String ROLE_AUTHOR = "AUTHOR";

    public static final String SOURCE_MEMBER = "MEMBER";

    public static final String STOCK_UNKNOWN = "UNKNOWN";
    public static final String STOCK_SOLD_OUT = "SOLD_OUT";

    public static final String IMAGE_OG = "OG";
    public static final String IMAGE_UPLOAD = "UPLOAD";

    /** 작성자 되돌리기 기한(D2 — 종료 뒤 24시간) */
    public static final Duration REOPEN_WINDOW = Duration.ofHours(24);

    @Id
    private Long postId;

    private Long boardId;
    private Long authorId;
    private String source;
    private String storeName;
    private String productUrl;
    private String productHost;
    private String finalHost;
    private String productUrlKey;
    private Long normalPrice;
    private Long dealPrice;
    private Long shippingFee;
    private String couponNote;
    private String priceBasisKind;
    private boolean dutyIncluded;
    private String priceBasis;
    private Instant endsAt;
    private String stockState;
    private boolean affiliate;
    private String imageSource;
    private Long ogImageId;
    private Long imageId;
    private String imageUrl;
    private String status;
    private String endedBy;
    private Long endedById;
    private String endedByRole;
    private Instant endedAt;
    private String endNote;
    private boolean reopenUsed;
    private Instant reopenedAt;
    private Instant flaggedAt;
    private Instant keptAt;
    private int keepCount;
    private int priceEditCount;
    private boolean linkChanged;
    private Instant lastEditedAt;
    @Column(updatable = false)
    private long clickCount;
    private Instant expiryNotifiedAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected PostDealEntity() {
    }

    /**
     * 새 딜(진행 중). 재고 {@code SOLD_OUT} 으로 올린 글은 처음부터 작성자 종료다(규칙 6).
     *
     * @param link 판정한 호스트·최종 호스트·같은 딜 키
     */
    public static PostDealEntity create(long postId, long boardId, Long authorId, DealValues v, Link link, Instant now) {
        PostDealEntity d = new PostDealEntity();
        d.postId = postId;
        d.boardId = boardId;
        d.authorId = authorId;
        d.source = SOURCE_MEMBER;
        d.assign(v, link);
        d.status = ACTIVE;
        d.createdAt = now;
        d.updatedAt = now;
        if (STOCK_SOLD_OUT.equals(v.stockState())) {
            d.endByAuthor(authorId, now);
        }
        return d;
    }

    /** 링크 판정 결과(저장 칸) */
    public record Link(String productHost, String finalHost, String productUrlKey) {
    }

    /**
     * 작성자 고치기 — 딜 칸을 바꾼다. 「가격 수정 N회」·「링크 바뀜」 셈은 부르는 쪽이 판정해 넘긴다(D3). 재고를 {@code SOLD_OUT} 으로 바꾸면 작성자 종료(규칙 6).
     *
     * @param priceEdit   딜가·정상가가 바뀐 수정인가(배송비 제외)
     * @param hostChanged 링크 호스트가 바뀐 수정인가
     */
    public void edit(DealValues v, Link link, boolean priceEdit, boolean hostChanged, Instant now) {
        boolean soldOutNow = STOCK_SOLD_OUT.equals(v.stockState()) && !STOCK_SOLD_OUT.equals(stockState);
        assign(v, link);
        if (priceEdit) {
            priceEditCount++;
        }
        if (hostChanged) {
            linkChanged = true;
        }
        lastEditedAt = now;
        updatedAt = now;
        if (soldOutNow && ACTIVE.equals(status)) {
            endByAuthor(authorId, now);
        }
    }

    private void assign(DealValues v, Link link) {
        storeName = v.storeName();
        productUrl = v.productUrl();
        productHost = link.productHost();
        finalHost = link.finalHost();
        productUrlKey = link.productUrlKey();
        normalPrice = v.normalPrice();
        dealPrice = v.dealPrice();
        shippingFee = v.shippingFee();
        couponNote = v.couponNote();
        priceBasisKind = v.priceBasisKind();
        dutyIncluded = v.dutyIncluded();
        priceBasis = v.priceBasis();
        endsAt = v.endsAt();
        stockState = v.stockState();
        affiliate = v.affiliate();
        imageSource = v.imageSource();
        ogImageId = v.ogImageId();
        imageId = v.imageId();
        imageUrl = v.imageUrl();
    }

    // ── 상태 전이(2·3단계가 쓴다 — 검사는 서비스, 여기서는 상태만) ──────────────────

    /** 작성자 종료(ACTIVE·FLAGGED → ENDED, AUTHOR) — 탈퇴 작성자의 진행 중 딜도 이 길(규칙 6) */
    public void endByAuthor(Long userId, Instant now) {
        end(BY_AUTHOR, userId, null, now);
    }

    /**
     * 운영진 종료(게시판 매니저·스텝·{@code hotdeal.manage}) — 사유는 작성자에게 가는 글(≤200자, 없을 수 있다)
     *
     * @param role {@link #ROLE_OPERATOR} · {@link #ROLE_BOARD_MANAGER} · {@link #ROLE_BOARD_STAFF}
     */
    public void endByManager(Long actorId, String role, String note, Instant now) {
        if (!ROLE_OPERATOR.equals(role) && !ROLE_BOARD_MANAGER.equals(role) && !ROLE_BOARD_STAFF.equals(role)) {
            throw new IllegalArgumentException("운영진 종류: " + role);
        }
        end(BY_MANAGER, actorId, note, now);
        endedByRole = role;
    }

    /** 자동 종료(의심 유예가 지남 — 배치) */
    public void autoEnd(Instant now) {
        end(BY_AUTO, null, null, now);
    }

    private void end(String by, Long actorId, String note, Instant now) {
        status = ENDED;
        endedBy = by;
        endedById = actorId;
        endedByRole = null;
        endedAt = now;
        endNote = note;
        updatedAt = now;
    }

    /** 작성자 되돌리기를 할 수 있는가(D2) — AUTHOR 종료 · 아직 안 씀 · 종료 뒤 24시간 안 */
    public boolean canReopenByAuthor(Instant now) {
        return ENDED.equals(status) && BY_AUTHOR.equals(endedBy) && !reopenUsed && endedAt != null && now.isBefore(endedAt.plus(REOPEN_WINDOW));
    }

    /** 작성자 되돌리기 기한 — 할 수 없으면 null(응답 {@code reopenUntil}) */
    public Instant reopenUntil(Instant now) {
        return canReopenByAuthor(now) ? endedAt.plus(REOPEN_WINDOW) : null;
    }

    /** 작성자 되돌리기(D2) — 다시 진행 중, 한 번만. 재고가 품절이었으면 「모름」으로 */
    public void reopenByAuthor(Instant now) {
        reopen(now);
        reopenUsed = true;
    }

    /** 운영진 재개 — 의심·keep 을 처음으로 돌린다(종료 요청 줄 리셋은 서비스가 표에서) */
    public void reopenByManager(Instant now) {
        reopen(now);
        flaggedAt = null;
        keptAt = null;
        keepCount = 0;
    }

    private void reopen(Instant now) {
        status = ACTIVE;
        endedBy = null;
        endedById = null;
        endedByRole = null;
        endedAt = null;
        endNote = null;
        reopenedAt = now;
        if (STOCK_SOLD_OUT.equals(stockState)) {
            stockState = STOCK_UNKNOWN;
        }
        updatedAt = now;
    }

    /** 종료 요청이 문턱에 닿음 → 의심 */
    public void flag(Instant now) {
        flaggedAt = now;
        updatedAt = now;
    }

    /** 작성자 「아직 진행 중」 — 의심을 풀고 셈을 하나 올린다 */
    public void keep(Instant now) {
        flaggedAt = null;
        keptAt = now;
        keepCount++;
        updatedAt = now;
    }

    /** 기간 종료 알림을 한 번 보냈다(2단계 배치) */
    public void markExpiryNotified(Instant now) {
        expiryNotifiedAt = now;
    }

    // ── 읽기 ────────────────────────────────────────────────────────────────

    public boolean isEnded() {
        return ENDED.equals(status);
    }

    /** 기간 종료로 바뀌는 시각 — 마감이 있으면 그 마감, 없으면 등록 + {@code autoExpireDays}(0 이면 없음 → null) */
    public Instant expiresAt(int autoExpireDays) {
        return DealViews.expiresAt(endsAt, createdAt, autoExpireDays);
    }

    /** 읽을 때 계산한 상태 — ENDED > EXPIRED > FLAGGED > ACTIVE */
    public DealView view(int autoExpireDays, Instant now) {
        return DealViews.view(status, flaggedAt, endsAt, createdAt, autoExpireDays, now);
    }

    public Long getPostId() {
        return postId;
    }

    public Long getBoardId() {
        return boardId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getSource() {
        return source;
    }

    public String getStoreName() {
        return storeName;
    }

    public String getProductUrl() {
        return productUrl;
    }

    public String getProductHost() {
        return productHost;
    }

    public String getFinalHost() {
        return finalHost;
    }

    public String getProductUrlKey() {
        return productUrlKey;
    }

    public Long getNormalPrice() {
        return normalPrice;
    }

    public Long getDealPrice() {
        return dealPrice;
    }

    public Long getShippingFee() {
        return shippingFee;
    }

    public String getCouponNote() {
        return couponNote;
    }

    public String getPriceBasisKind() {
        return priceBasisKind;
    }

    public boolean isDutyIncluded() {
        return dutyIncluded;
    }

    public String getPriceBasis() {
        return priceBasis;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public String getStockState() {
        return stockState;
    }

    public boolean isAffiliate() {
        return affiliate;
    }

    public String getImageSource() {
        return imageSource;
    }

    public Long getOgImageId() {
        return ogImageId;
    }

    public Long getImageId() {
        return imageId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getStatus() {
        return status;
    }

    public String getEndedBy() {
        return endedBy;
    }

    public Long getEndedById() {
        return endedById;
    }

    /** 운영진 종료를 한 사람 종류(운영진 종료가 아니면 null) */
    public String getEndedByRole() {
        return endedByRole;
    }

    /**
     * 종료한 사람 종류(관리자 상세 {@code endedByRole}) — 작성자 종료 {@link #ROLE_AUTHOR} · 운영진 종료는 저장한 종류 · 자동 종료·진행 중은 null
     */
    public String endedByRole() {
        if (!isEnded()) {
            return null;
        }
        if (BY_AUTHOR.equals(endedBy)) {
            return ROLE_AUTHOR;
        }
        return BY_MANAGER.equals(endedBy) ? endedByRole : null;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public String getEndNote() {
        return endNote;
    }

    public boolean isReopenUsed() {
        return reopenUsed;
    }

    public Instant getReopenedAt() {
        return reopenedAt;
    }

    public Instant getFlaggedAt() {
        return flaggedAt;
    }

    public Instant getKeptAt() {
        return keptAt;
    }

    public int getKeepCount() {
        return keepCount;
    }

    public int getPriceEditCount() {
        return priceEditCount;
    }

    public boolean isLinkChanged() {
        return linkChanged;
    }

    public Instant getLastEditedAt() {
        return lastEditedAt;
    }

    public long getClickCount() {
        return clickCount;
    }

    public Instant getExpiryNotifiedAt() {
        return expiryNotifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
