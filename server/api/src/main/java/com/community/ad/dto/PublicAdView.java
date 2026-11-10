package com.community.ad.dto;

import java.util.List;

/**
 * 공개 광고 응답·요청(user-web {@code usePublicAdApi} — docs/features/ad-contract.md 「공개 노출 목업」, 화면·서버 공용 계약 2026-11-01).
 */
public final class PublicAdView {

    private PublicAdView() {
    }

    /** {@code GET /api/ads} */
    public record Page(List<Ad> items) {
    }

    /**
     * {@code GET /api/ads/placements} — 자리 설정({@link AdPlacements} 네 묶음) + 구글 애드센스(V176, docs/features/adsense-contract.md).
     *
     * @param adsense 끔(OFF)이거나 게시자 ID 가 없으면 null. {@code units} 는 SHOW 일 때 단위 ID 가 있는 자리만(VERIFY 면 빈 묶음)
     */
    public record Placements(AdPlacements.PostInline postInline, AdPlacements.Wings wings, AdPlacements.BoardList boardList, AdPlacements.Adult adult,
                             AdSenseSettings.PublicView adsense) {
    }

    /**
     * 나가는 광고 한 건(고르기 — 비중 가중 무작위 — 는 화면이 한다)
     *
     * @param id             신청 건 ID
     * @param type           상품 이름({@code WIDE_BANNER} 등)
     * @param version        소재 버전(최신 승인 버전) — 노출·클릭 집계에 그대로 돌려보낸다
     * @param imageUrl       배너 이미지 공개 주소(한 줄 광고는 null)
     * @param text           한 줄 문구(배너는 null)
     * @param altText        대체 글 — 없으면 {@code text}
     * @param weight         비중 1~10
     * @param deal           핫딜 노출 자리의 글 정보(사업자 글이 진행 중·공개일 때만 — 배너·한 줄은 null)
     * @param advertiserName 광고주 표시 이름(사업자 상호 또는 광고주 이름, 없으면 null)
     */
    public record Ad(String id, String type, int version, String imageUrl, String text, String linkUrl, String altText, int weight, Deal deal,
                     String advertiserName) {
    }

    /** 핫딜 노출 자리의 글(V123) — thumbnailUrl 은 딜 이미지(없으면 null), prefixName 은 글 말머리 */
    public record Deal(String postId, String title, String shopName, Long dealPrice, Long listPrice, String thumbnailUrl, String prefixName) {
    }

    /** {@code POST /api/ads/impressions} 한 줄 */
    public record Impression(String adId, Integer version, String placement) {
    }

    /** {@code POST /api/ads/impressions} 본문 */
    public record Impressions(List<Impression> items) {
    }

    /** {@code POST /api/ads/{id}/clicks} 본문 */
    public record Click(Integer version, String placement) {
    }
}
