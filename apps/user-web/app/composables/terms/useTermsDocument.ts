import { ApiError } from '~/composables/common/useApiClient';
import type { TermsDocument, TermsKind } from '~/composables/terms/api/useTermsApi.types';
import type { TermsShown } from '~/composables/terms/useTermsDocument.types';
import UtilBoardMessages from '~/utils/util-board-messages';

/**
 * 공개 약관 화면(`/terms`·`/privacy`)이 같이 쓰는 불러오기 — 종류의 문서(지금·시행 예정·이전 버전 목록)와, 주소에 버전이 있으면 그 버전 본문.
 * 버전이 지금 약관과 같으면 따로 부르지 않는다. 버전이 바뀌면(`?version=` 이동) 그 버전만 다시 받고 앞 요청은 끊는다.
 *
 * @param kind - 약관 종류.
 * @param version - 주소의 버전(없으면 null — 지금 약관). 읽는 자리에서 푼다.
 * @returns `status`(`loading`·`error`·`ready`) · `errorMessage` · `doc` · `shown`(그릴 본문, 빈 상태면 null) · `versionMissing`(그 버전이 없다) · `versionLoading` · `load()`(다시 시도).
 */
export function useTermsDocument(kind: TermsKind, version: MaybeRef<number | null>) {
    const api = useTermsApi();
    const t = useTranslate();

    const doc = ref<TermsDocument | null>(null);
    const status = ref<'loading' | 'error' | 'ready'>('loading');
    const errorMessage = ref('');

    const picked = ref<TermsShown | null>(null);
    const versionLoading = ref(false);
    const versionMissing = ref(false);
    const versionError = ref('');
    let versionAbort: AbortController | null = null;

    async function loadDocument(): Promise<void> {
        status.value = 'loading';
        errorMessage.value = '';
        try {
            doc.value = await api.document(kind);
            // 따로 받을 버전이면 「받는 중」을 먼저 켠다 — 빈 상태가 한 번 비치지 않게
            const wanted = toValue(version);
            if (wanted !== null && wanted !== doc.value.current?.version) versionLoading.value = true;
            status.value = 'ready';
        } catch (e) {
            errorMessage.value = UtilBoardMessages.of(t, e);
            status.value = 'error';
        }
    }

    /** 주소의 버전 본문 — 지금 약관이면 문서의 것을 쓴다 */
    async function loadVersion(): Promise<void> {
        versionAbort?.abort();
        versionAbort = null;
        versionMissing.value = false;
        versionError.value = '';
        picked.value = null;
        const wanted = toValue(version);
        if (wanted === null || wanted === doc.value?.current?.version) {
            versionLoading.value = false;
            return;
        }
        const controller = new AbortController();
        versionAbort = controller;
        versionLoading.value = true;
        try {
            const res = await api.version(kind, wanted, controller.signal);
            if (controller.signal.aborted) return;
            picked.value = { version: res.version, effectiveDate: res.effectiveDate, publishedAt: res.publishedAt, changeNote: res.changeNote, status: res.status, body: res.body };
        } catch (e) {
            if (controller.signal.aborted) return;
            if (e instanceof ApiError && e.status === 404) versionMissing.value = true;
            else versionError.value = UtilBoardMessages.of(t, e);
        } finally {
            if (!controller.signal.aborted) versionLoading.value = false;
        }
    }

    async function load(): Promise<void> {
        await loadDocument();
        if (status.value === 'ready') await loadVersion();
    }

    watch(
        () => toValue(version),
        () => {
            if (status.value === 'ready') void loadVersion();
        },
    );

    onScopeDispose(() => versionAbort?.abort());

    /** 그릴 본문 — 주소에 버전이 있으면 그 버전, 없으면 지금 약관. 게시본이 없으면 null(빈 상태) */
    const shown = computed<TermsShown | null>(() => {
        if (picked.value) return picked.value;
        const wanted = toValue(version);
        const current = doc.value?.current;
        if (!current || (wanted !== null && wanted !== current.version)) return null;
        return { ...current, status: 'CURRENT' };
    });

    return { doc, status, errorMessage, shown, versionLoading, versionMissing, versionError, load };
}
