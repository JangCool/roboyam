import type { MenuBoardOption } from '~/composables/menu/api/useMenuApi.types';
import type { GadgetPageBoard } from '~/composables/page/api/useGadgetPageApi.types';
import type { GadgetPageFolder } from '~/utils/util-gadget-page';
import UtilGadgetPage from '~/utils/util-gadget-page';

/**
 * 가젯 페이지의 기준 폴더 고르기·가젯 설정의 게시판 고르기 값 — 공개 메뉴(main) 트리 + 메뉴 대상 게시판 목록(+ 게시판 목록 모양)을 한 번 읽어 나눠 쓴다.
 * 읽기 권한은 메뉴 조회(`site.read`)다. 게시판 목록 모양(처음 배치의 갤러리 고르기)은 게시판 목록을 못 읽으면 모두 `LIST` 로 본다.
 * 컴포저블은 setup 에서 부른다. 값은 이 방문 동안 앱 전체가 같이 쓴다(`useState`).
 *
 * @returns `folders` 고를 폴더들 · `boards` 고를 게시판들(켜진 것 먼저가 아니라 서버 차례) · `load` 읽기(한 번만 — `force` 면 다시) · `loading`·`error`.
 */
export function useGadgetPageFolders() {
    const folders = useState<GadgetPageFolder[]>('gadget-page-folders', () => []);
    const boards = useState<MenuBoardOption[]>('gadget-page-boards', () => []);
    const loaded = useState<boolean>('gadget-page-folders-loaded', () => false);
    const loading = ref(false);
    const error = ref('');

    const menuApi = useMenuApi();
    const boardApi = useBoardApi();
    const toApiError = useApiErrorOf();
    const { localizedName } = useLocalizedName();

    async function load(force = false): Promise<void> {
        if ((loaded.value && !force) || loading.value) return;
        loading.value = true;
        error.value = '';
        try {
            const [tree, options, layouts] = await Promise.all([
                menuApi.tree('main'),
                menuApi.targetOptions('main'),
                boardApi
                    .list()
                    .then((res) => new Map(res.boards.map((b) => [b.id, b.layout as GadgetPageBoard['layout']])))
                    .catch(() => new Map<string, GadgetPageBoard['layout']>()),
            ]);
            boards.value = options.boards;
            folders.value = UtilGadgetPage.folders(tree.nodes, options.boards, (id) => layouts.get(id) ?? 'LIST', (name, nameEn) => localizedName(name, nameEn));
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
