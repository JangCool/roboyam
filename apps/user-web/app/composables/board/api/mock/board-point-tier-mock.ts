// MOCK: 게시판 포인트 등급(point-board-tier-contract — 게시판 정보 응답에 붙을 예정). 서버가 `pointTier` 를 주면 이 파일과 `useBoardApi.ts` 의 `// MOCK:` 줄을 지운다.
// 기본 배정(계약 「기본 배정」): 정보 = 뉴스(`news`)·핫딜(`hotdeal`, 핫딜 종류 게시판), 핵심 글 = 사용기(`review`)·팁·강좌(`tip`), 그 밖 = 일반. 금액은 계약 기본값, 추천 문턱은 정책 기본값 5.

import type { BoardDetail, BoardPointTier, BoardPointTierView } from '~/composables/board/api/useBoardApi.types';

const INFO_SLUGS: ReadonlySet<string> = new Set(['news', 'hotdeal']);
const CORE_SLUGS: ReadonlySet<string> = new Set(['tip', 'review']);
const LIKE_THRESHOLD = 5;

const DEFAULTS: Record<BoardPointTier, Omit<BoardPointTierView, 'tier' | 'likeThreshold'>> = {
    NORMAL: { postPoints: 20, likeBonusPoints: 10, featuredPoints: 0, minLength: 50, minImages: 0, holdHours: 0 },
    INFO: { postPoints: 30, likeBonusPoints: 50, featuredPoints: 100, minLength: 100, minImages: 0, holdHours: 24 },
    CORE: { postPoints: 50, likeBonusPoints: 150, featuredPoints: 500, minLength: 500, minImages: 1, holdHours: 24 },
};

/** 게시판 응답에 `pointTier` 가 없으면 slug 기본 배정으로 채운다(서버가 주면 그대로) */
export function withPointTierMock(detail: BoardDetail): BoardDetail {
    if (detail.pointTier !== undefined) return detail;
    const tier: BoardPointTier = INFO_SLUGS.has(detail.slug) || detail.kind === 'DEAL' ? 'INFO' : CORE_SLUGS.has(detail.slug) ? 'CORE' : 'NORMAL';
    return { ...detail, pointTier: { tier, likeThreshold: LIKE_THRESHOLD, ...DEFAULTS[tier] } };
}
