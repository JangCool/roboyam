import type { LottoDrawRequest, LottoDrawResult, LottoStatus, RouletteSpinResult, RouletteStatus } from '~/composables/pointgame/api/usePointGameApi.types';
// MOCK:
import PointGameMock from '~/composables/pointgame/api/mock/point-game-mock';

/**
 * 포인트 게임 API(룰렛 · 로또 번호 뽑기) — 서버 주소 문자열(`/api/me/point-games…`)이 있는 **유일한 곳**이다. 상태는 없다(화면이 `ref` 로 든다).
 * ⚠ 목업(화면 확정 전, 2026-11-16) — 메서드마다 `// MOCK:` 줄이 메모리 목업(`mock/point-game-mock.ts`, 회원별)을 부르고 `// REAL:` 줄이 후보 주소다. 서버가 생기면 MOCK 줄과 목업 import 만 지운다.
 * 로그인 필요(401) · 본인 것만 · 적립 포인트만 쓴다(충전 포인트 안 씀). 사업자 전용 계정은 포인트가 없어 서버가 `403 BUSINESS_ACCOUNT_RESTRICTED` 를 줄 자리다(화면은 미리 감춘다).
 *
 *  - `GET  /api/me/point-games/roulette` → `RouletteStatus`(`no-store`)
 *  - `POST /api/me/point-games/roulette/spins` → `RouletteSpinResult` — 차감·추첨·지급을 한 트랜잭션에서. `409 POINT_GAME_DAILY_LIMIT` · `409 POINT_INSUFFICIENT` · `403 ACCOUNT_RESTRICTED`(정지)
 *  - `GET  /api/me/point-games/lotto` → `LottoStatus`(내 번호 최신이 위 · 당첨 확인 링크는 관리자 설정값)
 *  - `POST /api/me/point-games/lotto/draws` → `LottoDrawResult` — `400 VALIDATION fields.sets|fixed|excluded` · `409 LOTTO_STORAGE_FULL` · `409 POINT_INSUFFICIENT`
 *  - `DELETE /api/me/point-games/lotto/draws/{id}` → 204(이미 없으면 그대로 204)
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
    // MOCK: 목업은 회원별 메모리라 지금 로그인한 회원 ID 로 나눈다(서버는 세션으로 안다)
    const { user } = useAuth();
    // MOCK:
    const owner = () => user.value?.id ?? 'guest';

    return {
        /** 룰렛 — 오늘 남은 횟수·상품표·적립 잔액 */
        async roulette(): Promise<RouletteStatus> {
            // MOCK:
            if (owner()) return PointGameMock.rouletteStatus(owner());
            // REAL:
            return client.get<RouletteStatus>(PATH.roulette);
        },

        /** 룰렛 한 번 — 결과는 서버가 정한다 */
        async spin(): Promise<RouletteSpinResult> {
            // MOCK:
            if (owner()) return PointGameMock.spin(owner());
            // REAL:
            return client.post<RouletteSpinResult>(PATH.rouletteSpins);
        },

        /** 로또 번호 — 값·보관 한도·내 번호·당첨 확인 링크 */
        async lotto(): Promise<LottoStatus> {
            // MOCK:
            if (owner()) return PointGameMock.lottoStatus(owner());
            // REAL:
            return client.get<LottoStatus>(PATH.lotto);
        },

        /** 번호 뽑기(1세트 또는 묶음) — 뽑은 세트는 내 번호에 남는다 */
        async draw(body: LottoDrawRequest): Promise<LottoDrawResult> {
            // MOCK:
            if (owner()) return PointGameMock.draw(owner(), body);
            // REAL:
            return client.post<LottoDrawResult>(PATH.lottoDraws, body);
        },

        /** 내 번호 한 줄 지우기 */
        async removeDraw(id: string): Promise<void> {
            // MOCK:
            if (owner()) return PointGameMock.removeDraw(owner(), id);
            // REAL:
            return client.del<void>(PATH.lottoDraw(id));
        },
    };
}
