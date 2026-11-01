package com.community.hotdeal.dto.response;

import java.time.Instant;

/**
 * 글에 붙은 딜(user-web {@code PostDeal}) — 글 상세·목록 줄의 {@code deal}. 상태({@code view})·할인율·기간 종료 시각·되돌리기 기한·HOT·같은 딜 수·제휴 표시 종류는 서버가
 * 읽을 때 계산한다. 공개 응답의 {@code image.originUrl} 은 늘 null(원본 주소는 관리자 상세만).
 *
 * @param finalHost       리디렉션을 따라간 최종 호스트 — {@code productHost} 와 같거나 모르면 null
 * @param discountRate    정상가 &gt; 딜가 일 때 내림 정수 %
 * @param autoExpireAt    마감이 없을 때 기간 종료로 바뀌는 시각(설정이 꺼졌거나 마감이 있으면 null)
 * @param reopenUntil     작성자 되돌리기 기한(D2) — 작성자 종료 · 아직 안 씀 · 24시간 안일 때만
 * @param autoEndAt       의심이면 자동 종료 예정 시각({@code flaggedAt} + 유예, 유예 0 = 자동 종료 안 함 → null)
 * @param keepMax         「아직 진행 중」 최대 횟수(0 = 제한 없음)
 * @param hot             추천 ≥ {@code hotLikeThreshold}
 * @param sameDealCount   공개·진행 중인 같은 딜 글 수(이 글 제외)
 * @param buyUrl          구매하기 — 늘 서버 경유 {@code /api/deals/{postId}/go}
 * @param affiliateKind   지금 방식으로 계산한 제휴 표시 — MEMBER · SITE · null
 * @param siteProgramName {@code affiliateKind = SITE} 일 때 사이트 제휴 프로그램 이름
 * @param business        사업자 글(source = BUSINESS)이면 상호 — 「광고」 표시 · 작성자 자리(회원 글 null)
 */
public record PostDealView(String source, String storeName, String productUrl, String productHost, String finalHost, Long normalPrice, long dealPrice,
                           Integer discountRate, Long shippingFee, String couponNote, String priceBasisKind, boolean dutyIncluded, String priceBasis,
                           Instant endsAt, Instant autoExpireAt, String stockState, boolean affiliate, DealImageView image, String status, String view,
                           String endedBy, Instant endedAt, Instant reopenUntil, DealEndRequestsView endRequests, Instant flaggedAt, Instant autoEndAt,
                           int keepCount, int keepMax, DealRevisionView revision, boolean hot, int sameDealCount, String buyUrl, String affiliateKind,
                           String siteProgramName, DealBusinessView business) {
}