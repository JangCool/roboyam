<template>
    <div class="flex flex-col gap-4">
        <h1 class="text-lg font-bold">{{ t('settings.view') }}</h1>

        <div v-if="phase === 'loading' || phase === 'idle'" class="space-y-3" role="status" :aria-label="t('view.loading')" data-testid="view-loading">
            <div v-for="n in 4" :key="n" class="h-14 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="phase === 'error'" :title="t('view.loadFailed')" :description="errorMessage" retry data-testid="view-error" @retry="load" />

        <template v-else>
            <!-- 화면 — 야간 모드 · 글자 크기. 바꾸는 즉시 저장한다 -->
            <section :aria-labelledby="`${uid}-screen`">
                <h2 :id="`${uid}-screen`" class="px-1 pb-1.5 text-xs font-semibold text-muted-color">{{ t('view.sectionScreen') }}</h2>
                <div class="rounded-xl border border-surface bg-surface-0 dark:bg-surface-900">
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

                    <!-- 글자 크기 — − / + 로 한 단씩. 끝에서는 `aria-disabled` 로(진짜 disabled 는 누른 채 비활성이 되면 초점이 사라진다) -->
                    <div class="flex flex-col gap-2 px-4 py-3 md:flex-row md:items-center md:justify-between md:gap-4">
                        <div class="min-w-0 flex-1">
                            <p :id="`${uid}-scale-label`" class="m-0 text-sm font-medium">{{ t('view.textScale') }}</p>
                            <p :id="`${uid}-scale-hint`" class="m-0 mt-0.5 text-xs leading-snug text-muted-color">{{ t('view.textScaleHint') }}</p>
                        </div>
                        <div class="flex w-full items-center justify-between gap-1 md:w-64 md:shrink-0" role="group" :aria-labelledby="`${uid}-scale-label`" :aria-describedby="`${uid}-scale-hint`" data-testid="setting-text-scale">
                            <Button
                                type="button"
                                icon="pi pi-minus"
                                text
                                rounded
                                severity="secondary"
                                :aria-label="t('view.textSmaller')"
                                :aria-disabled="canSmaller ? undefined : 'true'"
                                :class="canSmaller ? '' : 'opacity-40'"
                                data-testid="setting-text-smaller"
                                @click="stepText(-1)"
                            />
                            <span class="min-w-14 text-center text-sm font-medium tabular-nums" aria-live="polite" data-testid="setting-text-scale-value">{{ t('view.textScaleValue', { n: settings.textScale }) }}</span>
                            <Button
                                type="button"
                                icon="pi pi-plus"
                                text
                                rounded
                                severity="secondary"
                                :aria-label="t('view.textLarger')"
                                :aria-disabled="canLarger ? undefined : 'true'"
                                :class="canLarger ? '' : 'opacity-40'"
                                data-testid="setting-text-larger"
                                @click="stepText(1)"
                            />
                        </div>
                    </div>
                </div>
            </section>

            <!-- 댓글 — 처음 보이는 정렬 -->
            <section :aria-labelledby="`${uid}-comments`">
                <h2 :id="`${uid}-comments`" class="px-1 pb-1.5 text-xs font-semibold text-muted-color">{{ t('view.sectionComments') }}</h2>
                <div class="flex flex-col gap-2 rounded-xl border border-surface bg-surface-0 px-4 py-3 md:flex-row md:items-center md:justify-between md:gap-4 dark:bg-surface-900">
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
                class="flex min-h-14 items-center gap-3 rounded-xl border border-surface bg-surface-0 px-4 py-2.5 hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:bg-surface-900 dark:hover:bg-surface-800"
                data-testid="view-display-link"
            >
                <i class="pi pi-clock text-muted-color" aria-hidden="true" />
                <span class="min-w-0 flex-1">
                    <span class="block text-sm font-medium">{{ t('settings.display') }}</span>
                    <small class="block text-xs text-muted-color">{{ t('settings.displayHint') }}</small>
                </span>
                <i class="pi pi-angle-right text-muted-color" aria-hidden="true" />
            </NuxtLink>

            <!-- 미리보기 — 라이트·다크를 나란히(지금 화면의 모드와 상관없이). 고른 모드에 「선택됨」, 글자 크기는 이 안의 배율 -->
            <section :aria-labelledby="`${uid}-preview`">
                <h2 :id="`${uid}-preview`" class="px-1 pb-2 text-xs font-semibold text-muted-color">{{ t('view.previewTitle') }}</h2>
                <div class="grid grid-cols-2 gap-2" aria-hidden="true" :style="{ '--text-scale': String(settings.textScale / 100) }" data-testid="view-preview">
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
import UtilViewSettings from '~/utils/util-view-settings';

/**
 * 보기 설정 `/settings/view` — 로그인 필요(community `pages/settings/view.vue` 중 로보얌에 적용할 곳이 있는 것).
 * 「화면」: 야간 모드(자동·라이트·다크) · 글자 크기(90·100·110·125%), 「댓글」: 처음 정렬, 「시간 표시」는 따로 있는 화면으로 잇는다, 맨 아래 라이트·다크 미리보기.
 * **바꾸는 즉시 저장한다** — 화면에 먼저 반영하고(`useViewSettings().update`) 실패하면 바꾼 칸을 되돌리고 서버 문구를 토스트(8초). 성공은 토스트(3초).
 * 야간 모드·글자 크기를 `<html>` 에 거는 것은 `plugins/view-theme.client.ts` 다 — 여기서는 값만 바꾼다.
 * community 대비 뺀 것(themes.md — 사용자 개인 테마·보기 설정 금지, 사이트 테마 몫): 주 색상·배경 색상·본문 너비·목록 밀도·메뉴 위치·화면 꾸미기 되돌리기.
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
const orderOptions = computed(() => [
    { value: 'OLDEST', label: t('view.orderOldest') },
    { value: 'LATEST', label: t('view.orderLatest') },
]);

const canSmaller = computed(() => UtilViewSettings.canStepTextScale(settings.value.textScale, -1));
const canLarger = computed(() => UtilViewSettings.canStepTextScale(settings.value.textScale, 1));

/** 바꾼 칸을 저장한다 — 이미 그 값이면 저장도 토스트도 없다 */
async function change(partial: Partial<ViewSettings>) {
    if (UtilViewSettings.isSame(settings.value, UtilViewSettings.merge(settings.value, partial))) return;
    try {
        const where = await update(partial);
        toast.success(where === 'server' ? t('view.saved') : t('view.savedDevice'));
    } catch (e) {
        toast.error(t('view.saveFailed', { reason: UtilBoardMessages.of(t, e) }), 8000);
    }
}

function stepText(by: number) {
    if (!UtilViewSettings.canStepTextScale(settings.value.textScale, by)) return;
    void change({ textScale: UtilViewSettings.stepTextScale(settings.value.textScale, by) });
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
