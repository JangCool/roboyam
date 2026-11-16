import type { LottoDrawRequest, LottoDrawResult, LottoStatus, RouletteSpinResult, RouletteStatus } from '~/composables/pointgame/api/usePointGameApi.types';

/**
 * 포인트 게임 API(룰렛 · 로또 번호 뽑기) — 서버 주소 문자열(`/api/me/point-games…`)이 있는 **유일한 곳**이다. 상태는 없다(화면이 `ref` 로 든다).
 * 서버 `MyPointGameController`(server/api). 로그인 필요(401) · 본인 것만 · 적립 포인트만 쓴다(충전 포인트 안 씀) · 모두 `no-store`.
 * 거절 차례(돌리기·뽑기): 401 → 정지·탈퇴 진행 중 `403 ACCOUNT_RESTRICTED` → 사업자 `403 BUSINESS_ACCOUNT_RESTRICTED` → 포인트 동결 `403 MEMBER_RESTRICTED`.
 * 읽기(GET 둘)는 401 → 사업자 403 만. 사업자 전용 계정은 화면이 미리 거른다(403 을 부르지 않는다).
 *
 *  - `GET  /api/me/point-games/roulette` → `RouletteStatus`
 *  - `POST /api/me/point-games/roulette/spins` → `RouletteSpinResult` — `409 POINT_GAME_DAILY_LIMIT`(횟수 먼저) · `409 POINT_INSUFFICIENT`
 *  - `GET  /api/me/point-games/lotto` → `LottoStatus`
 *  - `POST /api/me/point-games/lotto/draws` → `LottoDrawResult` — `400 VALIDATION fields.sets|mode|fixed|excluded`(서로 다른 N세트를 못 만들면 `fields.excluded`) → `409 LOTTO_STORAGE_FULL` → `409 POINT_INSUFFICIENT`
 *  - `DELETE /api/me/point-games/lotto/draws/{id}` → 204(이미 없으면 그대로 204, 남의 줄·틀린 ID `404 LOTTO_DRAW_NOT_FOUND`)
 */
const PATH = {
    roulette: '/api/me/point-games/roulette',
    rouletteSpins: '/api/me/point-games/roulette/spins',
    lotto: '/api/me/point-games/lotto',
    lottoDraws: '/api/me/point-games/lotto/draws',
    lottoDraw: (id: string) => `/api/me/point-games/lotto/draws/${encodeURIComponent(id)}`,
};

export function usePointGameApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 룰렛 — 오늘 남은 횟수·상품표·적립 잔액 */
        roulette(): Promise<RouletteStatus> {
            return client.get<RouletteStatus>(PATH.roulette);
        },

        /** 룰렛 한 번 — 결과는 서버가 정한다 */
        spin(): Promise<RouletteSpinResult> {
            return client.post<RouletteSpinResult>(PATH.rouletteSpins);
        },

        /** 로또 번호 — 값·보관 한도·내 번호·당첨 확인 링크 */
        lotto(): Promise<LottoStatus> {
            return client.get<LottoStatus>(PATH.lotto);
        },

        /** 번호 뽑기(1세트 또는 묶음) — 뽑은 세트는 내 번호에 남는다 */
        draw(body: LottoDrawRequest): Promise<LottoDrawResult> {
            return client.post<LottoDrawResult>(PATH.lottoDraws, { sets: body.sets, mode: body.mode, fixed: [...body.fixed], excluded: [...body.excluded] });
        },

        /** 내 번호 한 줄 지우기 */
        removeDraw(id: string): Promise<void> {
            return client.del<void>(PATH.lottoDraw(id));
        },
    };
}
