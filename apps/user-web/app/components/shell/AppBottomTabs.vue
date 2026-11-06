<template>
    <!-- 하단 탭(모바일) — 홈·최신글·커뮤니티·소모임·게시판·내 정보 6개 고정(사용자 결정 2026-11-06 — 알림은 상단 바의 알림 종으로, 최신글은 모바일 상단 메뉴 줄에서 빼 여기로). 소모임 탭은 `/groups` 로 시작하는 모든 주소(목록·내 소모임·상세·관리)에서 밝다.
         커뮤니티 탭은 메뉴의 「커뮤니티」 묶음(그 폴더의 「누르면 가는 곳」 = 가젯 페이지 `/p/community`)으로 가고, 그 페이지와 묶음 안 게시판에서 밝다(그때 게시판 탭은 꺼진다).
         6칸이라 글자는 조금 작게(11px), 길면 말줄임 — 360px 폭에서 칸마다 60px -->
    <nav :aria-label="t('shell.bottomTabs')" class="sticky bottom-0 z-30 border-t border-surface bg-surface-0 pb-[env(safe-area-inset-bottom)] dark:bg-surface-900">
        <ul class="m-0 flex list-none justify-around p-0">
            <li v-for="tab in tabs" :key="tab.key" class="min-w-0 flex-1">
                <NuxtLink
                    :to="tab.to"
                    class="flex flex-col items-center gap-0.5 px-0.5 py-2 text-[0.6875rem] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
                    :class="tab.active ? 'font-semibold text-color' : 'text-muted-color'"
                    :aria-current="tab.active ? 'page' : undefined"
                    :data-testid="`tab-${tab.key}`"
                >
                    <i :class="[tab.icon, 'text-lg']" aria-hidden="true" />
                    <span class="w-full truncate text-center">{{ tab.label }}</span>
                </NuxtLink>
            </li>
        </ul>
    </nav>
</template>

<script setup lang="ts">
import type { MenuGroupNode, MenuNode } from '~/composables/menu/api/useMenuApi.types';

/** 하단 탭 한 칸 */
interface BottomTab {
    key: string;
    to: string;
    label: string;
    icon: string;
    active: boolean;
}

/**
 * 커뮤니티 탭이 가는 곳 — 메뉴 「커뮤니티」 폴더의 「누르면 가는 곳」(가젯 페이지, 설치 프리셋 `roboyam.json`).
 * 메뉴 그룹에는 고유 키가 없어 이 주소로 그 그룹을 먼저 찾고, 없으면 이름으로 찾는다(아래 `community`). 이름·영문명·가는 곳은 찾은 그룹 것을 쓴다
 */
const COMMUNITY_PATH = '/p/community';

const { t } = useI18n();
const route = useRoute();
/** 소모임 탭의 이름은 서비스 이름(`GROUPS`)이다 — 운영자가 바꾸면 따라 바뀐다 */
const { term } = useSiteTerms();
const { nodes } = useMenu();
const { localizedName } = useLocalizedName();

/** 끝의 `/` 를 뗀 경로 */
function trim(path: string): string {
    return path.length > 1 ? path.replace(/\/+$/, '') : path;
}

/** `to` 그 자체이거나 그 아래 주소인가(홈은 `/` 에서만) */
function under(to: string): boolean {
    if (to === '/') return route.path === '/';
    const here = trim(route.path);
    return here === to || here.startsWith(`${to}/`);
}

/** 그룹 아래(하위 그룹까지) 항목들의 사이트 안 주소 */
function itemPaths(list: readonly MenuNode[]): string[] {
    return list.flatMap((node) => (node.kind === 'GROUP' ? itemPaths(node.children) : node.path.startsWith('/') ? [trim(node.path)] : []));
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
const communityActive = computed(() => under(communityTo.value) || (community.value ? itemPaths(community.value.children).some((path) => path !== '/' && under(path)) : false));

const tabs = computed<BottomTab[]>(() => [
    { key: 'home', to: '/', label: t('shell.tabHome'), icon: 'pi pi-home', active: under('/') },
    // 최신글 — 이름은 서비스 이름(`LATEST`)이다. 모바일 상단 메뉴 줄에서는 뺐다(`AppMobileMenuRows`)
    { key: 'latest', to: '/latest', label: term('LATEST'), icon: 'pi pi-clock', active: under('/latest') },
    {
        key: 'community',
        to: communityTo.value,
        label: community.value ? localizedName(community.value.label, community.value.nameEn) : t('shell.tabCommunity'),
        icon: 'pi pi-comments',
        active: communityActive.value,
    },
    { key: 'groups', to: '/groups', label: term('GROUPS'), icon: 'pi pi-users', active: under('/groups') },
    // 게시판 탭은 게시판 허브뿐 아니라 개별 게시판 목록도 밝힌다 — 커뮤니티 묶음 게시판이면 커뮤니티 탭이 대신 밝다
    { key: 'boards', to: '/boards', label: t('shell.tabBoards'), icon: 'pi pi-th-large', active: under('/boards') && !communityActive.value },
    { key: 'me', to: '/me', label: t('shell.myInfo'), icon: 'pi pi-user', active: under('/me') },
]);
</script>
