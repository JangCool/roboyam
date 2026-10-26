import type { ApiError } from '~/composables/common/useApiClient';
import type { PointMemberLots } from '~/composables/point/api/usePointApi.types';

/** 묶음 한 쪽의 크기 — 한 회원의 묶음은 수천 개일 수 있어 쪽으로 나눈다 */
const LOTS_PAGE_SIZE = 10;

/**
 * 회원 상세 서랍의 「등급·포인트」 구획이 보는 포인트 묶음 상태(커머스 1-1) — 적립/충전 잔액 · 소멸 예정 · 미회수 · 묶음 한 쪽.
 * 회원 관리 화면이 부를 때마다 새 상태가 생긴다(`useMemberRestrictions` 와 같은 방식 — 여러 화면이 공유하지 않는다). 서버 호출은 `usePointApi().memberLots` 한 겹.
 * 다른 회원을 열면 거르기(남은 것만)·쪽(첫 쪽)을 처음으로 돌린다. 늦게 도착한 앞 요청의 응답은 버린다(번호표).
 * 거르기·쪽을 바꾸는 동안에는 받아 둔 값을 그대로 보인다(`loading` 으로 흐리게) — 실패하면 값을 비우고 오류를 둔다.
 *
 * ⚠ 컴포넌트 setup 에서 부른다.
 *
 * @returns `data`(아직 없으면 null) · `loading` · `error` · `active`(남은 것만) · `page`(0부터) · `size` · `open(memberId)` · `query({ active, page })` · `retry()` · `clear()`.
 */
export function useMemberPointLots() {
    const api = usePointApi();
    const errorOf = useApiErrorOf();

    const data = ref<PointMemberLots | null>(null);
    const loading = ref(false);
    const error = ref<ApiError | null>(null);
    const active = ref(true);
    const page = ref(0);
    let memberId: string | null = null;
    let ticket = 0;

    async function load(): Promise<void> {
        const id = memberId;
        if (!id) return;
        const mine = ++ticket;
        loading.value = true;
        error.value = null;
        try {
            const found = await api.memberLots(id, { active: active.value, page: page.value, size: LOTS_PAGE_SIZE });
            if (mine === ticket) data.value = found;
        } catch (e) {
            if (mine === ticket) {
                data.value = null;
                error.value = errorOf(e);
            }
        } finally {
            if (mine === ticket) loading.value = false;
        }
    }

    function open(id: string): void {
        if (memberId !== id) {
            data.value = null;
            active.value = true;
            page.value = 0;
        }
        memberId = id;
        void load();
    }

    /** 거르기(남은 것만 / 전체)나 쪽을 바꾼다 — 거르기를 바꾸면 첫 쪽부터 */
    function query(next: { active: boolean; page: number }): void {
        page.value = next.active !== active.value ? 0 : Math.max(0, next.page);
        active.value = next.active;
        void load();
    }

    function retry(): void {
        void load();
    }

    function clear(): void {
        ticket++;
        memberId = null;
        data.value = null;
        error.value = null;
        loading.value = false;
        active.value = true;
        page.value = 0;
    }

    return { data, loading, error, active, page, size: LOTS_PAGE_SIZE, open, query, retry, clear };
}
