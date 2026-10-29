import type { MenuBoardOption } from '~/composables/menu/api/useMenuApi.types';
import type { GadgetPageFolder } from '~/composables/page/api/useGadgetPageApi.types';

/**
 * 가젯 페이지의 기준 폴더 고르기·가젯 설정의 게시판 고르기 값 — 폴더와 그 게시판은 서버(`GET /api/admin/gadget-pages/folders` — 폴더 → 게시판 풀기의 원천),
 * 직접 고르기 게시판 목록은 메뉴 대상 고르기 값(`GET /api/admin/menu/target-options`)을 한 번 읽어 나눠 쓴다.
 * 컴포저블은 setup 에서 부른다. 값은 이 방문 동안 앱 전체가 같이 쓴다(`useState`). 폴더를 새로 만들거나 고친 뒤에는 `load(true)`.
 *
 * @returns `folders` 고를 폴더들 · `boards` 고를 게시판들(서버 차례) · `load` 읽기(한 번만 — `force` 면 다시) · `loading`·`error` · `folderOf`.
 */
export function useGadgetPageFolders() {
    const folders = useState<GadgetPageFolder[]>('gadget-page-folders', () => []);
    const boards = useState<MenuBoardOption[]>('gadget-page-boards', () => []);
    const loaded = useState<boolean>('gadget-page-folders-loaded', () => false);
    const loading = ref(false);
    const error = ref('');

    const pageApi = useGadgetPageApi();
    const menuApi = useMenuApi();
    const toApiError = useApiErrorOf();

    async function load(force = false): Promise<void> {
        if ((loaded.value && !force) || loading.value) return;
        loading.value = true;
        error.value = '';
        try {
            const [res, options] = await Promise.all([pageApi.folders(), menuApi.targetOptions('main').catch(() => null)]);
            folders.value = res.folders;
            boards.value = options?.boards ?? [];
            loaded.value = true;
        } catch (e) {
            error.value = toApiError(e).message;
        } finally {
            loading.value = false;
        }
    }

    /** 폴더 하나(없으면 null) */
    function folderOf(id: string | null): GadgetPageFolder | null {
        return id ? (folders.value.find((f) => f.id === id) ?? null) : null;
    }

    return { folders, boards, loading, error, load, folderOf };
}
