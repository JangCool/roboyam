import type { MenuGroupNode, MenuNode } from '~/composables/menu/api/useMenuApi.types';

/** 하단 탭 한 칸 */
export interface BottomTab {
    key: string;
    to: string;
    label: string;
    /** PrimeIcons 클래스(`pi pi-…`) 또는 `flame` — 불꽃은 PrimeIcons 에 없어 하단 탭이 `TablerIcon name="flame"`(가젯 제목 아이콘과 같은 Tabler 세트)을 그린다 */
    icon: string;
    active: boolean;
}

/**
 * 커뮤니티 탭이 가는 곳 — 메뉴 「커뮤니티」 폴더의 「누르면 가는 곳」(가젯 페이지, 설치 프리셋 `irobit.json`).
 * 메뉴 그룹에는 고유 키가 없어 이 주소로 그 그룹을 먼저 찾고, 없으면 이름으로 찾는다(아래 `community`). 이름·영문명·가는 곳은 찾은 그룹 것을 쓴다
 */
const COMMUNITY_PATH = '/p/community';

/** 끝의 `/` 를 뗀 경로 */
function trim(path: string): string {
    return path.length > 1 ? path.replace(/\/+$/, '') : path;
}

/** 그룹 아래(하위 그룹까지) 항목들의 사이트 안 주소 */
function itemPaths(list: readonly MenuNode[]): string[] {
    // 바로가기(`shortcut` — 진짜 자리가 다른 묶음인 게시판)는 세지 않는다: 그 게시판에 있을 때 밝은 묶음은 진짜 자리 쪽이다
    return list.flatMap((node) => (node.kind === 'GROUP' ? itemPaths(node.children) : node.shortcut !== true && node.path.startsWith('/') ? [trim(node.path)] : []));
}

/** 메뉴 트리에서 조건에 맞는 그룹 — 어느 단계든 처음 맞는 것 */
function findGroup(list: readonly MenuNode[], hit: (group: MenuGroupNode) => boolean): MenuGroupNode | null {
    for (const node of list) {
        if (node.kind !== 'GROUP') continue;
        if (hit(node)) return node;
        const inner = findGroup(node.children, hit);
        if (inner) return inner;
    }
    return null;
}

/**
 * 모바일 하단 탭(숏컷) 목록 — 홈·최신글·커뮤니티·소모임·메뉴(2026-11-12 인기글 뺌)(mobile-nav-prefix-style-contract 3절, 2026-11-11 — 게시판·내 정보 탭을 빼고 인기글·메뉴를 넣었다. 내 정보는 상단 바 오른쪽 끝 아이콘).
 * **이 목록 하나가 원천이다**:
 * 하단 탭(`AppBottomTabs`)이 그리고, 모바일 상단 메뉴 줄(`AppMobileMenuRows`·`AppTopTabs`)은 `covers` 로 하단 탭에 있는 항목·그룹을 뺀다(데스크톱 메뉴는 그대로).
 * 탭을 바꾸면 상단 줄도 따라간다.
 */
export function useBottomTabs() {
    const { t } = useI18n();
    const route = useRoute();
    /** 소모임·최신글 탭의 이름은 서비스 이름(`GROUPS`·`LATEST`)이다 — 운영자가 바꾸면 따라 바뀐다 */
    const { term } = useSiteTerms();
    const { nodes } = useMenu();
    const { localizedName } = useLocalizedName();

    /** `to` 그 자체이거나 그 아래 주소인가(홈은 `/` 에서만) */
    function under(to: string): boolean {
        if (to === '/') return route.path === '/';
        const here = trim(route.path);
        return here === to || here.startsWith(`${to}/`);
    }

    /**
     * 커뮤니티 그룹 — ① 누르면 `/p/community` 로 가는 폴더 ② 없으면 이름이 「커뮤니티」(영문명 Community)인 폴더(운영자가 폴더 연결을 바꾼 경우 — 그 폴더의 연결로 간다).
     * 둘 다 없으면 null — 탭은 기본 이름으로 `/p/community` 에 간다
     */
    const community = computed(
        () =>
            findGroup(nodes.value, (group) => Boolean(group.path) && trim(group.path as string) === COMMUNITY_PATH) ??
            findGroup(nodes.value, (group) => group.label.trim() === '커뮤니티' || group.nameEn?.trim().toLowerCase() === 'community'),
    );
    /** 커뮤니티 탭이 가는 곳 — 그 그룹의 사이트 안 연결, 없으면 기본 가젯 페이지 */
    const communityTo = computed(() => (community.value?.path?.startsWith('/') ? trim(community.value.path) : COMMUNITY_PATH));
    /** 커뮤니티 탭이 밝은가 — 가젯 페이지이거나 그 묶음 안 게시판(목록·그 아래 주소) */
    const communityActive = computed(
        () => under(communityTo.value) || (community.value ? itemPaths(community.value.children).some((path) => path !== '/' && under(path)) : false),
    );

    const tabs = computed<BottomTab[]>(() => [
        { key: 'home', to: '/', label: t('shell.tabHome'), icon: 'pi pi-home', active: under('/') },
        // 인기글은 뺐다(사용자 2026-11-12 「하단에서 인기글 빼」) — 모바일 상단 첫 줄에 나온다
        { key: 'latest', to: '/latest', label: term('LATEST'), icon: 'pi pi-clock', active: under('/latest') },
        {
            key: 'community',
            to: communityTo.value,
            label: community.value ? localizedName(community.value.label, community.value.nameEn) : t('shell.tabCommunity'),
            icon: 'pi pi-comments',
            active: communityActive.value,
        },
        { key: 'groups', to: '/groups', label: term('GROUPS'), icon: 'pi pi-users', active: under('/groups') },
        // 메뉴 — 햄버거 서랍 대신 게시판 페이지 모양의 전체 화면(`/menu`)
        { key: 'menu', to: '/menu', label: t('shell.menu'), icon: 'pi pi-bars', active: under('/menu') },
    ]);

    /** 하단 탭이 가는 주소들(끝 `/` 없이) */
    const tabPaths = computed(() => new Set(tabs.value.map((tab) => trim(tab.to))));

    /**
     * 이 메뉴 노드가 하단 탭에 이미 있는가 — 모바일 상단 줄에서 뺄 것.
     * 항목은 주소가 탭 주소와 같으면(홈 `/`·인기글 `/popular`·최신글 `/latest` …), 그룹은 커뮤니티 그룹이거나 「누르면 가는 곳」이 탭 주소면(소모임 `/groups` …).
     * 게시판 모음(`/boards`)은 탭이 아니라 메뉴에 그 항목이 있으면 상단 줄에 나온다
     * 사이트 밖 주소(`https://…`)는 견주지 않는다
     */
    function covers(node: Pick<MenuNode, 'kind' | 'id'> & { path?: string | null }): boolean {
        if (node.kind === 'GROUP' && community.value && node.id === community.value.id) return true;
        return Boolean(node.path?.startsWith('/')) && tabPaths.value.has(trim(node.path as string));
    }

    /** 이 노드가 커뮤니티 묶음인가 — 모바일 상단 첫 줄은 하단 탭에 있어도 커뮤니티를 홈 오른쪽에 남긴다(사용자 2026-11-12) */
    function isCommunity(node: Pick<MenuNode, 'kind' | 'id'>): boolean {
        return node.kind === 'GROUP' && Boolean(community.value) && node.id === community.value?.id;
    }

    return { tabs, covers, isCommunity };
}
