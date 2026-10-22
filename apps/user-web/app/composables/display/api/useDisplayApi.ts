import type { DisplayEffectiveResponse, MyDisplayRequest, MyDisplayResponse } from '~/composables/display/api/useDisplayApi.types';

/**
 * 표시 값(언어·시간대·시간 형식·날짜 형식) API — 서버 주소 문자열(`/api/display/effective`·`/api/me/display`)이 있는 **유일한 곳**이다 (frontend-guide 2장).
 * 화면(.vue)과 다른 컴포저블은 여기 함수만 부른다. 상태는 없다(상태는 `useDisplaySettings`). 계약은 `docs/features/display-contract.md`(원본 community `docs/api-display.md`).
 *
 * 규약(서버 `server/api` `com.community.display`, 모두 `Cache-Control: no-store`):
 *  - `effective` — 비로그인도. 로그인했으면 개인 값까지 합친 값 `{ values, from, serviceZoneId }`. `from` 은 `USER`·`DEFAULT`·`FALLBACK`(설정 행 없음 — 처음 값의 날짜 형식은 `YMD_DASH`).
 *    못 받으면(실패·`404`·깨진 응답) 부르는 쪽(`useDisplaySettings`)이 던지지 않고 기억값 → 시스템 폴백으로 내려간다.
 *  - `me`·`saveMe`·`resetMe` — 로그인한 본인 것만(사용자를 받지 않는다, 세션으로 안다). 비로그인은 `401 UNAUTHENTICATED`. 응답은 셋 모두 `{ own, defaults, effective }` 이고 `own` 은 정한 적이 없으면 null.
 *    `PUT` 은 `zoneId`·`timeFormat`·`dateFormat` 을 모두 보낸다(`language` 는 선택 — 빼면 지금 개인 값의 언어, 없으면 사이트 기본 언어). 틀린 칸은 한꺼번에 `400 VALIDATION` + `fields.zoneId|timeFormat|dateFormat|language`, 아무것도 저장하지 않는다.
 *    `DELETE` 는 기본값으로 되돌리기 — **200 + 본문**(`own: null`), 행이 없어도 성공. `PUT`·`DELETE` 는 CSRF(`403 CSRF_INVALID` 면 `useApiClient` 가 쿠키를 다시 받아 한 번 다시 보낸다).
 *
 * 서버 연결됨(2026-10-23).
 */
const PATH = {
    effective: '/api/display/effective',
    me: '/api/me/display',
};

export function useDisplayApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다). 로그인 상태를 묻는 `me` 가 쿠키를 내려준다
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 지금 사람이 볼 표시 값(개인 → 기본값 → 시스템 폴백) */
        effective: (signal?: AbortSignal): Promise<DisplayEffectiveResponse> => client.get<DisplayEffectiveResponse>(PATH.effective, { signal }),

        /** 내 개인 값·기본값·효과값 — 행을 만들지 않는다 */
        me: (signal?: AbortSignal): Promise<MyDisplayResponse> => client.get<MyDisplayResponse>(PATH.me, { signal }),

        /** 개인 값을 만들거나 고친다(세 칸 + 선택 `language`) */
        saveMe: (body: MyDisplayRequest): Promise<MyDisplayResponse> => client.put<MyDisplayResponse>(PATH.me, body),

        /** 기본값으로 되돌리기 — 개인 값(행)을 지운다. 200 + 본문 */
        resetMe: (): Promise<MyDisplayResponse> => client.del<MyDisplayResponse>(PATH.me),
    };
}
