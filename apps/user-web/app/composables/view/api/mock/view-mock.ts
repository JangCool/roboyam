// MOCK: 보기 설정 API(`GET·PUT /api/me/settings`)의 메모리 목업 — `useViewSettingsApi.ts` 가 부른다. 서버가 붙으면 이 파일을 지운다.
// 새로고침하면 목업의 「서버 값」은 기본값으로 돌아가지만 화면은 이 브라우저에 저장해 둔 값(`roboyam:view-settings`)으로 먼저 그린 뒤 서버 값(기본값)이 이긴다 —
// 목업 단계에서 새로고침 뒤에도 고른 값이 남게, 처음 읽을 때 이 브라우저의 값을 서버 값으로 삼는다(가정 — 계약 「목업 가정」).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` ─
//   view-fail        읽기 실패(500)        view-save-fail   저장 실패(500 — 바꾼 칸이 되돌아간다)

import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { ViewSettings } from '~/composables/view/api/useViewSettingsApi.types';
import UtilViewSettings from '~/utils/util-view-settings';

let stored: ViewSettings | null = null;

function seed(): ViewSettings {
    if (stored) return stored;
    let local: ViewSettings | null = null;
    try {
        local = UtilViewSettings.parseStored(window.localStorage.getItem(UtilViewSettings.STORAGE_KEY));
    } catch {
        local = null;
    }
    stored = local ?? UtilViewSettings.defaults();
    return stored;
}

export async function mockViewSettings(): Promise<ViewSettings> {
    await mockDelay();
    if (mockFlags().has('view-fail')) throw MOCK_ERRORS.internal();
    return mockClone(seed());
}

export async function mockUpdateViewSettings(body: ViewSettings): Promise<ViewSettings> {
    await mockDelay();
    if (mockFlags().has('view-save-fail')) throw MOCK_ERRORS.internal();
    const fields: Record<string, string> = {};
    if (!UtilViewSettings.THEMES.includes(body.theme)) fields.theme = '야간 모드 값을 확인해 주세요.';
    if (!UtilViewSettings.TEXT_SCALES.includes(body.textScale)) fields.textScale = '글자 크기 값을 확인해 주세요.';
    if (!UtilViewSettings.COMMENT_ORDERS.includes(body.commentOrder)) fields.commentOrder = '댓글 정렬 값을 확인해 주세요.';
    if (Object.keys(fields).length > 0) throw MOCK_ERRORS.validation(fields);
    stored = { theme: body.theme, textScale: body.textScale, commentOrder: body.commentOrder };
    return mockClone(stored);
}
