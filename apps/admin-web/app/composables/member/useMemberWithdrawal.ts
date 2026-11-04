import type { ApiError } from '~/composables/common/useApiClient';
import type { ForceWithdrawPreview, ForceWithdrawRequest, ForceWithdrawResult, MemberWithdrawalOfMember, WithdrawalCancelRequest } from '~/composables/member/api/useMemberWithdrawalApi.types';

/**
 * 회원 상세 서랍의 「탈퇴 신청」·「탈퇴 완료」 블록과 강제 탈퇴·대리 취소 대화상자가 보는 상태 — 그 회원의 최근 탈퇴 기록 · 강제 탈퇴 미리보기(남은 일·돈).
 * 회원 관리 화면이 부를 때마다 새 상태가 생긴다. 서버 호출은 `useMemberWithdrawalApi` 한 겹.
 * 404 `WITHDRAWAL_NOT_FOUND`(신청한 적 없음)는 오류가 아니라 「기록 없음」(`record = null`, `error = null`)이다.
 * ⚠ 컴포넌트 setup 에서 부른다.
 *
 * @returns `record` · `loading` · `error` · `open(memberNo)` · `retry()` · `clear()` ·
 *          `preview` · `previewLoading` · `previewError` · `loadPreview(memberNo)` · `force(body)`·`cancel(body)` 성공하면 결과, 실패하면 `ApiError` 를 던진다.
 */
export function useMemberWithdrawal() {
    const api = useMemberWithdrawalApi();
    const errorOf = useApiErrorOf();

    const record = ref<MemberWithdrawalOfMember | null>(null);
    const loading = ref(false);
    const error = ref<ApiError | null>(null);
    let memberNo: string | null = null;
    let ticket = 0;

    async function open(id: string): Promise<void> {
        const mine = ++ticket;
        memberNo = id;
        record.value = null;
        error.value = null;
        loading.value = true;
        try {
            const found = await api.forMember(id);
            if (mine === ticket) record.value = found;
        } catch (e) {
            if (mine !== ticket) return;
            const failure = errorOf(e);
            if (failure.code !== 'WITHDRAWAL_NOT_FOUND') error.value = failure;
        } finally {
            if (mine === ticket) loading.value = false;
        }
    }

    function retry(): void {
        if (memberNo) void open(memberNo);
    }

    function clear(): void {
        ticket++;
        memberNo = null;
        record.value = null;
        error.value = null;
        loading.value = false;
        preview.value = null;
        previewError.value = null;
    }

    // ── 강제 탈퇴 — 대화상자를 열 때 미리보기를 받고, 확인하면 요청한다 ─
    const preview = ref<ForceWithdrawPreview | null>(null);
    const previewLoading = ref(false);
    const previewError = ref<ApiError | null>(null);
    let pticket = 0;

    async function loadPreview(id: string): Promise<void> {
        const mine = ++pticket;
        preview.value = null;
        previewError.value = null;
        previewLoading.value = true;
        try {
            const found = await api.forcePreview(id);
            if (mine === pticket) preview.value = found;
        } catch (e) {
            if (mine === pticket) previewError.value = errorOf(e);
        } finally {
            if (mine === pticket) previewLoading.value = false;
        }
    }

    /** 강제 탈퇴 — 성공하면 결과, 실패하면 `ApiError` 를 던진다(대화상자가 칸 오류·폼 오류로 나눈다). 성공하면 그 회원의 기록을 다시 받는다 */
    async function force(body: ForceWithdrawRequest): Promise<ForceWithdrawResult> {
        const id = preview.value?.memberNo ?? memberNo;
        if (!id) throw new Error('no member');
        try {
            const result = await api.force(id, body);
            void open(id);
            return result;
        } catch (e) {
            throw errorOf(e);
        }
    }

    /** 운영자 대리 취소(회원 요청 시) — 지금 받은 기록(`record`)을 취소한다. 성공하면 바뀐 기록으로 바꾸고, 실패하면 `ApiError` 를 던진다 */
    async function cancel(body: WithdrawalCancelRequest): Promise<MemberWithdrawalOfMember> {
        const id = record.value?.id;
        if (!id) throw new Error('no record');
        try {
            const updated = await api.cancel(id, body);
            if (record.value?.id === id) record.value = updated;
            return updated;
        } catch (e) {
            const failure = errorOf(e);
            // 그 사이 끝났다(409) — 기록을 새로 받아 화면을 맞춘다
            if (failure.code === 'WITHDRAWAL_NOT_SCHEDULED') retry();
            throw failure;
        }
    }

    return { record, loading, error, open, retry, clear, preview, previewLoading, previewError, loadPreview, force, cancel };
}
