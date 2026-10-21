<template>
    <!-- 하단 탭(모바일) — 홈·게시판·소모임·알림·내 정보 5개 고정(design 01-shell 2). 소모임 탭은 `/groups` 로 시작하는 모든 주소(목록·내 소모임·상세·관리)에서 밝다.
         알림 탭에는 안 읽은 숫자가 붙는다(링크 이름에 "알림, 안 읽은 알림 3개"가 들어가고 숫자 글자는 눈으로만 보는 것이라 aria-hidden) -->
    <nav :aria-label="t('shell.bottomTabs')" class="sticky bottom-0 z-30 border-t border-surface bg-surface-0 pb-[env(safe-area-inset-bottom)] dark:bg-surface-900">
        <ul class="m-0 flex list-none justify-around p-0">
            <li v-for="tab in TABS" :key="tab.to" class="flex-1">
                <NuxtLink
                    :to="tab.to"
                    class="flex flex-col items-center gap-0.5 py-2 text-xs focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
                    :class="isActive(tab.to) ? 'font-semibold text-color' : 'text-muted-color'"
                    :aria-current="isActive(tab.to) ? 'page' : undefined"
                    :aria-label="tab.notify ? badgeLabel : undefined"
                    :data-testid="tab.notify ? 'tab-notifications' : undefined"
                >
                    <span class="relative inline-flex">
                        <i :class="[tab.icon, 'text-lg']" aria-hidden="true" />
                        <span
                            v-if="tab.notify && badgeText"
                            class="absolute -right-3 -top-1.5 grid min-w-4 place-items-center rounded-full bg-red-600 px-1 text-[0.625rem] font-bold leading-4 text-white"
                            aria-hidden="true"
                            data-testid="tab-notifications-badge"
                        >
                            {{ badgeText }}
                        </span>
                    </span>
                    <span>{{ tab.group ? term('GROUPS') : t(tab.label) }}</span>
                </NuxtLink>
            </li>
        </ul>
    </nav>
</template>

<script setup lang="ts">
const { t } = useI18n();
const route = useRoute();
const { text: badgeText, label: badgeLabel } = useNotificationBadge();
/** 소모임 탭의 이름은 서비스 이름(`GROUPS`)이다 — 운영자가 바꾸면 따라 바뀐다 */
const { term } = useSiteTerms();

const TABS = [
    { to: '/', label: 'shell.tabHome', icon: 'pi pi-home', notify: false, group: false },
    { to: '/boards', label: 'shell.tabBoards', icon: 'pi pi-th-large', notify: false, group: false },
    { to: '/groups', label: 'shell.tabGroups', icon: 'pi pi-users', notify: false, group: true },
    { to: '/notifications', label: 'shell.tabNotifications', icon: 'pi pi-bell', notify: true, group: false },
    { to: '/me', label: 'shell.myInfo', icon: 'pi pi-user', notify: false, group: false },
] as const;

/** 게시판 탭은 게시판 허브뿐 아니라 개별 게시판 목록도, 소모임 탭은 소모임 목록뿐 아니라 `/groups/…`(내 소모임·상세·관리)도 밝힌다 */
function isActive(to: string): boolean {
    if (to === '/') return route.path === '/';
    return route.path === to || route.path.startsWith(`${to}/`);
}
</script>
