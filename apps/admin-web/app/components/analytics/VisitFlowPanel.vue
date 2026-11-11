<template>
    <!--
        이동 흐름 구획 — 기준 페이지(머리 아래 한 줄: 종류 · 제목 · 경로 · 들어온/나간 이동 합) → 「들어온 곳 TOP 5 → 기준 → 나간 곳 TOP 5」 흐름 그림(`FlowSankeyChart`, `@roboyam/chart`)
        → 「상위 이동」 표(기간 전체 A → B · 횟수 10). 세션 첫 페이지는 「바로 들어옴」(주황), 상위 5 밖은 「그 밖」(회색)으로 묶는다. 같은 주소 다시 보기는 서버가 저장하지 않아 없다.
        기준 바꾸기: 머리 오른쪽 찾기(경로·제목 — 화면이 페이지 API 로 찾아 `suggestions` 로 준다) · 그림의 페이지 칸 누르기 · 상위 이동 표의 경로 단추 → 모두 `select(path)`(「방문자 통계 › 이동 흐름」 쪽이 주소 `path` 에 적는다).
        이동 수집이 꺼져 있으면 「수집이 꺼져 있어요」.
    -->
    <AppGadget :title="t('visitAnalytics.flowsTitle')">
        <template #title-extra>
            <span class="hidden text-[12px] font-normal text-muted-color md:inline">{{ t('visitAnalytics.flowsHint') }}</span>
        </template>
        <template v-if="collect.flows" #actions>
            <label class="sr-only" :for="searchId">{{ t('visitAnalytics.searchLabel') }}</label>
            <AutoComplete
                v-model="query"
                :input-id="searchId"
                :suggestions="suggestions"
                option-label="path"
                :placeholder="t('visitAnalytics.searchPlaceholder')"
                :delay="300"
                :min-length="1"
                :loading="searching"
                class="w-56 sm:w-64"
                fluid
                @complete="(e) => emit('search', e.query)"
                @option-select="onPick"
            >
                <template #option="{ option }">
                    <span class="flex min-w-0 items-center gap-2 text-[13px]">
                        <span class="inline-flex shrink-0 items-center justify-center rounded bg-surface-100 px-1.5 py-1 text-[11px] leading-none dark:bg-surface-800">{{ kindName(option.kind) }}</span>
                        <span class="flex min-w-0 flex-col">
                            <span class="truncate">{{ UtilVisitPathMessages.label(t, localizedName, option) }}</span>
                            <span v-if="UtilVisitPathMessages.named(option)" class="truncate font-mono text-[11px] text-muted-color">{{ option.path }}</span>
                        </span>
                    </span>
                </template>
                <template #empty>
                    <span class="block px-3 py-2 text-[13px] text-muted-color">{{ t('visitAnalytics.searchEmpty') }}</span>
                </template>
            </AutoComplete>
        </template>

        <VisitCollectOff v-if="!collect.flows" item="flows" :enabled="collect.enabled" />
        <ErrorState v-else-if="error" :title="t('visitAnalytics.flowsLoadFailed')" :description="error.message" :retry="error.status !== 403" :retrying="loading" @retry="emit('retry')" />
        <div v-else-if="!data" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <Skeleton height="1.5rem" width="50%" />
            <Skeleton height="14rem" />
        </div>
        <div v-else class="flex min-w-0 flex-col gap-4 transition-opacity" :class="{ 'opacity-60': loading }" :aria-busy="loading">
            <!-- 기준 페이지 -->
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg bg-surface-50 px-3 py-2 dark:bg-surface-900">
                <span class="text-[12px] text-muted-color">{{ t('visitAnalytics.base') }}</span>
                <span class="inline-flex shrink-0 items-center justify-center rounded bg-surface-100 px-1.5 py-1 text-[11px] leading-none dark:bg-surface-800">{{ kindName(data.kind) }}</span>
                <span class="min-w-0 truncate text-[13px] font-medium" :class="UtilVisitPathMessages.named(data) ? '' : 'font-mono'">{{ baseLabel }}</span>
                <span v-if="UtilVisitPathMessages.named(data)" class="min-w-0 truncate font-mono text-[11px] text-muted-color">{{ data.path }}</span>
                <span class="ml-auto flex gap-3 text-[12px] text-muted-color tabular-nums">
                    <span>{{ t('visitAnalytics.incomingTotal', { n: num(data.incomingTotal) }) }}</span>
                    <span>{{ t('visitAnalytics.outgoingTotal', { n: num(data.outgoingTotal) }) }}</span>
                </span>
            </div>

            <EmptyState v-if="!incoming.length && !outgoing.length" icon="pi pi-sitemap" :title="t('visitAnalytics.flowsEmpty')" :description="t('visitAnalytics.flowsEmptyHint')" />
            <template v-else>
                <div class="grid grid-cols-[1fr_auto_1fr] items-center gap-2 text-[12px] text-muted-color" aria-hidden="true">
                    <span>{{ t('visitAnalytics.incoming') }}</span>
                    <i class="pi pi-arrow-right text-[10px]" />
                    <span class="text-right">{{ t('visitAnalytics.outgoing') }}</span>
                </div>
                <FlowSankeyChart :center="center" :incoming="incoming" :outgoing="outgoing" :labels="chartLabels" :format="chartFormat" @select="(key) => emit('select', key)" />
            </template>

            <!-- 상위 이동 -->
            <section class="flex flex-col gap-1.5" :aria-labelledby="topId">
                <div class="flex flex-wrap items-baseline gap-x-2">
                    <h3 :id="topId" class="m-0 text-sm font-medium">{{ t('visitAnalytics.topTitle') }}</h3>
                    <span class="text-[12px] text-muted-color">{{ t('visitAnalytics.topHint') }}</span>
                </div>
                <p v-if="!data.top.length" class="m-0 py-2 text-[13px] text-muted-color">{{ t('visitAnalytics.topEmpty') }}</p>
                <div v-else class="min-w-0 overflow-x-auto">
                    <table class="w-full border-collapse text-[13px]">
                        <thead>
                            <tr class="border-b border-(--line) text-left text-[11px] text-muted-color">
                                <th scope="col" class="w-8 py-1.5 text-center font-normal">{{ t('visitAnalytics.col.rank') }}</th>
                                <th scope="col" class="py-1.5 pr-2 font-normal">{{ t('visitAnalytics.col.from') }}</th>
                                <th scope="col" class="w-6 py-1.5 font-normal"><span class="sr-only">→</span></th>
                                <th scope="col" class="py-1.5 pr-2 font-normal">{{ t('visitAnalytics.col.to') }}</th>
                                <th scope="col" class="w-20 py-1.5 text-right font-normal">{{ t('visitAnalytics.col.moves') }}</th>
                            </tr>
                        </thead>
                        <tbody class="divide-y divide-(--line)">
                            <tr v-for="(m, i) in data.top" :key="`${m.from}>${m.to}`">
                                <td class="py-1.5 text-center text-[11px] font-semibold text-muted-color tabular-nums">{{ i + 1 }}</td>
                                <td class="max-w-0 py-1.5 pr-2">
                                    <button type="button" class="block max-w-full cursor-pointer truncate rounded text-left hover:text-primary focus-visible:outline-2 focus-visible:outline-primary" :class="[moveClass(m.from), { 'text-primary': m.from === data.path }]" :title="t('visitAnalytics.setBase', { page: m.from })" @click="emit('select', m.from)">{{ moveLabel(m.from) }}</button>
                                </td>
                                <td class="py-1.5 text-muted-color" aria-hidden="true"><i class="pi pi-arrow-right text-[10px]" /></td>
                                <td class="max-w-0 py-1.5 pr-2">
                                    <button type="button" class="block max-w-full cursor-pointer truncate rounded text-left hover:text-primary focus-visible:outline-2 focus-visible:outline-primary" :class="[moveClass(m.to), { 'text-primary': m.to === data.path }]" :title="t('visitAnalytics.setBase', { page: m.to })" @click="emit('select', m.to)">{{ moveLabel(m.to) }}</button>
                                </td>
                                <td class="py-1.5 text-right text-muted-color tabular-nums">{{ num(m.moves) }}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </section>
        </div>
    </AppGadget>
</template>

<script setup lang="ts">
import type { AutoCompleteOptionSelectEvent } from 'primevue/autocomplete';
import type { FlowSankeyCenter, FlowSankeyEnd, FlowSankeyFormat, FlowSankeyLabels } from '#layers/chart/components/FlowSankeyChart.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { VisitCollect, VisitFlowEnd, VisitFlows, VisitPageKind, VisitPageRow } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';
import UtilVisitPathMessages from '~/utils/util-visit-path-messages';

interface Props {
    /** 흐름 — null 이면 처음 읽는 중 */
    data?: VisitFlows | null;
    loading?: boolean;
    error?: ApiError | null;
    collect: VisitCollect;
    /** 기준 페이지 찾기 결과 */
    suggestions?: VisitPageRow[];
    searching?: boolean;
}

const props = withDefaults(defineProps<Props>(), { data: null, loading: false, error: null, suggestions: () => [], searching: false });

const emit = defineEmits<{
    (e: 'select', path: string): void;
    (e: 'search', query: string): void;
    (e: 'retry'): void;
}>();

const { t, te, locale } = useI18n();
const { localizedName } = useLocalizedName();
const searchId = useId();
const topId = useId();

/** 찾기 칸 — 고르면 비운다 */
const query = ref<VisitPageRow | string | null>(null);
function onPick(event: AutoCompleteOptionSelectEvent): void {
    const row = event.value as VisitPageRow;
    query.value = null;
    emit('select', row.path);
}

function num(n: number): string {
    return UtilVisitAnalytics.num(n, locale.value);
}

function kindName(kind: VisitPageKind | string): string {
    const key = `dashboard.pageKind.${kind}`;
    return te(key) ? t(key) : t('dashboard.pageKind.OTHER');
}

function endLabel(e: { path: string | null; title: string | null; titleEn: string | null }): string {
    if (e.path === null) return t('visitAnalytics.directEntry');
    return UtilVisitPathMessages.label(t, localizedName, { ...e, path: e.path });
}

/** 상위 이동 표의 칸 — 서버가 제목을 주지 않으므로 묶음 주소만 화면 이름, 나머지는 경로(고정폭) */
function moveLabel(path: string): string {
    return UtilVisitPathMessages.routeName(t, path) ?? path;
}
function moveClass(path: string): string {
    return UtilVisitPathMessages.routeName(t, path) ? 'text-[13px]' : 'font-mono text-[12px]';
}

const baseLabel = computed(() => (props.data ? endLabel(props.data) : ''));

const center = computed<FlowSankeyCenter>(() => ({ key: props.data?.path ?? '', label: baseLabel.value, detail: props.data?.path }));

/** 끝 칸들 — 「바로 들어옴」(null)은 바깥, 상위 5 밖은 「그 밖」 한 칸 */
function endsOf(list: VisitFlowEnd[], total: number, side: 'in' | 'out'): FlowSankeyEnd[] {
    const out: FlowSankeyEnd[] = list.map((e) =>
        e.path === null
            ? { key: '__entry', label: t('visitAnalytics.directEntry'), detail: t('visitAnalytics.directEntryHelp'), value: e.moves, tone: 'entry', selectable: false }
            : { key: e.path, label: endLabel(e), detail: e.path, value: e.moves, tone: 'page', selectable: e.path !== props.data?.path },
    );
    const rest = total - list.reduce((a, e) => a + e.moves, 0);
    if (rest > 0) out.push({ key: `__rest_${side}`, label: t('visitAnalytics.rest'), detail: t('visitAnalytics.restHelp'), value: rest, tone: 'rest', selectable: false });
    return out;
}

const incoming = computed(() => (props.data ? endsOf(props.data.incoming, props.data.incomingTotal, 'in') : []));
const outgoing = computed(() => (props.data ? endsOf(props.data.outgoing, props.data.outgoingTotal, 'out') : []));

const chartLabels = computed<FlowSankeyLabels>(() => ({
    summary: t('visitAnalytics.flowSummary', { page: baseLabel.value, in: incoming.value.length, out: outgoing.value.length }),
    tableCaption: t('visitAnalytics.flowTableCaption'),
    direction: t('visitAnalytics.flowDirection'),
    page: t('visitAnalytics.flowPage'),
    moves: t('visitAnalytics.flowMoves'),
    incoming: t('visitAnalytics.incoming'),
    outgoing: t('visitAnalytics.outgoing'),
    movesTip: t('visitAnalytics.flowMovesTip'),
    selectHint: t('visitAnalytics.flowSelectHint'),
}));

const chartFormat = computed<FlowSankeyFormat>(() => {
    const lang = locale.value;
    return { number: (n) => UtilVisitAnalytics.num(n, lang) };
});
</script>
