<template>
    <!--
        메뉴 본문 — 하단 탭 「메뉴」 서랍(`AppMenuDrawer`)과 전체 화면 메뉴(`/menu`)가 같이 쓴다(mobile-nav-prefix-style-contract 4절, 2026-11-12 서랍으로 되돌림).
        게시판 페이지(`/boards`)와 같은 모양(글자는 한 단계씩 작게 — `compact`): (로그인 전) 로그인·가입 안내 카드 → 최근 방문 알약 → 바로 가기 칸(메뉴의 최상위 항목, 4열 아이콘) → 메뉴 그룹별 게시판 줄(화살표 없음) → 약관·개인정보처리방침.
        링크를 누르면 주소가 바뀌고, 서랍은 주소가 바뀌면 스스로 닫힌다.
    -->
    <div data-testid="menu-panel" data-cache-area="board.catalog menu.public">
        <!-- 로그인 전에만 — 로그인·가입 안내. 로그인한 뒤의 계정 줄(내 정보·포인트·쪽지 …)은 상단 바의 내 정보 아이콘(`/me`)이 맡는다 -->
        <div v-if="loaded && !user" class="mt-3 rounded-xl bg-surface-100 p-3 dark:bg-surface-800" data-testid="menu-login-invite">
            <p class="text-xs font-semibold">{{ t('shell.loginInvite') }}</p>
            <p class="mt-0.5 text-[0.6875rem] text-muted-color">{{ t('shell.loginInviteHint') }}</p>
            <div class="mt-2 flex gap-2">
                <Button as="router-link" :to="loginTarget" :label="t('common.login')" size="small" class="flex-1" />
                <Button as="router-link" to="/signup" :label="t('common.signup')" size="small" severity="secondary" variant="outlined" class="flex-1" />
            </div>
        </div>

        <BoardHubBody :arrows="false" compact>
            <template #before-sections>
                <!-- 바로 가기 — 메뉴의 최상위 항목(그룹에 들지 않은 것)에서 홈을 뺀 것을 4열 아이콘 칸으로. 아이콘은 연결 유형별 기본(`UtilMenu.itemIcon` — 공개 메뉴 항목에는 아이콘 칸이 없다).
                     아이콘 칸 40px · 아이콘 22px · 글자 11px(사용자 2026-11-12 「너무 작다, 적당히 키워」 — 전엔 30px · 16px · 10px) -->
                <section v-if="shortcuts.length || user" class="mt-5" :aria-labelledby="shortcutsId" data-testid="menu-shortcuts">
                    <h2 :id="shortcutsId" class="text-[0.6875rem] font-normal text-muted-color">{{ t('menu.shortcutsTitle') }}</h2>
                    <ul class="m-0 mt-1.5 grid list-none grid-cols-4 gap-1.5 p-0">
                        <li v-for="item in shortcuts" :key="item.id" class="min-w-0">
                            <AppMenuLink :node="item" :class="SHORTCUT" data-testid="menu-shortcut">
                                <span :class="SHORTCUT_ICON" aria-hidden="true">
                                    <TablerIcon v-if="iconOf(item).startsWith('tabler:')" :name="iconOf(item).slice(7) as TablerIconName" class="block size-[1.375rem]" />
                                    <i v-else :class="[iconOf(item), 'text-[1.25rem]']" />
                                </span>
                                <span class="w-full truncate">{{ localizedName(item.label, item.nameEn) }}</span>
                            </AppMenuLink>
                        </li>
                        <!-- 보기 설정 — 로그인했을 때만(그 화면이 로그인 필요). 메뉴 구성과 따로 셸이 붙이는 칸(사용자 결정 2026-11-12) -->
                        <li v-if="user" class="min-w-0">
                            <NuxtLink to="/settings/view" :class="SHORTCUT" data-testid="menu-shortcut-view">
                                <span :class="SHORTCUT_ICON" aria-hidden="true"><i class="pi pi-eye text-[1.25rem]" /></span>
                                <span class="w-full truncate">{{ t('view.shortcut') }}</span>
                            </NuxtLink>
                        </li>
                    </ul>
                </section>
            </template>
        </BoardHubBody>

        <p class="mt-5 flex gap-3 text-[0.6875rem] text-muted-color">
            <NuxtLink to="/terms" class="hover:underline">{{ t('common.terms') }}</NuxtLink>
            <NuxtLink to="/privacy" class="font-semibold hover:underline">{{ t('common.privacy') }}</NuxtLink>
        </p>
    </div>
</template>

<script setup lang="ts">
import type { TablerIconName } from '~/components/common/TablerIcon.vue';
import type { MenuItemNode } from '~/composables/menu/api/useMenuApi.types';
import UtilMenu from '~/utils/util-menu';
import UtilRedirect from '~/utils/util-redirect';

/**
 * 메뉴 트리는 앱이 열릴 때 받아 둔 공유 상태(`useMenu` — `GET /api/menu`)를 읽는다. 게시판 줄·최근 방문은 게시판 페이지와 같은 부품(`BoardHubBody`).
 * 바로 가기 칸에 넣을 항목 = 메뉴 최상위 항목 전부(운영자 메뉴 구성 그대로 — 따로 고르는 설정 없음).
 */
const { t } = useI18n();
const route = useRoute();
const { user, loaded } = useAuth();
const { nodes } = useMenu();
const { localizedName } = useLocalizedName();
const shortcutsId = useId();

const SHORTCUT =
    'flex min-w-0 flex-col items-center gap-1 rounded-lg px-1 py-1.5 text-center text-[0.6875rem] text-muted-color hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800';
const SHORTCUT_ICON = 'grid size-10 place-items-center rounded-xl bg-surface-100 text-color dark:bg-surface-800';

/** 바로 가기 — 메뉴 최상위 항목에서 홈은 뺀다(하단 탭에 늘 있다, 사용자 결정 2026-11-12) */
const shortcuts = computed(() => UtilMenu.topItems(nodes.value).filter((item) => !(item.type === 'BUILTIN' && item.key === 'HOME') && item.path !== '/'));
const { bySlug } = useBoards();
/** 바로 가기 아이콘 — 게시판 항목이면 그 게시판 종류도 넘겨 핫딜(`DEAL`)을 가른다(공지는 slug) */
function iconOf(item: MenuItemNode): string {
    return UtilMenu.itemIcon(item, item.board ? bySlug(item.board.slug)?.kind : undefined);
}
/** 로그인하면 지금 보던 화면으로 돌아온다 */
const loginTarget = computed(() => UtilRedirect.toLogin(route.fullPath));
</script>
