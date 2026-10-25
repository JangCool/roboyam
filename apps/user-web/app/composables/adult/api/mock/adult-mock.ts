// MOCK: 성인 확인 API(`GET·PUT /api/me/adult`)의 메모리 목업 — `useAdultApi.ts` 가 부른다. 서버가 붙으면 이 파일을 지운다. 새로고침하면 처음 상태(확인 전)로 돌아간다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` ─
//   adult-fail        읽기 실패(500)            adult-save-fail   저장 실패(500)
//   adult-confirmed   처음부터 성인 확인됨(1995년생)   adult-minor   처음부터 출생연도는 있지만 아직 성인이 아님(올해−17)
//
// ── 목업 규칙(가정 — 계약 「목업 가정」) ─
//   · 한국 시간 올해 − 출생연도 ≥ 19 이면 성인(만 19세가 되는 해의 1월 1일부터 — 청소년 보호법). 범위는 올해−100 ~ 올해−14.
//   · 칸 오류는 서버와 같은 `400 VALIDATION` + `fields.birthYear`·`fields.confirm`.

import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { AdultStatus, AdultUpdateRequest } from '~/composables/adult/api/useAdultApi.types';
import UtilAdult from '~/utils/util-adult';

let saved: { birthYear: number | null; confirmedAt: string | null } | null = null;

function seed(): { birthYear: number | null; confirmedAt: string | null } {
    if (saved) return saved;
    const flags = mockFlags();
    const year = UtilAdult.kstYear();
    if (flags.has('adult-confirmed')) saved = { birthYear: 1995, confirmedAt: new Date(Date.now() - 30 * 86_400_000).toISOString() };
    else if (flags.has('adult-minor')) saved = { birthYear: year - 17, confirmedAt: new Date().toISOString() };
    else saved = { birthYear: null, confirmedAt: null };
    return saved;
}

function view(): AdultStatus {
    const s = seed();
    const range = UtilAdult.rangeAt();
    const year = UtilAdult.kstYear();
    return mockClone({
        birthYear: s.birthYear,
        adult: s.birthYear !== null && year - s.birthYear >= UtilAdult.ADULT_AGE,
        method: s.birthYear !== null ? 'SELF' : null,
        confirmedAt: s.confirmedAt,
        minYear: range.minYear,
        maxYear: range.maxYear,
    });
}

export async function mockAdultStatus(): Promise<AdultStatus> {
    await mockDelay();
    if (mockFlags().has('adult-fail')) throw MOCK_ERRORS.internal();
    return view();
}

export async function mockAdultUpdate(body: AdultUpdateRequest): Promise<AdultStatus> {
    await mockDelay();
    if (mockFlags().has('adult-save-fail')) throw MOCK_ERRORS.internal();
    const range = UtilAdult.rangeAt();
    const fields: Record<string, string> = {};
    if (!Number.isInteger(body.birthYear) || body.birthYear < range.minYear) fields.birthYear = '출생연도를 다시 확인해 주세요.';
    else if (body.birthYear > range.maxYear) fields.birthYear = `만 ${UtilAdult.MIN_AGE}세 이상만 이용할 수 있어요.`;
    if (body.confirm !== true) fields.confirm = '입력한 출생연도가 사실과 같음을 확인해 주세요.';
    if (Object.keys(fields).length > 0) throw MOCK_ERRORS.validation(fields);
    saved = { birthYear: body.birthYear, confirmedAt: new Date().toISOString() };
    return view();
}
