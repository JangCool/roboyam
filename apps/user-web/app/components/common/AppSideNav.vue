<template>
    <!--
        화면 안의 좌측 서브메뉴(넓은 화면 전용) — 한 묶음의 화면들(설정 …)을 왼쪽에 세로로 늘어놓고, 고른 것은 알약 모양 배경으로 짚는다(admin-web `AppSideNav` 와 같은 모양).
        공개 사이트는 화면마다 주소가 있어 항목이 **링크**다(admin 은 값 고르기). 좁은 화면에서는 부르는 쪽이 이것 대신 목록 화면을 보인다.
    -->
    <nav class="w-full rounded-xl border border-surface bg-surface-0 p-2 dark:bg-surface-900" :aria-label="navLabel">
        <p v-if="title" class="m-0 px-2.5 pb-1.5 pt-1 text-xs font-semibold text-muted-color">{{ title }}</p>
        <ul class="m-0 flex list-none flex-col gap-0.5 p-0">
            <li v-for="item in items" :key="item.to">
                <NuxtLink
                    :to="item.to"
                    class="flex min-h-10 w-full items-center gap-2 rounded-lg px-2.5 text-sm focus-visible:outline-2 focus-visible:outline-primary"
                    :class="item.active ? 'bg-primary-50 font-medium text-primary-700 dark:bg-primary-950 dark:text-primary-300' : 'hover:bg-surface-100 dark:hover:bg-surface-800'"
                    :aria-current="item.active ? 'page' : undefined"
                    :data-testid="item.testid"
                >
                    <i v-if="item.icon" :class="item.icon" class="shrink-0 text-sm" aria-hidden="true" />
                    <span class="truncate">{{ item.label }}</span>
                </NuxtLink>
            </li>
        </ul>
    </nav>
</template>

<script setup lang="ts">
/** 메뉴 한 줄 */
interface SideNavLink {
    to: string;
    label: string;
    /** `pi pi-user` 처럼 통째로 */
    icon?: string;
    /** 지금 화면인가 */
    active: boolean;
    testid?: string;
}

interface Props {
    items: SideNavLink[];
    /** 메뉴의 이름(스크린리더용 — `nav` 의 `aria-label`) */
    navLabel: string;
    /** 메뉴 위 작은 제목 */
    title?: string;
}

withDefaults(defineProps<Props>(), { title: '' });
</script>
