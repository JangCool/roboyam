package com.community.hotdeal.service;

import com.community.hotdeal.dto.DealSettings;
import com.community.hotdeal.dto.response.DealEndRequestsView;
import com.community.hotdeal.dto.response.DealImageView;
import com.community.hotdeal.dto.response.DealRevisionView;
import com.community.hotdeal.dto.response.PostDealView;
import com.community.hotdeal.entity.DealView;
import com.community.hotdeal.entity.DealViews;
import com.community.hotdeal.entity.PostDealEntity;
import com.community.hotdeal.repository.master.DealQueries;
import com.community.hotdeal.service.SiteAffiliateLinker.SiteStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 딜 보기({@link PostDealView}) 한 장을 만드는 공용 계산 — 공개 글 상세·목록 줄(api {@code DealReader})과 관리자 핫딜 목록·상세(admin 3단계)가 <b>같은 판정</b>을 쓴다.
 * 상태·할인율·기간 종료·되돌리기 기한·자동 종료 예정·HOT·제휴 표시 종류는 넘겨받은 지금 설정·규칙으로 계산한다. 집계(종료 요청 수·같은 딜 수·추천 수)는 부르는 쪽이 한 번에 세어 넘긴다.
 */
@Component
public class DealViewAssembler {

    public static final String BUY_PATH = "/api/deals/%d/go";
    public static final String KIND_MEMBER = "MEMBER";
    public static final String KIND_SITE = "SITE";

    private final DealRuleBook rules;

    public DealViewAssembler(DealRuleBook rules) {
        this.rules = rules;
    }

    /**
     * @param counts    종료 요청 집계(요청이 없으면 null)
     * @param mine      보는 회원이 보낸 사유(공개 쪽만 — 관리자는 null)
     * @param sameCount 공개·진행 중인 같은 딜 수(이 글 제외)
     * @param originUrl OG 사본의 원본 주소 — <b>관리자 응답만</b>(공개 응답은 늘 null)
     */
    public PostDealView view(PostDealEntity d, int likeCount, DealQueries.EndCounts counts, String mine, int sameCount, DealSettings s, Instant now,
                             String originUrl) {
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
                : new DealImageView(d.getImageUrl(), d.getImageSource(), d.getImageId() == null ? null : Long.toString(d.getImageId()),
                        PostDealEntity.IMAGE_OG.equals(d.getImageSource()) ? originUrl : null);
        return new PostDealView(d.getSource(), d.getStoreName(), d.getProductUrl(), d.getProductHost(), d.getFinalHost(), d.getNormalPrice(), d.getDealPrice(),
                DealViews.discountRate(d.getNormalPrice(), d.getDealPrice()), d.getShippingFee(), d.getCouponNote(), d.getPriceBasisKind(), d.isDutyIncluded(),
                d.getPriceBasis(), d.getEndsAt(), DealViews.autoExpireAt(d.getEndsAt(), d.getCreatedAt(), s.autoExpireDays()), d.getStockState(), d.isAffiliate(),
                image, d.getStatus(), view.name(), d.getEndedBy(), d.getEndedAt(), d.reopenUntil(now), requests, d.getFlaggedAt(),
                autoEndAt, d.getKeepCount(), s.keepMaxCount(), new DealRevisionView(d.getPriceEditCount(), d.isLinkChanged(), d.getLastEditedAt()),
                likeCount >= s.hotLikeThreshold(), sameCount, String.format(BUY_PATH, d.getPostId()), affiliateKind, programName);
    }
}
