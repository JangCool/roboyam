/**
 * 표시 값(언어·시간대·시간 형식·날짜 형식) API 가 주고받는 모양 — community `docs/api-display.md` 와 같다(roboyam 서버 계약은 아직 없다).
 * 값 자체(`DisplayValues`)는 공유 레이어 `@roboyam/display-time` 의 `UtilTime` 코어가 주인이다.
 */
import type { DisplayValues } from '#layers/display-time/utils/util-time';

/** 효과값이 어디서 왔나 — 개인 값(`USER`) · 기본값(`DEFAULT`) · 설정 행이 없어 시스템 폴백(`FALLBACK`) */
export type DisplayFrom = 'USER' | 'DEFAULT' | 'FALLBACK';

/** `GET /api/display/effective` — 비로그인도, 로그인했으면 개인 값까지 합친 값 */
export interface DisplayEffectiveResponse {
    values: DisplayValues;
    from: DisplayFrom;
    /** 서비스 시간대(서비스 하루의 기준) — 표시 시간대와 다를 때 "한국 시간 기준" 같은 덧말의 재료 */
    serviceZoneId: string;
}

/** `GET`·`PUT`·`DELETE /api/me/display` 응답 — 개인 값(없으면 null)·기본값·그 둘을 합친 효과값 */
export interface MyDisplayResponse {
    own: DisplayValues | null;
    defaults: DisplayValues;
    effective: DisplayValues;
}

/** `PUT /api/me/display` 요청 — 세 칸을 모두 보낸다 */
export interface MyDisplayRequest {
    values: DisplayValues;
}
