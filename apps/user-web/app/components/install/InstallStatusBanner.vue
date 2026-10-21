<template>
    <!-- 설치 상태를 못 받았다 — 앱을 막지 않고 맨 위에 안내만 띄운다(마법사 화면은 자기 화면에서 다시 시도를 보인다) -->
    <div
        v-if="visible"
        class="flex flex-wrap items-center justify-center gap-x-3 gap-y-1 bg-amber-100 px-4 py-2 text-center text-sm text-amber-900 dark:bg-amber-950 dark:text-amber-200"
        role="alert"
        data-testid="install-status-banner"
    >
        <span>{{ t('install.statusBanner') }}</span>
        <Button type="button" :label="t('install.statusRecheck')" size="small" severity="secondary" variant="outlined" :loading="checking" data-testid="install-status-retry" @click="retry" />
    </div>
</template>

<script setup lang="ts">
import UtilInstallState from '~/utils/util-install-state';

/**
 * 설치 상태를 받지 못했을 때(`failed`)의 안내 띠 — `app.vue` 가 모든 화면 위에 한 번 놓는다.
 * 설치 마법사(`/install`)에서는 그 화면이 오류 안내와 다시 시도를 직접 보이므로 띠를 그리지 않는다.
 * 다시 확인해 설치 전(`pending`)으로 밝혀지면 마법사로 보낸다 — 설치 후면 띠가 사라질 뿐이다.
 */
const { t } = useI18n();
const route = useRoute();
const { phase, refresh } = useInstallState();

const checking = ref(false);
const visible = computed(() => phase.value === 'failed' && !UtilInstallState.isInstallPath(route.path));

async function retry() {
    if (checking.value) return;
    checking.value = true;
    try {
        if ((await refresh()) === 'pending') await navigateTo(UtilInstallState.PATH, { replace: true });
    } finally {
        checking.value = false;
    }
}
</script>
