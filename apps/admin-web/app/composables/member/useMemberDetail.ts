import type { ApiError } from '~/composables/common/useApiClient';
import type { MemberDetail } from '~/composables/member/api/useMemberApi.types';

/**
 * 회원 상세 Drawer 가 보는 상태 — 한 회원의 상세를 한 번 받아 들고 있다. 이 화면(회원 관리)이 부를 때마다 새 상태가 생긴다(`useState` 가 아니다 — 여러 화면이 공유하지 않는다).
 * 서버 호출은 `useMemberApi` 한 겹이고, 컴포넌트(`MemberDetailDrawer`)는 이 상태를 props 로 받는다.
 * 다른 회원을 연 뒤에 늦게 도착한 앞 요청의 응답은 버린다(번호표). 열 때마다 새로 받는다 — 상태가 바뀌었을 수 있다.
 *
 * ⚠ 컴포넌트 setup 에서 부른다(오류를 `ApiError` 로 바꾸는 함수가 번역 함수를 그때 붙잡는다).
 *
 * @returns `detail` 받은 상세 · `loading` 받는 중 · `error` 못 받았을 때의 오류 · `open(memberId)` 받기 · `retry()` 마지막 회원을 다시 · `clear()` 비우기.
 */
export function useMemberDetail() {
    const api = useMemberApi();
    const errorOf = useApiErrorOf();

    const detail = ref<MemberDetail | null>(null);
    const loading = ref(false);
    const error = ref<ApiError | null>(null);
    let lastId: string | null = null;
    let ticket = 0;

    async function open(memberId: string): Promise<void> {
        lastId = memberId;
        const mine = ++ticket;
        // 다른 회원이면 앞 회원의 내용을 지운다 — 새 회원 머리 아래 옛 내용이 비치면 안 된다
        if (detail.value?.memberNo !== memberId) detail.value = null;
        loading.value = true;
        error.value = null;
        try {
            const found = await api.getMember(memberId);
            if (mine === ticket) detail.value = found;
        } catch (e) {
            if (mine === ticket) {
                detail.value = null;
                error.value = errorOf(e);
            }
        } finally {
            if (mine === ticket) loading.value = false;
        }
    }

    function retry(): void {
        if (lastId) void open(lastId);
    }

    function clear(): void {
        ticket++;
        lastId = null;
        detail.value = null;
        error.value = null;
        loading.value = false;
    }

    return { detail, loading, error, open, retry, clear };
}
