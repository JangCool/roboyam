import type { RouletteStatus } from '~/composables/pointgame/api/usePointGameApi.types';
import UtilBusiness from '~/utils/util-business';

/**
 * 오늘 남은 룰렛 횟수 — 홈 가젯 `POINT_GAMES` 가 읽는 공유 상태(`useState`). 가젯 데이터는 회원마다 달라 서버가 싣지 않는다(`data: null` — point-games-contract 「홈 가젯」).
 *
 * - **로그인했고 사업자 전용 계정이 아닐 때만** 묻는다(사업자는 서버가 `403 BUSINESS_ACCOUNT_RESTRICTED` — 부르지 않게 미리 거른다). 비로그인·사업자는 `idle` 그대로.
 * - 받아 둔 값의 주인(`ownerId`)이 지금 로그인한 사람과 다르면 버린다(로그아웃·계정 전환). 받는 동안에도 받아 둔 값을 그대로 보인다.
 * - 실패해도 던지지 않는다 — 가젯은 횟수 없이 입구만 보인다.
 * 서버와 말하는 것은 `usePointGameApi` 다. 같은 요청이 겹치면 늦게 온 옛 응답은 요청 번호로 버린다.
 */

/** 오늘 횟수 — 하루 최대 · 오늘 돌린 수 */
export interface RouletteToday {
    dailyLimit: number;
    usedToday: number;
}

/** 요청 번호 — 모듈에 둔다(여러 가젯이 동시에 불러도 하나의 순서를 본다) */
let request = 0;

export function useRouletteToday() {
    const api = usePointGameApi();
    const { user } = useAuth();

    const today = useState<RouletteToday | null>('roulette-today', () => null);
    const ownerId = useState<string | null>('roulette-today-owner', () => null);

    /** 물어볼 수 있는 사람인가 — 로그인했고 사업자 전용 계정이 아님 */
    const eligible = computed(() => Boolean(user.value?.id) && !UtilBusiness.isBusinessAccount(user.value?.role));
    /** 오늘 남은 횟수 — 모르면 null */
    const remaining = computed(() => (today.value ? Math.max(0, today.value.dailyLimit - today.value.usedToday) : null));

    function reset(): void {
        request++;
        today.value = null;
        ownerId.value = null;
    }

    /** 룰렛 상태를 받아 오늘 횟수만 남긴다 */
    function apply(status: Pick<RouletteStatus, 'dailyLimit' | 'usedToday'>): void {
        today.value = { dailyLimit: status.dailyLimit, usedToday: status.usedToday };
        ownerId.value = user.value?.id ?? null;
    }

    async function load(): Promise<void> {
        const me = user.value?.id ?? null;
        if (!me || !eligible.value) return reset();
        if (ownerId.value !== me) reset();
        const mine = ++request;
        try {
            const status = await api.roulette();
            if (mine !== request) return;
            apply(status);
        } catch {
            // 가젯은 횟수 없이 입구만 보인다
        }
    }

    return { today, remaining, eligible, load, reset };
}
