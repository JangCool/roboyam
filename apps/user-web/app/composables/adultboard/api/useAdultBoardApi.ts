import type { AdultBoardEnabledRequest, AdultBoardPinRequest, AdultBoardRelockMode, AdultBoardStatus } from '~/composables/adultboard/api/useAdultBoardApi.types';

/**
 * 19금 게시판 접근 설정·핀번호 API — 서버 주소 문자열(`/api/me/adult-board…`)이 있는 **유일한 곳**이다. 상태는 없다(공유 상태는 `useAdultBoard`).
 * 계약: `docs/features/adult-board-pin-contract.md`. 서버: `MyAdultBoardController`(여섯 요청 모두 같은 응답 `AdultBoardStatus`, `no-store`).
 *
 *  - `GET  /api/me/adult-board` → `AdultBoardStatus`
 *  - `PUT  /api/me/adult-board` 켜기 `{ enabled: true, password, pin }` · 끄기 `{ enabled: false }` → `AdultBoardStatus`(켠 직후 이 기기는 열림)
 *  - `PUT  /api/me/adult-board/pin` `{ password, pin }`(바꾸기·다시 정하기 — 틀린 횟수·잠금도 푼다) → `AdultBoardStatus`(이 기기는 열림)
 *  - `PUT  /api/me/adult-board/relock-mode` `{ relockMode }` → `AdultBoardStatus`
 *  - `POST /api/me/adult-board/unlock` `{ pin }` → `AdultBoardStatus`(이 기기 1시간)
 *  - `POST /api/me/adult-board/lock` → `AdultBoardStatus`(이 기기 지금 잠그기)
 *
 * 계정 비밀번호가 없는 회원(`passwordAvailable: false` — 소셜 로그인만)은 `password` 를 싣지 않는다(서버가 「최근 10분 안 로그인」으로 확인한다).
 *
 * 오류:
 *  - `400 VALIDATION` `fields.enabled`·`password`·`pin`·`relockMode`
 *  - 켜기·핀 바꾸기의 본인 확인 — `401 CURRENT_PASSWORD_WRONG` · `429 LOGIN_LOCKED`(+ `retryAfterSeconds`) · `401 REAUTH_REQUIRED`(비밀번호 없는 회원이 최근 로그인이 아님)
 *    · `403 IDENTITY_REQUIRED`·`IDENTITY_UNDERAGE` · `409 ADULT_BOARD_DISABLED`(핀 바꾸기·핀 확인 — 접근이 꺼져 있다)
 *  - 핀 확인 — `400 ADULT_BOARD_PIN_WRONG`(+ 최상위 `attemptsLeft` 1~4) · `423 ADULT_BOARD_PIN_LOCKED`(+ `lockedUntil`·`retryAfterSeconds`, 5번째 틀림은 바로 423)
 *  - 성인 전용 게시판을 여는 API(게시판·글 목록·레이아웃·태그·글·댓글·투표·추천·신고·토론·글쓰기)는 핀 미확인이면 `403 ADULT_BOARD_PIN_REQUIRED`(+ `lockedUntil`)
 *    — 읽는 곳은 `UtilAdultBoard.pinRequired`, 어느 호출이든 알리는 곳은 `useApiClient` → `useAdultBoardPinDemand`.
 */
const PATH = {
    root: '/api/me/adult-board',
    pin: () => `${PATH.root}/pin`,
    relockMode: () => `${PATH.root}/relock-mode`,
    unlock: () => `${PATH.root}/unlock`,
    lock: () => `${PATH.root}/lock`,
};

export function useAdultBoardApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 접근 설정과 이 기기의 핀 확인 상태 */
        status: (): Promise<AdultBoardStatus> => client.get<AdultBoardStatus>(PATH.root),

        /** 켜기(계정 비밀번호 + 핀번호) · 끄기 */
        setEnabled: (body: AdultBoardEnabledRequest): Promise<AdultBoardStatus> => client.put<AdultBoardStatus>(PATH.root, body),

        /** 핀번호 바꾸기·잊었을 때 다시 정하기 */
        changePin: (body: AdultBoardPinRequest): Promise<AdultBoardStatus> => client.put<AdultBoardStatus>(PATH.pin(), body),

        /** 핀번호 다시 묻기 기준 */
        setRelockMode: (relockMode: AdultBoardRelockMode): Promise<AdultBoardStatus> => client.put<AdultBoardStatus>(PATH.relockMode(), { relockMode }),

        /** 핀번호 확인 — 맞으면 이 기기 1시간 */
        unlock: (pin: string): Promise<AdultBoardStatus> => client.post<AdultBoardStatus>(PATH.unlock(), { pin }),

        /** 이 기기 지금 잠그기 */
        lock: (): Promise<AdultBoardStatus> => client.post<AdultBoardStatus>(PATH.lock()),
    };
}
