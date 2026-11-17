package com.community.title.dto.response;

import java.util.List;

/**
 * 칭호 도감 한 줄({@code GET /api/titles} — member-title-contract 「구현 결정」 7). 켜진 칭호만, 칭호 차례.
 *
 * @param method  AUTO · APPLY · AWARD
 * @param howTo   받는 방법 한 줄(요청 언어)
 * @param holders 가진 회원 수(탈퇴 빼고, 같은 회원은 한 번)
 * @param ratio   보유 비율 0~1(소수 넷째 자리) — 분모는 정상 회원 중 AI 회원·운영팀 계정이 아닌 회원
 * @param tiers   단계 칭호의 단계별 문턱·보유자(동·은·금 차례) — 단계 없는 칭호는 빈 목록
 */
public record TitleCatalogItemView(String key, String label, String labelEn, String icon, String chip, String method, boolean tiered, String howTo, long holders,
                                   double ratio, List<TierView> tiers) {

    /** 단계 하나 */
    public record TierView(String tier, int threshold, long holders) {
    }
}
