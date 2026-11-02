package com.community.bizapplication.service;

import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizEventAction;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.BizTakedownCause;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.CreativeRow;
import com.community.bizapplication.repository.master.BizDealPostStore;
import com.community.bizapplication.repository.master.BizDealPostStore.PostRow;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.hotdeal.dto.DealValues;
import com.community.hotdeal.entity.PostDealEntity;
import com.community.hotdeal.repository.master.PostDealRepository;
import com.community.hotdeal.service.DealHosts;
import com.community.hotdeal.service.DealLinkJudge.ChainVerdict;
import com.community.hotdeal.service.DealRuleBook;
import com.community.hotdeal.service.DealUrlKey;
import com.community.post.entity.ContentFormat;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사업자 핫딜 글 게시(biz-hotdeal-contract 규칙 1~4 — {@link BizHotdealPosting} 구현). 고리 메서드는 신청 건 처리 트랜잭션 안(그 건 행을 잠근 뒤)에서 불리고, 배치는
 * {@link #publishDue} 가 건마다 새 트랜잭션에서 행을 잠근 뒤 같은 판정을 한다 — 결제 확정과 배치가 겹쳐도 신청 건 행 잠금 + 게시 결과 PK 로 한 번만 만든다.
 *
 * <ul>
 *   <li><b>게시</b>: 예약 · 시작 ≤ 지금 &lt; 끝 · 게시 결과 없음 → 게시 직전 재판정(사업자 승인 · 핫딜 서비스 줄 승인 · 게시판 있음·켬·핫딜 종류 · 지금 링크 규칙 — 말머리가 없어졌거나 꺼졌으면 실패가 아니라 말머리 없이 올리고
 *       {@code POSTED_WITHOUT_PREFIX} + 운영자 알림 {@code HOTDEAL_POST_PREFIX_DROPPED}) → 글(작성자 = 사업자 회원, 평문,
 *       글 시각 = 게시 시각) + 딜({@code source = BUSINESS}, 마감 = 신청 기간 끝, 상호 스냅샷) + 처리 기록 {@code POSTED} + 사업자 알림. 하나라도 아니면 글 없이
 *       {@code POST_FAILED}(사유 코드) + 운영자 알림 {@code HOTDEAL_POST_FAILED}(까닭) + 사업자 알림 — 다시 시도하지 않는다(운영자 취소·전액 환불). 단 서비스 줄 정지
 *       ({@code SERVICE_SUSPENDED})는 줄이 다시 승인되면 배치가 다시 판정해 올린다(결정 10).</li>
 *   <li><b>숨김</b>({@link #onClosed}): 보이는 글만 운영자 숨김 까닭으로 숨기고 그 시각을 게시 결과에 남긴다 + {@code POST_HIDDEN}. 이미 숨겨진 글은 건드리지 않는다.</li>
 *   <li><b>되돌림</b>({@link #onRestored}): 글의 숨김이 이 흐름이 남긴 시각 그대로일 때만 풀고 {@code POST_RESTORED} — 운영자가 따로 숨긴 글은 그대로. 딜 마감을 (연장된) 신청 기간 끝에
 *       맞춘다. 게시 전에 내렸다가 시작 뒤 되돌렸으면 그때 게시한다.</li>
 * </ul>
 */
@Component
public class BizHotdealPublisher implements BizHotdealPosting {

    /** 게시 실패 사유 코드 */
    public static final String BOARD_CLOSED = "BOARD_CLOSED";
    public static final String LINK_BLOCKED = "LINK_BLOCKED";
    public static final String BUSINESS_SUSPENDED = "BUSINESS_SUSPENDED";
    public static final String SERVICE_SUSPENDED = BizDealPostStore.SERVICE_SUSPENDED;
    public static final String OTHER = "OTHER";
    /** {@code POSTED_WITHOUT_PREFIX} 의 note — 말머리 상단 노출이 걸려 있어 그 노출이 나오지 않는다 */
    public static final String CATEGORY_TOP_OFF = "CATEGORY_TOP_OFF";

    /** 게시 한 건의 결과 */
    public enum Outcome { POSTED, FAILED, SKIPPED }

    private final BizApplicationStore store;
    private final BizDealPostStore results;
    private final BizApplicationOps ops;
    private final PostRepository posts;
    private final PostDealRepository deals;
    private final BoardCatalog catalog;
    private final DealRuleBook rules;
    private final TransactionTemplate tx;

    public BizHotdealPublisher(BizApplicationStore store, BizDealPostStore results, BizApplicationOps ops, PostRepository posts, PostDealRepository deals,
                               BoardCatalog catalog, DealRuleBook rules, PlatformTransactionManager transactions) {
        this.store = store;
        this.results = results;
        this.ops = ops;
        this.posts = posts;
        this.deals = deals;
        this.catalog = catalog;
        this.rules = rules;
        this.tx = new TransactionTemplate(transactions);
    }

    // ── 고리 ───────────────────────────────────────────────────────────────

    /** 결제 완료·입금 확인 순간 — 시작이 이미 지난 건만 바로 게시(아니면 배치 몫) */
    @Override
    public void onScheduled(long applicationId) {
        store.find(applicationId).ifPresent(app -> publish(app, ops.now()));
    }

    @Override
    public void onClosed(long applicationId) {
        Optional<PostRow> row = results.find(applicationId).filter(PostRow::posted);
        if (row.isEmpty()) {
            return;
        }
        Instant now = ops.now();
        PostEntity post = posts.findWithLockById(row.get().postId()).orElse(null);
        if (post == null || post.isGoneAt(now) || post.isHiddenAt(now)) {
            return; // 이미 지웠거나 다른 까닭으로 숨겨져 있다 — 그 숨김은 이 흐름의 것이 아니다
        }
        post.staffHide(now);
        posts.flush();
        results.setHidden(applicationId, now, now);
        ops.event(applicationId, BizEventAction.POST_HIDDEN, BizActorType.SYSTEM, null, null, now);
    }

    @Override
    public void onSellerHidden(long applicationId, String reason) {
        Optional<PostRow> row = results.find(applicationId).filter(PostRow::posted);
        if (row.isEmpty()) {
            return;
        }
        Instant now = ops.now();
        PostEntity post = posts.findWithLockById(row.get().postId()).orElse(null);
        if (post == null || post.isGoneAt(now)) {
            return;
        }
        boolean ours = post.sellerHiddenAt(now) && row.get().hiddenAt() != null && row.get().hiddenAt().equals(post.getHiddenAt());
        if (post.isHiddenAt(now) && !ours) {
            return; // 운영자·신고로 숨겨져 있다 — 그 숨김은 이 흐름의 것이 아니다
        }
        if (ours && reason.equals(post.getHiddenReason())) {
            return;
        }
        post.sellerHide(reason, now);
        posts.flush();
        results.setHidden(applicationId, now, now);
        String cause = PostEntity.SELLER_PAUSED.equals(reason) ? BizTakedownCause.ADVERTISER_PAUSE.name() : BizTakedownCause.ADVERTISER_STOP.name();
        ops.event(applicationId, BizEventAction.POST_HIDDEN, BizActorType.SYSTEM, null, cause, now);
    }

    @Override
    public void onSellerShown(long applicationId) {
        Optional<PostRow> row = results.find(applicationId).filter(PostRow::posted);
        if (row.isEmpty()) {
            return;
        }
        Instant now = ops.now();
        PostEntity post = posts.findWithLockById(row.get().postId()).orElse(null);
        Instant ours = row.get().hiddenAt();
        if (post != null && ours != null && PostEntity.SELLER_PAUSED.equals(post.getHiddenReason()) && ours.equals(post.getHiddenAt()) && !post.isGoneAt(now)) {
            post.unhide(now);
            posts.flush();
            ops.event(applicationId, BizEventAction.POST_RESTORED, BizActorType.SYSTEM, null, BizTakedownCause.ADVERTISER_PAUSE.name(), now);
        }
        results.setHidden(applicationId, null, now);
    }

    @Override
    public void onRestored(long applicationId) {
        AppRow app = store.find(applicationId).orElse(null);
        if (app == null) {
            return;
        }
        Instant now = ops.now();
        Optional<PostRow> row = results.find(applicationId);
        if (row.isEmpty()) {
            publish(app, now); // 게시 전에 내렸다가 시작 뒤에 되돌렸다
            return;
        }
        if (!row.get().posted()) {
            return;
        }
        deals.findWithLockByPostId(row.get().postId()).ifPresent(d -> d.alignEndsAt(app.endAt(), now));
        PostEntity post = posts.findWithLockById(row.get().postId()).orElse(null);
        Instant ours = row.get().hiddenAt();
        if (post != null && ours != null && PostEntity.STAFF_HIDDEN.equals(post.getHiddenReason()) && ours.equals(post.getHiddenAt()) && !post.isGoneAt(now)) {
            post.unhide(now);
            ops.event(applicationId, BizEventAction.POST_RESTORED, BizActorType.SYSTEM, null, null, now);
        }
        posts.flush();
        deals.flush();
        results.setHidden(applicationId, null, now);
    }

    // ── 배치 ───────────────────────────────────────────────────────────────

    /** 배치 한 건 — 새 트랜잭션에서 신청 건 행을 잠그고 다시 판정한다 */
    public Outcome publishDue(long applicationId) {
        return tx.execute(status -> store.lock(applicationId).map(app -> publish(app, ops.now())).orElse(Outcome.SKIPPED));
    }

    // ── 게시 ───────────────────────────────────────────────────────────────

    /** 부르는 쪽이 신청 건 행을 잠근 뒤 */
    Outcome publish(AppRow app, Instant now) {
        if (app.kind() != BizApplicationKind.HOTDEAL_POST || app.status() != BizApplicationStatus.SCHEDULED || app.startAt().isAfter(now)
                || !app.endAt().isAfter(now) || !retryable(app)) {
            return Outcome.SKIPPED;
        }
        CreativeRow creative = store.latestCreative(app.id()).orElse(null);
        Map<String, Object> c = creative == null ? Map.of() : creative.content();
        EffectiveBoard board = BizDealContent.id(c.get("boardId")) == null ? null
                : catalog.snapshot().board(BizDealContent.id(c.get("boardId"))).orElse(null);
        Long prefixId = BizDealContent.id(c.get("prefixId"));
        String url = BizDealContent.text(c.get("linkUrl"));
        String host = url == null ? null : DealHosts.hostOf(url);
        String reason = null;
        ChainVerdict verdict = null;
        boolean prefixDropped = false;
        if (!results.businessApproved(app)) {
            reason = BUSINESS_SUSPENDED;
        } else if (!results.hotdealServiceApproved(app)) {
            reason = SERVICE_SUSPENDED; // 신원은 승인 — 핫딜 서비스 줄만 정지(결정 10, 줄이 다시 승인되면 배치가 다시 올린다)
        } else if (board == null || !board.active() || !BoardFields.KIND_DEAL.equals(board.kind())) {
            reason = BOARD_CLOSED;
        } else if (host == null || app.userId() == null || BizDealContent.text(c.get("shopName")) == null || !(c.get("dealPrice") instanceof Number)) {
            reason = OTHER;
        } else {
            verdict = rules.judge(List.of(host));
            if (verdict.blocked()) {
                reason = LINK_BLOCKED;
            }
        }
        if (reason == null && prefixId != null && board.prefix(prefixId).filter(BoardPrefixInfo::active).isEmpty()) {
            // 신청 때 고른 말머리가 없어졌거나 꺼졌다 — 실패가 아니라 말머리 없이 올리고 운영자에게 알린다(사용자 결정 2026-11-02, 말머리 상단 노출은 나오지 않음)
            prefixId = null;
            prefixDropped = true;
        }
        if (reason != null) {
            if (!results.claimFailed(app.id(), reason, now)) {
                return Outcome.SKIPPED;
            }
            ops.event(app.id(), BizEventAction.POST_FAILED, BizActorType.SYSTEM, null, reason, now);
            ops.notices().postFailed(app, reason);
            ops.notices().member(app, BizApplicationNotices.Kind.POST_FAILED, 0, reason);
            return Outcome.FAILED;
        }
        long postId = ops.nextId();
        if (!results.claimPosted(app.id(), postId, now)) {
            return Outcome.SKIPPED;
        }
        String body = BizDealContent.text(c.get("body"));
        posts.persist(PostEntity.create(postId, board.id(), prefixId, app.userId(), null, null, null, app.title(), ContentFormat.TEXT, body == null ? "" : body, null,
                false, false, now));
        posts.flush();
        long dealPrice = ((Number) c.get("dealPrice")).longValue();
        Long listPrice = c.get("listPrice") instanceof Number n && n.longValue() >= dealPrice ? n.longValue() : null;
        Long shipping = c.get("shippingFee") instanceof Number n && n.longValue() >= 0 ? n.longValue() : null;
        Long imageId = BizDealContent.id(c.get("imageFileId"));
        String imageUrl = BizDealContent.text(c.get("imageUrl"));
        boolean image = imageId != null && imageUrl != null;
        String store0 = BizDealContent.text(c.get("shopName"));
        DealValues values = new DealValues(store0.length() > 40 ? store0.substring(0, 40) : store0, url, listPrice, dealPrice, shipping, null, null, false, null,
                app.endAt(), BizDealContent.postStock(BizDealContent.text(c.get("stock"))), verdict.affiliate(), image ? PostDealEntity.IMAGE_UPLOAD : null, null,
                image ? imageId : null, image ? imageUrl : null);
        PostDealEntity.Link link = new PostDealEntity.Link(verdict.host(), verdict.finalHost(), DealUrlKey.of(url, verdict.keyParams()));
        deals.persist(PostDealEntity.createBusiness(postId, board.id(), app.userId(), app.id(), app.businessName(), values, link, now));
        deals.flush();
        ops.event(app.id(), BizEventAction.POSTED, BizActorType.SYSTEM, null, Long.toString(postId), now);
        if (prefixDropped) {
            boolean categoryPlacement = store.items(app.id()).stream().anyMatch(i -> i.product() == BizProduct.HOTDEAL_CATEGORY);
            ops.event(app.id(), BizEventAction.POSTED_WITHOUT_PREFIX, BizActorType.SYSTEM, null, categoryPlacement ? CATEGORY_TOP_OFF : null, now);
            ops.notices().postPrefixDropped(app, categoryPlacement);
        }
        ops.notices().member(app, BizApplicationNotices.Kind.POSTED, 0, null);
        return Outcome.POSTED;
    }

    /**
     * 게시 결과가 없으면 올릴 차례. 서비스 정지로 실패한 건은 지금 신원·서비스 줄이 둘 다 승인이면 그 실패 줄을 지우고 다시 판정한다(결정 10) — 아직 정지면 그대로(되풀이 알림 없음).
     * 다른 실패·게시는 다시 하지 않는다.
     */
    private boolean retryable(AppRow app) {
        Optional<PostRow> row = results.find(app.id());
        if (row.isEmpty()) {
            return true;
        }
        PostRow r = row.get();
        return !r.posted() && SERVICE_SUSPENDED.equals(r.failReason()) && results.businessApproved(app) && results.hotdealServiceApproved(app)
                && results.clearServiceSuspended(app.id());
    }
}
