import { mockDisplayEffective, mockDisplayMe, mockDisplayResetMe, mockDisplaySaveMe } from '~/composables/display/api/mock/display-mock';
import type { DisplayEffectiveResponse, MyDisplayRequest, MyDisplayResponse } from '~/composables/display/api/useDisplayApi.types';

/**
 * 표시 기본값(시간대·시간 형식·날짜 형식) API — 서버 주소 문자열(`/api/display/effective`·`/api/me/display`)이 있는 **유일한 곳**이다 (frontend-guide 2장).
 * 화면(.vue)과 다른 컴포저블은 여기 함수만 부른다. 상태는 없다(상태는 `useDisplaySettings`). 규약은 community `docs/api-display.md`.
 *
 * 규약:
 *  - `effective` — 비로그인도. 로그인했으면 개인 값까지 합친 값 `{ values, from, serviceZoneId }`. `Cache-Control: no-store`.
 *    서버에 이 API 가 없으면 `404` 이므로 부르는 쪽(`useDisplaySettings`)이 던지지 않고 기억값 → 시스템 폴백으로 내려간다.
 *  - `me`·`saveMe`·`resetMe` — 로그인한 본인 것만(사용자를 받지 않는다). 응답은 셋 모두 `{ own, defaults, effective }` 이고 `own` 은 정한 적이 없으면 null.
 *    `PUT` 은 세 칸을 모두 보낸다(틀린 값은 `400 VALIDATION` + `fields.zoneId|timeFormat|dateFormat`), `DELETE` 는 기본값으로 되돌리기(행이 없어도 성공).
 *
 * ⚠ MOCK: 지금은 서버에 이 API 가 없어서 모든 메서드가 메모리 목업(`./mock/display-mock.ts`)을 부른다. 메서드 이름·인자·반환 타입은 실제 호출과 같다.
 * 서버가 붙으면 각 메서드의 `// MOCK:` 줄을 바로 아래 `// REAL:` 줄로 바꾸고, 파일 위의 목업 import 와 `./mock/` 폴더를 지운다. 스위치(`?mock=…`)는 목업 파일 머리 주석에 있다.
 */
const PATH = {
    effective: '/api/display/effective',
    me: '/api/me/display',
};

export function useDisplayApi() {
    // REAL: refreshCsrf: CSRF 쿠키를 다시 받는 호출 — 로그인 상태를 묻는 `me` 가 쿠키를 내려준다
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;
    // MOCK: 목업 서버가 "누구의 개인 값인가"를 알아야 한다 — 로그인 상태를 키로 집어 둔다(컴포저블은 setup 에서만 부를 수 있다). 실제 서버는 세션으로 안다
    const user = useState<{ id: string } | null>(AUTH_USER_STATE_KEY);
    const userId = (): string | null => user.value?.id ?? null;

    return {
        /** 지금 사람이 볼 표시 값(개인 → 기본값 → 시스템 폴백) */
        effective: (signal?: AbortSignal): Promise<DisplayEffectiveResponse> =>
            // MOCK:
            mockDisplayEffective(userId(), signal),
        // REAL: client.get<DisplayEffectiveResponse>(PATH.effective, { signal }),

        /** 내 개인 값·기본값·효과값 — 행을 만들지 않는다 */
        me: (signal?: AbortSignal): Promise<MyDisplayResponse> =>
            // MOCK:
            mockDisplayMe(userId(), signal),
        // REAL: client.get<MyDisplayResponse>(PATH.me, { signal }),

        /** 개인 값을 만들거나 고친다(세 칸 모두) */
        saveMe: (body: MyDisplayRequest): Promise<MyDisplayResponse> =>
            // MOCK:
            mockDisplaySaveMe(userId(), body),
        // REAL: client.put<MyDisplayResponse>(PATH.me, body),

        /** 기본값으로 되돌리기 — 개인 값(행)을 지운다 */
        resetMe: (): Promise<MyDisplayResponse> =>
            // MOCK:
            mockDisplayResetMe(userId()),
        // REAL: client.del<MyDisplayResponse>(PATH.me),
    };
}
