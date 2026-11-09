<template>
    <!--
        캐시 적용 영역 미리보기 — 캐시 관리 머리의 「미리보기」와 상세 서랍 「쓰이는 곳」의 「미리보기에서 보기」가 연다. 실제 공개 화면(`CacheLiveFrame` — iframe) 위에 캐시 영역 상자를 겹쳐 보인다.
        오른쪽 칸: 쪽 고르기(서버가 준 쪽 + 캐시들의 쓰이는 곳 경로 + 직접 경로) · 「모든 영역 보기」 · 캐시 고르기(목록과 같은 캐시들 — 상태가 같이 바뀐다) · 고른 캐시 요약 · 이 쪽에서 찾은 영역.
        캐시를 고르면(오른쪽 칸에서) 지금 쪽이 그 캐시의 쓰이는 곳이 아니면 대표 쪽(첫 화면 영역·동작 줄의 경로)으로 옮긴다. iframe 의 상자를 누르면 그 캐시를 고른다(쪽은 그대로).
        고른 캐시의 「이 캐시가 보이는 쪽」 단추로 다른 쓰이는 곳을 연다. 글·상품 하나를 열어야 보이는 곳(예시 경로)은 서버가 고른 예시 번호(`sample.sampleId`)로 열고(없으면 기존 관리자 API 로 고른다 — `useCacheSamples`), 「예시」 칸에서 「다른 예시」·번호 직접 넣기로 바꾼다.
        사이트 팝업은 고른 캐시가 `site.popups` 일 때만 공개 사이트가 띄운다(모든 영역 보기에서는 숨기고, 띄울 팝업 수만 「이 쪽에서 찾은 영역」에 남는다). 서버 내부만인 캐시는 「공개 화면에 보이는 곳이 없습니다 — 서버에서 이렇게 쓰입니다」와 관련 관리 화면.
        로그인해야 보이는 쪽(내 정보 등)은 iframe 의 로그인 상태(공개 사이트가 알려 준다)가 로그인 안 함이면 안내한다. 서버 호출(갱신·TTL)은 화면이 하고 여기는 이벤트로 올린다(`components.md`).
    -->
    <Dialog
        :visible="visible"
        modal
        :draggable="false"
        :header="t('cache.previewTitle')"
        :style="{ width: 'min(100rem, calc(100vw - 2rem))' }"
        :content-style="{ paddingBottom: '1.25rem' }"
        @update:visible="(v: boolean) => emit('update:visible', v)"
    >
        <div class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_22rem]">
            <div class="flex min-w-0 flex-col gap-2">
                <Message v-if="loginNotice" severity="warn" size="small" :closable="false" role="status">{{ t('cache.previewLoginOnly') }}</Message>
                <CacheLiveFrame v-if="visible" :path="path" :keys="sentKeys" :show-all="showAll" :labels="labels" :off-keys="offKeys" @pick="onPick" @areas="onAreas" @reloading="onReloading" />
                <p class="m-0 text-[11px] leading-relaxed text-muted-color">{{ t('cache.previewNote') }}</p>
            </div>

            <aside class="flex min-w-0 flex-col gap-4">
                <LabeledField :label="t('cache.previewPage')">
                    <template #default="{ id }">
                        <Select :input-id="id" :model-value="path" :options="pageOptions" option-label="label" option-value="path" filter size="small" fluid @update:model-value="(v: string) => setPath(v)" />
                    </template>
                </LabeledField>
                <form class="-mt-2 flex items-center gap-1.5" @submit.prevent="openCustom">
                    <InputText v-model="customPath" size="small" class="min-w-0 flex-1 font-mono" :placeholder="t('cache.previewCustomPlaceholder')" :aria-label="t('cache.previewCustomPath')" autocomplete="off" spellcheck="false" />
                    <Button type="submit" :label="t('cache.previewOpen')" size="small" severity="secondary" variant="outlined" :disabled="!customValid" />
                </form>

                <div class="flex items-center gap-2">
                    <ToggleSwitch :model-value="showAll" :input-id="showAllId" @update:model-value="(v: boolean) => emit('update:showAll', v)" />
                    <label :for="showAllId" class="cursor-pointer text-[13px]">{{ t('cache.previewShowAll') }}</label>
                </div>

                <LabeledField :label="t('cache.previewCache')">
                    <template #default="{ id }">
                        <Select
                            :input-id="id"
                            :model-value="selectedCache?.cacheKey ?? null"
                            :options="cacheOptions"
                            option-label="label"
                            option-value="value"
                            filter
                            show-clear
                            size="small"
                            fluid
                            :placeholder="t('cache.previewCachePlaceholder')"
                            @update:model-value="onSelectCache"
                        />
                    </template>
                </LabeledField>

                <!-- 고른 캐시 요약 -->
                <section v-if="selectedCache" class="flex flex-col gap-2.5 rounded-lg border border-(--line) p-3" :aria-label="t('cache.previewSelected')">
                    <div class="flex min-w-0 flex-col gap-1">
                        <span class="truncate text-sm font-medium">{{ selectedCache.displayName }}</span>
                        <code class="truncate text-[11px] text-muted-color">{{ selectedCache.cacheKey }}</code>
                    </div>
                    <div class="flex flex-wrap items-center gap-1.5">
                        <CacheKindTag :kind="selectedCache.kind" />
                        <CacheStatusTag :status="selectedCache.status" :enabled-state="UtilCache.enabledState(selectedCache, globalEnabled)" />
                    </div>
                    <dl class="m-0 grid grid-cols-2 gap-2 text-[12px]">
                        <div>
                            <dt class="text-[11px] text-muted-color">{{ t('cache.ttl') }}</dt>
                            <dd class="m-0 tabular-nums">
                                {{ UtilCacheMessages.ttlText(t, selectedCache.ttlSeconds) }}
                                <span v-if="UtilCache.isTtlChanged(selectedCache)" class="text-[11px] text-amber-700 dark:text-amber-400">· {{ t('cache.ttlDefaultIs', { ttl: UtilCacheMessages.ttlText(t, selectedCache.defaultTtlSeconds) }) }}</span>
                            </dd>
                        </div>
                        <div>
                            <dt class="text-[11px] text-muted-color">{{ t('cache.lastRefreshed') }}</dt>
                            <dd class="m-0 tabular-nums">{{ selectedCache.lastRefreshedAt ? time.ago(selectedCache.lastRefreshedAt, now) : t('cache.neverRefreshed') }}</dd>
                        </div>
                    </dl>
                    <p v-if="selectedCache.pendingNodeIds.length" class="m-0 text-[11px] text-muted-color">{{ UtilCacheMessages.pendingNodes(t, selectedCache.pendingNodeIds) }}</p>
                    <div class="flex flex-wrap items-center gap-1.5">
                        <span v-tooltip.top="refreshLock" class="inline-flex">
                            <Button
                                :label="pending ? t('cache.pending') : t('cache.refreshNow')"
                                :icon="pending ? 'pi pi-hourglass' : 'pi pi-sync'"
                                size="small"
                                :disabled="!!refreshLock"
                                :loading="refreshingKey === selectedCache.cacheKey"
                                @click="emit('refresh', selectedCache)"
                            />
                        </span>
                        <span v-tooltip.top="canManage ? undefined : t('cache.noManageReason')" class="inline-flex">
                            <Button :label="t('cache.ttlChange')" icon="pi pi-clock" size="small" severity="secondary" variant="outlined" :disabled="!canManage" @click="emit('ttl', selectedCache)" />
                        </span>
                        <Button :label="t('cache.previewDetail')" icon="pi pi-list" size="small" severity="secondary" variant="text" @click="emit('open-detail', selectedCache.cacheKey)" />
                    </div>

                    <!-- 서버 내부만 — 공개 화면에 보이는 곳이 없다: 어떻게 쓰이는지와 관련 관리 화면 -->
                    <template v-if="UtilCache.serverOnly(selectedCache)">
                        <Message severity="secondary" size="small" :closable="false">{{ t('cache.previewServerOnly') }}</Message>
                        <CacheUsageList :usages="selectedCache.usages" :admin-links="selectedCache.adminLinks" />
                    </template>
                    <template v-else>
                        <Message :severity="selectedCount === 0 ? 'warn' : 'info'" size="small" :closable="false" role="status">{{ UtilCacheMessages.areaCount(t, selectedCount) }}</Message>
                        <!-- 이 캐시가 보이는 쪽 — 쓰이는 곳(화면 영역·동작) 중 경로가 있는 것. 지금 쪽은 진하게 -->
                        <div class="flex flex-col gap-1">
                            <span class="text-[11px] text-muted-color">{{ t('cache.previewCachePages') }}</span>
                            <div class="flex flex-wrap gap-1">
                                <Button
                                    v-for="u in selectedScreens"
                                    :key="`${u.label}-${u.path ?? u.sample?.template}`"
                                    :label="u.label"
                                    :icon="u.kind === 'ACTION' ? 'pi pi-bolt' : 'pi pi-eye'"
                                    size="small"
                                    :severity="UtilCache.usageAt(u, path) ? undefined : 'secondary'"
                                    :variant="UtilCache.usageAt(u, path) ? undefined : 'outlined'"
                                    :title="`${u.path ?? u.sample?.template ?? ''}${u.loginRequired ? ` · ${t('cache.loginRequired')}` : ''}`"
                                    @click="openUsage(u)"
                                />
                            </div>
                        </div>

                        <!-- 예시 — 글·상품 하나를 열어야 보이는 곳의 번호(기존 관리자 API 로 고른 최신 예시, 또는 직접 넣은 번호) -->
                        <div v-for="kind in sampleKinds" :key="kind" class="flex flex-col gap-1.5 rounded-md bg-emphasis px-2.5 py-2">
                            <div class="flex flex-wrap items-center justify-between gap-1.5 text-[12px]">
                                <span class="font-medium">{{ t(`cache.sampleKind.${kind}`) }}</span>
                                <span v-if="samples.state[kind].status === 'loading'" class="inline-flex items-center gap-1 text-muted-color"><i class="pi pi-spin pi-spinner text-[10px]" aria-hidden="true" />{{ t('cache.sampleLoading') }}</span>
                                <code v-else-if="sampleIdOf(kind)" class="text-[11px]">#{{ sampleIdOf(kind) }}</code>
                            </div>
                            <p v-if="samples.state[kind].status === 'empty' || samples.state[kind].status === 'error'" class="m-0 text-[11px] leading-relaxed text-amber-700 dark:text-amber-400" role="status">
                                {{ t(`cache.sampleMissing.${kind}`) }}
                            </p>
                            <div class="flex flex-wrap items-center gap-1.5">
                                <Button
                                    :label="t('cache.sampleNext')"
                                    icon="pi pi-forward"
                                    size="small"
                                    severity="secondary"
                                    variant="text"
                                    :disabled="samples.state[kind].status === 'loading' || (samples.state[kind].status !== 'idle' && samples.state[kind].ids.length < 2 && !samples.state[kind].manual)"
                                    @click="nextSample(kind)"
                                />
                                <form class="flex min-w-0 flex-1 items-center gap-1" @submit.prevent="openSampleId(kind)">
                                    <InputText v-model="sampleInput[kind]" size="small" class="min-w-0 flex-1 font-mono" :placeholder="t('cache.sampleIdPlaceholder')" :aria-label="t('cache.sampleIdLabel', { kind: t(`cache.sampleKind.${kind}`) })" autocomplete="off" spellcheck="false" />
                                    <Button type="submit" :label="t('cache.previewOpen')" size="small" severity="secondary" variant="outlined" :disabled="!UtilCache.validSampleId(sampleInput[kind])" />
                                </form>
                            </div>
                        </div>
                        <div v-if="selectedCache.adminLinks.length" class="flex flex-wrap items-center gap-x-3 gap-y-1 text-[12px]">
                            <span class="text-muted-color">{{ t('cache.adminLinks') }}</span>
                            <NuxtLink v-for="link in selectedCache.adminLinks" :key="link.path" :to="link.path" class="text-primary hover:underline">{{ UtilCacheMessages.adminLinkLabel(t, link) }}</NuxtLink>
                        </div>
                    </template>
                </section>

                <!-- 캐시하지 않는 가젯 상자를 눌렀을 때 -->
                <section v-else-if="selectedExclusion" class="flex flex-col gap-2 rounded-lg border border-dashed border-(--line) p-3" :aria-label="t('cache.previewSelected')">
                    <div class="flex flex-wrap items-center gap-2">
                        <span class="text-sm font-medium">{{ selectedExclusion.displayName }}</span>
                        <Tag :value="t('cache.noCacheTag')" severity="secondary" class="cache-small-tag" />
                    </div>
                    <p class="m-0 text-[12px] leading-relaxed text-muted-color">{{ selectedExclusion.reason }}</p>
                </section>

                <p v-else-if="!showAll" class="m-0 text-[12px] text-muted-color">{{ t('cache.previewPickHint') }}</p>

                <!-- 이 쪽에서 찾은 영역 — 누르면 그 캐시를 고른다 -->
                <section class="flex min-h-0 flex-col gap-1.5" :aria-label="t('cache.previewAreasOnPage')">
                    <h3 class="m-0 text-[12px] font-medium text-muted-color">{{ t('cache.previewAreasOnPage') }}</h3>
                    <p v-if="counts === null" class="m-0 text-[12px] text-muted-color">{{ t('cache.previewCounting') }}</p>
                    <p v-else-if="!areaRows.length" class="m-0 text-[12px] text-muted-color">{{ t('cache.previewNoAreas') }}</p>
                    <ul v-else class="m-0 flex max-h-72 list-none flex-col gap-0.5 overflow-y-auto p-0">
                        <li v-for="row in areaRows" :key="row.key">
                            <button
                                type="button"
                                class="flex w-full items-center justify-between gap-2 rounded px-2 py-1 text-left text-[12px] hover:bg-emphasis"
                                :class="row.key === selectedKey ? 'bg-emphasis font-medium' : ''"
                                :aria-pressed="row.key === selectedKey"
                                @click="emit('update:selectedKey', row.key)"
                            >
                                <span class="min-w-0 truncate" :class="row.nocache ? 'text-muted-color' : ''">{{ row.label }}</span>
                                <span class="shrink-0 tabular-nums text-muted-color">{{ t('cache.previewAreaN', { n: row.count }) }}</span>
                            </button>
                        </li>
                    </ul>
                </section>
            </aside>
        </div>
    </Dialog>
</template>

<script setup lang="ts">
import type { CacheExclusionView, CachePreviewPageView, CacheSampleKind, CacheUsageView, CacheView } from '~/composables/cache/api/useCacheAdminApi.types';
import type { CacheSamples } from '~/composables/cache/useCacheSamples';
import UtilCache from '~/utils/util-cache';
import UtilCacheMessages from '~/utils/util-cache-messages';

interface Props {
    visible: boolean;
    /** 캐시 목록(화면의 목록과 같은 값 — 상태·TTL 이 함께 바뀐다) */
    caches?: CacheView[];
    /** 캐시하지 않는 가젯 종류 */
    exclusions?: CacheExclusionView[];
    /** 고를 수 있는 쪽(서버가 준 것) — 캐시들의 쓰이는 곳 경로를 더해 보인다 */
    pages?: CachePreviewPageView[];
    /** 띄운 쪽 경로 */
    path?: string;
    /** 고른 영역 키 — 캐시 키 또는 `nocache:<종류>`. 없으면 null */
    selectedKey?: string | null;
    /** 모든 영역 보기 */
    showAll?: boolean;
    canManage?: boolean;
    /** 「지금 갱신」 요청을 넣는 중인 캐시 키 */
    refreshingKey?: string | null;
    /** 「몇 초 전」·오래 걸림 판정의 기준 시각 */
    now?: number;
    /** 예시 고르기(글·상품 하나를 열어야 보이는 곳 — 화면이 만든 `useCacheSamples`) */
    samples: CacheSamples;
    /** 전체 캐시 스위치 */
    globalEnabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    caches: () => [],
    exclusions: () => [],
    pages: () => [],
    path: '/',
    selectedKey: null,
    showAll: true,
    canManage: false,
    refreshingKey: null,
    now: 0,
    globalEnabled: true,
});
const emit = defineEmits<{
    'update:visible': [visible: boolean];
    'update:path': [path: string];
    'update:selectedKey': [key: string | null];
    'update:showAll': [showAll: boolean];
    refresh: [cache: CacheView];
    ttl: [cache: CacheView];
    'open-detail': [cacheKey: string];
}>();

const { t } = useI18n();
const time = useDisplayTime();
const showAllId = useId();

/** 이 쪽에서 찾은 영역 수(공개 사이트가 알려 준다) — 쪽을 다시 띄우는 동안은 null */
const counts = ref<Record<string, number> | null>(null);
/** 미리보기 창의 로그인 상태(공개 사이트가 알려 준다) — 모르면 null */
const loggedIn = ref<boolean | null>(null);
const customPath = ref('');

const selectedCache = computed(() => (props.selectedKey ? (props.caches.find((c) => c.cacheKey === props.selectedKey) ?? null) : null));
const selectedExclusion = computed(() => {
    const kind = props.selectedKey ? UtilCache.noCacheKind(props.selectedKey) : null;
    return kind ? (props.exclusions.find((e) => e.gadgetKind === kind) ?? null) : null;
});
/** 꺼진 캐시 — 공개 사이트가 회색 점선으로 「꺼짐」을 구분해 그린다 */
const offKeys = computed(() => props.caches.filter((c) => !c.effectiveEnabled).map((c) => c.cacheKey));
const sentKeys = computed(() => (props.selectedKey ? [props.selectedKey] : []));
const selectedCount = computed(() => (counts.value === null || !props.selectedKey ? null : (counts.value[props.selectedKey] ?? 0)));
const pending = computed(() => !!selectedCache.value && UtilCache.isPending(selectedCache.value));
/** 고른 캐시의 화면 영역·동작 중 열 수 있는 쪽(고정 경로 · 예시 경로) */
const selectedScreens = computed(() => (selectedCache.value ? selectedCache.value.usages.filter(UtilCache.openable) : []));
/** 고른 캐시에 필요한 예시 종류(같은 종류는 한 번) */
const sampleKinds = computed<CacheSampleKind[]>(() => [...new Set(selectedScreens.value.flatMap((u) => (u.sample ? [u.sample.kind] : [])))]);
/** 예시 번호 직접 넣기 칸 */
const sampleInput = reactive<Record<CacheSampleKind, string>>({ HOTDEAL_POST: '', POINT_SHOP_ITEM: '', GROUP_BUY: '' });

// 예시가 필요한 캐시를 고르면 그 종류의 예시를 고른다(처음 한 번 — 기존 관리자 API 호출)
watch(sampleKinds, (kinds) => {
    // 서버가 예시 번호(`sampleId`)를 준 종류는 묻지 않는다 — 없는 종류만 기존 관리자 API 로 고른다
    for (const kind of kinds) if (!selectedScreens.value.some((u) => u.sample?.kind === kind && u.sample.sampleId)) void props.samples.ensure(kind);
}, { immediate: true });
/** 로그인해야 보이는 쪽인데 미리보기 창이 로그인하지 않았다 */
const loginNotice = computed(() => loggedIn.value === false && UtilCache.loginRequiredAt(props.caches, props.path));

/** 이름표 — 캐시 이름, 캐시하지 않는 가젯은 「이름 · 캐시 안 함」 */
const labels = computed<Record<string, string>>(() => {
    const out: Record<string, string> = {};
    for (const c of props.caches) out[c.cacheKey] = c.effectiveEnabled ? c.displayName : `${c.displayName} · ${t('cache.statusOff')}`;
    for (const e of props.exclusions) out[`${UtilCache.NOCACHE_PREFIX}${e.gadgetKind}`] = `${e.displayName} · ${t('cache.noCacheTag')}`;
    return out;
});

const areaRows = computed(() =>
    Object.entries(counts.value ?? {})
        .map(([key, count]) => ({ key, count, label: labels.value[key] ?? key, nocache: UtilCache.noCacheKind(key) !== null }))
        // 캐시 영역을 먼저, 그다음 목록 차례
        .sort((a, b) => Number(a.nocache) - Number(b.nocache) || order(a.key) - order(b.key)),
);

function order(key: string): number {
    const i = props.caches.findIndex((c) => c.cacheKey === key);
    return i < 0 ? Number.MAX_SAFE_INTEGER : i;
}

/** 쪽 후보 — 서버가 준 쪽 → 캐시들의 쓰이는 곳 경로(같은 경로는 한 번) → 지금 쪽이 어디에도 없으면 「직접 입력」 */
const pageOptions = computed(() => {
    const out: CachePreviewPageView[] = [...props.pages];
    const seen = new Set(out.map((p) => p.path));
    for (const c of props.caches) {
        for (const u of UtilCache.screenUsages(c)) {
            const p = props.samples.pathFor(u);
            if (!p || seen.has(p)) continue;
            seen.add(p);
            out.push({ path: p, label: u.loginRequired ? `${u.label} (${t('cache.loginRequired')})` : u.label });
        }
    }
    if (!seen.has(props.path)) out.push({ path: props.path, label: t('cache.previewCustomLabel', { path: props.path }) });
    return out;
});
const customValid = computed(() => UtilCache.cleanPath(customPath.value) !== null);

const cacheOptions = computed(() =>
    props.caches.map((c) => ({ value: c.cacheKey, label: `${c.displayName} · ${UtilCacheMessages.kindLabel(t, c.kind)}${UtilCache.isPending(c) ? ` · ${t('cache.statusPending')}` : ''}` })),
);

/** 「지금 갱신」이 잠긴 이유 — 목록과 같은 규칙(권한 → 퍼지는 중, 오래 걸리면 풀림) */
const refreshLock = computed(() => {
    const c = selectedCache.value;
    if (!props.canManage) return t('cache.noManageReason');
    if (c && !c.effectiveEnabled) return props.globalEnabled ? t('cache.offLockReason') : t('cache.globalOffLockReason');
    if (c && UtilCache.isPending(c) && !UtilCache.isStuck(c, props.now)) return t('cache.pendingLockReason');
    return undefined;
});

function setPath(path: string): void {
    if (path === props.path) return;
    emit('update:path', path);
}

function openCustom(): void {
    const path = UtilCache.cleanPath(customPath.value);
    if (!path) return;
    setPath(path);
    customPath.value = '';
}

/** 오른쪽 칸에서 캐시를 고르면 — 지금 쪽이 그 캐시의 쓰이는 곳이 아니면 대표 쪽(예시 경로면 예시를 골라)으로 옮긴다 */
async function onSelectCache(key: string | null): Promise<void> {
    emit('update:selectedKey', key);
    const cache = key ? props.caches.find((c) => c.cacheKey === key) : undefined;
    if (!cache) return;
    const here = UtilCache.screenUsages(cache).some((u) => UtilCache.usageAt(u, props.path));
    const target = UtilCache.representativeUsage(cache);
    if (here || !target) return;
    const path = await props.samples.resolve(target);
    if (path) setPath(path);
}

/** 이 캐시가 보이는 쪽 단추 — 고정 경로는 바로, 예시 경로는 예시를 골라 연다(못 고르면 예시 칸이 안내한다) */
async function openUsage(usage: CacheUsageView): Promise<void> {
    const path = await props.samples.resolve(usage);
    if (path) setPath(path);
}

/** 지금 띄운 쪽이 이 종류의 예시 쪽이면 그 줄, 아니면 이 종류의 첫 줄 — 예시를 바꾼 뒤 다시 열 쪽 */
function sampleUsageOf(kind: CacheSampleKind): CacheUsageView | undefined {
    const rows = selectedScreens.value.filter((u) => u.sample?.kind === kind);
    return rows.find((u) => UtilCache.usageAt(u, props.path)) ?? rows[0];
}

/** 「다른 예시」 — 다음 예시로 바꾸고 그 쪽을 다시 연다 */
/** 지금 쓰는 예시 번호(그 종류의 줄 기준) */
function sampleIdOf(kind: CacheSampleKind): string | null {
    const usage = sampleUsageOf(kind);
    return usage ? props.samples.idFor(usage) : null;
}

async function nextSample(kind: CacheSampleKind): Promise<void> {
    await props.samples.next(kind);
    const usage = sampleUsageOf(kind);
    const path = usage ? props.samples.pathFor(usage) : null;
    if (path) setPath(path);
}

/** 번호를 직접 넣어 연다 */
function openSampleId(kind: CacheSampleKind): void {
    const id = sampleInput[kind];
    if (!UtilCache.validSampleId(id)) return;
    props.samples.setManual(kind, id);
    sampleInput[kind] = '';
    const usage = sampleUsageOf(kind);
    const path = usage ? props.samples.pathFor(usage) : null;
    if (path) setPath(path);
}

/** iframe 의 상자를 누르면 그 캐시를 고른다(쪽은 그대로) */
function onPick(key: string): void {
    emit('update:selectedKey', key);
}

function onAreas(next: Record<string, number>, login: boolean | null): void {
    counts.value = next;
    loggedIn.value = login;
}

function onReloading(): void {
    counts.value = null;
    loggedIn.value = null;
}
</script>

<style scoped>
/* 한글 글꼴은 글자가 줄 상자 중앙보다 약 0.09em 아래에 앉는다 — 글자만 올려 가운데에 맞춘다(components.md 2-4) */
.cache-small-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
