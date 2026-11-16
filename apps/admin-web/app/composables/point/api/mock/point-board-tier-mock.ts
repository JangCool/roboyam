// MOCK: 게시판 등급별 포인트(point-board-tier-contract) — 서버가 아직 등급 칸·확정 대기를 주지 않아 화면이 채운다.
// 서버가 붙으면 이 파일과 `usePointApi.ts` 의 `// MOCK:` 줄을 지운다(`// REAL:` 줄만 남긴다).

import type { PointMemberLots, PointPendingGrant, PointSettingsRequest, PointSettingsResponse } from '~/composables/point/api/usePointApi.types';
import UtilPoint from '~/utils/util-point';

/** 이 탭에서 마지막으로 저장한 등급 칸 — 서버가 모르는 칸을 버려도 화면을 다시 열 때까지는 저장한 값이 보이게(새로 고치면 기본값으로 돌아간다) */
let remembered: Pick<PointSettingsRequest, 'boardTiers' | 'infoSourceOnce' | 'excludeAiAndOps'> | null = null;

/**
 * 정책 응답에 등급 칸이 없으면 채운다 — 서버 값 › 방금 보낸 값 › 이 탭에서 저장한 값 › 계약 기본값 차례.
 * 일반 등급의 따라가는 칸(쓰면 바로·최소 글자 수)은 늘 기존 「글·댓글」 칸 값이다.
 */
export function withBoardTierMock(res: PointSettingsResponse, sent?: PointSettingsRequest): PointSettingsResponse {
    const partial = res as Partial<PointSettingsResponse>;
    if (sent) remembered = { boardTiers: sent.boardTiers, infoSourceOnce: sent.infoSourceOnce, excludeAiAndOps: sent.excludeAiAndOps };
    return {
        ...res,
        boardTiers: UtilPoint.completeTiers(partial.boardTiers ?? remembered?.boardTiers, res),
        infoSourceOnce: typeof partial.infoSourceOnce === 'boolean' ? partial.infoSourceOnce : (remembered?.infoSourceOnce ?? UtilPoint.DEFAULTS.infoSourceOnce),
        excludeAiAndOps: typeof partial.excludeAiAndOps === 'boolean' ? partial.excludeAiAndOps : (remembered?.excludeAiAndOps ?? UtilPoint.DEFAULTS.excludeAiAndOps),
    };
}

/** 지금부터 `hours` 시간 뒤(UTC ISO) */
function hoursFromNow(hours: number): string {
    return new Date(Date.now() + hours * 3_600_000).toISOString();
}

/**
 * 회원 포인트 묶음 응답에 확정 대기가 없으면 목업 몇 줄 — 회원번호 끝자리가 짝수인 회원만(빈 목록 모양도 보이게).
 * 금액은 계약 기본값(정보 추천 보너스 50P · 핵심 글 추천 보너스 150P · 핵심 글 우수 글 500P).
 */
export function withPendingMock(res: PointMemberLots, memberId: string): PointMemberLots {
    if (Array.isArray(res.pending)) return res;
    const last = Number(memberId.slice(-1));
    if (Number.isNaN(last) || last % 2 === 1) return { ...res, pending: [] };
    const pending: PointPendingGrant[] = [
        { id: 'mock-pending-1', reason: 'LIKE_BONUS', refType: 'POST', refId: 'mock-post-1', postTitle: '갤럭시 버즈 3 프로 한 달 사용기', amount: 150, releaseAt: hoursFromNow(5), createdAt: hoursFromNow(-19) },
        { id: 'mock-pending-2', reason: 'POST_FEATURED', refType: 'POST', refId: 'mock-post-1', postTitle: '갤럭시 버즈 3 프로 한 달 사용기', amount: 500, releaseAt: hoursFromNow(20), createdAt: hoursFromNow(-4) },
        { id: 'mock-pending-3', reason: 'LIKE_BONUS', refType: 'POST', refId: 'mock-post-2', postTitle: '반도체 수출 석 달째 늘어', amount: 50, releaseAt: hoursFromNow(23), createdAt: hoursFromNow(-1) },
    ];
    return { ...res, pending };
}
