<template>
    <!--
        설정 묶음의 틀 — 넓은 화면(md 이상)은 왼쪽 메뉴(`AppSideNav`) + 오른쪽 화면, 좁은 화면은 `/settings` 가 목록이고 각 화면 위에 「‹ 설정」 돌아가기 링크가 선다
        (하위 화면 — 이메일·비밀번호 변경 — 은 「‹ 계정·로그인 연결」로 부모에 돌아간다).
        메뉴의 차례·이름은 `UtilSettings.ENTRIES` 한 곳에서 온다(좁은 화면 목록과 같다). 하위 화면은 왼쪽 메뉴에만 들여 써서 보인다.
    -->
    <div class="mx-auto w-full max-w-4xl md:grid md:grid-cols-[13rem_minmax(0,1fr)] md:items-start md:gap-6">
        <AppSideNav class="max-md:hidden md:sticky md:top-16" :items="navItems" :title="t('settings.title')" :nav-label="t('settings.title')" />

        <div class="min-w-0">
            <NuxtLink
                v-if="!atIndex"
                :to="back.to"
                class="mb-2 inline-flex min-h-9 items-center gap-1 rounded text-sm text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary md:hidden"
                data-testid="settings-back"
            >
                <i class="pi pi-angle-left" aria-hidden="true" />{{ back.label }}
            </NuxtLink>
            <NuxtPage />
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilSettings from '~/utils/util-settings';

/**
 * 설정 묶음 `/settings/*` 의 부모 화면 — 로그인 필요(`auth` 미들웨어 — 아래 화면들도 각자 건다).
 * 화면: 프로필(`/settings/profile`) · 알림 설정(`/settings/notifications`) · 시간 표시(`/settings/display`) · 계정·로그인 연결(`/settings/account`)
 * › 이메일 변경(`/settings/account/email`) · 비밀번호 변경(`/settings/account/password`).
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const route = useRoute();

const atIndex = computed(() => route.path === '/settings' || route.path === '/settings/');

/** 좁은 화면의 돌아가기 — 하위 화면이면 부모, 아니면 설정 목록 */
const back = computed(() => {
    const parent = UtilSettings.parentOf(route.path);
    return parent ? { to: parent.to, label: t(parent.labelKey) } : { to: '/settings', label: t('settings.title') };
});

const navItems = computed(() =>
    UtilSettings.ENTRIES.flatMap((entry) => {
        const children = (entry.children ?? []).map((child) => ({
            to: child.to,
            label: t(child.labelKey),
            active: UtilSettings.isActive(route.path, child.to),
            sub: true,
            testid: `${child.testid}-nav`,
        }));
        // 하위 화면에 있으면 그 줄만 짚는다(부모는 짚지 않는다)
        const parentActive = UtilSettings.isActive(route.path, entry.to) && !children.some((child) => child.active);
        return [{ to: entry.to, label: t(entry.labelKey), icon: entry.icon, active: parentActive, testid: `${entry.testid}-nav` }, ...children];
    }),
);
</script>
