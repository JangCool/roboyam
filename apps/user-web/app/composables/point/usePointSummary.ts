import type { PointSummary } from '~/composables/point/api/usePointApi.types';

/**
 * 내 포인트 요약(보유 포인트·오늘 적립 현황·지급 규칙) — 헤더 프로필 메뉴·햄버거 메뉴·내 정보(`/me`)·포인트 화면(`/me/points`)이 같이 읽는 공유 상태(`useState`).
 *
 * - **로그인한 사람마다 따로다** — 받아 둔 값의 주인(`ownerId`)이 지금 로그인한 사람과 다르면 버리고 다시 받는다(로그아웃·계정 전환).
 * - 열 때마다 최신으로 보이고 싶은 곳(메뉴가 열릴 때·화면에 들어올 때)은 `load` 를 부른다 — 받아 둔 값이 있으면 **그 값을 그대로 보이면서** 뒤에서 다시 받는다(깜빡임 없음).
 * - 실패해도 던지지 않는다 — 메뉴는 포인트 줄만 빠지고, 화면은 `status === 'error'` 로 오류 상태를 그린다(받아 둔 값이 있으면 그 값을 계속 보인다).
 *
 * 원본(community)의 출석(`applyAttendance`)·교환(`applyExchange`)은 로보얌에 기능이 없어 없다.
 * 서버와 말하는 것은 `usePointApi` 다. 같은 요청이 겹치면 늦게 온 옛 응답은 요청 번호로 버린다.
 */

/** 받기 상태 — `idle` 아직 안 물어봄 · `loading` 받는 중(받아 둔 값이 없을 때만) · `ready` · `error` */
export type PointSummaryStatus = 'idle' | 'loading' | 'ready' | 'error';

/** 요청 번호 — 모듈에 둔다(여러 화면이 이 컴포저블을 동시에 불러도 하나의 순서를 본다) */
let request = 0;

export function usePointSummary() {
    const api = usePointApi();
    const { user } = useAuth();

    const summary = useState<PointSummary | null>('point-summary', () => null);
    /** 받아 둔 값의 주인(로그인한 회원 ID) — 다르면 버린다 */
    const ownerId = useState<string | null>('point-summary-owner', () => null);
    const status = useState<PointSummaryStatus>('point-summary-status', () => 'idle');

    function reset(): void {
        request++;
        summary.value = null;
        ownerId.value = null;
        status.value = 'idle';
    }

    /** 서버에서 받아 온다. 받아 둔 값이 있으면 그대로 두고 뒤에서 바꾼다 */
    async function load(): Promise<void> {
        const me = user.value?.id ?? null;
        if (!me) return reset();
        if (ownerId.value !== me) reset();
        const mine = ++request;
        if (!summary.value) status.value = 'loading';
        try {
            const result = await api.points();
            if (mine !== request) return;
            summary.value = result;
            ownerId.value = me;
            status.value = 'ready';
        } catch {
            // 메뉴는 포인트 줄만 빠진다. 받아 둔 값이 있으면 그 값을 계속 보인다
            if (mine === request && !summary.value) status.value = 'error';
        }
    }

    // 로그아웃·계정 전환 — 다른 사람의 포인트가 남아 보이지 않게 비운다
    watch(
        () => user.value?.id ?? null,
        (id) => {
            if (id !== ownerId.value) reset();
        },
    );

    return { summary, status, load, reset };
}
