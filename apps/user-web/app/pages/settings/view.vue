<template>
    <div class="content-card flex flex-col gap-4">
        <h1 class="text-lg font-bold">{{ t('settings.view') }}</h1>

        <div v-if="phase === 'loading' || phase === 'idle'" class="space-y-3" role="status" :aria-label="t('view.loading')" data-testid="view-loading">
            <div v-for="n in 4" :key="n" class="h-14 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="phase === 'error'" :title="t('view.loadFailed')" :description="errorMessage" retry data-testid="view-error" @retry="load" />

        <template v-else>
            <!-- 화면 — 야간 모드 · 글자 크기. 바꾸는 즉시 저장한다 -->
            <section :aria-labelledby="`${uid}-screen`">
                <h2 :id="`${uid}-screen`" class="px-1 pb-1.5 text-xs font-semibold text-muted-color">{{ t('view.sectionScreen') }}</h2>
                <div class="panel-card">
                    <div class="flex flex-col gap-2 border-b border-surface px-4 py-3 md:flex-row md:items-center md:justify-between md:gap-4">
                        <div class="min-w-0 flex-1">
                            <p :id="`${uid}-theme-label`" class="m-0 text-sm font-medium">{{ t('view.theme') }}</p>
                            <p :id="`${uid}-theme-hint`" class="m-0 mt-0.5 text-xs leading-snug text-muted-color">{{ t('view.themeHint') }}</p>
                        </div>
                        <div class="w-full md:w-64 md:shrink-0">
                            <SelectButton
                                :model-value="settings.theme"
                                :options="themeOptions"
                                option-label="label"
                                option-value="value"
                                :allow-empty="false"
                                :aria-labelledby="`${uid}-theme-label`"
                                :aria-describedby="`${uid}-theme-hint`"
                                fluid
                                data-testid="setting-theme"
                                @update:model-value="(v: ThemeSetting | null) => v && change({ theme: v })"
                            />
                        </div>
                    </div>

                    <!--
                        글자 크기 — PC·휴대폰 따로(화면 폭으로 고른다, `UtilViewSettings.MOBILE_QUERY`). − / + 로 한 단씩. 끝에서는 `aria-disabled` 로(진짜 disabled 는 누른 채 비활성이 되면 초점이 사라진다).
                        지금 보고 있는 기기의 줄에 「지금 이 기기」 칩과 옅은 강조 — 창 폭이 경계를 넘으면 따라 옮겨 간다
                    -->
                    <div
                        v-for="row in scaleRows"
                        :key="row.key"
                        class="flex flex-col gap-2 border-b border-surface px-4 py-3 md:flex-row md:items-center md:justify-between md:gap-4"
                        :class="row.current ? 'bg-primary-50/40 dark:bg-primary-950/30' : ''"
                        :data-current-device="row.current ? '' : undefined"
                        :data-testid="`${row.testid}-row`"
                    >
                        <div class="flex min-w-0 flex-1 items-start gap-2.5">
                            <i :class="[row.icon, row.current ? 'text-primary' : 'text-muted-color']" class="mt-0.5 shrink-0 text-base" aria-hidden="true" />
                            <div class="min-w-0 flex-1">
                                <p class="m-0 flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-sm font-medium">
                                    <span :id="`${uid}-${row.key}-label`">{{ row.label }}</span>
                                    <span
                                        v-if="row.current"
                                        class="inline-flex items-center rounded-full bg-primary-50 px-1.5 text-[0.625rem] font-semibold leading-4 text-primary-700 dark:bg-primary-950 dark:text-primary-300"
                                        :data-testid="`${row.testid}-this-device`"
                                        >{{ t('view.textScaleThisDevice') }}</span
                                    >
                                </p>
                                <p :id="`${uid}-${row.key}-hint`" class="m-0 mt-0.5 text-xs leading-snug text-muted-color">{{ row.hint }}</p>
                            </div>
                        </div>
                        <div
                            class="flex w-full items-center justify-between gap-1 md:w-64 md:shrink-0"
                            role="group"
                            :aria-labelledby="`${uid}-${row.key}-label`"
                            :aria-describedby="`${uid}-${row.key}-hint ${uid}-scale-note`"
                            :data-testid="row.testid"
                        >
                            <Button
                                type="button"
                                icon="pi pi-minus"
                                text
                                rounded
                                severity="secondary"
                                :aria-label="t('view.textSmaller')"
                                :aria-disabled="row.canSmaller ? undefined : 'true'"
                                :class="row.canSmaller ? '' : 'opacity-40'"
                                :data-testid="`${row.testid}-smaller`"
                                @click="stepText(row.key, -1)"
                            />
                            <span class="min-w-14 text-center text-sm font-medium tabular-nums" aria-live="polite" :data-testid="`${row.testid}-value`">{{ t('view.textScaleValue', { n: row.value }) }}</span>
                            <Button
                                type="button"
                                icon="pi pi-plus"
                                text
                                rounded
                                severity="secondary"
                                :aria-label="t('view.textLarger')"
                                :aria-disabled="row.canLarger ? undefined : 'true'"
                                :class="row.canLarger ? '' : 'opacity-40'"
                                :data-testid="`${row.testid}-larger`"
                                @click="stepText(row.key, 1)"
                            />
                        </div>
                    </div>
                    <!-- 두 줄 공통 안내 — 한 번만 -->
                    <p :id="`${uid}-scale-note`" class="m-0 px-4 py-2.5 text-xs leading-snug text-muted-color" data-testid="setting-text-scale-note">{{ t('view.textScaleHint') }}</p>
                </div>
            </section>

            <!-- 색상 — 색상 테마 8가지(강조색·바탕·로고 줄). 누르는 즉시 바뀐다. ⚠ MOCK: 이 기기에만 저장(`useColorPalette`) -->
            <section :aria-labelledby="`${uid}-color`">
                <h2 :id="`${uid}-color`" class="px-1 pb-1.5 text-xs font-semibold text-muted-color">{{ t('view.sectionColor') }}</h2>
                <div class="panel-card px-4 py-3">
                    <p :id="`${uid}-palette-label`" class="m-0 text-sm font-medium">{{ t('view.palette') }}</p>
                    <p :id="`${uid}-palette-hint`" class="m-0 mt-0.5 text-xs leading-snug text-muted-color">{{ t('view.paletteHint') }}</p>
                    <div
                        class="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4"
                        role="radiogroup"
                        :aria-labelledby="`${uid}-palette-label`"
                        :aria-describedby="`${uid}-palette-hint`"
                        data-testid="setting-palette"
                    >
                        <button
                            v-for="swatch in UtilColorPalette.SWATCHES"
                            :key="swatch.value"
                            type="button"
                            role="radio"
                            :aria-checked="palette === swatch.value"
                            class="min-w-0 overflow-hidden rounded-xl border-2 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                            :class="palette === swatch.value ? 'border-primary' : 'border-surface-200 hover:border-surface-300 dark:border-surface-700 dark:hover:border-surface-600'"
                            :data-palette-option="swatch.value"
                            @click="changePalette(swatch.value)"
                        >
                            <!-- 견본 — 화면 모드와 상관없이 그 테마의 라이트 모양을 정해 둔 색으로 칠한다 -->
                            <span class="block" aria-hidden="true" :style="{ backgroundColor: swatch.page }">
                                <span class="flex h-7 items-center justify-between px-2" :style="{ backgroundColor: swatch.topbar }">
                                    <b class="text-[0.6875rem] tracking-wide" :style="{ color: swatch.brand }">{{ siteLabel }}</b>
                                    <i class="pi pi-bell text-[0.6875rem]" :style="{ color: swatch.brand }" />
                                </span>
                                <span class="flex h-8 items-center justify-end px-2">
                                    <span class="rounded-full px-2 text-[0.625rem] font-semibold leading-4 text-white" :style="{ backgroundColor: swatch.accent }">{{ t('view.previewLike') }}</span>
                                </span>
                            </span>
                            <span class="flex items-center justify-between gap-1 bg-card px-2 py-1.5 text-xs font-medium">
                                <span class="truncate">{{ t(swatch.labelKey) }}</span>
                                <i v-if="palette === swatch.value" class="pi pi-check-circle text-primary" aria-hidden="true" />
                            </span>
                        </button>
                    </div>
                    <!-- 배경색 — 테마 기본이면 고른 색상 테마의 바탕, 그 밖은 고른 색으로 덮는다(`palettes.css` 의 `html[data-page-bg]`) -->
                    <div class="mt-3 border-t border-surface pt-3">
                        <p :id="`${uid}-bg-label`" class="m-0 text-sm font-medium">{{ t('view.background') }}</p>
                        <p :id="`${uid}-bg-hint`" class="m-0 mt-0.5 text-xs leading-snug text-muted-color">{{ t('view.backgroundHint') }}</p>
                        <div class="mt-2 flex flex-wrap gap-2" role="radiogroup" :aria-labelledby="`${uid}-bg-label`" :aria-describedby="`${uid}-bg-hint`" data-testid="setting-page-background">
                            <button
                                v-for="option in UtilColorPalette.BACKGROUNDS"
                                :key="option.value"
                                type="button"
                                role="radio"
                                :aria-checked="background === option.value"
                                class="inline-flex min-h-9 items-center gap-2 rounded-full border px-3 text-xs font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                                :class="background === option.value ? 'border-primary text-primary' : 'border-surface-200 hover:border-surface-300 dark:border-surface-700'"
                                :data-background-option="option.value"
                                @click="changeBackground(option.value)"
                            >
                                <!-- 견본 — 테마 기본은 고른 테마의 바탕색 -->
                                <span
                                    class="size-4 shrink-0 rounded-full border border-surface-300 dark:border-surface-600"
                                    :style="{ backgroundColor: option.color ?? currentSwatch.page }"
                                    aria-hidden="true"
                                />
                                {{ t(option.labelKey) }}
                            </button>
                        </div>
                    </div>

                    <!-- 상단 그라데이션 — 로고 줄 위쪽 끝의 그림자 띠(색은 고른 테마를 따른다) -->
                    <div class="mt-3 flex items-center justify-between gap-4 border-t border-surface pt-3">
                        <div class="min-w-0 flex-1">
                            <label :for="`${uid}-gradient`" class="m-0 block text-sm font-medium">{{ t('view.topbarGradient') }}</label>
                            <p :id="`${uid}-gradient-hint`" class="m-0 mt-0.5 text-xs leading-snug text-muted-color">{{ t('view.topbarGradientHint') }}</p>
                        </div>
                        <ToggleSwitch
                            :model-value="gradient"
                            :input-id="`${uid}-gradient`"
                            :aria-describedby="`${uid}-gradient-hint`"
                            data-testid="setting-topbar-gradient"
                            @update:model-value="changeGradient"
                        />
                    </div>
                    <p class="m-0 mt-2 text-xs text-muted-color" data-testid="setting-palette-device-note">{{ t('view.paletteDeviceNote') }}</p>
                </div>
            </section>

            <!-- 메뉴 — 데스크톱 메뉴 위치(자동 = 사이트 설정 따름). 모바일에는 적용하지 않는다(하단 탭·메뉴 단추 그대로) -->
            <section :aria-labelledby="`${uid}-menu`">
                <h2 :id="`${uid}-menu`" class="px-1 pb-1.5 text-xs font-semibold text-muted-color">{{ t('view.sectionMenu') }}</h2>
                <div class="flex flex-col gap-2 panel-card px-4 py-3 md:flex-row md:items-center md:justify-between md:gap-4">
                    <div class="min-w-0 flex-1">
                        <p :id="`${uid}-menu-label`" class="m-0 text-sm font-medium">{{ t('view.menuPosition') }}</p>
                        <p :id="`${uid}-menu-hint`" class="m-0 mt-0.5 text-xs leading-snug text-muted-color">
                            {{ settings.menuPosition === 'AUTO' ? t('view.menuPositionAutoHint', { position: positionName(sitePosition) }) : t('view.menuPositionHint') }}
                        </p>
                        <p class="m-0 mt-0.5 text-xs leading-snug text-muted-color" data-testid="setting-menu-mobile-note">{{ t('view.menuPositionMobileNote') }}</p>
                    </div>
                    <!-- 고를 것이 여섯이라 선택 단추 줄이 넘친다 — 펼침 목록으로 고른다 -->
                    <div class="w-full md:w-64 md:shrink-0">
                        <Select
                            :model-value="settings.menuPosition"
                            :options="menuOptions"
                            option-label="label"
                            option-value="value"
                            :aria-labelledby="`${uid}-menu-label`"
                            :aria-describedby="`${uid}-menu-hint`"
                            fluid
                            data-testid="setting-menu-position"
                            @update:model-value="(v: MemberMenuSetting | null) => v && change({ menuPosition: v })"
                        />
                    </div>
                </div>
            </section>

            <!-- 댓글 — 처음 보이는 정렬 -->
            <section :aria-labelledby="`${uid}-comments`">
                <h2 :id="`${uid}-comments`" class="px-1 pb-1.5 text-xs font-semibold text-muted-color">{{ t('view.sectionComments') }}</h2>
                <div class="flex flex-col gap-2 panel-card px-4 py-3 md:flex-row md:items-center md:justify-between md:gap-4">
                    <div class="min-w-0 flex-1">
                        <p :id="`${uid}-order-label`" class="m-0 text-sm font-medium">{{ t('view.commentOrder') }}</p>
                        <p :id="`${uid}-order-hint`" class="m-0 mt-0.5 text-xs leading-snug text-muted-color">{{ settings.commentOrder === 'LATEST' ? t('view.commentOrderLatestHint') : t('view.commentOrderOldestHint') }}</p>
                    </div>
                    <div class="w-full md:w-64 md:shrink-0">
                        <SelectButton
                            :model-value="settings.commentOrder"
                            :options="orderOptions"
                            option-label="label"
                            option-value="value"
                            :allow-empty="false"
                            :aria-labelledby="`${uid}-order-label`"
                            :aria-describedby="`${uid}-order-hint`"
                            fluid
                            data-testid="setting-comment-order"
                            @update:model-value="(v: CommentOrderSetting | null) => v && change({ commentOrder: v })"
                        />
                    </div>
                </div>
            </section>

            <!-- 시간 표시 — 로보얌은 따로 있는 화면(언어·시간대·형식, 자기 저장 줄)이라 이어지는 줄만 둔다 -->
            <NuxtLink
                to="/settings/display"
                class="flex min-h-14 items-center gap-3 panel-card px-4 py-2.5 hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                data-testid="view-display-link"
            >
                <i class="pi pi-clock text-muted-color" aria-hidden="true" />
                <span class="min-w-0 flex-1">
                    <span class="block text-sm font-medium">{{ t('settings.display') }}</span>
                    <small class="block text-xs text-muted-color">{{ t('settings.displayHint') }}</small>
                </span>
                <i class="pi pi-angle-right text-muted-color" aria-hidden="true" />
            </NuxtLink>

            <!-- 미리보기 — 라이트·다크를 나란히(지금 화면의 모드와 상관없이). 고른 모드에 「선택됨」, 글자 크기는 지금 보고 있는 기기 쪽(PC·휴대폰) 배율 -->
            <section :aria-labelledby="`${uid}-preview`">
                <h2 :id="`${uid}-preview`" class="px-1 pb-2 text-xs font-semibold text-muted-color">{{ t('view.previewTitle') }}</h2>
                <div class="grid grid-cols-2 gap-2" aria-hidden="true" :style="{ '--text-scale': String(previewScale / 100) }" data-testid="view-preview">
                    <div v-for="mode in modes" :key="mode.theme" class="relative min-w-0 rounded-xl border p-2 text-[0.6875rem] leading-snug" :class="mode.card" :data-mode="mode.theme">
                        <span v-if="settings.theme === mode.theme" class="absolute -top-2 right-1.5 rounded-full px-1.5 text-[0.625rem] font-semibold leading-4" :class="mode.fill" data-testid="preview-selected">{{
                            t('view.previewSelected')
                        }}</span>
                        <p class="m-0 mb-1 flex items-baseline justify-between font-bold">
                            <span>{{ siteLabel }}</span>
                            <small class="text-[0.625rem] font-normal opacity-70">{{ mode.label }}</small>
                        </p>
                        <div class="border-t py-1.5" :class="mode.line">
                            <span class="mr-1 rounded px-1 text-[0.625rem] font-semibold" :class="mode.tagA">{{ t('view.previewTagA') }}</span>{{ t('view.previewPostA') }}
                            <small class="mt-0.5 block text-[0.625rem] opacity-70">{{ t('view.previewMetaA') }}</small>
                        </div>
                        <div class="border-t py-1.5" :class="mode.line">
                            <span class="mr-1 rounded px-1 text-[0.625rem] font-semibold" :class="mode.tagB">{{ t('view.previewTagB') }}</span>{{ t('view.previewPostB') }}
                            <small class="mt-0.5 block text-[0.625rem] opacity-70">{{ t('view.previewMetaB') }}</small>
                        </div>
                        <p class="m-0 mt-1 text-right">
                            <span class="inline-block rounded-md px-2 text-[0.625rem] font-semibold leading-5" :class="mode.fill">{{ t('view.previewLike') }}</span>
                        </p>
                    </div>
                </div>
                <p class="m-0 mt-2 px-1 text-xs text-muted-color">{{ t('view.previewAutoNote') }}</p>
            </section>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { CommentOrderSetting, ThemeSetting, ViewSettings } from '~/composables/view/api/useViewSettingsApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import type { MemberMenuSetting, MenuPosition } from '~/utils/util-menu-position';
import UtilViewSettings from '~/utils/util-view-settings';
import type { ColorPalette, PageBackground } from '~/utils/util-color-palette';
import UtilColorPalette from '~/utils/util-color-palette';

/**
 * 보기 설정 `/settings/view` — 로그인 필요(community `pages/settings/view.vue` 중 로보얌에 적용할 곳이 있는 것).
 * 「화면」: 야간 모드(자동·라이트·다크) · 글자 크기 PC·휴대폰 따로(90·100·110·125%, 기본 PC 100·휴대폰 110 — 사용자 2026-11-11, 지금 기기 줄에 「지금 이 기기」), 「메뉴」: 데스크톱 메뉴 위치(자동·상단·왼쪽·오른쪽·상단+왼쪽·상단+오른쪽 — 회원 > 운영자 > 테마 기본, `useMenuPosition`), 「댓글」: 처음 정렬, 「시간 표시」는 따로 있는 화면으로 잇는다, 맨 아래 라이트·다크 미리보기.
 * **바꾸는 즉시 저장한다** — 화면에 먼저 반영하고(`useViewSettings().update`) 실패하면 바꾼 칸을 되돌리고 서버 문구를 토스트(8초). 성공은 토스트(3초).
 * 야간 모드·글자 크기를 `<html>` 에 거는 것은 `plugins/view-theme.client.ts` 다 — 여기서는 값만 바꾼다.
 * 「색상」: 색상 테마 8가지(기본 · 살구·코랄 · 꿀·버터 · 세이지 그린 · 하늘·크림 · 청록 바다 · 진한 파랑 · 남색 — 사용자 결정 2026-11-06, 개인 색 금지였던 themes.md 결정을 바꿨다) + 배경색(테마 기본 · 회색 · 아이보리 · 흰색 · 연한 하늘) + 상단 그라데이션 켜기.
 *   강조색·페이지 바탕·로고 줄을 한 벌로 바꾼다(`palettes.css`). ⚠ MOCK: 서버 칸이 없어 이 기기에만 저장한다(`useColorPalette`).
 * community 대비 뺀 것: 배경 색상(따로)·본문 너비·목록 밀도·화면 꾸미기 되돌리기.
 * 메뉴 위치는 ⚠ MOCK: 서버에 칸이 없어 이 브라우저에만 저장한다(`useViewSettingsApi`) — 로그인해 있어도 「이 기기에 저장했어요」로 알린다.
 * 적용할 곳이 아직 없어 뺀 것: 이미지 숨기기·답글 접기·한 번에 보는 댓글 수. 서버 `GET·PUT /api/me/settings`(`useViewSettingsApi`).
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('settings.view'), description: () => t('view.seo', { site: siteLabel.value }), robots: 'noindex' });

const { settings, phase, errorMessage, load, ensureLoaded, update } = useViewSettings();
const toast = useAppToast();
const uid = useId();
void ensureLoaded();

const themeOptions = computed(() => [
    { value: 'AUTO', label: t('view.themeAuto') },
    { value: 'LIGHT', label: t('view.themeLight') },
    { value: 'DARK', label: t('view.themeDark') },
]);
const menuOptions = computed<{ value: MemberMenuSetting; label: string }[]>(() => [
    { value: 'AUTO', label: t('view.menuAuto') },
    { value: 'TOP', label: t('view.menuTop') },
    { value: 'LEFT', label: t('view.menuLeft') },
    { value: 'RIGHT', label: t('view.menuRight') },
    { value: 'TOP_LEFT', label: t('view.menuTopLeft') },
    { value: 'TOP_RIGHT', label: t('view.menuTopRight') },
]);
/** 「자동」일 때 따라갈 위치(운영자 설정 → 테마 기본) — 안내 문구에 보인다 */
const { sitePosition } = useMenuPosition();
const POSITION_KEYS: Record<MenuPosition, string> = {
    TOP: 'view.menuTop',
    LEFT: 'view.menuLeft',
    RIGHT: 'view.menuRight',
    TOP_LEFT: 'view.menuTopLeft',
    TOP_RIGHT: 'view.menuTopRight',
};
function positionName(position: MenuPosition): string {
    return t(POSITION_KEYS[position]);
}
const orderOptions = computed(() => [
    { value: 'OLDEST', label: t('view.orderOldest') },
    { value: 'LATEST', label: t('view.orderLatest') },
]);

/** 글자 크기 두 칸 — PC(`textScale`)·휴대폰(`textScaleMobile`) */
type TextScaleKey = 'textScale' | 'textScaleMobile';
/** 지금 보고 있는 기기가 휴대폰 폭인가 — 셸·`view-theme` 플러그인과 같은 경계 */
const isMobile = useMediaQuery(UtilViewSettings.MOBILE_QUERY);
const scaleRows = computed(() => {
    const d = UtilViewSettings.DEFAULTS;
    const rows: { key: TextScaleKey; icon: string; label: string; hint: string; current: boolean; testid: string }[] = [
        { key: 'textScale', icon: 'pi pi-desktop', label: t('view.textScalePc'), hint: t('view.textScalePcHint', { n: d.textScale }), current: !isMobile.value, testid: 'setting-text-scale' },
        {
            key: 'textScaleMobile',
            icon: 'pi pi-mobile',
            label: t('view.textScaleMobile'),
            hint: t('view.textScaleMobileHint', { n: d.textScaleMobile }),
            current: isMobile.value,
            testid: 'setting-text-scale-mobile',
        },
    ];
    return rows.map((row) => {
        const value = settings.value[row.key];
        return { ...row, value, canSmaller: UtilViewSettings.canStepTextScale(value, -1), canLarger: UtilViewSettings.canStepTextScale(value, 1) };
    });
});
/** 미리보기 배율 — 지금 기기 쪽 값 */
const previewScale = computed(() => UtilViewSettings.textScaleFor(settings.value, isMobile.value));

/** 바꾼 칸을 저장한다 — 이미 그 값이면 저장도 토스트도 없다 */
async function change(partial: Partial<ViewSettings>) {
    if (UtilViewSettings.isSame(settings.value, UtilViewSettings.merge(settings.value, partial))) return;
    try {
        const where = await update(partial);
        // MOCK: 메뉴 위치는 서버에 칸이 없어 이 기기에만 남는다
        const deviceOnly = 'menuPosition' in partial;
        // REAL: const deviceOnly = false;
        toast.success(where === 'server' && !deviceOnly ? t('view.saved') : t('view.savedDevice'));
    } catch (e) {
        toast.error(t('view.saveFailed', { reason: UtilBoardMessages.of(t, e) }), 8000);
    }
}

/** 색상 테마 — 곧바로 바뀌고 이 기기에 남는다(서버 칸이 없다 — MOCK) */
const { palette, gradient, background, set: setPalette, setGradient, setBackground } = useColorPalette();
/** 지금 고른 색상 테마의 견본(배경색 「테마 기본」 견본 색) */
const currentSwatch = computed(() => UtilColorPalette.SWATCHES.find((s) => s.value === palette.value) ?? UtilColorPalette.SWATCHES[0]!);
function changeBackground(next: PageBackground) {
    if (background.value === next) return;
    const where = setBackground(next);
    toast.success(where === 'device' ? t('view.savedDevice') : t('view.savedMemory'));
}
function changePalette(next: ColorPalette) {
    if (palette.value === next) return;
    const where = setPalette(next);
    toast.success(where === 'device' ? t('view.savedDevice') : t('view.savedMemory'));
}
function changeGradient(on: boolean) {
    if (gradient.value === on) return;
    const where = setGradient(on);
    toast.success(where === 'device' ? t('view.savedDevice') : t('view.savedMemory'));
}

function stepText(key: TextScaleKey, by: number) {
    const current = settings.value[key];
    if (!UtilViewSettings.canStepTextScale(current, by)) return;
    const next = UtilViewSettings.stepTextScale(current, by);
    void change(key === 'textScale' ? { textScale: next } : { textScaleMobile: next });
}

/** 미리보기 두 장 — 화면 모드와 상관없이 칠하므로 `dark:` 변형을 쓰지 않고 정해 둔 값으로 칠한다 */
const modes = computed(() => [
    {
        theme: 'LIGHT' as ThemeSetting,
        label: t('view.themeLight'),
        card: 'border-surface-300 bg-surface-50 text-surface-900',
        line: 'border-surface-200',
        tagA: 'bg-primary-100 text-primary-700',
        tagB: 'bg-surface-200 text-surface-700',
        fill: 'bg-primary-600 text-white',
    },
    {
        theme: 'DARK' as ThemeSetting,
        label: t('view.themeDark'),
        card: 'border-surface-600 bg-surface-900 text-surface-50',
        line: 'border-surface-700',
        tagA: 'bg-primary-950 text-primary-300',
        tagB: 'bg-surface-800 text-surface-300',
        fill: 'bg-primary-400 text-surface-900',
    },
]);
</script>
