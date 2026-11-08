<template>
    <!--
        캐시 관리(`/settings/caches`, 설정 › 배치와 로그) — 공개 사이트 API 응답 캐시(Spring Cache + Caffeine 도입 예정)를 한 화면에서 보고, 캐시마다 TTL 을 바꾸고, 갱신이 안 됐을 때 모든 서버의 캐시를 비운다.
        ⚠ 화면 확정 전 목업(`useCacheAdminApi` → `mock/cache-admin-mock.ts`, 2026-11-08). 구조·문구 톤은 배치 관리(`settings/batches.vue`)를 따른다.
        서버 동작(문구의 근거): api 서버는 2~3대이고 캐시는 서버마다 메모리에 따로 있다. 「지금 갱신」은 DB 의 세대 번호를 올리고, 각 서버가 확인 주기(3초)마다 보고 비운다 —
          그래서 갱신은 「몇 초 안에」 닿고, 아직 확인하지 않은 서버가 있으면 그 캐시는 「갱신 대기」다. 관리자 화면에서 게시판·가젯·메뉴 등을 저장하면 관련 캐시는 자동으로 비워진다(기록에 「자동(저장)」).
          회원마다 결과가 다른 응답은 공용 부분만 캐시하고 회원 기준 처리는 꺼낸 뒤 덧입힌다(「회원별 처리」 칸).
        - 위쪽 요약: api 서버 수 · 관리자 서버 수 · 갱신 확인 주기(읽기 전용) · 갱신 대기 캐시 수 · 「전체 갱신」(확인 대화상자, `cache.manage`).
        - 거르기: 분류(공개 화면 / 설정 사본 / 내부 규칙) · 적용 서버(api / admin) · 이름·키·대상 검색 — 목록이 작아 화면에서 거른다.
        - 표: 이름 · 캐시 키 · 대상 · 분류 · 적용 서버 · TTL(기본값과 다르면 표시) · 회원별 처리 · 항목 수 · 적중률 · 마지막 갱신(가장 늦은 서버) · 상태 · 동작(지금 갱신 · TTL 변경).
          갱신 대기인 캐시가 하나라도 있으면 3초마다 목록(열린 서랍 포함)을 다시 읽고, 이 화면에서 갱신한 캐시가 정상이 되면 토스트로 알린다. 30초가 넘게 대기면 행에 안내.
        - 행을 누르면 상세 서랍(`CacheDetailDrawer`, 주소 `?cache=<cacheKey>`): 적용 영역(「미리보기에서 보기」) · 이 가젯 유형을 쓰는 곳(가젯 캐시) · 서버별 상태 · 최근 갱신 기록 · TTL 변경 이력.
        - 가젯은 「배치」 캐시(홈·가젯 페이지·게시판 레이아웃)와 유형별 「가젯 데이터」 캐시(`gadget.<종류>`, 항목 키 = 가젯 설정 지문)를 따로 둔다. 캐시하지 않는 가젯 종류는 목록 아래 안내 상자.
        - 머리 「미리보기」(`CachePreviewDialog`): 실제 공개 화면 iframe(`?adminPreview=1&cachePreview=1`) 위에 캐시 영역 상자를 겹친다 — 쪽 고르기 · 모든 영역 보기 · 캐시 고르기 · 상자를 누르면 그 캐시 요약(TTL·상태·지금 갱신).
        - 권한: 보기 `cache.read`(메뉴), TTL 변경·갱신 `cache.manage`. 숨기는 것은 편의이고 막는 것은 서버다.
    -->
    <div class="flex min-h-0 flex-col gap-4 lg:h-full">
        <PageTitle :title="t('cache.title')" :subtitle="t('cache.subtitle')">
            <template #actions>
                <Button :label="t('cache.preview')" icon="pi pi-eye" size="small" severity="secondary" variant="outlined" :disabled="!overview" @click="openPreview(null)" />
                <Button :label="t('cache.reload')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="busy" @click="reload()" />
            </template>
        </PageTitle>

        <!-- 요약 -->
        <section class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4 lg:flex-row lg:items-center" :aria-label="t('cache.summary')">
            <dl class="m-0 grid flex-1 grid-cols-2 gap-3 sm:grid-cols-5">
                <div v-for="tile in summaryTiles" :key="tile.key" class="rounded-lg border border-(--line) px-3 py-2.5">
                    <dt class="text-[11px] text-muted-color">{{ tile.label }}</dt>
                    <dd class="m-0 mt-0.5 flex flex-wrap items-baseline gap-x-1.5">
                        <span class="text-lg font-semibold tabular-nums" :class="tile.warn ? 'text-amber-700 dark:text-amber-400' : ''">{{ tile.value }}</span>
                        <span v-if="tile.hint" class="truncate text-[11px] text-muted-color" :title="tile.hint">{{ tile.hint }}</span>
                    </dd>
                </div>
            </dl>
            <div class="flex shrink-0 flex-col items-start gap-1 lg:items-end">
                <!-- 전체 캐시 스위치 — 끄면 모든 캐시가 꺼지고 요청마다 원본(DB)을 읽는다(확인 창). 항목별 값은 그대로 남는다 -->
                <div class="flex items-center gap-2">
                    <span v-tooltip.top="canManage ? undefined : t('cache.noManageReason')" class="inline-flex">
                        <ToggleSwitch :model-value="overview?.globalEnabled ?? true" :input-id="globalSwitchId" :disabled="!canManage || !overview || togglingGlobal" @update:model-value="onGlobalToggle" />
                    </span>
                    <label :for="globalSwitchId" class="cursor-pointer text-[13px] font-medium">{{ t('cache.globalSwitch') }}</label>
                </div>
                <span v-tooltip.top="canManage ? undefined : t('cache.noManageReason')" class="inline-flex">
                    <Button :label="t('cache.refreshAll')" icon="pi pi-sync" size="small" :disabled="!canManage || !overview" :loading="refreshingAll" @click="onRefreshAll" />
                </span>
                <span class="text-[11px] text-muted-color">{{ t('cache.refreshAllHint', { s: pollSeconds }) }}</span>
            </div>
        </section>

        <!-- 전체가 꺼져 있음 — 항목 스위치는 잠기고 값은 남는다 -->
        <Message v-if="overview && !overview.globalEnabled" severity="warn" :closable="false" role="status">{{ t('cache.globalOffBanner') }}</Message>

        <!-- 거르기 -->
        <div class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4">
            <FilterRow :label="t('cache.kind')">
                <template #default="{ labelId }">
                    <FilterOptionGroup :model-value="filter.kind" :options="kindOptions" :labelledby="labelId" @update:model-value="(v) => (filter.kind = v as CacheFilter['kind'])" />
                </template>
            </FilterRow>
            <FilterRow :label="t('cache.servers')">
                <template #default="{ labelId }">
                    <FilterOptionGroup :model-value="filter.server" :options="serverOptions" :labelledby="labelId" @update:model-value="(v) => (filter.server = v as CacheFilter['server'])" />
                </template>
            </FilterRow>
            <FilterRow :label="t('cache.enabledFilter')">
                <template #default="{ labelId }">
                    <FilterOptionGroup :model-value="filter.enabled" :options="enabledOptions" :labelledby="labelId" @update:model-value="(v) => (filter.enabled = v as CacheFilter['enabled'])" />
                </template>
            </FilterRow>
            <FilterRow :label="t('cache.search')" :label-for="searchId">
                <IconField class="w-full sm:w-80">
                    <InputIcon class="pi pi-search" />
                    <InputText :id="searchId" v-model="filter.keyword" size="small" fluid :placeholder="t('cache.searchPlaceholder')" autocomplete="off" />
                </IconField>
                <span class="text-[12px] tabular-nums text-muted-color">{{ t('cache.shownCount', { shown: filtered.length, total: caches.length }) }}</span>
            </FilterRow>
        </div>

        <DataTable
            :value="filtered"
            data-key="cacheKey"
            :loading="busy"
            row-hover
            :row-class="() => 'cursor-pointer'"
            :size="prefs.density === 'compact' ? 'small' : undefined"
            scrollable
            scroll-height="flex"
            class="min-h-0 flex-1"
            @row-click="(e: { data: CacheView }) => openDetail(e.data.cacheKey)"
        >
            <template #empty>
                <ErrorState v-if="loadError" :title="t('cache.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload()" />
                <div v-else-if="busy" class="h-32" aria-hidden="true" />
                <EmptyState v-else-if="caches.length" icon="pi pi-filter-slash" :title="t('cache.emptyFiltered')" />
                <EmptyState v-else :title="t('cache.empty')" />
            </template>

            <Column :header="t('cache.name')" style="min-width: 11rem">
                <template #body="{ data }">
                    <button type="button" class="block max-w-[14rem] truncate text-left text-[13px] hover:text-primary hover:underline" :aria-label="t('cache.openDetailLabel', { name: data.displayName })" @click.stop="openDetail(data.cacheKey)">
                        {{ data.displayName }}
                    </button>
                    <!-- 쓰이는 곳 한 줄(화면 영역·동작 이름 둘 + 외 N, 화면이 없으면 서버 내부) — 칸을 늘리지 않고 이름 아래에 -->
                    <span v-if="data.usages.length" class="block max-w-[16rem] truncate text-[11px] text-muted-color" :title="data.usages.map((u: CacheUsageView) => u.label).join(' · ')">
                        {{ UtilCacheMessages.usageSummary(t, data.usages) }}
                    </span>
                </template>
            </Column>

            <Column :header="t('cache.cacheKey')" style="min-width: 10rem">
                <template #body="{ data }">
                    <code class="whitespace-nowrap text-[12px]">{{ data.cacheKey }}</code>
                </template>
            </Column>

            <Column :header="t('cache.target')" style="min-width: 14rem">
                <template #body="{ data }">
                    <div class="max-w-[18rem] truncate text-[12px]" :title="data.target">
                        <code v-if="data.targetIsPath" class="text-[11px]">{{ data.target }}</code>
                        <span v-else class="text-muted-color">{{ data.target }}</span>
                    </div>
                </template>
            </Column>

            <Column :header="t('cache.kind')" style="width: 8.5rem">
                <template #body="{ data }">
                    <CacheKindTag :kind="data.kind" />
                </template>
            </Column>

            <Column :header="t('cache.servers')" style="width: 7rem">
                <template #body="{ data }">
                    <span class="whitespace-nowrap text-[12px]">{{ serversText(data) }}</span>
                </template>
            </Column>

            <Column :header="t('cache.ttl')" style="width: 8rem">
                <template #body="{ data }">
                    <div class="flex flex-col">
                        <span class="whitespace-nowrap text-[12px] tabular-nums" :class="UtilCache.isTtlChanged(data) ? 'font-semibold' : ''">{{ UtilCacheMessages.ttlText(t, data.ttlSeconds) }}</span>
                        <!-- 기본값과 다르면 기본값을 함께 -->
                        <span v-if="UtilCache.isTtlChanged(data)" class="inline-flex items-center gap-1 whitespace-nowrap text-[11px] text-amber-700 dark:text-amber-400">
                            <i class="pi pi-pencil text-[9px]" aria-hidden="true" />
                            {{ t('cache.ttlDefaultIs', { ttl: UtilCacheMessages.ttlText(t, data.defaultTtlSeconds) }) }}
                        </span>
                    </div>
                </template>
            </Column>

            <Column :header="t('cache.memberMode')" style="width: 9rem">
                <template #body="{ data }">
                    <span v-tooltip.top="UtilCacheMessages.memberModeHelp(t, data.memberMode)" class="whitespace-nowrap text-[12px]" :class="data.memberMode === 'SHARED' ? 'text-muted-color' : ''">
                        {{ UtilCacheMessages.memberModeLabel(t, data.memberMode) }}
                    </span>
                </template>
            </Column>

            <Column :header="t('cache.entries')" style="width: 5.5rem">
                <template #body="{ data }">
                    <span class="text-[12px] tabular-nums">{{ data.entryCount.toLocaleString() }}</span>
                </template>
            </Column>

            <Column :header="t('cache.hitRate')" style="width: 6rem">
                <template #body="{ data }">
                    <span v-tooltip.top="t('cache.hitMiss', { hit: data.hitCount.toLocaleString(), miss: data.missCount.toLocaleString() })" class="text-[12px] tabular-nums">
                        {{ UtilCache.percent(UtilCache.hitRate(data.hitCount, data.missCount)) }}
                    </span>
                </template>
            </Column>

            <Column :header="t('cache.lastRefreshed')" style="width: 8rem">
                <template #body="{ data }">
                    <span v-if="data.lastRefreshedAt" v-tooltip.top="time.full(data.lastRefreshedAt)" class="whitespace-nowrap text-[12px] tabular-nums">{{ time.ago(data.lastRefreshedAt, now) }}</span>
                    <span v-else v-tooltip.top="t('cache.neverRefreshedHint')" class="whitespace-nowrap text-[12px] text-muted-color">{{ t('cache.neverRefreshed') }}</span>
                </template>
            </Column>

            <Column :header="t('cache.status')" style="min-width: 9rem">
                <template #body="{ data }">
                    <div class="flex flex-col items-start gap-0.5">
                        <CacheStatusTag :status="data.status" :enabled-state="UtilCache.enabledState(data, globalEnabled)" />
                        <span v-if="data.pendingNodeIds.length" class="whitespace-nowrap text-[11px] text-muted-color">{{ UtilCacheMessages.pendingNodes(t, data.pendingNodeIds) }}</span>
                        <span v-if="UtilCache.isStuck(data, now)" class="flex items-start gap-1 text-[11px] leading-relaxed text-amber-700 dark:text-amber-400" role="status">
                            <i class="pi pi-exclamation-triangle mt-0.5 shrink-0 text-[10px]" aria-hidden="true" />
                            <span>{{ t('cache.stuckShort') }}</span>
                        </span>
                    </div>
                </template>
            </Column>

            <Column header="" style="width: 13rem">
                <template #body="{ data }">
                    <!-- 단추 자리는 행마다 같다 — 「지금 갱신」은 글자가 바뀌어도(갱신 대기) 폭이 같아 위아래 줄이 맞는다 -->
                    <div class="flex items-center justify-end gap-1">
                        <!-- 이 캐시 켜기/끄기 — 전체가 켜져 있을 때만(꺼져 있으면 값은 남고 잠긴다) -->
                        <span v-tooltip.top="enabledLock() ?? (data.enabled ? t('cache.turnOff') : t('cache.turnOn'))" class="inline-flex" @click.stop>
                            <ToggleSwitch :model-value="data.enabled" :disabled="!!enabledLock() || togglingKey === data.cacheKey" :aria-label="t('cache.enabledLabel', { name: data.displayName })" @update:model-value="(v: boolean) => onToggle(data, v)" />
                        </span>
                        <span v-tooltip.top="refreshLock(data)" class="inline-flex">
                            <Button
                                :label="UtilCache.isPending(data) ? t('cache.pending') : t('cache.refreshNow')"
                                :icon="UtilCache.isPending(data) ? 'pi pi-hourglass' : 'pi pi-sync'"
                                size="small"
                                severity="secondary"
                                outlined
                                class="min-w-26 justify-center whitespace-nowrap"
                                :aria-label="t('cache.refreshNowLabel', { name: data.displayName })"
                                :disabled="!!refreshLock(data)"
                                :loading="refreshing === data.cacheKey"
                                @click.stop="onRefresh(data)"
                            />
                        </span>
                        <span v-tooltip.top="canManage ? t('cache.ttlChange') : t('cache.noManageReason')" class="inline-flex">
                            <Button
                                icon="pi pi-clock"
                                size="small"
                                severity="secondary"
                                variant="text"
                                rounded
                                :aria-label="t('cache.ttlChangeLabel', { name: data.displayName })"
                                :disabled="!canManage"
                                @click.stop="openTtl(data)"
                            />
                        </span>
                    </div>
                </template>
            </Column>
        </DataTable>

        <!-- 캐시하지 않는 가젯 — 캐시가 아니라 목록 행으로 두지 않고 안내로(분류 「가젯」을 고르거나 전체일 때) -->
        <section v-if="overview?.uncachedGadgets.length && (!filter.kind || filter.kind === 'GADGET')" class="rounded-xl border border-dashed border-(--line) px-4 py-3" :aria-labelledby="uncachedId">
            <h2 :id="uncachedId" class="m-0 flex items-center gap-1.5 text-[13px] font-medium">
                <i class="pi pi-ban text-[12px] text-muted-color" aria-hidden="true" />
                {{ t('cache.uncachedTitle') }}
            </h2>
            <ul class="m-0 mt-2 grid list-none gap-x-6 gap-y-1.5 p-0 md:grid-cols-2">
                <li v-for="u in overview.uncachedGadgets" :key="u.gadgetKind" class="min-w-0 text-[12px] leading-relaxed">
                    <span class="font-medium">{{ u.displayName }}</span>
                    <code class="ml-1.5 text-[11px] text-muted-color">{{ u.gadgetKind }}</code>
                    <span class="text-muted-color"> — {{ u.reason }}</span>
                    <span v-if="u.includedIn.length" class="text-muted-color"> {{ t('cache.uncachedIncludedIn', { keys: u.includedIn.join(', ') }) }}</span>
                </li>
            </ul>
        </section>

        <p class="text-[11px] leading-relaxed text-muted-color">
            <span v-if="anyPending" class="mr-1 inline-flex items-center gap-1 text-primary" role="status">
                <i class="pi pi-sync pi-spin text-[10px]" aria-hidden="true" />
                {{ t('cache.autoRefresh', { s: UtilCache.POLL_MS / 1000 }) }}
            </span>
            <strong>{{ t('cache.refreshNote', { s: pollSeconds }) }}</strong>
            {{ t('cache.autoSaveNote') }}
            {{ t('cache.overlayNote') }}
        </p>

        <CacheTtlDialog v-model:visible="ttlOpen" :cache="ttlTarget" :poll-seconds="pollSeconds" :busy="ttlBusy" :error="ttlError" :field-errors="ttlFieldErrors" @submit="onTtl" />
        <CacheDetailDrawer
            :visible="!!detailKey"
            :detail="detail"
            :loading="detailBusy"
            :error="detailError"
            :refreshing="!!detail && refreshing === detail.cacheKey"
            :can-manage="canManage"
            :global-enabled="globalEnabled"
            :toggling="!!detail && togglingKey === detail.cacheKey"
            :poll-seconds="pollSeconds"
            :now="now"
            @close="closeDetail"
            @reload="loadDetail()"
            @refresh="onRefresh"
            @ttl="openTtl"
            @preview="(c: CacheView, u: CacheUsageView) => openPreview(c, u)"
            @toggle="(c: CacheView, v: boolean) => onToggle(c, v)"
        />
        <CachePreviewDialog
            v-model:visible="previewOpen"
            v-model:path="previewPath"
            v-model:selected-key="previewKey"
            v-model:show-all="previewShowAll"
            :caches="caches"
            :exclusions="overview?.uncachedGadgets ?? []"
            :pages="overview?.previewPages ?? []"
            :can-manage="canManage"
            :refreshing-key="refreshing"
            :now="now"
            :global-enabled="globalEnabled"
            @refresh="onRefresh"
            @ttl="openTtl"
            :samples="samples"
            @open-detail="onPreviewDetail"
        />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { CacheOverviewView, CacheServerRole, CacheUsageView, CacheView, CacheDetailView } from '~/composables/cache/api/useCacheAdminApi.types';
import UtilCache, { type CacheFilter } from '~/utils/util-cache';
import UtilCacheMessages from '~/utils/util-cache-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('cache.title') });

const api = useCacheAdminApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const { prefs } = useLayoutPrefs();
const route = useRoute();
const router = useRouter();
const searchId = useId();

/** 이 화면에서 하는 일에 필요한 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('cache.manage'));

const overview = ref<CacheOverviewView | null>(null);
const caches = ref<CacheView[]>([]);
const busy = ref(false);
/** 목록을 받지 못했을 때의 오류 — 표 자리에 오류 상태(다시 시도)로 보인다. 갱신·TTL 실패는 토스트·대화상자다 */
const loadError = ref<ApiError | null>(null);
const pollSeconds = computed(() => overview.value?.pollSeconds ?? 3);

/** 「몇 초 전」·오래 걸림 판정의 기준 시각 — 다시 읽을 때마다 맞춘다 */
const now = ref(Date.now());
let pollTimer: ReturnType<typeof setTimeout> | null = null;
let disposed = false;
/** 이 화면에서 갱신한 캐시 — 정상이 되면 「모든 서버에 반영됐다」고 알린다 */
const myRefreshes = new Set<string>();

const filter = reactive<CacheFilter>({ kind: '', server: '', enabled: '', keyword: '' });
const enabledOptions = computed(() => [
    { value: '', label: t('cache.all') },
    { value: 'ON', label: t('cache.enabledOn') },
    { value: 'OFF', label: t('cache.enabledOff') },
]);
/** 전체 캐시 스위치(DB 값) — 개요를 받기 전에는 켜짐으로 본다 */
const globalEnabled = computed(() => overview.value?.globalEnabled ?? true);
const globalSwitchId = useId();
/** 켜기/끄기 요청 중 — 전체 · 캐시 키 */
const togglingGlobal = ref(false);
const togglingKey = ref<string | null>(null);
/** 꺼진 캐시 수(실제 꺼짐 — 전체가 꺼졌으면 전부) */
const offCount = computed(() => caches.value.filter((c) => !c.effectiveEnabled).length);
const filtered = computed(() => caches.value.filter((c) => UtilCache.matches(c, filter)));

const kindOptions = computed(() => [{ value: '', label: t('cache.all') }, ...UtilCache.KINDS.map((kind) => ({ value: kind, label: UtilCacheMessages.kindLabel(t, kind) }))]);
const serverOptions = computed(() => [{ value: '', label: t('cache.all') }, ...UtilCache.ROLES.map((role) => ({ value: role, label: UtilCacheMessages.serverLabel(t, role) }))]);

const pendingCount = computed(() => caches.value.filter(UtilCache.isPending).length);
const anyPending = computed(() => pendingCount.value > 0 || (!!detail.value && UtilCache.isPending(detail.value)));

/** 요약 칸 — 서버 수는 이름을 함께 */
const summaryTiles = computed(() => {
    const nodes = overview.value?.nodes ?? [];
    const apiNodes = nodes.filter((n) => n.role === 'API');
    const adminNodes = nodes.filter((n) => n.role === 'ADMIN');
    const dash = overview.value ? undefined : '—';
    return [
        { key: 'api', label: t('cache.apiServerCount'), value: dash ?? String(apiNodes.length), hint: apiNodes.map((n) => n.nodeId).join(', '), warn: false },
        { key: 'admin', label: t('cache.adminServerCount'), value: dash ?? String(adminNodes.length), hint: adminNodes.map((n) => n.nodeId).join(', '), warn: false },
        { key: 'poll', label: t('cache.pollInterval'), value: dash ?? UtilCacheMessages.ttlText(t, pollSeconds.value), hint: t('cache.readOnly'), warn: false },
        { key: 'pending', label: t('cache.pendingCount'), value: dash ?? String(pendingCount.value), hint: '', warn: pendingCount.value > 0 },
        { key: 'off', label: t('cache.offCount'), value: dash ?? String(offCount.value), hint: globalEnabled.value ? '' : t('cache.offCountGlobal'), warn: offCount.value > 0 },
    ];
});

// ── 상세 서랍(`?cache=<cacheKey>`) ──
const detailKey = ref<string | null>(null);
const detail = ref<CacheDetailView | null>(null);
const detailBusy = ref(false);
const detailError = ref<ApiError | null>(null);

// ── TTL 변경 대화상자 — 열 때의 목록 한 줄을 붙들고, 서버 호출 결과(칸별 오류 포함)를 대화상자 안에 보인다 ──
const ttlOpen = ref(false);
const ttlTarget = ref<CacheView | null>(null);
const ttlBusy = ref(false);
const ttlError = ref('');
const ttlFieldErrors = ref<Record<string, string>>({});

// ── 적용 영역 미리보기(실제 공개 화면 iframe 위에 캐시 영역을 겹친다) ──
const previewOpen = ref(false);
const previewPath = ref('/');
/** 고른 영역 키 — 캐시 키 또는 `nocache:<가젯 종류>` */
const previewKey = ref<string | null>(null);
const previewShowAll = ref(true);
const uncachedId = useId();
/** 빌드한 관리자 화면은 서버가 준 사이트 공개 주소를 iframe origin 으로 쓴다(개발 서버는 runtimeConfig 가 먼저) */
const { setFromServer } = useUserWebOrigin();
/** 미리보기 예시(글·상품 하나를 열어야 보이는 곳의 번호) — 기존 관리자 API 로 고른다. 대화상자와 서랍 단추가 함께 쓴다 */
const samples = useCacheSamples();

/** 「지금 갱신」 요청을 넣는 중인 캐시 키 · 「전체 갱신」 중 */
const refreshing = ref<string | null>(null);
const refreshingAll = ref(false);

onMounted(() => reload());
onBeforeUnmount(() => {
    disposed = true;
    stopPolling();
});

/** 주소의 `?cache=` 와 서랍을 맞춘다 — 새로 고침·뒤로 가기에도 같은 캐시가 열린다 */
watch(
    () => route.query.cache,
    (value) => {
        const key = typeof value === 'string' && value ? value : null;
        if (key === detailKey.value) return;
        detailKey.value = key;
        detail.value = null;
        detailError.value = null;
        if (key) void loadDetail();
    },
    { immediate: true },
);

function openDetail(cacheKey: string): void {
    void router.replace({ path: route.path, query: { ...route.query, cache: cacheKey } });
}

function closeDetail(): void {
    void router.replace({ path: route.path, query: { ...route.query, cache: undefined } });
}

/** 적용 서버 글자 — `api · admin` */
function serversText(cache: CacheView): string {
    return cache.servers.map((role: CacheServerRole) => UtilCacheMessages.serverLabel(t, role)).join(' · ');
}

/** 「지금 갱신」이 잠긴 이유 — 권한 → 갱신이 퍼지는 중(오래 걸리면 다시 누를 수 있다). 잠기지 않았으면 undefined(툴팁 없음) */
function refreshLock(cache: CacheView): string | undefined {
    if (!canManage.value) return t('cache.noManageReason');
    if (!cache.effectiveEnabled) return globalEnabled.value ? t('cache.offLockReason') : t('cache.globalOffLockReason');
    if (UtilCache.isPending(cache) && !UtilCache.isStuck(cache, now.value)) return t('cache.pendingLockReason');
    return undefined;
}

/**
 * 목록을 다시 받는다. `silent` 는 자동 다시 읽기 — 표 로딩 덮개를 띄우지 않고, 실패해도 지금 목록을 그대로 둔다(다음 차례에 다시 읽는다).
 */
async function reload(silent = false): Promise<void> {
    if (!silent) {
        busy.value = true;
        loadError.value = null;
    }
    try {
        const next = await api.overview();
        overview.value = next;
        if (next.publicUrl) setFromServer(next.publicUrl);
        applyCaches(next.caches);
    } catch (e) {
        if (!silent) {
            loadError.value = errorOf(e);
            overview.value = null;
            caches.value = [];
        }
    } finally {
        if (!silent) busy.value = false;
        now.value = Date.now();
        schedulePoll();
    }
}

/** 새 목록으로 바꾸고, 이 화면에서 갱신한 캐시가 모든 서버에 닿았으면 알린다 */
function applyCaches(next: CacheView[]): void {
    caches.value = next;
    for (const key of [...myRefreshes]) {
        const cache = next.find((c) => c.cacheKey === key);
        if (!cache || UtilCache.isPending(cache)) continue;
        myRefreshes.delete(key);
        toast.success(t('cache.reachedAll', { name: cache.displayName }), 3000);
    }
}

/** 바뀐 줄만 목록에 바꿔 끼운다(갱신·TTL 변경 응답) */
function replaceRows(rows: CacheView[]): void {
    const byKey = new Map(rows.map((r) => [r.cacheKey, r]));
    caches.value = caches.value.map((c) => byKey.get(c.cacheKey) ?? c);
    now.value = Date.now();
}

async function loadDetail(silent = false): Promise<void> {
    const key = detailKey.value;
    if (!key) return;
    if (!silent) {
        detailBusy.value = true;
        detailError.value = null;
    }
    try {
        const next = await api.detail(key);
        if (detailKey.value === key) detail.value = next;
    } catch (e) {
        if (!silent && detailKey.value === key) {
            detailError.value = errorOf(e);
            detail.value = null;
        }
    } finally {
        if (!silent) detailBusy.value = false;
        schedulePoll();
    }
}

/** 갱신 대기가 있는 동안만 다시 읽는다 — 목록과 열린 서랍을 함께 */
function schedulePoll(): void {
    stopPolling();
    if (disposed || !anyPending.value) return;
    pollTimer = setTimeout(() => {
        pollTimer = null;
        void reload(true);
        if (detailKey.value) void loadDetail(true);
    }, UtilCache.POLL_MS);
}

function stopPolling(): void {
    if (pollTimer) clearTimeout(pollTimer);
    pollTimer = null;
}

/** 「지금 갱신」 — 세대 번호를 올리면 바로 「갱신 대기」가 되고, 각 서버가 몇 초 안에 따라잡으면 정상이 된다 */
async function onRefresh(cache: CacheView): Promise<void> {
    if (refreshLock(cache)) return;
    refreshing.value = cache.cacheKey;
    try {
        const result = await api.refresh(cache.cacheKey);
        replaceRows(result.caches);
        myRefreshes.add(cache.cacheKey);
        toast.success(t('cache.refreshRequested', { name: cache.displayName, s: pollSeconds.value }), 4000);
        if (detailKey.value === cache.cacheKey) await loadDetail(true);
        schedulePoll();
    } catch (e) {
        const error = errorOf(e);
        toast.error(error.message, 8000);
        if (error.code === 'CACHE_NOT_FOUND') await reload();
    } finally {
        refreshing.value = null;
    }
}

/** 「전체 갱신」 — 모든 캐시를 비우므로 잠깐 응답이 느려질 수 있어 한 번 더 묻는다 */
async function onRefreshAll(): Promise<void> {
    if (!canManage.value) return;
    const ok = await confirm({
        title: t('cache.refreshAllConfirmTitle'),
        message: t('cache.refreshAllConfirmMessage', { n: caches.value.length, s: pollSeconds.value }),
        confirmLabel: t('cache.refreshAll'),
    });
    if (!ok) return;
    refreshingAll.value = true;
    try {
        const result = await api.refreshAll();
        replaceRows(result.caches);
        for (const c of result.caches) myRefreshes.add(c.cacheKey);
        toast.success(t('cache.refreshAllRequested', { s: pollSeconds.value }), 4000);
        if (detailKey.value) await loadDetail(true);
        schedulePoll();
    } catch (e) {
        toast.error(errorOf(e).message, 8000);
    } finally {
        refreshingAll.value = false;
    }
}

/**
 * 미리보기를 연다 — 캐시를 주면(서랍 「쓰이는 곳」의 「미리보기에서 보기」) 그 줄의 쪽(없으면 대표 쪽)을 띄워 그 영역만 강조하고, 안 주면(머리 단추) 홈의 모든 영역을 이름표와 함께.
 * 서버 내부만인 캐시는 서랍이 단추를 보이지 않는다(미리보기 안에서 고르면 서버에서 쓰이는 방식을 설명한다).
 * 글·상품 하나를 열어야 보이는 줄(예시 경로)은 예시 번호를 먼저 고른다(`useCacheSamples` — 기존 관리자 API). 못 고르면 쪽은 그대로 두고 대화상자의 「예시」 칸이 직접 넣어 보라고 알린다.
 */
async function openPreview(cache: CacheView | null, usage?: CacheUsageView | null): Promise<void> {
    if (cache) {
        previewKey.value = cache.cacheKey;
        previewShowAll.value = false;
        previewOpen.value = true;
        const target = usage ?? UtilCache.representativeUsage(cache);
        const path = target ? await samples.resolve(target) : null;
        if (path) previewPath.value = path;
        return;
    }
    previewShowAll.value = true;
    previewOpen.value = true;
}

/** 미리보기의 「상세」 — 미리보기를 닫고 그 캐시의 상세 서랍을 연다 */
function onPreviewDetail(cacheKey: string): void {
    previewOpen.value = false;
    openDetail(cacheKey);
}

/** 이 캐시 켜기/끄기가 잠긴 이유 — 권한 → 전체가 꺼져 있음(값은 남는다). 잠기지 않았으면 undefined */
function enabledLock(): string | undefined {
    if (!canManage.value) return t('cache.noManageReason');
    if (!globalEnabled.value) return t('cache.globalOffItemLock');
    return undefined;
}

/**
 * 전체 캐시 켜기/끄기 — 끌 때만 확인한다(모든 공개 화면 요청이 DB 를 직접 읽어 부하가 는다). 각 서버가 확인 주기 안에 따라가며, 그동안 캐시들은 「갱신 대기」다.
 */
async function onGlobalToggle(enabled: boolean): Promise<void> {
    if (!canManage.value || !overview.value) return;
    if (!enabled) {
        const ok = await confirm({ title: t('cache.globalOffConfirmTitle'), message: t('cache.globalOffConfirmMessage', { s: pollSeconds.value }), confirmLabel: t('cache.turnOff'), tone: 'danger' });
        if (!ok) return;
    }
    togglingGlobal.value = true;
    try {
        const next = await api.setGlobalEnabled(enabled);
        overview.value = next;
        applyCaches(next.caches);
        now.value = Date.now();
        toast.success(enabled ? t('cache.globalTurnedOn', { s: pollSeconds.value }) : t('cache.globalTurnedOff', { s: pollSeconds.value }), 4000);
        if (detailKey.value) await loadDetail(true);
        schedulePoll();
    } catch (e) {
        toast.error(errorOf(e).message, 8000);
    } finally {
        togglingGlobal.value = false;
    }
}

/** 이 캐시 켜기/끄기 — 끌 때만 짧게 확인한다. 전체가 꺼져 있으면 잠겨 있다(서버도 409 `CACHE_GLOBAL_DISABLED`) */
async function onToggle(cache: CacheView, enabled: boolean): Promise<void> {
    if (enabledLock()) return;
    if (!enabled) {
        const ok = await confirm({ title: t('cache.itemOffConfirmTitle', { name: cache.displayName }), message: t('cache.itemOffConfirmMessage', { s: pollSeconds.value }), confirmLabel: t('cache.turnOff'), tone: 'danger' });
        if (!ok) return;
    }
    togglingKey.value = cache.cacheKey;
    try {
        const row = await api.setEnabled(cache.cacheKey, enabled);
        replaceRows([row]);
        toast.success(t(enabled ? 'cache.itemTurnedOn' : 'cache.itemTurnedOff', { name: cache.displayName, s: pollSeconds.value }), 4000);
        if (detailKey.value === cache.cacheKey) await loadDetail(true);
        schedulePoll();
    } catch (e) {
        const error = errorOf(e);
        toast.error(error.message, 8000);
        if (error.code === 'CACHE_GLOBAL_DISABLED' || error.code === 'CACHE_NOT_FOUND') await reload();
    } finally {
        togglingKey.value = null;
    }
}

function openTtl(cache: CacheView): void {
    if (!canManage.value) return;
    ttlTarget.value = cache;
    ttlError.value = '';
    ttlFieldErrors.value = {};
    ttlOpen.value = true;
}

async function onTtl(ttlSeconds: number): Promise<void> {
    const target = ttlTarget.value;
    if (!target) return;
    ttlBusy.value = true;
    ttlError.value = '';
    ttlFieldErrors.value = {};
    try {
        const row = await api.updateTtl(target.cacheKey, ttlSeconds);
        replaceRows([row]);
        ttlOpen.value = false;
        toast.success(t('cache.ttlChanged', { name: target.displayName, ttl: UtilCacheMessages.ttlText(t, row.ttlSeconds), s: pollSeconds.value }), 4000);
        if (detailKey.value === target.cacheKey) await loadDetail(true);
    } catch (e) {
        const error = errorOf(e);
        // 범위 밖 400 은 입력칸 아래에 서버 문구 그대로. 칸에 붙지 않는 오류는 대화상자 안에, 캐시가 없어졌으면 목록을 다시 읽는다
        ttlFieldErrors.value = error.fields ?? {};
        ttlError.value = error.fields && Object.keys(error.fields).length ? '' : error.message;
        if (error.code === 'CACHE_NOT_FOUND') await reload();
    } finally {
        ttlBusy.value = false;
    }
}
</script>
