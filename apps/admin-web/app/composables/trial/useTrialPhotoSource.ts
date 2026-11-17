import { ApiError } from '~/composables/common/useApiClient';

/** 사진 한 장의 받기 상태 — 없음 · 받는 중 · 그림 있음 · 보관 기한이 지나 지움(410 `TRIAL_PHOTO_PURGED`) · 실패 */
export type TrialPhotoSourceState = 'none' | 'loading' | 'ready' | 'purged' | 'error';

/**
 * 비공개 체험단·구매 인증 사진 한 장(V243 — 서버가 준 권한 API `url`, 바이트·`no-store`)을 받아 objectURL 로 들고 있는다. 그리는 것은 `TrialPhotoImage`.
 * 주소가 바뀌면 앞 요청을 끊고 앞 objectURL 을 해제한 뒤 다시 받는다. 부른 쪽(컴포넌트)이 사라지면 끊고 해제한다(메모리에 남기지 않는다).
 * 서버 호출은 `useTrialApi().photo` 한 겹. ⚠ 컴포넌트 setup 에서 부른다.
 */
export function useTrialPhotoSource(url: MaybeRefOrGetter<string | null>) {
    const api = useTrialApi();

    const src = ref<string | null>(null);
    const state = ref<TrialPhotoSourceState>('none');
    const errorMessage = ref('');
    let objectUrl: string | null = null;
    let controller: AbortController | null = null;

    function release(): void {
        controller?.abort();
        controller = null;
        if (objectUrl) URL.revokeObjectURL(objectUrl);
        objectUrl = null;
        src.value = null;
    }

    async function load(target: string | null): Promise<void> {
        release();
        errorMessage.value = '';
        if (!target) {
            state.value = 'none';
            return;
        }
        const mine = new AbortController();
        controller = mine;
        state.value = 'loading';
        try {
            const blob = await api.photo(target, mine.signal);
            if (controller !== mine) return;
            objectUrl = URL.createObjectURL(blob);
            src.value = objectUrl;
            state.value = 'ready';
        } catch (e) {
            if (controller !== mine) return;
            const purged = e instanceof ApiError && (e.code === 'TRIAL_PHOTO_PURGED' || e.status === 410);
            state.value = purged ? 'purged' : 'error';
            errorMessage.value = e instanceof Error ? e.message : '';
        } finally {
            if (controller === mine) controller = null;
        }
    }

    watch(
        () => toValue(url),
        (next) => void load(next),
        { immediate: true },
    );
    onScopeDispose(release);

    return { src, state, errorMessage, reload: () => load(toValue(url)) };
}
