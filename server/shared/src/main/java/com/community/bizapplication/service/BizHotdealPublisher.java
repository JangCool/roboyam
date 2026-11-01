package com.community.bizapplication.service;

import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizEventAction;
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
 *   <li><b>게시</b>: 예약 · 시작 ≤ 지금 &lt; 끝 · 게시 결과 없음 → 게시 직전 재판정(사업자 승인 · 게시판 있음·켬·핫딜 종류 · 말머리 · 지금 링크 규칙) → 글(작성자 = 사업자 회원, 평문,
 *       글 시각 = 게시 시각) + 딜({@code source = BUSINESS}, 마감 = 신청 기간 끝, 상호 스냅샷) + 처리 기록 {@code POSTED} + 사업자 알림. 하나라도 아니면 글 없이
 *       {@code POST_FAILED}(사유 코드) + 운영자 알림 {@code HOTDEAL_POST_FAILED} + 사업자 알림 — 다시 시도하지 않는다(운영자 취소·전액 환불).</li>
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
    public static final String OTHER = "OTHER";

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
                || !app.endAt().isAfter(now) || results.find(app.id()).isPresent()) {
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
        if (!results.businessApproved(app)) {
            reason = BUSINESS_SUSPENDED;
        } else if (board == null || !board.active() || !BoardFields.KIND_DEAL.equals(board.kind())) {
            reason = BOARD_CLOSED;
        } else if (prefixId != null && board.prefix(prefixId).filter(BoardPrefixInfo::active).isEmpty()) {
            reason = OTHER; // 신청 때 고른 말머리가 없어졌다(말머리 상단 노출 자리가 사라진다)
        } else if (host == null || app.userId() == null || BizDealContent.text(c.get("shopName")) == null || !(c.get("dealPrice") instanceof Number)) {
            reason = OTHER;
        } else {
            verdict = rules.judge(List.of(host));
            if (verdict.blocked()) {
                reason = LINK_BLOCKED;
            }
        }
        if (reason != null) {
            if (!results.claimFailed(app.id(), reason, now)) {
                return Outcome.SKIPPED;
            }
            ops.event(app.id(), BizEventAction.POST_FAILED, BizActorType.SYSTEM, null, reason, now);
            ops.notices().postFailed(app);
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
        ops.notices().member(app, BizApplicationNotices.Kind.POSTED, 0, null);
        return Outcome.POSTED;
    }
}
