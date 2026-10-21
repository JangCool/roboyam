// MOCK: 내 포인트 요약 API(`GET /api/me/points`)의 메모리 목업 — `usePointApi.ts` 가 이 파일의 함수를 부른다. 서버가 붙으면 `point/api/mock/` 와 함께 지운다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   points-fail       포인트 요약 요청 실패 → 메뉴에서 포인트 줄만 빠진다
//   points-attended   오늘 출석 완료 상태 → "오늘 출석하기" 안내가 빠진다

import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { PointSummary } from '~/composables/point/api/usePointApi.types';

export async function mockPoints(): Promise<PointSummary> {
    await mockDelay();
    const on = mockFlags();
    if (on.has('points-fail')) throw MOCK_ERRORS.internal();
    return { balance: 1280, ticketCount: 0, today: { date: new Date().toISOString().slice(0, 10), attended: on.has('points-attended') }, rules: { attendance: 10 } };
}
