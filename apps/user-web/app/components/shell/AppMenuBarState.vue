<template>
    <!--
        상단 메뉴 막대(테마 셸의 가로 메뉴)의 상태 자리 — 받는 중(줄 모양 스켈레톤) · 빈 메뉴(홈으로) · 기본 메뉴/오류 안내(다시 시도). 메뉴가 준비됐으면(`ready`) 아무것도 그리지 않는다.
        `fallback`·`error` 는 안내 뒤에 기본 메뉴 칸이 이어지므로 막대가 칸을 함께 그린다. 상태 판정은 `useMenuItems().state`(`UtilMenu.viewState`).
        흰 면에서도 주 색상 바(`.app-nav`) 위에서도 읽히게 스켈레톤은 글자색(`bg-current`)을 옅게 쓰고, 단추는 `nav-btn-outline`(바 위에서만 뒤집힌다)을 단다.
    -->
    <div v-if="state === 'loading'" class="flex h-10 shrink-0 items-center gap-3" role="status" :aria-label="t('menu.loadingLabel')">
        <span v-for="n in 6" :key="n" class="h-3.5 animate-pulse rounded bg-current opacity-15" :class="n % 2 ? 'w-14' : 'w-10'" />
    </div>

    <div v-else-if="state === 'empty'" class="flex h-10 shrink-0 items-center gap-2 text-xs text-muted-color" data-testid="menu-empty">
        <span>{{ t('menu.emptyTitle') }}</span>
        <Button type="button" :label="t('menu.emptyHome')" size="small" variant="outlined" severity="secondary" class="nav-btn-outline" @click="goHome" />
    </div>

    <div
        v-else-if="state === 'fallback' || state === 'error'"
        class="mr-2 flex h-10 shrink-0 items-center gap-2 text-xs text-muted-color"
        :role="state === 'error' ? 'alert' : 'status'"
        data-testid="menu-fallback-notice"
    >
        <span>{{ state === 'error' ? t('menu.errorTitle') : t('menu.fallbackNotice') }}</span>
        <Button type="button" :label="t('common.retry')" size="small" variant="outlined" severity="secondary" class="nav-btn-outline" :loading="loading" @click="emit('reload')" />
    </div>
</template>

<script setup lang="ts">
import type { MenuViewState } from '~/utils/util-menu';

interface Props {
    /** 메뉴 자리의 모양(`useMenuItems().state`) */
    state: MenuViewState;
    /** 다시 받는 중인가 — 다시 시도 단추가 돈다 */
    loading?: boolean;
}

withDefaults(defineProps<Props>(), { loading: false });
const emit = defineEmits<{ (e: 'reload'): void }>();

const { t } = useI18n();

/** 메뉴 자리가 비었을 때 — 로고를 누르는 것과 같이 홈으로 간다 */
async function goHome() {
    await navigateTo('/');
}
</script>
