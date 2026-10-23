import type { Ref } from 'vue';
import type { ApiError } from '~/composables/common/useApiClient';
import type { SettingHistoryItem, SettingHistoryPage } from '~/composables/common/useSettingHistory.types';

/**
 * 설정 변경 이력 구획의 쪽 넘김 상태 — 소셜 로그인 설정·메일 서버 설정 화면이 같이 쓴다(그리는 것은 `SettingHistoryTable`).
 * 상태는 화면마다 따로다(`useState` 가 아니라 `ref` — 화면을 떠나면 버린다). 서버 호출은 넘겨받은 `loader`(각 업무의 `useXxxApi` 메서드)가 한다.
 *
 * - `load()` 는 지금 쪽을 다시 받는다. `reloadFirst()` 는 첫 쪽부터(저장·삭제 직후 — 새 줄이 맨 위에 생긴다).
 * - 늦게 온 앞 요청의 응답은 버린다(쪽을 빨리 넘기거나 거르기를 바꿀 때).
 * - 실패는 `error` 에 둔다(표 자리에 `ErrorState` + 다시 시도).
 *
 * ⚠ 컴포넌트 setup 에서 부른다(오류 변환 함수의 번역을 그때 붙잡는다).
 *
 * @param loader 한 쪽을 받는 함수 — `page` 0 부터, `size` 1~100.
 */
export function useSettingHistory<T extends SettingHistoryItem>(loader: (page: number, size: number) => Promise<SettingHistoryPage<T>>) {
    const toApiError = useApiErrorOf();

    const rows = ref<T[]>([]) as Ref<T[]>;
    const total = ref(0);
    /** 첫 줄의 순번(PrimeVue 표의 `first`) */
    const first = ref(0);
    const size = ref(10);
    const loading = ref(false);
    const error = ref<ApiError | null>(null);
    /** 한 번이라도 받았는가 — 처음 그릴 때만 뼈대(스켈레톤)를 보인다 */
    const loaded = ref(false);

    let seq = 0;

    async function load(): Promise<void> {
        const mine = ++seq;
        loading.value = true;
        error.value = null;
        try {
            const page = await loader(Math.floor(first.value / size.value), size.value);
            if (mine !== seq) return;
            rows.value = page.items;
            total.value = page.total;
            loaded.value = true;
        } catch (e) {
            if (mine !== seq) return;
            error.value = toApiError(e);
            rows.value = [];
            total.value = 0;
        } finally {
            if (mine === seq) loading.value = false;
        }
    }

    function reloadFirst(): Promise<void> {
        first.value = 0;
        return load();
    }

    /** 표의 쪽 넘김(`@page`) */
    function onPage(event: { first: number; rows: number }): void {
        first.value = event.first;
        size.value = event.rows;
        void load();
    }

    return { rows, total, first, size, loading, error, loaded, load, reloadFirst, onPage };
}
