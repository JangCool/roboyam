<template>
    <!--
        캐시 상세 서랍 — 캐시 관리 목록의 행을 누르면 같은 화면에서 연다(주소 `?cache=<cacheKey>`). 늘 오른쪽, 폭 56rem(좁은 화면은 화면 폭).
        서버 호출·다시 읽기는 화면이 하고 여기는 그리기만 한다(`components.md` — 부품은 서버를 부르지 않는다). 머리 단추는 이벤트로 올린다.
        머리: 이름 · 상태 · 단추 줄(지금 갱신 · TTL 변경 · 다시 조회).
        본문 차례: 요약(캐시 키 · 세대 번호 · TTL) → 갱신 대기 띠 → 정보 → 쓰이는 곳(화면 영역·동작·서버 내부 + 관련 관리 화면, 「미리보기에서 보기」 → `preview(cache, path)`) → 이 가젯 유형을 놓은 곳(가젯 캐시만) → 서버별 상태 → 최근 갱신 기록(수동 / 자동(저장) — TTL 만료는 기록하지 않는다) → TTL 변경 이력.
    -->
    <Drawer :visible="visible" position="right" :aria-labelledby="titleId" :style="{ width: '56rem', maxWidth: '100vw' }" @update:visible="(v: boolean) => { if (!v) emit('close'); }">
        <template #header>
            <div class="flex min-w-0 flex-1 flex-col gap-2">
                <div class="flex min-w-0 flex-wrap items-center gap-2">
                    <h2 :id="titleId" class="min-w-0 truncate text-base font-semibold">{{ d?.displayName ?? t('cache.detailTitle') }}</h2>
                    <CacheStatusTag v-if="d" :status="d.status" :enabled-state="UtilCache.enabledState(d, globalEnabled)" />
                </div>
                <div v-if="d" class="flex flex-wrap items-center gap-1.5">
                    <span v-tooltip.bottom="refreshLock" class="inline-flex">
                        <Button
                            :label="pending ? t('cache.pending') : t('cache.refreshNow')"
                            :icon="pending ? 'pi pi-hourglass' : 'pi pi-sync'"
                            size="small"
                            :disabled="!!refreshLock"
                            :loading="refreshing"
                            @click="emit('refresh', d)"
                        />
                    </span>
                    <span v-tooltip.bottom="canManage ? undefined : t('cache.noManageReason')" class="inline-flex">
                        <Button :label="t('cache.ttlChange')" icon="pi pi-clock" size="small" severity="secondary" variant="outlined" :disabled="!canManage" @click="emit('ttl', d)" />
                    </span>
                    <Button :label="t('cache.reload')" icon="pi pi-refresh" size="small" severity="secondary" variant="text" :loading="loading" @click="emit('reload')" />
                </div>
            </div>
        </template>

        <div v-if="loading && !d" class="flex flex-col gap-4" role="status" :aria-label="t('common.loading')" aria-busy="true">
            <Skeleton height="7rem" />
            <Skeleton height="12rem" />
        </div>

        <template v-else-if="!d">
            <Message v-if="error?.status === 403" severity="info" :closable="false" size="small">{{ t('cache.detailDenied') }}</Message>
            <ErrorState v-else-if="error?.status === 404" code="404" :title="t('cache.detailNotFound')" :description="error.message" />
            <ErrorState v-else :title="t('cache.detailLoadFailed')" :description="error?.message ?? ''" retry :retrying="loading" @retry="emit('reload')" />
        </template>

        <div v-else class="flex flex-col gap-4">
            <DrawerSummary :label="t('drawerSummary.label')" :items="summaryItems" :columns="3" class="mb-0!" />

            <!-- 갱신 대기 띠 — 아직 지금 세대를 확인하지 않은 서버. 확인 주기마다 따라잡는다 -->
            <Message v-if="pending" :severity="stuck ? 'warn' : 'info'" :closable="false" role="status">
                <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                    <i class="pi pi-hourglass" aria-hidden="true" />
                    <span>{{ t('cache.pendingBanner', { nodes: d.pendingNodeIds.join(', '), s: pollSeconds }) }}</span>
                </div>
                <div v-if="stuck" class="mt-1 text-[12px]">{{ t('cache.stuckNotice') }}</div>
            </Message>

            <!-- 이 캐시 켜기/끄기 — 전체가 켜져 있을 때만 바꿀 수 있다(꺼져 있으면 값은 남고 잠긴다). 끌 때 확인은 화면이 한다 -->
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1.5 rounded-xl border border-(--line) px-4 py-3">
                <span v-tooltip.top="enabledLock" class="inline-flex">
                    <ToggleSwitch :model-value="d.enabled" :input-id="enabledId" :disabled="!!enabledLock || toggling" @update:model-value="(v: boolean) => d && emit('toggle', d, v)" />
                </span>
                <label :for="enabledId" class="text-[13px] font-medium">{{ t('cache.itemSwitch') }}</label>
                <span class="text-[12px] text-muted-color">{{ d.effectiveEnabled ? t('cache.itemOnHint') : globalEnabled ? t('cache.itemOffHint') : t('cache.globalOffItemLock') }}</span>
            </div>

            <AppGadget :title="t('cache.info')">
                <dl class="grid gap-x-6 gap-y-3 sm:grid-cols-2 lg:grid-cols-3">
                    <div class="sm:col-span-2 lg:col-span-3">
                        <dt class="text-[11px] text-muted-color">{{ t('cache.target') }}</dt>
                        <dd class="mt-1 text-[13px]">
                            <code v-if="d.targetIsPath">{{ d.target }}</code>
                            <span v-else>{{ d.target }}</span>
                        </dd>
                    </div>
                    <div>
                        <dt class="text-[11px] text-muted-color">{{ t('cache.kind') }}</dt>
                        <dd class="mt-1"><CacheKindTag :kind="d.kind" /></dd>
                    </div>
                    <div>
                        <dt class="text-[11px] text-muted-color">{{ t('cache.servers') }}</dt>
                        <dd class="mt-1 flex flex-wrap gap-1">
                            <Tag v-for="role in d.servers" :key="role" :value="UtilCacheMessages.serverLabel(t, role)" severity="secondary" />
                        </dd>
                    </div>
                    <div>
                        <dt class="text-[11px] text-muted-color">{{ t('cache.memberMode') }}</dt>
                        <dd class="mt-1 text-[13px]">{{ UtilCacheMessages.memberModeLabel(t, d.memberMode) }}</dd>
                        <dd class="mt-0.5 text-[11px] leading-relaxed text-muted-color">{{ UtilCacheMessages.memberModeHelp(t, d.memberMode) }}</dd>
                    </div>
                    <div>
                        <dt class="text-[11px] text-muted-color">{{ t('cache.ttl') }}</dt>
                        <dd class="mt-1 text-[13px] tabular-nums">
                            {{ UtilCacheMessages.ttlText(t, d.ttlSeconds) }}
                            <span class="text-[12px] text-muted-color">· {{ t('cache.ttlDefaultIs', { ttl: UtilCacheMessages.ttlText(t, d.defaultTtlSeconds) }) }}</span>
                        </dd>
                        <dd class="mt-0.5 text-[11px] text-muted-color">{{ t('cache.ttlAllowedIs', { range: UtilCacheMessages.rangeText(t, d.minTtlSeconds, d.maxTtlSeconds) }) }}</dd>
                    </div>
                    <div>
                        <dt class="text-[11px] text-muted-color">{{ t('cache.entries') }}</dt>
                        <dd class="mt-1 text-[13px] tabular-nums">{{ d.entryCount.toLocaleString() }}</dd>
                    </div>
                    <div>
                        <dt class="text-[11px] text-muted-color">{{ t('cache.hitRate') }}</dt>
                        <dd class="mt-1 text-[13px] tabular-nums">
                            {{ UtilCache.percent(UtilCache.hitRate(d.hitCount, d.missCount)) }}
                            <span class="text-[12px] text-muted-color">· {{ t('cache.hitMiss', { hit: d.hitCount.toLocaleString(), miss: d.missCount.toLocaleString() }) }}</span>
                        </dd>
                    </div>
                </dl>
            </AppGadget>

            <!-- 쓰이는 곳 — 화면 영역(값이 보이는 곳) · 동작(누르거나 쓸 때 규칙이 적용되는 곳) · 서버 내부. 화면 영역·동작 줄은 그 쪽을 미리보기로 열어 강조한다 -->
            <AppGadget :title="t('cache.usages')">
                <template v-if="representative" #actions>
                    <Button :label="t('cache.viewInPreview')" icon="pi pi-eye" size="small" severity="secondary" variant="outlined" @click="emit('preview', d, representative)" />
                </template>
                <Message v-if="UtilCache.serverOnly(d)" severity="secondary" size="small" :closable="false" class="mb-3">{{ t('cache.serverOnlyNote') }}</Message>
                <CacheUsageList :usages="d.usages" :admin-links="d.adminLinks" previewable @preview="previewAt" />
            </AppGadget>

            <!-- 이 가젯 유형을 놓은 곳 — 같은 설정의 가젯은 어디에 있든 한 항목을 함께 쓴다 -->
            <AppGadget v-if="d.kind === 'GADGET'" :title="t('cache.placements')">
                <ul v-if="d.gadgetPlacements.length" class="m-0 flex list-none flex-col gap-1.5 p-0">
                    <li v-for="(u, i) in d.gadgetPlacements" :key="`${u.place}-${i}`" class="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[13px]">
                        <Tag :value="UtilCacheMessages.placeLabel(t, u.place)" severity="secondary" class="cache-small-tag" />
                        <span>{{ u.name }}</span>
                        <code v-if="u.path" class="text-[11px] text-muted-color">{{ u.path }}</code>
                        <span class="ml-auto text-[12px] tabular-nums text-muted-color">{{ t('cache.usageCount', { n: u.count }) }}</span>
                    </li>
                </ul>
                <p v-else class="m-0 text-[12px] text-muted-color">{{ t('cache.placementsEmpty') }}</p>
                <p class="mt-2 text-[11px] leading-relaxed text-muted-color">
                    {{ t('cache.placementsNote', { gadgets: placementTotal, configs: d.distinctConfigs ?? 0 }) }}
                </p>
            </AppGadget>

            <AppGadget :title="t('cache.nodes')">
                <DataTable :value="d.nodes" data-key="nodeId" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable>
                    <Column :header="t('cache.node')" style="width: 8rem">
                        <template #body="{ data }">
                            <div class="flex items-center gap-1.5">
                                <code class="text-[12px]">{{ data.nodeId }}</code>
                                <Tag :value="UtilCacheMessages.serverLabel(t, data.role)" severity="secondary" class="cache-small-tag" />
                            </div>
                        </template>
                    </Column>
                    <Column :header="t('cache.appliedGeneration')" style="width: 9rem">
                        <template #body="{ data }">
                            <div class="flex items-center gap-1.5">
                                <span class="text-[12px] tabular-nums">#{{ data.appliedGeneration }}</span>
                                <CacheStatusTag v-if="data.appliedGeneration < d.generation" status="PENDING" />
                            </div>
                        </template>
                    </Column>
                    <Column :header="t('cache.appliedEnabled')" style="width: 8rem">
                        <template #body="{ data }">
                            <div class="flex items-center gap-1.5">
                                <span class="text-[12px]" :class="data.appliedEnabled ? '' : 'text-muted-color'">{{ data.appliedEnabled ? t('cache.enabledOn') : t('cache.enabledOff') }}</span>
                                <CacheStatusTag v-if="data.appliedEnabled !== d.effectiveEnabled" status="PENDING" />
                            </div>
                        </template>
                    </Column>
                    <Column :header="t('cache.lastCleared')" style="min-width: 10rem">
                        <template #body="{ data }">
                            <span v-if="data.lastClearedAt" v-tooltip.top="time.full(data.lastClearedAt)" class="text-[12px] tabular-nums">{{ time.ago(data.lastClearedAt, now) }}</span>
                            <span v-else class="text-[12px] text-muted-color">{{ t('cache.neverCleared') }}</span>
                        </template>
                    </Column>
                    <Column :header="t('cache.lastChecked')" style="min-width: 9rem">
                        <template #body="{ data }">
                            <span v-if="data.lastCheckedAt" v-tooltip.top="time.full(data.lastCheckedAt)" class="text-[12px] tabular-nums text-muted-color">{{ time.ago(data.lastCheckedAt, now) }}</span>
                            <span v-else class="text-[12px] text-muted-color">—</span>
                        </template>
                    </Column>
                    <Column :header="t('cache.entries')" style="width: 6rem">
                        <template #body="{ data }">
                            <span class="text-[12px] tabular-nums">{{ data.entryCount.toLocaleString() }}</span>
                        </template>
                    </Column>
                    <Column :header="t('cache.hitMissHeader')" style="min-width: 11rem">
                        <template #body="{ data }">
                            <span class="text-[12px] tabular-nums">{{ data.hitCount.toLocaleString() }} / {{ data.missCount.toLocaleString() }}</span>
                            <span class="ml-1.5 text-[11px] tabular-nums text-muted-color">{{ UtilCache.percent(UtilCache.hitRate(data.hitCount, data.missCount)) }}</span>
                        </template>
                    </Column>
                </DataTable>
                <p class="mt-2 text-[11px] leading-relaxed text-muted-color">{{ t('cache.nodesNote', { s: pollSeconds }) }}</p>
            </AppGadget>

            <AppGadget :title="t('cache.refreshLog')">
                <DataTable :value="d.refreshes" data-key="id" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable>
                    <template #empty>
                        <EmptyState icon="pi pi-history" :title="t('cache.refreshLogEmpty')" />
                    </template>
                    <Column :header="t('cache.requestedAt')" style="width: 11rem">
                        <template #body="{ data }">
                            <span class="text-[12px] tabular-nums">{{ time.full(data.requestedAt) }}</span>
                        </template>
                    </Column>
                    <Column :header="t('cache.trigger')" style="width: 10rem">
                        <template #body="{ data }">
                            <Tag :value="UtilCacheMessages.triggerLabel(t, data)" :icon="data.trigger === 'MANUAL' ? 'pi pi-user' : 'pi pi-save'" :severity="data.trigger === 'MANUAL' ? 'secondary' : 'info'" class="cache-small-tag" />
                        </template>
                    </Column>
                    <Column :header="t('cache.refreshSource')" style="min-width: 12rem">
                        <template #body="{ data }">
                            <span v-if="data.source" class="text-[12px]">{{ data.source }}</span>
                            <span v-else class="text-[12px] text-muted-color">—</span>
                        </template>
                    </Column>
                    <Column :header="t('cache.who')" style="width: 9rem">
                        <template #body="{ data }">
                            <span class="text-[12px]">{{ data.operatorName ?? '—' }}</span>
                        </template>
                    </Column>
                    <Column :header="t('cache.generation')" style="width: 6rem">
                        <template #body="{ data }">
                            <span class="text-[12px] tabular-nums">#{{ data.generation }}</span>
                        </template>
                    </Column>
                </DataTable>
                <p class="mt-2 text-[11px] leading-relaxed text-muted-color">{{ t('cache.refreshLogNote') }}</p>
            </AppGadget>

            <AppGadget :title="t('cache.settingHistory')">
                <DataTable :value="d.settingChanges" data-key="id" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable>
                    <template #empty>
                        <EmptyState icon="pi pi-clock" :title="t('cache.settingHistoryEmpty')" />
                    </template>
                    <Column :header="t('cache.changedAt')" style="width: 11rem">
                        <template #body="{ data }">
                            <span class="text-[12px] tabular-nums">{{ time.full(data.changedAt) }}</span>
                        </template>
                    </Column>
                    <Column :header="t('cache.ttlChangeHeader')" style="min-width: 12rem">
                        <template #body="{ data }">
                            <span class="text-[12px] tabular-nums">{{ UtilCacheMessages.settingChangeText(t, data) }}</span>
                        </template>
                    </Column>
                    <Column :header="t('cache.who')" style="width: 9rem">
                        <template #body="{ data }">
                            <span class="text-[12px]">{{ data.operatorName ?? '—' }}</span>
                        </template>
                    </Column>
                </DataTable>
            </AppGadget>
        </div>
    </Drawer>
</template>

<script setup lang="ts">
import type { DrawerSummaryItem } from '~/components/common/DrawerSummary.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { CacheDetailView, CacheUsageView } from '~/composables/cache/api/useCacheAdminApi.types';
import UtilCache from '~/utils/util-cache';
import UtilCacheMessages from '~/utils/util-cache-messages';

interface Props {
    visible: boolean;
    /** 받은 상세 — 아직 못 받았으면 null */
    detail?: CacheDetailView | null;
    loading?: boolean;
    /** 상세를 못 받았을 때의 오류 */
    error?: ApiError | null;
    /** 「지금 갱신」 요청을 넣는 중 */
    refreshing?: boolean;
    /** TTL 변경·갱신 권한(`cache.manage`) */
    canManage?: boolean;
    /** 서버가 세대 번호를 확인하는 주기(초) */
    pollSeconds?: number;
    /** 「몇 초 전」·오래 걸림 판정의 기준 시각 — 화면이 다시 읽을 때마다 맞춘다 */
    now?: number;
    /** 전체 캐시 스위치 — 꺼져 있으면 이 캐시 스위치는 잠긴다 */
    globalEnabled?: boolean;
    /** 켜기/끄기 요청 중 */
    toggling?: boolean;
}

const props = withDefaults(defineProps<Props>(), { detail: null, loading: false, error: null, refreshing: false, canManage: false, pollSeconds: 3, now: 0, globalEnabled: true, toggling: false });
const emit = defineEmits<{ close: []; reload: []; refresh: [cache: CacheDetailView]; ttl: [cache: CacheDetailView]; preview: [cache: CacheDetailView, usage: CacheUsageView]; toggle: [cache: CacheDetailView, enabled: boolean] }>();

const { t } = useI18n();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const titleId = useId();
const enabledId = useId();
/** 켜기/끄기가 잠긴 이유 — 권한 → 전체가 꺼져 있음 */
const enabledLock = computed(() => (!props.canManage ? t('cache.noManageReason') : !props.globalEnabled ? t('cache.globalOffItemLock') : undefined));

const d = computed(() => props.detail);
const pending = computed(() => !!d.value && UtilCache.isPending(d.value));
const stuck = computed(() => !!d.value && UtilCache.isStuck(d.value, props.now));
/** 이 가젯 유형을 놓은 가젯 수(모든 쪽 합) */
const placementTotal = computed(() => d.value?.gadgetPlacements.reduce((sum, u) => sum + u.count, 0) ?? 0);
/** 미리보기 대표 줄 — 첫 화면 영역·동작 줄 중 열 수 있는 것(예시 경로 포함) */
const representative = computed(() => (d.value ? UtilCache.representativeUsage(d.value) : null));

/** 쓰이는 곳 한 줄의 「미리보기에서 보기」 — 그 줄의 쪽으로(예시 줄은 미리보기가 번호를 고른다) */
function previewAt(usage: CacheUsageView): void {
    if (d.value) emit('preview', d.value, usage);
}

/** 「지금 갱신」이 잠긴 이유 — 권한 → 갱신이 퍼지는 중(오래 걸리면 다시 누를 수 있다). 잠기지 않았으면 undefined */
const refreshLock = computed(() => {
    if (!props.canManage) return t('cache.noManageReason');
    if (d.value && !d.value.effectiveEnabled) return props.globalEnabled ? t('cache.offLockReason') : t('cache.globalOffLockReason');
    if (pending.value && !stuck.value) return t('cache.pendingLockReason');
    return undefined;
});

const summaryItems = computed<DrawerSummaryItem[]>(() => {
    const c = d.value;
    if (!c) return [];
    return [
        { key: 'cacheKey', label: t('cache.cacheKey'), value: c.cacheKey, code: true },
        { key: 'generation', label: t('cache.generation'), value: `#${c.generation}`, code: true },
        { key: 'ttl', label: t('cache.ttl'), value: UtilCacheMessages.ttlText(t, c.ttlSeconds), valueClass: UtilCache.isTtlChanged(c) ? 'font-medium' : '' },
    ];
});
</script>

<style scoped>
/* 한글 글꼴은 글자가 줄 상자 중앙보다 약 0.09em 아래에 앉는다 — 글자만 올려 가운데에 맞춘다(components.md 2-4) */
.cache-small-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
