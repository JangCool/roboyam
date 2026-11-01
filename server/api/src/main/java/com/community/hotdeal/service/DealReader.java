package com.community.hotdeal.service;

import com.community.board.dto.EffectiveBoard;
import com.community.hotdeal.dto.DealSettings;
import com.community.hotdeal.dto.response.DealEndRequestsView;
import com.community.hotdeal.dto.response.DealImageView;
import com.community.hotdeal.dto.response.DealRevisionView;
import com.community.hotdeal.dto.response.DealSameRefView;
import com.community.hotdeal.dto.response.PostDealView;
import com.community.hotdeal.entity.DealView;
import com.community.hotdeal.entity.DealViews;
import com.community.hotdeal.entity.PostDealEntity;
import com.community.hotdeal.repository.master.DealQueries;
import com.community.hotdeal.repository.master.PostDealRepository;
import com.community.hotdeal.service.SiteAffiliateLinker.SiteStore;
import com.community.post.dto.PostRowData;
import com.community.post.entity.PostEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 딜의 보기({@link PostDealView}) — 글 상세·목록 줄·고치기 응답이 같은 모양을 쓴다. 상태·할인율·기간 종료·되돌리기 기한·HOT·같은 딜 수·제휴 표시 종류는 <b>읽을 때 지금 설정·규칙으로</b>
 * 계산한다(서버가 정하고 화면은 그대로 그린다). 트랜잭션은 부르는 쪽이 연다. 목록은 줄마다가 아니라 한 번에 센다(종료 요청·같은 딜).
 */
@Component
public class DealReader {

    public static final String BUY_PATH = "/api/deals/%d/go";
    public static final String KIND_MEMBER = "MEMBER";
    public static final String KIND_SITE = "SITE";
    public static final int SAME_DEALS_MAX = 20;

    private final PostDealRepository deals;
    private final DealQueries queries;
    private final DealSettingsReader settings;
    private final DealRuleBook rules;

    public DealReader(PostDealRepository deals, DealQueries queries, DealSettingsReader settings, DealRuleBook rules) {
        this.deals = deals;
        this.queries = queries;
        this.settings = settings;
        this.rules = rules;
    }

    /** 글 상세의 {@code deal} — 핫딜 게시판이 아니거나 딜 줄이 없으면 null */
    public PostDealView forPost(PostEntity post, EffectiveBoard b, Long viewerId, Instant now) {
        if (!DealWriter.dealBoard(b)) {
            return null;
        }
        return deals.findById(post.getId()).map(d -> {
            DealSettings s = settings.current();
            List<Long> ids = List.of(d.getPostId());
            DealQueries.EndCounts counts = queries.endRequestCounts(ids).get(d.getPostId());
            String mine = viewerId == null ? null : queries.myEndRequests(ids, viewerId).get(d.getPostId());
            int live = queries.liveCountsByKey(List.of(d.getProductUrlKey()), s.autoExpireDays(), now).getOrDefault(d.getProductUrlKey(), 0);
            boolean selfCounted = d.view(s.autoExpireDays(), now).live() && !post.isGroupPost() && !post.isHiddenAt(now);
            return view(d, post.getLikeCount(), counts, mine, Math.max(0, live - (selfCounted ? 1 : 0)), s, now);
        }).orElse(null);
    }

    /**
     * 목록 줄들의 {@code deal}(숨긴 줄은 빼고 넘긴다) — 글 ID → 딜. 딜 줄이 없는 글은 빠진다.
     */
    public Map<Long, PostDealView> forRows(List<PostRowData> rows, EffectiveBoard b, Long viewerId, Instant now) {
        Map<Long, PostDealView> out = new HashMap<>();
        if (!DealWriter.dealBoard(b) || rows.isEmpty()) {
            return out;
        }
        DealSettings s = settings.current();
        List<Long> ids = rows.stream().map(PostRowData::id).toList();
        List<PostDealEntity> found = deals.findAllById(ids);
        if (found.isEmpty()) {
            return out;
        }
        Map<Long, DealQueries.EndCounts> counts = queries.endRequestCounts(ids);
        Map<Long, String> mine = viewerId == null ? Map.of() : queries.myEndRequests(ids, viewerId);
        Map<String, Integer> live = queries.liveCountsByKey(found.stream().map(PostDealEntity::getProductUrlKey).distinct().toList(), s.autoExpireDays(), now);
        Map<Long, Integer> likes = new HashMap<>();
        rows.forEach(r -> likes.put(r.id(), r.likeCount() == null ? 0 : r.likeCount()));
        for (PostDealEntity d : found) {
            boolean selfCounted = d.view(s.autoExpireDays(), now).live();
            int same = Math.max(0, live.getOrDefault(d.getProductUrlKey(), 0) - (selfCounted ? 1 : 0));
            out.put(d.getPostId(), view(d, likes.getOrDefault(d.getPostId(), 0), counts.get(d.getPostId()), mine.get(d.getPostId()), same, s, now));
        }
        return out;
    }

    /** 공개·진행 중인 같은 딜(이 글 제외, 최신 차례) */
    public List<DealSameRefView> same(PostDealEntity d, Instant now) {
        return same(d.getProductUrlKey(), d.getPostId(), now, SAME_DEALS_MAX);
    }

    public List<DealSameRefView> same(String key, Long excludePostId, Instant now, int limit) {
        return queries.sameDeals(key, excludePostId, settings.current().autoExpireDays(), now, limit).stream()
                .map(r -> new DealSameRefView(Long.toString(r.postId()), r.title(), r.dealPrice(), r.shippingFee(), r.createdAt())).toList();
    }

    PostDealView view(PostDealEntity d, int likeCount, DealQueries.EndCounts counts, String mine, int sameCount, DealSettings s, Instant now) {
        DealView view = d.view(s.autoExpireDays(), now);
        Instant autoEndAt = view == DealView.FLAGGED && s.endGraceHours() > 0 && d.getFlaggedAt() != null ? d.getFlaggedAt().plus(Duration.ofHours(s.endGraceHours()))
                : null;
        DealEndRequestsView requests = counts == null ? new DealEndRequestsView(0, 0, 0, 0, mine)
                : new DealEndRequestsView(counts.total(), counts.soldOut(), counts.priceChanged(), counts.linkDead(), mine);
        String affiliateKind = null;
        String programName = null;
        if (s.siteMode()) {
            Optional<SiteStore> store = rules.siteStore(d.getProductHost(), d.getFinalHost());
            if (store.isPresent()) {
                affiliateKind = KIND_SITE;
                programName = store.get().programName();
            }
        } else if (d.isAffiliate()) {
            affiliateKind = KIND_MEMBER;
        }
        DealImageView image = d.getImageSource() == null ? null
                : new DealImageView(d.getImageUrl(), d.getImageSource(), d.getImageId() == null ? null : Long.toString(d.getImageId()), null);
        return new PostDealView(d.getSource(), d.getStoreName(), d.getProductUrl(), d.getProductHost(), d.getFinalHost(), d.getNormalPrice(), d.getDealPrice(),
                DealViews.discountRate(d.getNormalPrice(), d.getDealPrice()), d.getShippingFee(), d.getCouponNote(), d.getPriceBasisKind(), d.isDutyIncluded(),
                d.getPriceBasis(), d.getEndsAt(), DealViews.autoExpireAt(d.getEndsAt(), d.getCreatedAt(), s.autoExpireDays()), d.getStockState(), d.isAffiliate(),
                image, d.getStatus(), view.name(), d.getEndedBy(), d.getEndedAt(), d.reopenUntil(now), requests, d.getFlaggedAt(),
                autoEndAt, d.getKeepCount(), s.keepMaxCount(), new DealRevisionView(d.getPriceEditCount(), d.isLinkChanged(), d.getLastEditedAt()),
                likeCount >= s.hotLikeThreshold(), sameCount, String.format(BUY_PATH, d.getPostId()), affiliateKind, programName);
    }
}
