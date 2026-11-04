<template>
    <!--
        방문자 통계 › 정보주체 요청(`/analytics/visits/requests`, 2단계 2026-11-04) — 이용자가 공개 사이트 「개인정보 처리방침」 아래 「이 브라우저의 방문 기록 지우기」로 보낸 삭제 요청.
        숫자 카드 3(대기 중 · 지난 30일 · 전체) → 요청 표(요청 번호(서버 Snowflake 숫자 글자 — 19자리 안팎이라 줄바꿈 없이) · 접수 시각 · 상태 대기/완료 · 지운 줄 수 · 처리 시각, 최신 차례, 20줄씩) → 처리 방식 안내.
        요청은 방문자 ID 해시로만 처리하고 회원 계정과 잇지 않는다(표에 사람을 가리키는 칸이 없다). 처리는 DB 작업(5분마다)이 몇 분 안에 — **운영자가 할 일은 없고 확인용**이다.
        스위치 없음(늘 동작). 기간 없음. 권한: `site.read`(메뉴 `visitRequests`).
        서버 호출은 `useVisitAnalyticsApi().requests`(`GET /api/admin/analytics/visits/requests` — 목록 캐시 60초).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <VisitStatsHeader :title="t('menu.visitRequests')" :subtitle="t('visitAnalytics.requests.subtitle')" :busy="list.loading.value" :range="false" @refresh="load" />

        <EmptyState v-if="!allowed" icon="pi pi-lock" :title="t('dashboard.forbiddenTitle')" :description="t('visitAnalytics.forbiddenBody')" />

        <template v-else>
            <VisitStatCards :items="cards" :skeletons="3" />

            <AppGadget :title="t('visitAnalytics.requests.listTitle')">
                <div class="-mx-1 min-w-0">
                    <DataTable
                        :value="rows"
                        data-key="id"
                        lazy
                        paginator
                        :first="first"
                        :rows="PAGE_SIZE"
                        :total-records="p?.total ?? 0"
                        :loading="list.loading.value"
                        scrollable
                        :current-page-report-template="totalTemplate"
                        paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport"
                        @page="(e) => onPage(e.first)"
                    >
                        <template #empty>
                            <ErrorState v-if="list.error.value" :code="list.error.value.status === 403 ? '403' : '!'" :title="t('visitAnalytics.requests.loadFailed')" :description="list.error.value.message" :retry="list.error.value.status !== 403" :retrying="list.loading.value" @retry="load" />
                            <div v-else-if="list.loading.value || !p" class="h-32" aria-hidden="true" />
                            <EmptyState v-else icon="pi pi-eraser" :title="t('visitAnalytics.requests.empty')" :description="t('visitAnalytics.requests.emptyHint')" />
                        </template>

                        <Column :header="t('visitAnalytics.requests.colId')" style="min-width: 11rem">
                            <template #body="{ data }"><span class="font-mono text-[12px] whitespace-nowrap tabular-nums">{{ data.id }}</span></template>
                        </Column>
                        <Column :header="t('visitAnalytics.requests.colReceived')" style="min-width: 10rem">
                            <template #body="{ data }"><span class="text-[13px] whitespace-nowrap tabular-nums">{{ time.full(data.receivedAt) }}</span></template>
                        </Column>
                        <Column :header="t('visitAnalytics.requests.colStatus')" style="width: 7rem">
                            <template #body="{ data }">
                                <Tag :value="t(`visitAnalytics.requests.status.${data.status}`)" :icon="data.status === 'DONE' ? 'pi pi-check' : 'pi pi-clock'" :severity="data.status === 'DONE' ? 'success' : 'secondary'" />
                            </template>
                        </Column>
                        <Column :header="t('visitAnalytics.requests.colRows')" style="width: 7rem" :pt="right">
                            <template #body="{ data }">
                                <div class="text-right text-[13px] tabular-nums" :class="data.deletedRows === null ? 'text-muted-color' : ''">{{ data.deletedRows === null ? '—' : UtilVisitAnalytics.num(data.deletedRows, locale) }}</div>
                            </template>
                        </Column>
                        <Column :header="t('visitAnalytics.requests.colProcessed')" style="min-width: 10rem">
                            <template #body="{ data }">
                                <span v-if="data.processedAt" class="text-[13px] whitespace-nowrap tabular-nums">{{ time.full(data.processedAt) }}</span>
                                <span v-else class="text-[12px] text-muted-color">{{ t('visitAnalytics.requests.waiting') }}</span>
                            </template>
                        </Column>
                    </DataTable>
                </div>
            </AppGadget>

            <AppGadget :title="t('visitAnalytics.requests.aboutTitle')">
                <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-[13px] leading-relaxed">
                    <li v-for="key in ABOUT" :key="key" class="flex items-start gap-2">
                        <i class="pi pi-info-circle mt-1 text-[11px] text-muted-color" aria-hidden="true" /><span>{{ t(`visitAnalytics.requests.${key}`) }}</span>
                    </li>
                </ul>
            </AppGadget>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { VisitStatCard } from '~/components/analytics/VisitStatCards.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';

/** 한 쪽 줄 수 */
const PAGE_SIZE = 20;
/** 처리 방식 안내 줄 */
const ABOUT = ['aboutHash', 'aboutNoMember', 'aboutProcess', 'aboutScope', 'aboutNoAction'] as const;

const { t, locale } = useI18n();
useSeoMeta({ title: () => `${t('menu.visitRequests')} · ${t('menu.visitStats')}` });

const api = useVisitAnalyticsApi();
const displayTime = useDisplayTime();
const time = computed(() => displayTime.value);
const { can } = useAdminPermission();
const allowed = computed(() => can('site.read'));

const list = useVisitLoad(api.requests);
const p = computed(() => list.data.value);
const rows = computed(() => p.value?.items ?? []);
const first = ref(0);

function load(): void {
    void list.load({ page: Math.floor(first.value / PAGE_SIZE), size: PAGE_SIZE });
}

function onPage(next: number): void {
    first.value = next;
    load();
}

onMounted(() => {
    if (allowed.value) load();
});

const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));
const right = { columnHeaderContent: { class: 'justify-end' } };

const cards = computed<VisitStatCard[] | null>(() => {
    const v = p.value;
    if (!v) return list.error.value ? [] : null;
    const num = (n: number) => UtilVisitAnalytics.num(n, locale.value);
    return [
        { key: 'pending', label: t('visitAnalytics.requests.cardPending'), value: num(v.pending), sub: v.pending ? t('visitAnalytics.requests.cardPendingSub') : t('visitAnalytics.requests.cardPendingNone') },
        { key: 'month', label: t('visitAnalytics.requests.cardMonth'), value: num(v.last30Days) },
        { key: 'total', label: t('visitAnalytics.requests.cardTotal'), value: num(v.total) },
    ];
});
</script>
