<template>
    <!--
        대시보드 가젯 한 장의 겉 — `AppGadget fill`(격자 칸 높이를 채우고 내용만 스크롤)에 머리 오른쪽 「HH:mm 기준」·바로 가기·새로 고침 단추를 단다.
        상태: 값이 오기 전 Skeleton(`#skeleton` 으로 바꿀 수 있다) · 값 없이 실패하면 ErrorState(다시 시도) · 권한 없음(403·묶음의 null) ·
        수집 꺼짐(권한은 있으나 「통계 수집」 설정에서 그 수집이 꺼진 종류 — 묶음 응답의 `disabled`. `site.manage` 가 있으면 설정의 그 항목으로 가는 링크) ·
        준비 중(권한은 있으나 서버가 아직 데이터를 주지 않는 종류 — 묶음 응답의 `unavailable`) · 비었으면 EmptyState · 아니면 내용.
        값이 있는데 새로 고침만 실패하면 내용은 두고 머리에 경고 아이콘(문구는 title·aria-label).
        편집 모드의 손잡이(옮기기·크기·⚙·✕)는 격자(`DashboardGrid`)가 위에 덮는다 — 여기는 모른다.
        내용 스크롤 영역은 카드 안쪽 여백으로 4px 넓힌다 — 내용이 영역에 꼭 맞을 때 맨 아래 칸의 테두리가 소수점 px 만큼 잘리지 않게(사용자 지적 2026-11-03).
    -->
    <AppGadget :title="title" fill class="dash-gadget h-full">
        <template #actions>
            <i v-if="error && ready" class="pi pi-exclamation-circle text-xs text-amber-600 dark:text-amber-400" role="img" :title="t('dashboard.refreshFailed', { reason: error.message })" :aria-label="t('dashboard.refreshFailed', { reason: error.message })" />
            <span v-if="at && refreshable" class="text-[11px] whitespace-nowrap text-muted-color">{{ t('dashboard.generatedAt', { at: time.clock(at) }) }}</span>
            <NuxtLink
                v-if="more"
                :to="more"
                class="-my-1 inline-grid size-8 place-items-center rounded-md text-muted-color hover:bg-surface-100 hover:text-primary focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                :title="t('dashboard.moreOf', { title })"
                :aria-label="t('dashboard.moreOf', { title })"
            >
                <i class="pi pi-arrow-up-right text-xs" aria-hidden="true" />
            </NuxtLink>
            <Button v-if="refreshable" type="button" icon="pi pi-refresh" size="small" severity="secondary" variant="text" class="-my-1" :aria-label="t('dashboard.refreshOf', { title })" :loading="loading" @click="emit('refresh')" />
        </template>

        <EmptyState v-if="forbidden" icon="pi pi-lock" :title="t('dashboard.forbiddenTitle')" :description="t('dashboard.forbiddenBody')" />
        <EmptyState v-else-if="disabled" icon="pi pi-power-off" :title="t('dashboard.disabledTitle')" :description="disabledBody">
            <NuxtLink v-if="canManage && collect" :to="UtilAnalytics.link(collect)" class="inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
                <i class="pi pi-cog text-xs" aria-hidden="true" />{{ t('dashboard.disabledLink') }}
            </NuxtLink>
        </EmptyState>
        <EmptyState v-else-if="unavailable" icon="pi pi-hourglass" :title="t('dashboard.unavailableTitle')" :description="t('dashboard.unavailableBody')" />
        <ErrorState v-else-if="error && !ready" :title="t('dashboard.gadgetLoadFailed')" :description="error.message" retry :retrying="loading" @retry="emit('refresh')" />
        <template v-else-if="!ready">
            <slot name="skeleton">
                <div class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                    <Skeleton height="1.25rem" width="60%" />
                    <Skeleton height="1.25rem" />
                    <Skeleton height="1.25rem" width="80%" />
                </div>
            </slot>
        </template>
        <EmptyState v-else-if="empty" :icon="emptyIcon" :title="emptyTitle || t('dashboard.gadgetEmpty')" :description="emptyBody" />
        <slot v-else />
    </AppGadget>
</template>

<script setup lang="ts">
import type { AnalyticsSwitch } from '~/composables/analytics/api/useAnalyticsSettingsApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilAnalytics from '~/utils/util-analytics';

interface Props {
    title: string;
    /** 센 시각(ISO) — 「HH:mm 기준」 */
    at?: string | null;
    loading?: boolean;
    error?: ApiError | null;
    /** 값을 받았나 */
    ready?: boolean;
    /** 받은 값이 비었나 */
    empty?: boolean;
    emptyTitle?: string;
    emptyBody?: string;
    emptyIcon?: string;
    /** 볼 권한이 없다(403) */
    forbidden?: boolean;
    /** 아직 준비 중인 가젯이다(서버가 이 종류의 데이터를 아직 주지 않는다) */
    unavailable?: boolean;
    /** 「통계 수집」 설정에서 이 가젯의 수집이 꺼져 있다(묶음 응답의 `disabled`) */
    disabled?: boolean;
    /** 이 가젯의 데이터를 만드는 「통계 수집」 항목 — 「수집 꺼짐」의 설명·설정 링크(`/settings/analytics#…`) */
    collect?: AnalyticsSwitch;
    /** 서버를 부르는 가젯(새로 고침·기준 시각) */
    refreshable?: boolean;
    /** 머리의 「바로 가기」 화면 주소 */
    more?: string;
}

const props = withDefaults(defineProps<Props>(), {
    at: null,
    loading: false,
    error: null,
    ready: false,
    empty: false,
    emptyTitle: '',
    emptyBody: '',
    emptyIcon: 'pi pi-inbox',
    forbidden: false,
    unavailable: false,
    disabled: false,
    collect: undefined,
    refreshable: true,
    more: '',
});

const emit = defineEmits<{ (e: 'refresh'): void }>();

const { t } = useI18n();
const time = useDisplayTime();
const { can } = useAdminPermission();

/** 수집 설정을 바꿀 수 있나(`site.manage`) — 없으면 링크 없이 요청하라는 설명. 막는 것은 서버다 */
const canManage = computed(() => can('site.manage'));
const disabledBody = computed(() => {
    const item = props.collect ? t(`analytics.label.${props.collect}`) : '';
    if (!canManage.value) return t('dashboard.disabledBodyAsk', { item });
    return t('dashboard.disabledBody', { item });
});
</script>

<style scoped>
.dash-gadget :deep(.p-card-content) {
    margin: 0 -4px -4px;
    padding: 0 4px 4px;
}
</style>
