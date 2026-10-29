<template>
    <!-- 좁은 화면의 설정 목록 — 넓은 화면은 왼쪽 메뉴가 있어 첫 화면(프로필)으로 바로 간다 -->
    <div class="flex flex-col gap-4">
        <h1 class="text-lg font-bold">{{ t('settings.title') }}</h1>

        <ul class="m-0 list-none panel-card p-0" data-testid="settings-list">
            <li v-for="entry in UtilSettings.ENTRIES" :key="entry.to" class="border-b border-surface last:border-b-0">
                <NuxtLink
                    :to="entry.to"
                    class="flex min-h-14 items-center gap-3 px-4 py-2.5 hover:bg-surface-100 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                    :data-testid="`${entry.testid}-row`"
                >
                    <i :class="[entry.icon, 'text-muted-color']" aria-hidden="true" />
                    <span class="min-w-0 flex-1">
                        <span class="block text-sm font-medium">{{ t(entry.labelKey) }}</span>
                        <small class="block text-xs text-muted-color">{{ t(entry.hintKey) }}</small>
                    </span>
                    <i class="pi pi-angle-right text-muted-color" aria-hidden="true" />
                </NuxtLink>
            </li>
        </ul>
    </div>
</template>

<script setup lang="ts">
import UtilSettings from '~/utils/util-settings';

/**
 * 설정 `/settings` — 좁은 화면은 설정 화면 목록, 넓은 화면(md 이상)은 왼쪽 메뉴가 있으므로 첫 화면(`UtilSettings.FIRST_PATH`)으로 주소를 바꿔 끼운다
 * (뒤로 가기가 이 화면에 걸리지 않게 `replace`). 창 폭이 바뀌어 넓어져도 따라간다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('settings.title'), description: () => t('settings.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

const router = useRouter();
const isDesktop = useIsDesktop();
watch(
    isDesktop,
    (desktop) => {
        if (desktop) void router.replace(UtilSettings.FIRST_PATH);
    },
    { immediate: true },
);
</script>
