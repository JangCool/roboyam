import type { ApiError } from '~/composables/common/useApiClient';
import type { SanctionPreview, SanctionRunningLine } from '~/composables/sanction/api/useSanctionApi.types';

/**
 * 회원 한 명의 제재 현황(`GET /api/admin/sanctions/preview?userId=`)을 들고 있는 상태 — 회원 상세 서랍의 제재 구획·제재 화면의 서랍·신고 처리 대화창(줄 메뉴로 연 사건)이 쓴다.
 * 화면이 부를 때마다 새 상태다(`useState` 가 아니다). 다른 회원을 연 뒤 늦게 온 앞 요청의 응답은 버린다(번호표). 서버 호출은 `useSanctionApi` 한 겹.
 * `lift` 는 진행 중 정지·영구 정지를 풀고 현황을 다시 받는다 — 실패하면 `ApiError`(대화상자가 서버 문구를 보인다), `SANCTION_NOT_LIFTABLE`·`SANCTION_NOT_FOUND` 면 현황도 새로 받는다.
 *
 * ⚠ 컴포넌트 setup 에서 부른다.
 */
export function useSanctionPreview() {
    const api = useSanctionApi();
    const errorOf = useApiErrorOf();

    const userId = ref<string | null>(null);
    const preview = ref<SanctionPreview | null>(null);
    const loading = ref(false);
    const error = ref<ApiError | null>(null);
    let ticket = 0;

    async function open(id: string, options: { silent?: boolean } = {}): Promise<void> {
        const mine = ++ticket;
        // 조용히 다시 받으면(같은 회원) 보이던 현황을 지우지 않고 바꾼다
        if (userId.value !== id || !options.silent) preview.value = null;
        userId.value = id;
        loading.value = true;
        error.value = null;
        try {
            const found = await api.preview(id);
            if (mine === ticket) preview.value = found;
        } catch (e) {
            if (mine !== ticket) return;
            if (!options.silent || !preview.value) {
                preview.value = null;
                error.value = errorOf(e);
            }
        } finally {
            if (mine === ticket) loading.value = false;
        }
    }

    function reload(): Promise<void> {
        return userId.value ? open(userId.value, { silent: true }) : Promise.resolve();
    }

    function retry(): void {
        if (userId.value) void open(userId.value);
    }

    function clear(): void {
        ticket++;
        userId.value = null;
        preview.value = null;
        error.value = null;
        loading.value = false;
    }

    /** 진행 중 정지·영구 정지 해제 — 성공하면 null, 실패하면 오류 */
    async function lift(line: SanctionRunningLine, reason: string): Promise<ApiError | null> {
        try {
            // 봉인 진행 중 정지는 ID 가 없다 — 이 현황의 회원 기준으로 푼다(anonymous-board-contract 2026-11-11)
            if (line.id) await api.lift(line.id, reason);
            else if (userId.value) await api.liftActive(userId.value, reason);
            else return null;
            void reload();
            return null;
        } catch (e) {
            const failure = errorOf(e);
            if (failure.code === 'SANCTION_NOT_LIFTABLE' || failure.code === 'SANCTION_NOT_FOUND') void reload();
            return failure;
        }
    }

    return { userId, preview, loading, error, open, reload, retry, clear, lift };
}
