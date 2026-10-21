import UtilTime, { type DisplayValues } from './util-time';

/**
 * 표시 값 폼(`DisplayValueForm`)의 **순수 규칙** — 미리보기와 서버 칸 오류 읽기. 상태도 요청도 없다.
 * 글자 만들기는 `UtilTime` 이 한다 — 이 파일은 날짜 글자를 직접 조립하지 않는다. 미리보기는 **고르는 중인 값**으로 `UtilTime.of(values)` 를 만들어 그린다(저장 전 값 — 앱 전체의 표시 값에는 영향이 없다).
 * 이 파일은 `vue`·`~/` 를 쓰지 않는 순수 TypeScript 다.
 */

/** 폼의 칸 이름 — 서버 오류 `fields` 의 이름과 같다 */
export type DisplayField = 'language' | 'zoneId' | 'timeFormat' | 'dateFormat';

/** 칸 아래에 보일 서버 문구 — 틀린 칸만 있다 */
export type DisplayFieldErrors = Partial<Record<DisplayField, string>>;

/** 미리보기 두 줄 — 오늘의 시각과 어제 이전의 날짜·시각 */
export type DisplayPreview = { today: string; past: string };

/** "어제 이전" 견본으로 쓰는 날수 — 오늘·어제 판정에 걸리지 않는 만큼 앞이다 */
const PAST_DAYS = 2;
const DAY_MS = 86_400_000;

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export default class UtilDisplayForm {
    /**
     * 미리보기 — 고르는 중인 값으로 "오늘"의 지금과 "어제 이전"의 한 시각을 그린다. 글 목록·상세의 모양과 같은 함수(`listLabel`·`full`)다.
     * @param {DisplayValues} values - 지금 폼의 값(저장 전 값).
     * @param {number} now - 지금(밀리초).
     * @returns {DisplayPreview} `today` 오늘 14:32 · `past` 2026.10.12 14:32(시간·날짜 형식 모두 반영).
     */
    static preview(values: DisplayValues, now: number): DisplayPreview {
        const time = UtilTime.of(values);
        return { today: time.listLabel(new Date(now).toISOString(), { now }), past: time.full(new Date(now - PAST_DAYS * DAY_MS).toISOString()) };
    }

    /**
     * 서버가 틀렸다고 알린 칸의 문구 — `400 VALIDATION` 의 `fields.language|zoneId|timeFormat|dateFormat`. 서버 문구를 그대로 쓴다.
     * @param {unknown} e - 잡은 오류(`fields` 를 가진 객체면 읽는다 — `ApiError` 가 그렇다).
     * @returns {DisplayFieldErrors} 틀린 칸의 문구. 칸 오류가 없으면 빈 객체.
     */
    static fieldErrors(e: unknown): DisplayFieldErrors {
        const fields = isObject(e) ? e.fields : null;
        if (!isObject(fields)) return {};
        const out: DisplayFieldErrors = {};
        for (const key of ['language', 'zoneId', 'timeFormat', 'dateFormat'] as const) {
            const text = fields[key];
            if (typeof text === 'string' && text !== '') out[key] = text;
        }
        return out;
    }
}
