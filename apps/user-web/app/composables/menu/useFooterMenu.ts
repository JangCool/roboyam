import type { MenuItemNode } from '~/composables/menu/api/useMenuApi.types';
import UtilMenu from '~/utils/util-menu';

/**
 * 사이트 푸터 「고객 지원」 링크 — 메뉴 그룹 `footer`(`GET /api/menu?set=footer`, 운영자가 관리자 메뉴 구성에서 정한다)의 **공유 상태**다.
 * 메인 메뉴 상태(`useMenu` — `main` 그룹)와 섞지 않는다. 서버와 말하는 것은 `useMenuApi().menuSet` 이고 여기는 상태만 든다.
 *
 * - 트리는 평평하게 편다(폴더가 오면 그 아래 항목을 차례대로 — `UtilMenu.items`). 게시판 slug 를 코드에 박지 않는다(링크는 서버가 준 `path`).
 * - 못 받았거나(오류·모양이 깨짐) 비었으면 빈 목록 — 푸터가 그 묶음을 숨긴다(다시 시도 단추는 두지 않는다).
 * - 응답이 보는 사람마다 다를 수 있어(보이는 대상) 로그인·로그아웃으로 사람이 바뀌면 다시 받는다 — 부르는 쪽(푸터)이 사람이 바뀔 때 `ensureLoaded` 를 다시 부른다.
 *
 * `undefined` = 아직 안 물어봄, 배열 = 받은 결과(빈 배열 = 없음·못 받음). 컴포저블은 setup 에서 부른다.
 */

/** 진행 중인 요청과 그 요청이 누구 것인지 — 여러 곳이 동시에 물어도 한 번만 보낸다 */
let inflight: Promise<void> | null = null;
let inflightOwner = '';

export function useFooterMenu() {
    const api = useMenuApi();
    const { user } = useAuth();

    const state = useState<MenuItemNode[] | undefined>('footer-menu-items', () => undefined);
    /** 들고 있는 목록이 누구 것인가 — 로그인 사용자 ID, 비로그인은 빈 글자 */
    const owner = useState<string | null>('footer-menu-owner', () => null);
    const ownerKey = computed(() => user.value?.id ?? '');

    /** 그릴 링크 — 받기 전·못 받음·없음은 빈 목록 */
    const items = computed<MenuItemNode[]>(() => (owner.value === ownerKey.value ? (state.value ?? []) : []));

    function load(who: string): Promise<void> {
        owner.value = who;
        inflightOwner = who;
        inflight = api
            .menuSet('footer')
            .then((res) => {
                if (owner.value !== who) return;
                const parsed = UtilMenu.parse(res);
                state.value = parsed ? UtilMenu.items(parsed.nodes) : [];
            })
            .catch(() => {
                if (owner.value === who) state.value = [];
            })
            .finally(() => {
                if (inflightOwner === who) inflight = null;
            });
        return inflight;
    }

    /** 이 사람 것으로 이미 받았으면 다시 묻지 않는다 */
    function ensureLoaded(): Promise<void> {
        const who = ownerKey.value;
        if (inflight && inflightOwner === who) return inflight;
        if (owner.value === who && state.value !== undefined) return Promise.resolve();
        state.value = undefined;
        return load(who);
    }

    return { items, ensureLoaded };
}
