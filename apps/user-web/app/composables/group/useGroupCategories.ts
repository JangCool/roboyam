import type { GroupCategoryItem } from '~/composables/group/api/useGroupApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';

/**
 * 소모임 분류 — 목록의 칩과 만들기 화면의 분류 고르기가 한 벌을 나눠 쓴다(`useState`).
 * 이름·순서·소모임 수는 관리자가 언제든 바꾸므로 화면에 들어올 때마다 `reload()` 로 새로 받는다(받는 동안 앞의 것을 그대로 보인다).
 * `items` 가 null 이면 아직 못 받았다(받는 중이거나 실패 — `status` 가 가른다).
 *
 * ⚠ setup 에서 부른다(`await` 뒤에는 Nuxt 문맥이 없다).
 */
export type GroupCategoriesStatus = 'loading' | 'ready' | 'error';

/** 진행 중인 요청 — 여러 곳이 동시에 불러도 한 번만 보낸다 */
let inflight: Promise<void> | null = null;

export function useGroupCategories() {
    const api = useGroupApi();
    const t = useTranslate();

    const items = useState<GroupCategoryItem[] | null>('group-categories', () => null);
    const serverTotal = useState<number | null>('group-categories-total', () => null);
    const errorMessage = useState<string>('group-categories-error', () => '');
    const loading = useState<boolean>('group-categories-loading', () => false);

    const status = computed<GroupCategoriesStatus>(() => {
        if (items.value !== null) return 'ready';
        return errorMessage.value ? 'error' : 'loading';
    });

    /** 「전체」 칩의 숫자 — 서버 `total`, 없으면 분류 소모임 수의 합. 못 받았으면 null */
    const total = computed(() => (items.value === null ? null : (serverTotal.value ?? items.value.reduce((sum, c) => sum + c.groupCount, 0))));

    function reload(): Promise<void> {
        if (inflight) return inflight;
        loading.value = true;
        if (items.value === null) errorMessage.value = '';
        inflight = api
            .categories()
            .then((res) => {
                items.value = res.items;
                serverTotal.value = typeof res.total === 'number' ? res.total : null;
                errorMessage.value = '';
            })
            .catch((e: unknown) => {
                errorMessage.value = UtilBoardMessages.of(t, e);
            })
            .finally(() => {
                loading.value = false;
                inflight = null;
            });
        return inflight;
    }

    return { items, status, loading, errorMessage, total, reload };
}
