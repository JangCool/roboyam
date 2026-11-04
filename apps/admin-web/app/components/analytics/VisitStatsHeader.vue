<template>
    <!--
        「방문자 통계」 쪽마다 같은 머리 — 제목 + 한 줄 설명 · 오른쪽 「HH:mm 기준」(+ 덧말) · 새로 고침 → 기간 거르기(실시간 쪽은 `range=false` 로 없음) → 한 줄 요약(인사이트).
        기간은 `useVisitRange`(주소 `from`·`to` 가 정본, 고른 기간은 이 브라우저에 기억). 입력 칸 값(draft)이 둘 다 맞는 날짜가 되면 주소로 올리고, 주소가 바뀌면 칸을 따라 맞춘다.
        인사이트는 쪽이 받은 값으로 만든 1~2문장(`insights`) — 비면 줄을 두지 않는다.
    -->
    <div class="flex min-w-0 flex-col gap-3">
        <PageTitle :title="title" :subtitle="subtitle">
            <template #actions>
                <span v-if="asOf" class="text-[12px] whitespace-nowrap text-muted-color tabular-nums">{{ asOfText }}</span>
                <Button type="button" icon="pi pi-refresh" :label="t('visitAnalytics.refresh')" size="small" severity="secondary" variant="outlined" :loading="busy" @click="emit('refresh')" />
            </template>
        </PageTitle>

        <template v-if="range">
            <form class="flex flex-wrap items-center gap-x-3 gap-y-2" role="search" :aria-label="t('visitAnalytics.filters')" @submit.prevent>
                <DateRangeField v-model:from="draftFrom" v-model:to="draftTo" :from-label="t('visitAnalytics.from')" :to-label="t('visitAnalytics.to')">
                    <div class="flex flex-wrap items-center gap-1" role="group" :aria-label="t('visitAnalytics.presets')">
                        <Button
                            v-for="p in presets"
                            :key="p.kind"
                            type="button"
                            :label="p.label"
                            size="small"
                            :severity="p.active ? undefined : 'secondary'"
                            :variant="p.active ? undefined : 'outlined'"
                            :aria-pressed="p.active"
                            @click="vr.apply(UtilVisitAnalytics.presetRange(p.kind))"
                        />
                    </div>
                    <span class="text-[12px] text-muted-color">{{ t('visitAnalytics.maxDays', { n: UtilVisitAnalytics.MAX_DAYS }) }}</span>
                </DateRangeField>
            </form>
            <p v-if="vr.clamped.value" class="m-0 flex items-center gap-1.5 text-[12px] text-amber-700 dark:text-amber-400" role="status">
                <i class="pi pi-info-circle text-[11px]" aria-hidden="true" />{{ t('visitAnalytics.clamped', { n: UtilVisitAnalytics.MAX_DAYS }) }}
            </p>
        </template>

        <div v-if="insights.length" class="flex items-start gap-3 rounded-xl border border-(--line) bg-(--p-content-background) px-4 py-3" role="status" :aria-label="t('visitAnalytics.insightLabel')">
            <span class="grid size-8 shrink-0 place-items-center rounded-full bg-primary-50 text-primary dark:bg-primary-950/40" aria-hidden="true"><i class="pi pi-lightbulb text-sm" /></span>
            <div class="flex min-w-0 flex-col gap-0.5 pt-1">
                <p v-for="(line, i) in insights" :key="i" class="m-0 text-[14px] leading-relaxed" :class="i === 0 ? 'font-medium' : 'text-muted-color'">{{ line }}</p>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilVisitAnalytics from '~/utils/util-visit-analytics';

interface Props {
    title: string;
    subtitle?: string;
    /** 센 시각(ISO) — 없으면 숨김 */
    asOf?: string | null;
    /** 「HH:mm 기준」 뒤 덧말 — 기본 「최대 1분 늦음」 */
    asOfNote?: string;
    busy?: boolean;
    /** 기간 거르기를 두나 */
    range?: boolean;
    /** 한 줄 요약 문장 */
    insights?: string[];
}

const props = withDefaults(defineProps<Props>(), { subtitle: '', asOf: null, asOfNote: '', busy: false, range: true, insights: () => [] });

const emit = defineEmits<{ (e: 'refresh'): void }>();

const { t } = useI18n();
const time = useDisplayTime();
const route = useRoute();
const vr = useVisitRange();
const SELF = route.path;

const asOfText = computed(() => (props.asOfNote ? `${time.value.clock(props.asOf)} · ${props.asOfNote}` : t('visitAnalytics.asOf', { at: time.value.clock(props.asOf) })));

const presets = computed(() => {
    const preset = UtilVisitAnalytics.presetOf(vr.range.value);
    return UtilVisitAnalytics.PRESETS.map((kind) => ({ kind, label: t(`visitAnalytics.preset.${kind}`), active: preset === kind }));
});

const draftFrom = ref(vr.range.value.from);
const draftTo = ref(vr.range.value.to);
watch([draftFrom, draftTo], ([from, to]) => {
    if (!from || !to) return;
    if (from === vr.range.value.from && to === vr.range.value.to) return;
    vr.apply({ from, to });
    // 고친 기간이 지금 주소와 같으면(잘린 값 등) 주소가 바뀌지 않으니 칸을 되돌린다
    void nextTick(() => {
        if (draftFrom.value !== vr.range.value.from) draftFrom.value = vr.range.value.from;
        if (draftTo.value !== vr.range.value.to) draftTo.value = vr.range.value.to;
    });
});
watch(
    () => [vr.range.value.from, vr.range.value.to] as const,
    ([from, to]) => {
        if (route.path !== SELF) return;
        if (draftFrom.value !== from) draftFrom.value = from;
        if (draftTo.value !== to) draftTo.value = to;
    },
);
</script>
