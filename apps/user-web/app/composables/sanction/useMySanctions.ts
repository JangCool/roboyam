import type { MySanctions } from '~/composables/sanction/api/useSanctionApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilSanction from '~/utils/util-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

/**
 * `/sanction`(제재 안내)의 흐름 — **내 제재 불러오기 → 안내 갈래 → (경고) 확인하고 계속** (community `useMySanctions` 에서 게시판 한정 제재를 뺐다).
 * 한 화면이 들고 있는 값이라 `useState` 에 두지 않는다 — 제재는 늘 새로 받는다(옛 목록을 기억해 두면 해제·인용 뒤에도 정지로 보인다). 서버와 말하는 것은 `useSanctionApi` 다.
 *  - `load`: 앞 요청을 끊고 처음부터 받는다(늦게 온 옛 응답은 요청 번호로 버린다). 받고 나면 로그인 정보(`useAuth().refresh`)도 뒤에서 다시 읽어 전역 배너를 서버 값에 맞춘다.
 *  - `acknowledge`: 「확인하고 계속」 — 경고 안내에서 「안내를 확인했어요」에 체크했을 때만 확인하지 않은 경고를 모두 확인한다(멱등). 이미 없어진 제재(404)는 넘긴다. 성공하면 `DONE` — 이동은 부르는 쪽이 한다.
 * ⚠ setup 에서 부른다(`await` 뒤에는 Nuxt 문맥이 없다) — 번역은 `useTranslate`(뒤에서도 안전)로 잡아 둔다.
 */
export type AcknowledgeResult = 'DONE' | 'FAILED' | 'IGNORED';

export function useMySanctions() {
    const api = useSanctionApi();
    const t = useTranslate();
    const { refresh } = useAuth();
    const time = useDisplayTime();

    const status = ref<'loading' | 'ready' | 'error'>('loading');
    const data = ref<MySanctions | null>(null);
    const errorMessage = ref('');
    const confirmed = ref(false);
    const acknowledging = ref(false);
    const ackError = ref('');

    let seq = 0;
    let abort: AbortController | null = null;

    const screen = computed(() => (data.value ? UtilSanction.screenOf(data.value) : null));
    const focus = computed(() => (data.value && screen.value ? UtilSanction.focusOf(data.value, screen.value) : null));
    const warningCount = computed(() => (data.value ? UtilSanction.warningCount(data.value, focus.value) : 0));
    const rows = computed(() => (data.value ? data.value.items.map((s) => UtilSanctionMessages.historyRow(t, s, time.value)) : []));
    const canAcknowledge = computed(() => screen.value === 'WARNING' && confirmed.value && !acknowledging.value);

    async function load(): Promise<void> {
        const mine = ++seq;
        abort?.abort();
        const controller = new AbortController();
        abort = controller;
        status.value = 'loading';
        errorMessage.value = '';
        try {
            const res = await api.mine(controller.signal);
            if (mine !== seq) return;
            data.value = res;
            status.value = 'ready';
            void refresh().catch(() => undefined);
        } catch (e) {
            if (mine !== seq) return;
            if (e instanceof ApiError && e.code === 'ABORTED') return;
            errorMessage.value = e instanceof ApiError ? e.message : t('sanction.loadError');
            status.value = 'error';
        }
    }

    function cancel(): void {
        seq++;
        abort?.abort();
    }

    async function acknowledge(): Promise<AcknowledgeResult> {
        const current = data.value;
        if (!current || !canAcknowledge.value) return 'IGNORED';
        const ids = UtilSanction.pendingWarnings(current.items).map((s) => s.id);
        acknowledging.value = true;
        ackError.value = '';
        try {
            for (const id of ids) {
                try {
                    await api.acknowledge(id);
                } catch (e) {
                    // 그 사이 이의제기가 인용돼 없어졌거나 내 것이 아니다 — 확인할 것이 없으니 넘어간다
                    if (!(e instanceof ApiError && e.code === 'SANCTION_NOT_FOUND')) throw e;
                }
            }
        } catch (e) {
            ackError.value = e instanceof ApiError && e.message ? e.message : t('sanction.ackFailed');
            return 'FAILED';
        } finally {
            acknowledging.value = false;
        }
        try {
            await refresh();
        } catch {
            /* 다음에 로그인 정보를 읽을 때 맞춰진다 */
        }
        return 'DONE';
    }

    return { status, data, errorMessage, screen, focus, warningCount, rows, confirmed, acknowledging, ackError, canAcknowledge, load, cancel, acknowledge };
}
