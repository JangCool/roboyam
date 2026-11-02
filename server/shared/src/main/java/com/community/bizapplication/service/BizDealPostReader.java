package com.community.bizapplication.service;

import com.community.bizapplication.dto.response.BizDealPostView;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.CreativeRow;
import com.community.bizapplication.repository.master.BizApplicationStore.ItemRow;
import com.community.bizapplication.repository.master.BizDealPostStore;
import com.community.bizapplication.repository.master.BizDealPostStore.PostRow;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.hotdeal.dto.DealSettings;
import com.community.hotdeal.entity.DealView;
import com.community.hotdeal.entity.PostDealEntity;
import com.community.hotdeal.repository.master.DealQueries;
import com.community.hotdeal.repository.master.PostDealRepository;
import com.community.hotdeal.service.DealSettingsReader;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신청 건 상세의 「게시된 글」({@link BizDealPostView}) — 공개·관리자 같은 판정. 핫딜 글 신청 건만, 결제 전(예약·내림이 아니고 게시 결과도 없음)이면 null.
 * 노출 자리 상태: 게시 전·실패 WAITING · 글 숨김 STOPPED/POST_HIDDEN · 딜 종료 STOPPED/DEAL_ENDED · 기간 끝(EXPIRED) ENDED · 말머리 없이 올라간 글의 말머리 상단 STOPPED/PREFIX_MISSING · 그 밖 SHOWING.
 */
@Service
@Transactional(readOnly = true)
public class BizDealPostReader {

    private final BizDealPostStore results;
    private final PostRepository posts;
    private final PostDealRepository deals;
    private final DealQueries queries;
    private final DealSettingsReader settings;
    private final BoardCatalog catalog;
    private final BizApplicationOps ops;

    public BizDealPostReader(BizDealPostStore results, PostRepository posts, PostDealRepository deals, DealQueries queries, DealSettingsReader settings,
                             BoardCatalog catalog, BizApplicationOps ops) {
        this.results = results;
        this.posts = posts;
        this.deals = deals;
        this.queries = queries;
        this.settings = settings;
        this.catalog = catalog;
        this.ops = ops;
    }

    /**
     * 정지·중단할 수 있는 사업자 핫딜 글인가(V127 — biz-pause-contract 「상태 모델」): 게시됨 · 지운 글 아님 · 딜 진행 중(ACTIVE·FLAGGED — 품절로 종료·기간 종료 뒤엔 없음).
     * 진행 중 건({@code paused = false})은 글이 보여야 한다(운영 숨김 뒤엔 없음). 정지 중 건의 중단은 글 숨김 상태를 보지 않는다.
     */
    public boolean dealPausable(AppRow app, boolean paused) {
        Optional<PostRow> row = results.find(app.id()).filter(PostRow::posted);
        if (row.isEmpty()) {
            return false;
        }
        Instant now = ops.now();
        PostEntity post = posts.findById(row.get().postId()).orElse(null);
        if (post == null || post.isGoneAt(now) || (!paused && post.isHiddenAt(now))) {
            return false;
        }
        PostDealEntity deal = deals.findById(post.getId()).orElse(null);
        if (deal == null) {
            return false;
        }
        DealView view = deal.view(settings.current().autoExpireDays(), now);
        return view == DealView.ACTIVE || view == DealView.FLAGGED;
    }

    /**
     * 노출 자리가 지금 멈춘 까닭(광고 성과 — ad-performance-contract 「확인 뒤 고침」): 사업자 핫딜 글 신청 건이 예약({@code SCHEDULED})·기간 안(시작 ≤ 지금 &lt; 끝)인데
     * 노출 자리가 나가지 않으면 {@code DEAL_ENDED}(딜 종료·기간 종료) · {@code POST_HIDDEN}(글 숨김·지움) · {@code PREFIX_MISSING}(말머리 없이 올라간 글의 말머리 상단) ·
     * {@code NOT_POSTED}(아직 못 올림·게시 실패). 판정은 {@link #view} 의 노출 자리 상태(= 공개 노출 판정 biz-hotdeal-contract 규칙 7)와 같다. 그 밖(노출 중·다른 종류·다른 상태)은 null.
     */
    public String exposureStop(AppRow app, List<ItemRow> items) {
        Instant now = ops.now();
        if (app.kind() != BizApplicationKind.HOTDEAL_POST || app.status() != BizApplicationStatus.SCHEDULED || app.startAt().isAfter(now) || !app.endAt().isAfter(now)) {
            return null;
        }
        BizDealPostView v = view(app, items, null);
        if (v == null || v.placement() == null) {
            return null;
        }
        return switch (v.placement().status()) {
            case "STOPPED" -> "PAUSED".equals(v.placement().stopReason()) ? "POST_HIDDEN" : v.placement().stopReason();
            case "ENDED" -> "DEAL_ENDED";
            case "WAITING" -> "NOT_POSTED";
            default -> null;
        };
    }

    public BizDealPostView view(AppRow app, List<ItemRow> items, CreativeRow creative) {
        if (app.kind() != BizApplicationKind.HOTDEAL_POST) {
            return null;
        }
        Optional<PostRow> row = results.find(app.id());
        if (row.isEmpty() && !EnumSet.of(BizApplicationStatus.SCHEDULED, BizApplicationStatus.TAKEN_DOWN).contains(app.status())) {
            return null;
        }
        Instant now = ops.now();
        Map<String, Object> c = creative == null ? Map.of() : creative.content();
        String product = items.stream().map(ItemRow::product).filter(BizProduct::placement).map(Enum::name).findFirst().orElse(null);
        PostEntity post = row.filter(PostRow::posted).flatMap(r -> posts.findById(r.postId())).orElse(null);
        PostDealEntity deal = post == null ? null : deals.findById(post.getId()).orElse(null);
        Long boardId = post != null ? post.getBoardId() : BizDealContent.id(c.get("boardId"));
        EffectiveBoard board = boardId == null ? null : catalog.snapshot().board(boardId).orElse(null);
        Long prefixId = post != null ? post.getPrefixId() : BizDealContent.id(c.get("prefixId"));
        String snapshotPrefix = BizDealContent.text(c.get("prefixName"));
        String boardSlug = board != null ? board.slug() : BizDealContent.text(c.get("boardSlug"));
        String boardName = board != null ? board.name() : BizDealContent.text(c.get("boardName"));
        // 올라간 글은 글의 말머리만 본다(말머리 없이 올라갔으면 null) — 게시 전·실패만 신청 때 스냅샷으로 채운다
        String prefixName = prefixId == null ? (post != null ? null : snapshotPrefix)
                : board != null ? board.prefix(prefixId).map(BoardPrefixInfo::name).orElse(snapshotPrefix) : snapshotPrefix;
        if (row.isEmpty() || !row.get().posted()) {
            String failReason = row.map(PostRow::failReason).orElse(null);
            return new BizDealPostView(failReason == null ? "WAITING" : "FAILED", null, boardSlug, boardName, prefixName, app.startAt(), null, null, null, failReason,
                    product == null ? null : new BizDealPostView.Placement(product, "WAITING", null), null, null);
        }
        DealSettings s = settings.current();
        boolean hidden = post == null || post.isGoneAt(now) || post.isHiddenAt(now);
        DealView view = deal == null ? null : deal.view(s.autoExpireDays(), now);
        BizDealPostView.EndRequests requests = null;
        Instant autoEndAt = null; // 사업자 글은 종료 요청으로 자동 종료하지 않는다(운영자 확인 — 결정 11)
        if (deal != null) {
            DealQueries.EndCounts n = queries.endRequestCounts(List.of(deal.getPostId())).get(deal.getPostId());
            requests = n == null ? new BizDealPostView.EndRequests(0, 0, 0, 0) : new BizDealPostView.EndRequests(n.total(), n.soldOut(), n.priceChanged(), n.linkDead());
        }
        BizDealPostView.Placement placement = null;
        if (product != null) {
            if (hidden && post != null && post.isHiddenAt(now) && PostEntity.SELLER_PAUSED.equals(post.getHiddenReason())) {
                // 광고주 일시 정지(V127) — 다시 켜면 같이 나간다
                placement = new BizDealPostView.Placement(product, "STOPPED", "PAUSED");
            } else if (hidden) {
                placement = new BizDealPostView.Placement(product, "STOPPED", "POST_HIDDEN");
            } else if (view == DealView.ENDED) {
                placement = new BizDealPostView.Placement(product, "STOPPED", "DEAL_ENDED");
            } else if (view == DealView.EXPIRED || !app.endAt().isAfter(now)) {
                placement = new BizDealPostView.Placement(product, "ENDED", null);
            } else if (BizProduct.HOTDEAL_CATEGORY.name().equals(product) && post.getPrefixId() == null) {
                // 말머리 없이 올라간 글(신청 때 고른 말머리가 없어짐) — 말머리 상단 노출은 나오지 않는다
                placement = new BizDealPostView.Placement(product, "STOPPED", "PREFIX_MISSING");
            } else {
                placement = new BizDealPostView.Placement(product, "SHOWING", null);
            }
        }
        return new BizDealPostView(hidden ? "HIDDEN" : "POSTED", Long.toString(row.get().postId()), boardSlug, boardName, prefixName, app.startAt(),
                view == null ? null : view.name(), deal == null ? null : deal.getEndedBy(), requests, null, placement, autoEndAt,
                deal != null && deal.isEnded() ? deal.getEndedAt() : null);
    }
}
