<template>
    <!--
        페이지 순위 표 — 순위 · 화면 종류 꼬리표 · 페이지(제목 — 없으면 묶음 주소의 화면 이름 `UtilVisitPathMessages`, 그것도 없으면 경로 · 이름이 있으면 아래 작게 경로) · 조회수 · 비율 막대(거른 범위 조회수 합 대비). 20줄씩 쪽 넘김은 서버(`lazy`).
        머리 오른쪽 경로·제목 찾기(`searchable` — 0.3초 멈추면 `search`) · 화면 종류 거르기(전체 + 10종). 줄(또는 제목 단추 — 키보드)을 누르면 `select(path)` — 화면이 서랍을 연다(그 줄은 강조).
        상태는 표 안: 오류 ErrorState(다시 시도) · 읽는 중 빈 칸 · 빈 결과 EmptyState.
    -->
    <AppGadget :title="t('visitAnalytics.pagesTitle')">
        <template #title-extra>
            <span class="hidden text-[12px] font-normal text-muted-color sm:inline">{{ t('visitAnalytics.pagesHint') }}</span>
        </template>
        <template #actions>
            <template v-if="searchable">
                <label class="sr-only" :for="qId">{{ t('visitAnalytics.pageSearchLabel') }}</label>
                <IconField class="w-40 sm:w-52">
                    <InputIcon class="pi pi-search" />
                    <InputText :id="qId" :model-value="qDraft" type="search" :placeholder="t('visitAnalytics.searchPlaceholder')" fluid @update:model-value="onQ" />
                </IconField>
            </template>
            <label class="sr-only" :for="kindId">{{ t('visitAnalytics.kindLabel') }}</label>
            <Select :model-value="kind ?? ''" :input-id="kindId" :options="kindOptions" option-label="label" option-value="value" class="w-36" @update:model-value="onKind" />
        </template>

        <div class="-mx-1 min-w-0">
            <DataTable
                :value="rows"
                data-key="path"
                lazy
                paginator
                :first="first"
                :rows="size"
                :total-records="total"
                :loading="loading"
                scrollable
                :row-class="rowClass"
                :current-page-report-template="totalTemplate"
                paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport"
                @page="(e) => emit('page', e.first)"
                @row-click="(e) => emit('select', (e.data as VisitPageRow).path)"
            >
                <template #empty>
                    <ErrorState v-if="error" :title="t('visitAnalytics.pagesLoadFailed')" :description="error.message" :retry="error.status !== 403" :retrying="loading" @retry="emit('retry')" />
                    <div v-else-if="loading" class="h-32" aria-hidden="true" />
                    <EmptyState v-else icon="pi pi-desktop" :title="q ? t('visitAnalytics.searchEmpty') : kind ? t('visitAnalytics.pagesKindEmpty') : t('visitAnalytics.pagesEmpty')" />
                </template>

                <Column :header="t('visitAnalytics.col.rank')" style="width: 3.5rem">
                    <template #body="{ index }">
                        <span class="block text-center text-[13px] font-semibold tabular-nums" :class="first + index < 3 ? 'text-primary' : 'text-muted-color'">{{ first + index + 1 }}</span>
                    </template>
                </Column>
                <Column :header="t('visitAnalytics.col.kind')" style="width: 6rem">
                    <template #body="{ data }">
                        <span class="inline-flex shrink-0 items-center justify-center rounded bg-surface-100 px-1.5 py-1 text-[11px] leading-none whitespace-nowrap dark:bg-surface-800">{{ kindName(data.kind) }}</span>
                    </template>
                </Column>
                <Column :header="t('visitAnalytics.col.page')" style="min-width: 14rem">
                    <template #body="{ data }">
                        <span class="flex min-w-0 flex-col gap-0.5">
                            <button
                                type="button"
                                class="min-w-0 cursor-pointer truncate rounded text-left text-[13px] hover:text-primary focus-visible:outline-2 focus-visible:outline-primary"
                                :class="UtilVisitPathMessages.named(data) ? '' : 'font-mono text-[12px]'"
                                :title="data.path"
                                :aria-pressed="data.path === selectedPath"
                                @click.stop="emit('select', data.path)"
                            >
                                {{ UtilVisitPathMessages.label(t, localizedName, data) }}
                            </button>
                            <span v-if="UtilVisitPathMessages.named(data)" class="truncate font-mono text-[11px] text-muted-color">{{ data.path }}</span>
                        </span>
                    </template>
                </Column>
                <Column :header="t('visitAnalytics.col.views')" style="width: 6rem" :pt="right">
                    <template #body="{ data }"><div class="text-right text-[13px] tabular-nums">{{ UtilVisitAnalytics.num(data.views, locale) }}</div></template>
                </Column>
                <Column :header="t('visitAnalytics.col.share')" style="width: 9rem">
                    <template #body="{ data }">
                        <span class="flex items-center gap-2" :title="t('visitAnalytics.shareOf', { rate: shareText(data.views) })">
                            <span class="h-1.5 min-w-0 flex-1 overflow-hidden rounded-full bg-surface-100 dark:bg-surface-800" aria-hidden="true">
                                <span class="block h-full rounded-full bg-primary" :style="{ width: `${UtilVisitAnalytics.share(data.views, totalViews)}%` }" />
                            </span>
                            <span class="w-11 shrink-0 text-right text-[11px] text-muted-color tabular-nums">{{ shareText(data.views) }}</span>
                        </span>
                    </template>
                </Column>
            </DataTable>
        </div>
    </AppGadget>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import { VISIT_PAGE_KINDS, type VisitPageKind, type VisitPageRow } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';
import UtilVisitPathMessages from '~/utils/util-visit-path-messages';

interface Props {
    rows?: VisitPageRow[];
    /** 거른 페이지 수 */
    total?: number;
    /** 거른 범위 조회수 합(비율의 분모) */
    totalViews?: number;
    /** 이 쪽 첫 줄의 순번(0부터) */
    first?: number;
    size?: number;
    loading?: boolean;
    error?: ApiError | null;
    /** 화면 종류 거르기 — null 이면 전체 */
    kind?: VisitPageKind | null;
    /** 고른 페이지(강조 — 서랍·이동 흐름의 기준) */
    selectedPath?: string | null;
    /** 경로·제목 찾기 칸을 두나 */
    searchable?: boolean;
    /** 찾는 글자 */
    q?: string;
}

const props = withDefaults(defineProps<Props>(), {
    rows: () => [],
    total: 0,
    totalViews: 0,
    first: 0,
    size: 20,
    loading: false,
    error: null,
    kind: null,
    selectedPath: null,
    searchable: false,
    q: '',
});

const emit = defineEmits<{
    (e: 'page', first: number): void;
    (e: 'kind', kind: VisitPageKind | null): void;
    (e: 'select', path: string): void;
    (e: 'search', q: string): void;
    (e: 'retry'): void;
}>();

const qId = useId();
/** 찾기 칸 — 치는 동안은 칸만 바꾸고 0.3초 멈추면 `search` */
const qDraft = ref(props.q);
let qTimer: ReturnType<typeof setTimeout> | null = null;
function onQ(value: string | undefined): void {
    qDraft.value = value ?? '';
    if (qTimer) clearTimeout(qTimer);
    qTimer = setTimeout(() => emit('search', qDraft.value.trim()), 300);
}
onBeforeUnmount(() => {
    if (qTimer) clearTimeout(qTimer);
});

const { t, te, locale } = useI18n();
const { localizedName } = useLocalizedName();
const kindId = useId();
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));
const right = { columnHeaderContent: { class: 'justify-end' } };

const kindOptions = computed(() => [{ value: '', label: t('visitAnalytics.kindAll') }, ...VISIT_PAGE_KINDS.map((value) => ({ value, label: kindName(value) }))]);

function onKind(value: string): void {
    emit('kind', (value || null) as VisitPageKind | null);
}

/** 화면 종류 꼬리표 — 대시보드 「인기 페이지」와 같은 이름, 모르는 종류는 「기타」 */
function kindName(kind: VisitPageKind | string): string {
    const key = `dashboard.pageKind.${kind}`;
    return te(key) ? t(key) : t('dashboard.pageKind.OTHER');
}

function shareText(views: number): string {
    return UtilVisitAnalytics.percent(UtilVisitAnalytics.share(views, props.totalViews), locale.value);
}

function rowClass(row: VisitPageRow): string {
    return row.path === props.selectedPath ? 'cursor-pointer bg-primary-50! dark:bg-primary-950/40!' : 'cursor-pointer';
}
</script>
