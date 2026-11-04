<template>
    <!--
        통계 수집(`/settings/analytics`) — 방문 분석·알림 발송 기록·게시판 활동 집계를 항목별로 켜고 끈다. **모든 항목은 기본 꺼짐**(사용자 결정 2026-11-04 「필요하면 켤 수 있게」).
        구획: ① 수집 방식(방문자 구분·늦음·마지막 변경 — 내비에 올리지 않는다) ② 방문 수집(전체 스위치 + 방문자·페이지뷰 / 원본 기록(+ 보관 기간) / 유입 경로 / 이동 흐름)
        ③ 알림·게시판 집계(알림 발송 기록 · 게시판 활동 집계). 항목마다 부담 꼬리표와 「쓰는 곳」(대시보드 가젯 — 종류표 `collect` 에서 읽는다).
        - 방문 하위 항목은 방문 수집이 꺼지면 잠긴다(값은 그대로 저장). 원본 기록을 켜면 개인정보 처리방침 안내 + 약관 관리 링크(`terms.read` 가 있을 때).
        - 항목 줄의 `id` = `UtilAnalytics.anchor`(`analytics-raw-events` …) — 대시보드 가젯의 「수집 꺼짐」 링크가 `#앵커` 로 들어오면 불러온 뒤 그 줄로 옮기고 잠깐 강조한다.
        - 저장은 한 번에(PUT) — 떠 있는 저장 줄(`StickySaveBar`). 입력 오류는 `fields.rawRetentionDays` 를 그 칸 아래, 나머지는 저장 줄에 서버 문구 그대로.
        - 변경 이력은 제목 옆 단추 → 오른쪽 서랍(`SettingHistoryDrawer`, 처음 열 때 받는다).
        - 권한: 조회 `site.read`(없으면 권한 없음) · 바꾸기 `site.manage` — 없으면 스위치가 잠기고 저장 줄이 없다. 감추는 것은 편의이고 막는 것은 서버다.
        - 메뉴: 설정 › 일반의 「통계 수집」(약관 다음 — `UtilAdminMenu` 의 `analyticsSettings` 와 서버 `AdminScreenRegistry` 의 같은 한 줄).
        서버 API: `useAnalyticsSettingsApi`(`GET/PUT /api/admin/analytics/settings` · `GET …/history`).
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('analytics.title')" :subtitle="t('analytics.subtitle')">
            <template v-if="canRead" #actions>
                <Button :label="t('settingHistory.open')" icon="pi pi-history" severity="secondary" outlined size="small" @click="openHistory" />
            </template>
        </PageTitle>

        <ErrorState v-if="!canRead" code="403" :title="t('shell.forbiddenTitle')" :description="t('analytics.forbiddenBody')" />

        <template v-else>
            <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('analytics.readOnly') }}</Message>

            <div v-if="loading && !saved" class="flex flex-col gap-4" role="status" :aria-label="t('common.loading')">
                <AppGadget v-for="n in 2" :key="n" :title="t('common.loading')">
                    <div class="flex flex-col gap-3">
                        <Skeleton height="0.75rem" width="60%" />
                        <Skeleton height="2.25rem" width="min(36rem, 100%)" />
                        <Skeleton height="2.25rem" width="min(36rem, 100%)" />
                    </div>
                </AppGadget>
            </div>

            <AppGadget v-else-if="!saved" :title="t('analytics.title')">
                <ErrorState :code="loadError?.status === 403 ? '403' : '!'" :title="t('analytics.loadFailed')" :description="loadError?.message" :retry="loadError?.status !== 403" :retrying="loading" @retry="load" />
            </AppGadget>

            <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="save">
                <!-- ① 수집 방식 -->
                <AppGadget :title="t('analytics.howTitle')">
                    <div class="flex flex-col gap-3">
                        <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-sm leading-relaxed">
                            <li v-for="key in HOW_KEYS" :key="key" class="flex items-start gap-2">
                                <i class="pi pi-info-circle mt-1 text-xs text-muted-color" aria-hidden="true" /><span>{{ t(`analytics.${key}`) }}</span>
                            </li>
                        </ul>
                        <p class="m-0 text-xs text-muted-color">
                            {{ saved.updatedAt ? t('analytics.updatedBy', { at: time.full(saved.updatedAt), by: saved.updatedBy ?? '—' }) : t('analytics.neverUpdated') }}
                        </p>
                    </div>
                </AppGadget>

                <!-- ② 방문 수집 -->
                <AppGadget :title="t('analytics.visitSection')" anchor="analytics-visit-section" :changes="changesIn(VISIT_SECTION)" :invalid="serverErrors.rawRetentionDays ? 1 : 0">
                    <ul class="m-0 flex list-none flex-col gap-1 p-0">
                        <li v-for="key in VISIT_SECTION" :id="UtilAnalytics.anchor(key)" :key="key" :class="rowClass(key)">
                            <div v-if="key === 'visits' && !form.enabled" class="mb-2 flex items-start gap-2 text-xs text-amber-700 dark:text-amber-400">
                                <i class="pi pi-exclamation-triangle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('analytics.visitOffNote') }}</span>
                            </div>
                            <div class="flex items-start gap-3">
                                <ToggleSwitch v-model="form[key]" :input-id="inputId(key)" :disabled="!canManage || saving || locked(key)" :aria-describedby="`${inputId(key)}-help`" />
                                <div class="flex min-w-0 flex-1 flex-col gap-1">
                                    <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                                        <label :for="inputId(key)" class="text-sm font-medium" :class="locked(key) ? 'text-muted-color' : canManage ? 'cursor-pointer' : ''">{{ t(`analytics.label.${key}`) }}</label>
                                        <template v-if="UtilAnalytics.load(key)">
                                            <span class="inline-flex items-center rounded px-1.5 py-1 text-[11px] leading-none" :class="UtilAnalytics.load(key) === 'MEDIUM' ? 'bg-amber-50 text-amber-800 dark:bg-amber-950 dark:text-amber-300' : 'bg-surface-100 text-muted-color dark:bg-surface-800'">
                                                {{ UtilAnalytics.load(key) === 'MEDIUM' ? t('analytics.loadMedium') : t('analytics.loadLow') }}
                                            </span>
                                            <span class="text-[11px] text-muted-color">{{ t(`analytics.loadNote.${key}`) }}</span>
                                        </template>
                                    </div>
                                    <small :id="`${inputId(key)}-help`" class="text-xs leading-relaxed text-muted-color">{{ t(`analytics.help.${key}`) }}</small>
                                    <p v-if="key !== 'enabled'" class="m-0 flex flex-wrap items-center gap-1.5 text-[11px] text-muted-color">
                                        <span>{{ t('analytics.usedBy') }}</span>
                                        <span v-for="name in usedBy(key)" :key="name" class="inline-flex items-center rounded bg-surface-100 px-1.5 py-1 leading-none dark:bg-surface-800">{{ name }}</span>
                                    </p>

                                    <!-- 원본 기록 — 보관 기간 + 처리방침 안내 -->
                                    <div v-if="key === 'rawEvents'" class="mt-2 flex flex-col gap-3">
                                        <div class="w-full max-w-60">
                                            <LabeledField :label="t('analytics.retentionLabel')" :help="t('analytics.retentionHelp')" :error="serverErrors.rawRetentionDays ?? ''">
                                                <template #default="{ id, describedBy, invalid }">
                                                    <Select
                                                        v-model="form.rawRetentionDays"
                                                        :input-id="id"
                                                        :options="retentionOptions"
                                                        option-label="label"
                                                        option-value="value"
                                                        :disabled="!canManage || saving || locked('rawEvents') || !form.rawEvents"
                                                        :invalid="invalid"
                                                        :aria-describedby="describedBy"
                                                        class="w-full"
                                                        @update:model-value="clearServer"
                                                    />
                                                </template>
                                            </LabeledField>
                                        </div>
                                        <Message v-if="form.rawEvents" severity="warn" :closable="false" size="small">
                                            {{ t('analytics.privacyNotice') }}
                                            <NuxtLink v-if="can('terms.read')" to="/settings/terms" class="font-medium text-primary hover:underline">{{ t('analytics.privacyLink') }}</NuxtLink>
                                        </Message>
                                    </div>
                                </div>
                            </div>
                        </li>
                    </ul>
                </AppGadget>

                <!-- ③ 알림·게시판 집계 -->
                <AppGadget :title="t('analytics.otherSection')" anchor="analytics-other-section" :changes="changesIn(OTHER_SECTION)">
                    <ul class="m-0 flex list-none flex-col gap-1 p-0">
                        <li v-for="key in OTHER_SECTION" :id="UtilAnalytics.anchor(key)" :key="key" :class="rowClass(key)">
                            <div class="flex items-start gap-3">
                                <ToggleSwitch v-model="form[key]" :input-id="inputId(key)" :disabled="!canManage || saving" :aria-describedby="`${inputId(key)}-help`" />
                                <div class="flex min-w-0 flex-1 flex-col gap-1">
                                    <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                                        <label :for="inputId(key)" class="text-sm font-medium" :class="canManage ? 'cursor-pointer' : ''">{{ t(`analytics.label.${key}`) }}</label>
                                        <span class="inline-flex items-center rounded bg-surface-100 px-1.5 py-1 text-[11px] leading-none text-muted-color dark:bg-surface-800">{{ t('analytics.loadLow') }}</span>
                                        <span class="text-[11px] text-muted-color">{{ t(`analytics.loadNote.${key}`) }}</span>
                                    </div>
                                    <small :id="`${inputId(key)}-help`" class="text-xs leading-relaxed text-muted-color">{{ t(`analytics.help.${key}`) }}</small>
                                    <p class="m-0 flex flex-wrap items-center gap-1.5 text-[11px] text-muted-color">
                                        <span>{{ t('analytics.usedBy') }}</span>
                                        <span v-for="name in usedBy(key)" :key="name" class="inline-flex items-center rounded bg-surface-100 px-1.5 py-1 leading-none dark:bg-surface-800">{{ name }}</span>
                                    </p>
                                </div>
                            </div>
                        </li>
                    </ul>
                </AppGadget>

                <StickySaveBar v-if="canManage" submit revertable :dirty="dirty" :saving="saving" :count="changedCount" :error-count="serverErrors.rawRetentionDays ? 1 : 0" :error="formError" @revert="revert" />
            </form>
        </template>

        <!-- 변경 이력 — 제목 옆 단추가 오른쪽 서랍으로 연다(최신이 위) -->
        <SettingHistoryDrawer v-model:visible="historyOpen" :hint="t('analytics.historyHint')">
            <SettingHistoryTable
                :items="history.rows.value"
                :total="history.total.value"
                :first="history.first.value"
                :page-size="history.size.value"
                :loading="history.loading.value"
                :loaded="history.loaded.value"
                :error="history.error.value"
                :compact="prefs.density === 'compact'"
                @page="history.onPage"
                @retry="history.load"
            />
        </SettingHistoryDrawer>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { AnalyticsSettingsRequest, AnalyticsSettingsView, AnalyticsSwitch } from '~/composables/analytics/api/useAnalyticsSettingsApi.types';
import { ANALYTICS_RETENTION_DAYS, ANALYTICS_VISIT_SWITCHES } from '~/composables/analytics/api/useAnalyticsSettingsApi.types';
import UtilAnalytics from '~/utils/util-analytics';
import UtilDashboardLayout from '~/utils/util-dashboard-layout';

/** 구획별 항목(화면 차례) */
const VISIT_SECTION: readonly AnalyticsSwitch[] = ['enabled', ...ANALYTICS_VISIT_SWITCHES];
const OTHER_SECTION: readonly AnalyticsSwitch[] = ['notificationSends', 'boardActivity'];
/** 수집 방식 안내 줄 */
const HOW_KEYS = ['howVisitorId', 'howDelayVisits', 'howDelayBoard', 'howDefaultOff'] as const;
/** 저장 값의 칸(바뀐 칸 세기) */
const FIELDS: readonly (keyof AnalyticsSettingsRequest)[] = ['enabled', 'visits', 'rawEvents', 'rawRetentionDays', 'referrers', 'flows', 'notificationSends', 'boardActivity'];

const { t } = useI18n();
useSeoMeta({ title: () => t('analytics.title') });

const api = useAnalyticsSettingsApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const route = useRoute();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다
const { prefs } = useLayoutPrefs();

/** 조회·바꾸기 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canRead = computed(() => can('site.read'));
const canManage = computed(() => can('site.manage'));

const baseId = useId();
const saved = ref<AnalyticsSettingsView | null>(null);
const form = ref<AnalyticsSettingsRequest>(formOf(null));
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');
const serverErrors = ref<{ rawRetentionDays?: string }>({});
/** `#앵커` 로 들어와 잠깐 강조하는 줄 */
const highlight = ref<AnalyticsSwitch | null>(null);

const retentionOptions = computed(() => ANALYTICS_RETENTION_DAYS.map((n) => ({ value: n, label: t('analytics.retentionDays', { n }) })));

function formOf(view: AnalyticsSettingsView | null): AnalyticsSettingsRequest {
    return {
        enabled: view?.enabled ?? false,
        visits: view?.visits ?? false,
        rawEvents: view?.rawEvents ?? false,
        rawRetentionDays: view?.rawRetentionDays ?? 90,
        referrers: view?.referrers ?? false,
        flows: view?.flows ?? false,
        notificationSends: view?.notificationSends ?? false,
        boardActivity: view?.boardActivity ?? false,
    };
}

function inputId(key: AnalyticsSwitch): string {
    return `${baseId}-${key}`;
}

/** 방문 하위 항목은 방문 수집이 꺼지면 잠근다(값은 그대로) */
function locked(key: AnalyticsSwitch): boolean {
    return key !== 'enabled' && (ANALYTICS_VISIT_SWITCHES as readonly string[]).includes(key) && !form.value.enabled;
}

function rowClass(key: AnalyticsSwitch): string[] {
    return [
        'scroll-mt-(--section-nav-offset) rounded-lg px-3 py-3 transition-colors duration-500',
        key !== 'enabled' && (ANALYTICS_VISIT_SWITCHES as readonly string[]).includes(key) ? 'ml-4 border-l-2 border-(--line) rounded-l-none' : '',
        highlight.value === key ? 'bg-primary-50 dark:bg-primary-950' : '',
    ];
}

/** 이 항목의 데이터를 읽는 대시보드 가젯 이름(종류표 `collect`) — 없으면 다음 단계 화면 */
function usedBy(key: AnalyticsSwitch): string[] {
    const names = UtilDashboardLayout.KINDS.filter((k) => k.collect === key).map((k) => t(`dashboard.kind.${k.type}`));
    return names.length ? names : [t('analytics.usedByFlows')];
}

const changedKeys = computed(() => {
    const base = saved.value;
    if (!base) return [];
    return FIELDS.filter((k) => form.value[k] !== base[k]);
});
const changedCount = computed(() => changedKeys.value.length);
const dirty = computed(() => changedCount.value > 0);

/** 구획 안 바뀐 칸 수(보관 기간은 방문 구획) */
function changesIn(section: readonly AnalyticsSwitch[]): number {
    return changedKeys.value.filter((k) => (k === 'rawRetentionDays' ? section.includes('rawEvents') : section.includes(k))).length;
}

function clearServer(): void {
    if (serverErrors.value.rawRetentionDays) serverErrors.value = {};
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        applySaved(await api.settings());
        await nextTick();
        focusHash();
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

/** 대시보드 「수집 꺼짐」 링크(`#analytics-…`)로 들어왔으면 그 줄로 옮기고 잠깐 강조한다(내용이 늦게 그려져 라우터의 해시 이동이 못 찾는다) */
function focusHash(): void {
    const hash = decodeURIComponent(route.hash.slice(1));
    const key = FIELDS.find((k): k is AnalyticsSwitch => k !== 'rawRetentionDays' && UtilAnalytics.anchor(k) === hash);
    if (!key) return;
    document.getElementById(hash)?.scrollIntoView({ block: 'center', behavior: 'smooth' });
    document.getElementById(inputId(key))?.focus({ preventScroll: true });
    highlight.value = key;
    setTimeout(() => {
        if (highlight.value === key) highlight.value = null;
    }, 2400);
}

function applySaved(view: AnalyticsSettingsView): void {
    saved.value = view;
    form.value = formOf(view);
    serverErrors.value = {};
    formError.value = '';
}

function revert(): void {
    if (saved.value) applySaved(saved.value);
}

async function save(): Promise<void> {
    if (saving.value || !dirty.value || !canManage.value) return;
    const f = form.value;
    // 서버에는 반응형 객체가 아니라 평범한 값으로 넘긴다
    const body: AnalyticsSettingsRequest = {
        enabled: f.enabled,
        visits: f.visits,
        rawEvents: f.rawEvents,
        rawRetentionDays: f.rawRetentionDays,
        referrers: f.referrers,
        flows: f.flows,
        notificationSends: f.notificationSends,
        boardActivity: f.boardActivity,
    };
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        applySaved(await api.save(body));
        toast.success(t('analytics.saved'));
        if (history.loaded.value) void history.reloadFirst();
    } catch (e) {
        const failure = toApiError(e);
        const retention = failure.field('rawRetentionDays');
        if (failure.code === 'VALIDATION' && retention) {
            serverErrors.value = { rawRetentionDays: retention };
            formError.value = t('analytics.saveFieldsRejected');
        } else formError.value = failure.message;
    } finally {
        saving.value = false;
    }
}

/* ── 이력 ── */

const history = useSettingHistory((page, size) => api.history({ page, size }));
/** 이력 서랍 — 처음 열 때 받는다(화면을 열 때마다 이력을 부르지 않는다) */
const historyOpen = ref(false);
function openHistory(): void {
    historyOpen.value = true;
    if (!history.loaded.value && !history.loading.value) void history.load();
}

onMounted(() => {
    if (canRead.value) void load();
});

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('analytics.leaveTitle'), message: t('analytics.leaveMessage'), confirmLabel: t('analytics.leaveConfirm'), tone: 'danger' });
});
</script>
