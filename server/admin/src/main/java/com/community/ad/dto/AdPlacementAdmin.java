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
            Operator updatedBy) {
    }

    public record Operator(String id, String name) {
    }

    /** {@code PUT} 본문 — 네 묶음 모두 보낸다(빠지면 400 {@code fields.<묶음>}) */
    public record Save(PostInline postInline, Wings wings, BoardList boardList, Adult adult) {
    }

    public record PostInline(Boolean enabled, String adType, String position) {
    }

    public record Wings(Boolean left, Boolean right, List<String> screens) {
    }

    public record BoardList(Boolean enabled, Integer after) {
    }

    /** 19금 게시판에도 광고 보이기 */
    public record Adult(Boolean enabled) {
    }
}
