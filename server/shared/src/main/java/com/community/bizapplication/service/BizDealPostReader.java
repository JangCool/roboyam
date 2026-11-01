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
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신청 건 상세의 「게시된 글」({@link BizDealPostView}) — 공개·관리자 같은 판정. 핫딜 글 신청 건만, 결제 전(예약·내림이 아니고 게시 결과도 없음)이면 null.
 * 노출 자리 상태: 게시 전·실패 WAITING · 글 숨김 STOPPED/POST_HIDDEN · 딜 종료 STOPPED/DEAL_ENDED · 기간 끝(EXPIRED) ENDED · 그 밖 SHOWING.
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
        String prefixName = board != null && prefixId != null ? board.prefix(prefixId).map(BoardPrefixInfo::name).orElse(snapshotPrefix) : snapshotPrefix;
        if (row.isEmpty() || !row.get().posted()) {
            String failReason = row.map(PostRow::failReason).orElse(null);
            return new BizDealPostView(failReason == null ? "WAITING" : "FAILED", null, boardSlug, boardName, prefixName, app.startAt(), null, null, null, failReason,
                    product == null ? null : new BizDealPostView.Placement(product, "WAITING", null), null, null);
        }
        DealSettings s = settings.current();
        boolean hidden = post == null || post.isGoneAt(now) || post.isHiddenAt(now);
        DealView view = deal == null ? null : deal.view(s.autoExpireDays(), now);
        BizDealPostView.EndRequests requests = null;
        Instant autoEndAt = null;
        if (deal != null) {
            DealQueries.EndCounts n = queries.endRequestCounts(List.of(deal.getPostId())).get(deal.getPostId());
            requests = n == null ? new BizDealPostView.EndRequests(0, 0, 0, 0) : new BizDealPostView.EndRequests(n.total(), n.soldOut(), n.priceChanged(), n.linkDead());
            if (view == DealView.FLAGGED && s.endGraceHours() > 0 && deal.getFlaggedAt() != null) {
                autoEndAt = deal.getFlaggedAt().plus(Duration.ofHours(s.endGraceHours()));
            }
        }
        BizDealPostView.Placement placement = null;
        if (product != null) {
            if (hidden) {
                placement = new BizDealPostView.Placement(product, "STOPPED", "POST_HIDDEN");
            } else if (view == DealView.ENDED) {
                placement = new BizDealPostView.Placement(product, "STOPPED", "DEAL_ENDED");
            } else if (view == DealView.EXPIRED || !app.endAt().isAfter(now)) {
                placement = new BizDealPostView.Placement(product, "ENDED", null);
            } else {
                placement = new BizDealPostView.Placement(product, "SHOWING", null);
            }
        }
        return new BizDealPostView(hidden ? "HIDDEN" : "POSTED", Long.toString(row.get().postId()), boardSlug, boardName, prefixName, app.startAt(),
                view == null ? null : view.name(), deal == null ? null : deal.getEndedBy(), requests, null, placement, autoEndAt,
                deal != null && deal.isEnded() ? deal.getEndedAt() : null);
    }
}
