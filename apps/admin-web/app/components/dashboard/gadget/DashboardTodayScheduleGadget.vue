<template>
    <!--
        오늘·내일 일정 — 광고 시작·종료 · 공구 마감 · 포인트 상점 판매 시작·종료 · 예약 배치를 시각 차례 타임라인으로(최대 10).
        항목마다 그 종류의 조회 권한이 있을 때만 보인다(서버가 거르고 화면도 한 번 더 — 감추는 것은 편의). 누르면 그 종류의 화면.
    -->
    <DashboardGadgetFrame
        :title="title"
        :at="state.at.value"
        :loading="state.loading.value"
        :error="state.error.value"
        :ready="!!v"
        :forbidden="state.forbidden.value"
        :unavailable="state.unavailable.value"
        :empty="!days.length"
        empty-icon="pi pi-calendar"
        :empty-title="t('dashboard.scheduleNone')"
        @refresh="state.reload"
    >
        <div class="flex flex-col gap-3">
            <section v-for="day in days" :key="day.key" class="flex flex-col gap-1" :aria-labelledby="`${baseId}-${day.key}`">
                <h3 :id="`${baseId}-${day.key}`" class="m-0 text-[12px] font-semibold text-muted-color">{{ day.label }}</h3>
                <!-- 왼쪽 4px 들임 — 선 위의 점(8px)이 스크롤 영역 왼쪽 끝에서 반쯤 잘리지 않게 -->
                <ol class="m-0 ml-1 flex list-none flex-col border-l-2 border-(--line) p-0 pl-3">
                    <li v-for="it in day.items" :key="it.id" class="relative">
                        <span class="absolute top-3 -left-[17px] size-2 rounded-full" :class="KIND_STYLE[it.kind].dot" aria-hidden="true" />
                        <NuxtLink :to="KIND_STYLE[it.kind].to" class="flex items-center gap-2 rounded-md px-1.5 py-1.5 text-[13px] hover:bg-surface-50 dark:hover:bg-surface-900">
                            <span class="w-11 shrink-0 text-[12px] text-muted-color tabular-nums">{{ time.clock(it.at) }}</span>
                            <span class="inline-flex shrink-0 items-center justify-center rounded bg-surface-100 px-1.5 py-1 text-[11px] leading-none dark:bg-surface-800">{{ t(`dashboard.scheduleKind.${it.kind}`) }}</span>
                            <span class="min-w-0 flex-1 truncate" :title="localizedName(it.title, it.titleEn)">{{ localizedName(it.title, it.titleEn) }}</span>
                        </NuxtLink>
                    </li>
                </ol>
            </section>
        </div>
    </DashboardGadgetFrame>
</template>

<script setup lang="ts">
import type { DashboardScheduleKind, DashboardTodaySchedule } from '~/composables/dashboard/api/useDashboardGadgetApi.types';
import type { DashboardGadgetItem } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import UtilBizApplication from '~/utils/util-biz-application';

interface Props {
    gadget: DashboardGadgetItem;
    title: string;
}

const props = withDefaults(defineProps<Props>(), {});

/** 종류별 필요 권한 · 이동 화면 · 점 색 */
const KIND_STYLE: Record<DashboardScheduleKind, { permission: string; to: string; dot: string }> = {
    AD_START: { permission: 'ad.read', to: '/ads', dot: 'bg-primary' },
    AD_END: { permission: 'ad.read', to: '/ads', dot: 'bg-surface-400' },
    GROUPBUY_CLOSE: { permission: 'commerce.product.read', to: '/group-buys?status=RECRUITING', dot: 'bg-amber-500' },
    POINTSHOP_START: { permission: 'pointshop.read', to: '/point-shop/items', dot: 'bg-green-500' },
    POINTSHOP_END: { permission: 'pointshop.read', to: '/point-shop/items', dot: 'bg-surface-400' },
    BATCH: { permission: 'batch.read', to: '/settings/batches', dot: 'bg-violet-500' },
};

const { t } = useI18n();
const time = useDisplayTime();
const { localizedName } = useLocalizedName();
const { can } = useAdminPermission();
const baseId = useId();
const state = useDashboardKindData('TODAY_SCHEDULE', () => props.gadget);
const v = computed(() => state.data.value);

type Item = DashboardTodaySchedule['items'][number];

/** 오늘·내일로 나눈다(한국 시간 날짜) — 권한 없는 종류는 뺀다 */
const days = computed(() => {
    const items = (v.value?.items ?? []).filter((it) => KIND_STYLE[it.kind] && can(KIND_STYLE[it.kind].permission));
    const today = UtilBizApplication.kstToday();
    const tomorrow = UtilBizApplication.kstToday(Date.now() + 86_400_000);
    const out: { key: string; label: string; items: Item[] }[] = [
        { key: 'today', label: t('dashboard.scheduleToday'), items: items.filter((it) => UtilBizApplication.kstDate(it.at) === today) },
        { key: 'tomorrow', label: t('dashboard.scheduleTomorrow'), items: items.filter((it) => UtilBizApplication.kstDate(it.at) === tomorrow) },
    ];
    return out.filter((d) => d.items.length > 0);
});
</script>
