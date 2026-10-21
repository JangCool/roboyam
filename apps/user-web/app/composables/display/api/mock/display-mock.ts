// MOCK: 표시 값(언어·시간대·시간 형식·날짜 형식) API(`/api/display/effective`·`/api/me/display`)의 메모리 목업 — 서버(`server/`)에 아직 없어서 `useDisplayApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 `display/api/mock/` 와 함께 지운다.
//
// 규약(원본): community `docs/api-display.md` — 효과값은 개인 값 → 사이트 기본값 → 시스템 폴백 순서로 서버 한 곳에서 합친다.
//  · 사이트 기본값(`defaults`): 기본은 "설정 행 없음" 이라 시스템 폴백(`UtilTime.FALLBACK_DISPLAY`: Asia/Seoul · 24시간 · `2026.10.02`)이고 `from` 은 `FALLBACK`.
//  · 사용자 선택값(`own`): 로그인한 사람마다 메모리에만 둔다(새로고침하면 사라진다). 있으면 효과값이 이것이고 `from` 은 `USER`.
//  · 개인 값 API(`me`·`saveMe`·`resetMe`)는 로그인한 본인 것만 — 비로그인은 `401 UNAUTHENTICATED`. `PUT` 은 세 칸을 모두 받고 틀린 값은 `400 VALIDATION` + `fields`.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   display-site-ny     사이트 기본값이 설정돼 있음(America/New_York · 12시간 · `월/일/연`) → 개인 값이 없으면 이것이 효과값(`from: DEFAULT`)
//   display-fail        효과값 조회(`effective`)가 실패 → 기억값 → 시스템 폴백 그대로 쓴다
//   display-save-fail   개인 값 저장이 `400 VALIDATION`(`fields.zoneId`) 으로 거절된다

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { DisplayEffectiveResponse, MyDisplayRequest, MyDisplayResponse } from '~/composables/display/api/useDisplayApi.types';
import UtilDisplayValues from '#layers/display-time/utils/util-display-values';
import UtilTime, { type DisplayValues } from '#layers/display-time/utils/util-time';

/** 사이트 기본값을 정해 둔 경우(스위치)의 값 */
const SITE_NY: DisplayValues = { zoneId: 'America/New_York', timeFormat: 'H12', dateFormat: 'MDY_SLASH', language: 'en' };

/** 사람마다의 개인 값 — 목업 서버의 메모리 */
const own = new Map<string, DisplayValues>();

function siteDefaults(): { values: DisplayValues; configured: boolean } {
    return mockFlags().has('display-site-ny') ? { values: SITE_NY, configured: true } : { values: UtilDisplayValues.copy(UtilTime.FALLBACK_DISPLAY), configured: false };
}

function mine(userId: string | null): MyDisplayResponse {
    if (!userId) throw MOCK_ERRORS.unauthenticated();
    const defaults = siteDefaults().values;
    const own1 = own.get(userId) ?? null;
    return { own: own1 ? UtilDisplayValues.copy(own1) : null, defaults, effective: UtilDisplayValues.copy(own1 ?? defaults) };
}

export async function mockDisplayEffective(userId: string | null, signal?: AbortSignal): Promise<DisplayEffectiveResponse> {
    await mockDelay(signal);
    if (mockFlags().has('display-fail')) throw MOCK_ERRORS.internal();
    const personal = userId ? own.get(userId) : undefined;
    if (personal) return { values: UtilDisplayValues.copy(personal), from: 'USER', serviceZoneId: UtilTime.SERVICE_ZONE };
    const site = siteDefaults();
    return { values: site.values, from: site.configured ? 'DEFAULT' : 'FALLBACK', serviceZoneId: UtilTime.SERVICE_ZONE };
}

export async function mockDisplayMe(userId: string | null, signal?: AbortSignal): Promise<MyDisplayResponse> {
    await mockDelay(signal);
    return mine(userId);
}

export async function mockDisplaySaveMe(userId: string | null, body: MyDisplayRequest): Promise<MyDisplayResponse> {
    await mockDelay();
    if (!userId) throw MOCK_ERRORS.unauthenticated();
    const clean = UtilDisplayValues.sanitize(body.values);
    if (!clean || mockFlags().has('display-save-fail')) {
        throw new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 내용을 다시 확인해 주세요.', fields: { zoneId: '쓸 수 없는 시간대예요.' } });
    }
    own.set(userId, clean);
    return mine(userId);
}

export async function mockDisplayResetMe(userId: string | null): Promise<MyDisplayResponse> {
    await mockDelay();
    if (!userId) throw MOCK_ERRORS.unauthenticated();
    own.delete(userId); // 행이 없어도 성공
    return mine(userId);
}
