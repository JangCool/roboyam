<template>
    <!--
        화면 설정 서랍 — 강조색 · 상단 네비 색 · 배경 색상 · 글자 크기 · 다크 모드 · 본문 폭 · 밀도 · 메뉴 위치 · 서랍 위치 · (사이드바일 때) 사이드바 위치와 호버 확장 · 알림 방식.
        바꾸는 즉시 화면에 걸리고 이 브라우저에 저장된다(`useLayoutPrefs`). 색은 PrimeVue 의 primary/surface 팔레트를 실행 중에 바꾸는 방식이다.
        열리는 쪽은 설정의 "서랍 위치"를 따른다. 한 줄은 `SettingRow`(이름 + 고르는 부품), 켜고 끄는 줄은 `.setting-row` 안에 라벨과 스위치를 나란히 둔다.
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :header="t('shell.settingsTitle')" :style="{ width: '20rem', maxWidth: '94vw' }">
        <div class="flex flex-col gap-4">
            <section>
                <h3 :id="primaryId" class="mb-2 text-sm font-medium">{{ t('shell.settingsPrimary') }}</h3>
                <div class="grid grid-cols-5 gap-2" role="radiogroup" :aria-labelledby="primaryId">
                    <button
                        v-for="color in UtilLayoutPrefs.PRIMARY_COLORS"
                        :key="color.name"
                        type="button"
                        role="radio"
                        class="swatch"
                        :class="{ 'swatch--selected': prefs.primary === color.name }"
                        :style="{ backgroundColor: `var(--p-${color.name}-500)` }"
                        :aria-checked="prefs.primary === color.name"
                        :aria-label="t(color.labelKey)"
                        :title="t(color.labelKey)"
                        @click="set('primary', color.name)"
                    >
                        <i v-if="prefs.primary === color.name" class="pi pi-check text-xs text-white" aria-hidden="true" />
                    </button>
                </div>
            </section>

            <section class="setting-row flex items-center justify-between gap-3">
                <div class="min-w-0">
                    <label :for="barTintId" class="text-sm">{{ t('shell.settingsBarTint') }}</label>
                    <p class="mt-0.5 text-xs text-muted-color">{{ t('shell.barTintHint') }}</p>
                </div>
                <ToggleSwitch class="shrink-0" :input-id="barTintId" :model-value="prefs.barTint" @update:model-value="(on: boolean) => set('barTint', on)" />
            </section>

            <section>
                <h3 :id="surfaceId" class="mb-2 text-sm font-medium">{{ t('shell.settingsSurface') }}</h3>
                <div class="grid grid-cols-6 gap-2" role="radiogroup" :aria-labelledby="surfaceId">
                    <button
                        v-for="color in UtilLayoutPrefs.SURFACE_COLORS"
                        :key="color.name"
                        type="button"
                        role="radio"
                        class="swatch swatch--bordered"
                        :class="{ 'swatch--selected': prefs.surface === color.name }"
                        :style="{ backgroundColor: color.swatch }"
                        :aria-checked="prefs.surface === color.name"
                        :aria-label="t(color.labelKey)"
                        :title="t(color.labelKey)"
                        @click="set('surface', color.name)"
                    >
                        <i v-if="prefs.surface === color.name" class="pi pi-check text-xs" aria-hidden="true" />
                    </button>
                </div>
            </section>

            <SettingRow :label="t('shell.settingsTextSize')">
                <div class="flex items-center justify-between gap-1">
                    <Button type="button" icon="pi pi-minus" variant="text" rounded size="small" severity="secondary" :disabled="prefs.textScale === minScale" :aria-label="t('shell.textSmaller')" @click="step(-1)" />
                    <output class="w-12 text-center text-sm tabular-nums" aria-live="polite">{{ prefs.textScale }}%</output>
                    <Button type="button" icon="pi pi-plus" variant="text" rounded size="small" severity="secondary" :disabled="prefs.textScale === maxScale" :aria-label="t('shell.textLarger')" @click="step(1)" />
                </div>
            </SettingRow>

            <section class="setting-row flex items-center justify-between gap-3">
                <label :for="darkId" class="text-sm">{{ t('shell.settingsDark') }}</label>
                <ToggleSwitch :input-id="darkId" :model-value="prefs.theme === 'dark'" @update:model-value="(on: boolean) => set('theme', on ? 'dark' : 'light')" />
            </section>

            <SettingRow :label="t('shell.settingsWidth')">
                <SelectButton :model-value="prefs.contentWidth" :options="widthOptions" option-label="label" option-value="value" :allow-empty="false" fluid @update:model-value="(value: ContentWidth) => set('contentWidth', value)" />
            </SettingRow>

            <SettingRow :label="t('shell.settingsDensity')">
                <SelectButton :model-value="prefs.density" :options="densityOptions" option-label="label" option-value="value" :allow-empty="false" fluid @update:model-value="(value: Density) => set('density', value)" />
            </SettingRow>

            <SettingRow :label="t('shell.settingsMenuPosition')">
                <SelectButton :model-value="prefs.menuPosition" :options="menuOptions" option-label="label" option-value="value" :allow-empty="false" fluid @update:model-value="(value: MenuPosition) => set('menuPosition', value)" />
                <p v-if="isMobile" class="mt-2 text-xs text-muted-color">{{ t('shell.menuPositionMobileHint') }}</p>
            </SettingRow>

            <!-- 서랍은 메뉴 위치와 무관하게 늘 있다 -->
            <SettingRow :label="t('shell.settingsDrawerPosition')">
                <SelectButton :model-value="prefs.drawerPosition" :options="sideOptions" option-label="label" option-value="value" :allow-empty="false" fluid @update:model-value="(value: SidePosition) => set('drawerPosition', value)" />
            </SettingRow>

            <!-- 아래 둘은 메뉴 위치가 사이드바일 때만 뜻이 있다 -->
            <template v-if="prefs.menuPosition === 'sidebar'">
                <SettingRow :label="t('shell.settingsSidebarPosition')">
                    <SelectButton :model-value="prefs.sidebarPosition" :options="sideOptions" option-label="label" option-value="value" :allow-empty="false" fluid @update:model-value="(value: SidePosition) => set('sidebarPosition', value)" />
                </SettingRow>

                <section class="setting-row flex items-center justify-between gap-3">
                    <div class="min-w-0">
                        <label :for="hoverId" class="text-sm">{{ t('shell.settingsHoverExpand') }}</label>
                        <p class="mt-0.5 text-xs text-muted-color">{{ t('shell.hoverExpandHint') }}</p>
                    </div>
                    <ToggleSwitch class="shrink-0" :input-id="hoverId" :model-value="prefs.hoverExpand" @update:model-value="(on: boolean) => set('hoverExpand', on)" />
                </section>
            </template>

            <!-- 새 알림을 어떻게 알리나 — 이 브라우저의 설정이다 -->
            <section class="flex flex-col gap-2">
                <h3 class="text-sm font-medium">{{ t('shell.settingsNotify') }}</h3>
                <div class="setting-row flex items-center justify-between gap-3">
                    <div class="min-w-0">
                        <label :for="toastId" class="text-sm">{{ t('shell.notifyToast') }}</label>
                        <p class="mt-0.5 text-xs text-muted-color">{{ t('shell.notifyToastHint') }}</p>
                    </div>
                    <ToggleSwitch class="shrink-0" :input-id="toastId" :model-value="prefs.notifyToast" @update:model-value="(on: boolean) => set('notifyToast', on)" />
                </div>
                <div class="setting-row flex items-center justify-between gap-3">
                    <div class="min-w-0">
                        <label :for="osId" class="text-sm">{{ t('shell.notifyOs') }}</label>
                        <p class="mt-0.5 text-xs" :class="osBlocked ? 'text-orange-600 dark:text-orange-400' : 'text-muted-color'">{{ osHint }}</p>
                    </div>
                    <ToggleSwitch class="shrink-0" :input-id="osId" :model-value="prefs.notifyOs" :disabled="osPermission === 'unsupported'" @update:model-value="onNotifyOs" />
                </div>
            </section>
        </div>
    </Drawer>
</template>

<script setup lang="ts">
import type { ContentWidth, Density, MenuPosition, SidePosition } from '~/composables/shell/useLayoutPrefs.types';
import UtilLayoutPrefs from '~/utils/util-layout-prefs';

const open = defineModel<boolean>('visible', { required: true });

const { t } = useI18n();
const { prefs, set, isMobile } = useLayoutPrefs();
const { osPermission, requestOsPermission, refreshOsPermission } = useNotification();

const primaryId = useId();
const surfaceId = useId();
const darkId = useId();
const barTintId = useId();
const hoverId = useId();
const toastId = useId();
const osId = useId();

const minScale = UtilLayoutPrefs.TEXT_SCALES[0];
const maxScale = UtilLayoutPrefs.TEXT_SCALES[UtilLayoutPrefs.TEXT_SCALES.length - 1];

/** 옵션 이름은 번역이라 `computed` — 언어를 바꾸면 다시 계산된다 */
const widthOptions = computed(() => [
    { label: t('shell.widthFull'), value: 'full' },
    { label: t('shell.widthBoxed'), value: 'boxed' },
]);
const densityOptions = computed(() => [
    { label: t('shell.densityNormal'), value: 'normal' },
    { label: t('shell.densityCompact'), value: 'compact' },
]);
const menuOptions = computed(() => [
    { label: t('shell.menuSidebar'), value: 'sidebar' },
    { label: t('shell.menuTop'), value: 'top' },
]);
const sideOptions = computed(() => [
    { label: t('shell.sideLeft'), value: 'left' },
    { label: t('shell.sideRight'), value: 'right' },
]);

function step(by: number) {
    set('textScale', UtilLayoutPrefs.stepScale(prefs.value.textScale, by));
}

// ── OS 알림 ────────────────────────────────────────────────────────────────────

// 브라우저 설정에서 바꿨을 수 있다 — 서랍을 열 때마다 다시 읽는다
watch(open, (isOpen) => isOpen && refreshOsPermission());

/** 켰는데 브라우저가 막고 있다 — 켜 둔 것만 보고 「왜 안 오나」가 되지 않게 말해 준다 */
const osBlocked = computed(() => prefs.value.notifyOs && osPermission.value === 'denied');

const osHint = computed(() => {
    if (osPermission.value === 'unsupported') return t('shell.notifyOsUnsupported');
    if (osBlocked.value) return t('shell.notifyOsDenied');
    return t('shell.notifyOsHint');
});

/** OS 알림을 켠다 — 누른 이 자리가 사용자 조작이라 여기서 권한을 묻는다(브라우저는 조작 없이 묻는 것을 막는다) */
async function onNotifyOs(on: boolean) {
    set('notifyOs', on);
    if (on && osPermission.value === 'default') await requestOsPermission();
}
</script>
