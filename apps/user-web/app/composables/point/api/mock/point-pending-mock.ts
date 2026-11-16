// MOCK: 확정 대기(point-board-tier-contract — 후보 `GET /api/me/points/pending`). 서버가 생기면 이 파일과 `usePointApi.ts` 의 `// MOCK:` 줄을 지운다.
// `?mock=pending-empty` 면 빈 목록(구획이 보이지 않는 모양), `?mock=pending-frozen` 이면 포인트 동결 중(3일 뒤까지) 안내가 붙는다.

import type { MyPointPendingResult } from '~/composables/point/api/usePointApi.types';
import { mockDelay, mockFlags } from '~/composables/common/mock/mock-util';

/** 지금부터 `hours` 시간 뒤(UTC ISO) */
function hoursFromNow(hours: number): string {
    return new Date(Date.now() + hours * 3_600_000).toISOString();
}

/** 확정 예정이 가까운 것부터 — 금액은 계약 기본값(핵심 글 추천 보너스 150P · 핵심 글 우수 글 500P · 정보 추천 보너스 50P) */
export async function mockPending(): Promise<MyPointPendingResult> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('pending-empty')) return { items: [], freeze: null };
    return {
        items: [
            { id: 'mock-pending-1', reason: 'LIKE_BONUS', amount: 150, releaseAt: hoursFromNow(5), target: { postId: 'mock-post-1', title: '갤럭시 버즈 3 프로 한 달 사용기', available: true } },
            { id: 'mock-pending-2', reason: 'POST_FEATURED', amount: 500, releaseAt: hoursFromNow(20), target: { postId: 'mock-post-1', title: '갤럭시 버즈 3 프로 한 달 사용기', available: true } },
            { id: 'mock-pending-3', reason: 'LIKE_BONUS', amount: 50, releaseAt: hoursFromNow(23), target: { postId: 'mock-post-2', title: null, available: false } },
        ],
        freeze: flags.has('pending-frozen') ? { until: hoursFromNow(72) } : null,
    };
}
