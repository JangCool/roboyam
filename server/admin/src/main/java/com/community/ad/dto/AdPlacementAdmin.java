package com.community.ad.dto;

import java.util.List;

/**
 * 관리자 광고 노출 자리 설정(admin-web {@code /ads/placements} — {@code GET·PUT /api/admin/ads/placements}). 설정 칸 모양은 공개 {@code GET /api/ads/placements} 와 같다
 * ({@link AdPlacements}).
 */
public final class AdPlacementAdmin {

    private AdPlacementAdmin() {
    }

    /**
     * @param updatedAt 마지막 저장 시각 — 저장한 적 없으면(기본값) null
     * @param updatedBy 마지막 저장 운영자 — 저장한 적 없으면 null(지워진 운영자면 이름 null)
     */
    public record View(AdPlacements.PostInline postInline, AdPlacements.Wings wings, AdPlacements.BoardList boardList, AdPlacements.Adult adult, String updatedAt,
            Operator updatedBy, RefundView refund) {
    }

    public record Operator(String id, String name) {
    }

    /** 환불 묶음(V127 — 탭 {@code ?tab=refund}) — 중도 해지 수수료율(0~30 정수 %, 기본 10). 신청 건을 낼 때 그 건에 고정된다(바꿔도 이미 낸 건은 그대로) */
    public record RefundView(int stopFeeRate) {
    }

    /**
     * {@code PUT} 본문 — 네 묶음 모두 보낸다(빠지면 400 {@code fields.<묶음>}). {@code refund} 는 선택(V127 — 빠지면 지금 수수료율 그대로, 화면이 붙기 전 요청과 맞춘다 — 가정),
     * 보내면 {@code refund.stopFeeRate} 0~30 정수 필수(400 {@code fields.refund.stopFeeRate})
     */
    public record Save(PostInline postInline, Wings wings, BoardList boardList, Adult adult, Refund refund) {

        public Save(PostInline postInline, Wings wings, BoardList boardList, Adult adult) {
            this(postInline, wings, boardList, adult, null);
        }
    }

    public record Refund(Integer stopFeeRate) {
    }

    public record PostInline(Boolean enabled, String adType, String position) {
    }

    public record Wings(Boolean left, Boolean right, List<String> screens) {
    }

    /** @param count 1~2(없으면 1) · @param gap 3~20(없으면 10) — V155, 옛 요청은 두 칸 없이 와도 된다 */
    public record BoardList(Boolean enabled, Integer after, Integer count, Integer gap) {
    }

    /** 19금 게시판에도 광고 보이기 */
    public record Adult(Boolean enabled) {
    }
}
