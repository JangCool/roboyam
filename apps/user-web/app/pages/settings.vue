<template>
    <!--
        설정 묶음의 틀 — 넓은 화면(md 이상)은 왼쪽 메뉴(`AppSideNav`) + 오른쪽 화면, 좁은 화면은 `/settings` 가 목록이고 각 화면 위에 「‹ 설정」 돌아가기 링크가 선다.
        메뉴의 차례·이름은 `UtilSettings.ENTRIES` 한 곳에서 온다(좁은 화면 목록과 같다).
    -->
    <div class="mx-auto w-full max-w-4xl md:grid md:grid-cols-[13rem_minmax(0,1fr)] md:items-start md:gap-6">
        <AppSideNav class="max-md:hidden md:sticky md:top-16" :items="navItems" :title="t('settings.title')" :nav-label="t('settings.title')" />

        <div class="min-w-0">
            <NuxtLink
                v-if="!atIndex"
                to="/settings"
                class="mb-2 inline-flex min-h-9 items-center gap-1 rounded text-sm text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary md:hidden"
                data-testid="settings-back"
            >
                <i class="pi pi-angle-left" aria-hidden="true" />{{ t('settings.title') }}
            </NuxtLink>
            <NuxtPage />
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilSettings from '~/utils/util-settings';

/**
 * 설정 묶음 `/settings/*` 의 부모 화면 — 로그인 필요(`auth` 미들웨어 — 아래 화면들도 각자 건다).
 * 화면: 프로필(`/settings/profile`) · 알림 설정(`/settings/notifications`) · 시간 표시(`/settings/display`) · 계정·로그인 연결(`/settings/account`).
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const route = useRoute();

const atIndex = computed(() => route.path === '/settings' || route.path === '/settings/');

const navItems = computed(() =>
    UtilSettings.ENTRIES.map((entry) => ({
        to: entry.to,
        label: t(entry.labelKey),
        icon: entry.icon,
        active: UtilSettings.isActive(route.path, entry.to),
        testid: `${entry.testid}-nav`,
    })),
);
</script>
