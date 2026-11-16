package com.community.board.dto.response;

import com.community.board.dto.EffectiveBoard;
import com.community.point.dto.PointRules;
import com.community.point.dto.PointTierRules;
import com.community.point.dto.PointTierValues;
import com.community.point.entity.PointBoardTier;

/**
 * 글쓰기 화면용 게시판 포인트 등급(point-board-tier-contract 「API 후보」 — user-web {@code BoardPointTierView}). 관리자 포인트 정책 「게시판 등급별 지급」의 그 등급 값 + 함께 쓰는 추천 문턱.
 *
 * @param tier          NORMAL · INFO · CORE
 * @param postPoints    쓰면 바로(P)
 * @param likeBonusPoints 글이 추천 문턱을 채우면 더 주는 보너스(P)
 * @param likeThreshold 추천 문턱(인정된 추천 개수 — 정책 {@code likeBonusThreshold})
 * @param featuredPoints 우수 글로 뽑히면 주는 포인트(P)
 * @param minLength     적립 최소 글자 수(서식·공백 뺀 글자) — 게시판이 「적립 최소 글자 수 적용」을 껐으면 0
 * @param minImages     적립 사진 최소(장)
 * @param holdHours     보너스 보류(시간) — 0 이면 바로
 */
public record BoardPointTierView(String tier, int postPoints, int likeBonusPoints, int likeThreshold, int featuredPoints, int minLength, int minImages, int holdHours) {

    /** 게시판 유효 pointMode 가 끔이면 null */
    public static BoardPointTierView of(EffectiveBoard board, PointRules rules, PointTierRules tiers) {
        return PointBoardTier.ofMode(board.pointMode()).map(tier -> {
            PointTierValues v = tiers.values(tier, rules);
            return new BoardPointTierView(tier.name(), v.postPoints(), v.likeBonusPoints(), rules.likeBonusThreshold(), v.featuredPoints(),
                    board.pointMinLengthApplied() ? v.minLength() : 0, v.minImages(), v.holdHours());
        }).orElse(null);
    }
}
