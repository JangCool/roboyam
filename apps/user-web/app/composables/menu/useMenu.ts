import type { MenuNode } from '~/composables/menu/api/useMenuApi.types';
import type { MenuPhase, MenuSource, MenuViewState } from '~/utils/util-menu';
import UtilMenu from '~/utils/util-menu';

/**
 * 메뉴 트리 — 왼쪽·오른쪽·상단 메뉴, 햄버거, 모바일 상단 탭, `/boards` 모음이 함께 쓰는 **공유 상태**다 (frontend-guide 3장).
 * 서버(`useMenuApi().menu` — `GET /api/menu`)에서 한 번만 받고 화면 전체가 같은 트리를 본다. 서버와 말하는 것은 `useMenuApi` 이고 여기는 상태만 든다.
 *
 * 규칙 셋
 * - **한 번 받으면 같은 세션에서는 다시 묻지 않는다**(`ensureLoaded`). 동시에 여러 곳이 물어도 한 번만 보낸다.
 * - **응답은 사람마다 다르다**(보이는 대상으로 서버가 거른다) → 로그인·로그아웃으로 사용자가 바뀌면 다시 받는다. 다른 사람이 받은 메뉴는 바로 비워
 *   로그아웃 뒤에 이전 계정의 메뉴(내 소모임·운영진 항목)가 남아 보이지 않게 하고, 요청이 겹치면 마지막 요청의 응답만 반영한다.
 * - **못 받으면(오류·시간 초과) 기본 메뉴로 그린다**(`UtilMenu.fallbackNodes` — 게시판 목록 + 고정 항목). 이미 받은 메뉴가 있는데 다시 받다 실패하면 있는 메뉴를 그대로 둔다(오류로 덮지 않는다).
 *
 * 게시판 목록(`useBoards`)도 같이 받는다 — 기본 메뉴가 그것을 쓴다(메뉴 줄의 색 점은 메뉴 응답의 `color` 만 본다). 컴포저블은 setup 에서 부른다(`await` 뒤에는 Nuxt 문맥이 없다).
 */

/** 진행 중인 요청과 그 요청이 누구 것인지 — 여러 곳이 동시에 물어도 한 번만 보낸다 */
let inflight: Promise<void> | null = null;
let inflightOwner = '';
/** 요청 번호 — 늦게 도착한 앞 요청의 응답이 뒤 요청의 결과를 덮어쓰지 않게 한다 */
let sequence = 0;
let controller: AbortController | null = null;

export function useMenu() {
    const api = useMenuApi();
    const t = useTranslate();
    const { user } = useAuth();
    const { boards, errorMessage: boardsError, reload: reloadBoards, ensureLoaded: ensureBoards } = useBoards();
    // 기본 메뉴의 고정 항목 글자(공동구매·소모임)는 서비스 이름이다 — 서버 메뉴는 글자를 이미 반영해서 준다
    const { terms } = useSiteTerms();

    /** 서버가 준 트리. `null` 은 "받은 적 없음"(첫 응답 전·못 받음)이고 빈 배열은 "보여 드릴 메뉴가 없음"이다 */
    const serverNodes = useState<MenuNode[] | null>('menu-nodes', () => null);
    const serverTabs = useState<string[]>('menu-mobile-tabs', () => []);
    const phase = useState<MenuPhase>('menu-phase', () => 'idle');
    /** 지금 들고 있는 메뉴(또는 실패)가 누구 것인가 — 로그인 사용자 ID, 비로그인은 빈 글자. 아직 안 물어봤으면 null */
    const owner = useState<string | null>('menu-owner', () => null);
    /** 서버에 요청이 나가 있는 동안 true — 다시 시도 단추의 진행 표시 */
    const loading = useState<boolean>('menu-loading', () => false);

    const ownerKey = computed(() => user.value?.id ?? '');

    const fallback = computed(() => UtilMenu.fallbackNodes(t, boards.value ?? [], user.value !== null, terms.value));

    /** 지금 그릴 트리 — 서버 것, 없으면 기본 메뉴 */
    const nodes = computed<MenuNode[]>(() => serverNodes.value ?? fallback.value.nodes);
    /** 모바일 상단 탭 항목의 ID — 서버가 정한 순서(기본 메뉴는 홈 + 주제 게시판) */
    const mobileTabIds = computed<string[]>(() => (serverNodes.value !== null ? serverTabs.value : fallback.value.mobileTabs));
    const source = computed<MenuSource>(() => (serverNodes.value !== null ? 'server' : 'fallback'));
    const state = computed<MenuViewState>(() => {
        const pending = boards.value === null && !boardsError.value;
        const failed = boards.value === null && !!boardsError.value;
        return UtilMenu.viewState(phase.value, serverNodes.value === null ? null : serverNodes.value.length, pending, failed);
    });

    /** 서버에서 메뉴를 새로 받는다. 실패해도 던지지 않는다 — 가진 메뉴가 있으면 그대로 두고, 없으면 기본 메뉴로 그린다 */
    function fetchMenu(): Promise<void> {
        const who = ownerKey.value;
        if (inflight && inflightOwner === who) return inflight;

        // 사용자가 바뀌어 앞 요청이 아직 오는 중이면 끊는다. 늦게 와도 번호가 달라 반영되지 않는다
        controller?.abort();
        // 다른 사람이 받은 메뉴는 이 사람에게 보이면 안 된다(로그아웃 뒤 이전 계정의 항목) — 비우고 다시 받는다
        if (owner.value !== who) {
            serverNodes.value = null;
            serverTabs.value = [];
        }
        owner.value = who;
        if (serverNodes.value === null) phase.value = 'loading';

        const mine = ++sequence;
        const own = new AbortController();
        controller = own;
        inflightOwner = who;
        loading.value = true;
        inflight = api
            .menu(own.signal)
            .then((res) => {
                if (mine !== sequence) return;
                const parsed = UtilMenu.parse(res);
                if (!parsed) throw new Error('menu-shape');
                // 서버 구분선(2026-10-29)이 없는 최상위 노드에만 개발 모드의 `?divider=` 미리보기를 입힌다(운영 빌드는 그대로)
                serverNodes.value = UtilMenu.withDividerPreview(parsed.nodes);
                serverTabs.value = parsed.mobileTabs;
                phase.value = 'ready';
            })
            .catch(() => {
                if (mine !== sequence) return;
                // 이미 받은 메뉴가 있으면 그대로 둔다(메뉴를 오류 안내로 덮지 않는다). 없을 때만 기본 메뉴로 넘어간다
                if (serverNodes.value === null) phase.value = 'error';
            })
            .finally(() => {
                if (mine !== sequence) return;
                loading.value = false;
                inflight = null;
                controller = null;
            });
        return inflight;
    }

    /**
     * 이미 받았으면 다시 묻지 않는다(실패한 채라면 다시 시도 단추를 누를 때까지 다시 묻지 않는다 — 이동마다 실패한 요청을 반복하지 않는다).
     * 사용자가 바뀌었으면 받는다. 게시판 목록도 같이 받는다.
     */
    async function ensureLoaded(): Promise<void> {
        void ensureBoards();
        const who = ownerKey.value;
        if (inflight && inflightOwner === who) return inflight;
        if (owner.value === who && (phase.value === 'ready' || phase.value === 'error')) return;
        await fetchMenu();
    }

    /** 서버에서 새로 받는다 — 메뉴와, 아직 못 받은 게시판 목록(기본 메뉴가 쓴다)을 함께. 다시 시도 단추가 쓴다 */
    async function reload(): Promise<void> {
        const tasks: Promise<void>[] = [fetchMenu()];
        if (boards.value === null) tasks.push(reloadBoards());
        await Promise.all(tasks);
    }

    // 로그인·로그아웃·계정 전환 — 응답이 사람마다 다르다. 같은 화면 여러 곳이 이 컴포저블을 불러도 `ensureLoaded` 가 같은 요청을 합친다.
    // 앱이 아직 첫 메뉴를 묻기 전(`idle`)이면 시작은 셸이 한다.
    watch(ownerKey, () => {
        if (phase.value !== 'idle') void ensureLoaded();
    });

    return { nodes, mobileTabIds, state, source, loading, reload, ensureLoaded };
}
