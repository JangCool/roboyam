import type { ApiError } from '~/composables/common/useApiClient';
import type { WithdrawnContact, WithdrawnContactRevealResult } from '~/composables/member/api/useWithdrawnContactApi.types';

/**
 * 탈퇴 회원 연락처(분리 보관) 블록이 보는 상태 — 보관 여부·파기 예정일·열람 기록, 열람한 이메일과 다시 가릴 때까지 남은 초(commerce.md 17-2·10-9).
 * 회원 관리 화면이 부를 때마다 새 상태가 생긴다. 서버 호출은 `useWithdrawnContactApi` 한 겹.
 *
 * - 열람한 이메일은 **이 상태의 메모리에만** 둔다(`forms.md` 2-1 — 저장소·URL·로그·토스트에 남기지 않는다). 서버가 정한 `maskAt` 이 지나면 지우고, 다른 회원을 열거나 서랍을 닫아도 지운다.
 * - 열람에 성공하면 열람 기록이 늘었으니 블록을 다시 받는다(받는 동안 이메일은 그대로 보인다).
 * ⚠ 컴포넌트 setup 에서 부른다.
 *
 * @returns `contact` · `loading` · `error` · `revealed`(열린 이메일, 없으면 null) · `remaining`(다시 가릴 때까지 초) ·
 *          `open(memberId)` · `retry()` · `clear()` · `mask()` 지금 가리기 · `reveal(reason)` 성공하면 null, 실패하면 `ApiError`.
 */
export function useWithdrawnContact() {
    const api = useWithdrawnContactApi();
    const errorOf = useApiErrorOf();

    const contact = ref<WithdrawnContact | null>(null);
    const loading = ref(false);
    const error = ref<ApiError | null>(null);
    const revealed = ref<WithdrawnContactRevealResult | null>(null);
    /** 다시 가리는 순간(밀리초) — 카운트다운이 이것을 본다 */
    const maskEndsAt = ref(0);
    const countdown = useCountdown(maskEndsAt, () => mask());
    let memberId: string | null = null;
    let ticket = 0;

    function mask(): void {
        revealed.value = null;
        countdown.stop();
    }

    async function load(id: string, keepRevealed: boolean): Promise<void> {
        const mine = ++ticket;
        if (!keepRevealed) loading.value = true;
        error.value = null;
        try {
            const found = await api.get(id);
            if (mine === ticket) contact.value = found;
        } catch (e) {
            if (mine === ticket && !keepRevealed) {
                contact.value = null;
                error.value = errorOf(e);
            }
        } finally {
            if (mine === ticket) loading.value = false;
        }
    }

    function open(id: string): Promise<void> {
        if (memberId !== id) {
            contact.value = null;
            mask();
        }
        memberId = id;
        return load(id, false);
    }

    function retry(): void {
        if (memberId) void open(memberId);
    }

    function clear(): void {
        ticket++;
        memberId = null;
        contact.value = null;
        error.value = null;
        loading.value = false;
        mask();
    }

    async function reveal(reason: string): Promise<ApiError | null> {
        const id = memberId;
        if (!id) return null;
        try {
            const result = await api.reveal(id, { reason });
            if (memberId !== id) return null;
            revealed.value = result;
            maskEndsAt.value = Date.parse(result.maskAt);
            void load(id, true);
            return null;
        } catch (e) {
            const failure = errorOf(e);
            // 그 사이 파기됐거나 보관한 것이 없다(404) — 블록을 새로 받아 「보관 안 함」으로 바꾼다
            if (failure.code === 'WITHDRAWN_CONTACT_NOT_FOUND' && memberId === id) void load(id, false);
            return failure;
        }
    }

    return { contact, loading, error, revealed, remaining: countdown.remaining, open, retry, clear, mask, reveal };
}
