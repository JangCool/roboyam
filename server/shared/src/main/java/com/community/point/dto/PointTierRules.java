package com.community.point.dto;

import com.community.point.entity.PointBoardTier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 포인트 정책의 게시판 등급 칸(point-board-tier-contract, V238) — 등급별 금액·조건 + 스위치 두 개. {@link PointRules} 와 같은 정책 행({@code point_settings})에 있다.
 *
 * <p><b>일반 등급의 「쓰면 바로」·「최소 글자 수」는 이 값이 아니라 기존 전역 칸</b>({@link PointRules#postPoints()}·{@link PointRules#postMinLength()})이다 — 화면이 그렇게 묶었다.
 * 그래서 {@link #normal} 의 그 두 칸은 저장하지 않는 자리(0)이고, 실제 값은 {@link #values(PointBoardTier, PointRules)} 로 읽는다.
 *
 * @param infoSourceOnce  정보 등급 글은 같은 출처 링크로 한 번만 적립(사이트 전체)
 * @param excludeAiAndOps AI 회원·운영팀 계정은 모든 적립(글·댓글·추천 보너스·우수 글·인기글) 안 함
 */
public record PointTierRules(PointTierValues normal, PointTierValues info, PointTierValues core, boolean infoSourceOnce, boolean excludeAiAndOps) {

    /** 정책 행이 없을 때의 기본값(V238 기본값과 같다) */
    public static final PointTierRules DEFAULTS = new PointTierRules(new PointTierValues(0, 10, 0, 0, 0, 0), new PointTierValues(30, 50, 100, 100, 0, 24),
            new PointTierValues(50, 150, 500, 500, 1, 24), true, true);

    /** 일반 등급에서 저장하지 않는(전역 칸을 따르는) 칸 */
    public static final java.util.Set<String> NORMAL_FOLLOWS = java.util.Set.of("postPoints", "minLength");

    /** 그 등급의 실제 금액·조건 — 일반 등급의 쓰면 바로·최소 글자 수는 전역 칸 */
    public PointTierValues values(PointBoardTier tier, PointRules rules) {
        return switch (tier) {
            case NORMAL -> new PointTierValues(rules.postPoints(), normal.likeBonusPoints(), normal.featuredPoints(), rules.postMinLength(), normal.minImages(),
                    normal.holdHours());
            case INFO -> info;
            case CORE -> core;
        };
    }

    /** 저장한 등급 칸 그대로(일반 등급의 따르는 칸은 0 자리) */
    public PointTierValues stored(PointBoardTier tier) {
        return switch (tier) {
            case NORMAL -> normal;
            case INFO -> info;
            case CORE -> core;
        };
    }

    /** 칸 이름 → 값 — 감사 기록의 바뀐 칸({@code boardTiers.INFO.postPoints} 꼴, 일반 등급의 따르는 칸은 빼고) + 스위치 */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (PointBoardTier tier : PointBoardTier.values()) {
            stored(tier).toMap().forEach((field, value) -> {
                if (tier != PointBoardTier.NORMAL || !NORMAL_FOLLOWS.contains(field)) {
                    map.put("boardTiers." + tier.name() + "." + field, value);
                }
            });
        }
        map.put("infoSourceOnce", infoSourceOnce);
        map.put("excludeAiAndOps", excludeAiAndOps);
        return map;
    }
}
