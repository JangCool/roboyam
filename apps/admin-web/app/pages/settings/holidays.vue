<template>
    <!--
        휴일 관리(`/settings/holidays`) — 사이트의 쉬는 날은 서버 표 하나다(출처 `PUBLIC_DATA` 공공데이터 · `SEED` 기본값 · `OPERATOR` 운영자가 넣은 회사 휴무일).
        모든 달력의 휴일 표시(레이어 `useHolidays`)와 공구 영업일 계산이 이 표를 쓴다. 공휴일은 공공데이터(한국천문연구원 특일 정보)에서 매월 받는다.
          ① 공공데이터 받기 — 마지막으로 받은 때·결과(받은 해·바뀐 수·받지 못한 해)와 「지금 받기」. 인증키가 없으면(`keyConfigured=false`) 공공데이터 설정으로 가는 안내.
          ② 해별 목록 — 해 고르기(올해 기본, 앞뒤 해). 날짜·이름·출처 딱지·대체공휴일. 지우기는 회사 휴무일(OPERATOR) 줄만 — 공공데이터·기본값은 읽기만.
          ③ 회사 휴무일 더하기 — 날짜(`AppDatePicker`) + 이름(1~30자, 필수). 서버 칸 오류(`fields.date`·`fields.name`)는 그 칸 아래에 그대로.
        더하거나 지우거나 받으면 그 해의 달력 표시 캐시를 다시 받는다(`useHolidaySource().invalidate`).
        권한: 조회 `site.read`(메뉴가 거른다), 바꾸기(더하기·지우기·지금 받기) `site.manage` — 없으면 바꾸기 단추·더하기 구획이 사라지고 읽기 전용 안내. 감추는 것은 편의이고 막는 것은 서버다.
        서버 API: `useHolidayApi`.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('holiday.title')" :subtitle="t('holiday.subtitle')" />

        <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('holiday.readOnly') }}</Message>

        <div v-if="loading && !view" class="flex flex-col gap-4" role="status" :aria-label="t('common.loading')">
            <AppGadget v-for="n in 2" :key="n" :title="t('common.loading')">
                <div class="flex flex-col gap-3">
                    <Skeleton height="0.75rem" width="60%" />
                    <Skeleton height="2.25rem" width="min(36rem, 100%)" />
                </div>
            </AppGadget>
        </div>

        <AppGadget v-else-if="!view" :title="t('holiday.title')">
            <ErrorState :code="loadError?.status === 403 ? '403' : '!'" :title="t('holiday.loadFailed')" :description="loadError?.message" :retry="loadError?.status !== 403" :retrying="loading" @retry="load(year)" />
        </AppGadget>

        <template v-else>
            <!-- ① 공공데이터 받기 -->
            <AppGadget :title="t('holiday.syncSection')" anchor="holiday-sync">
                <div class="flex max-w-2xl flex-col gap-3">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('holiday.syncLead') }}</p>

                    <Message v-if="!view.keyConfigured" severity="warn" :closable="false" size="small" data-testid="holiday-no-key">
                        <b>{{ t('holiday.noKeyTitle') }}</b>
                        <span class="mt-0.5 block">{{ t('holiday.noKeyText') }}</span>
                        <NuxtLink to="/settings/public-data" class="mt-1 inline-block font-semibold underline">{{ t('holiday.noKeyLink') }}</NuxtLink>
                    </Message>

                    <p v-if="!view.sync" class="m-0 text-sm text-muted-color">{{ t('holiday.syncNever') }}</p>
                    <dl v-else class="m-0 grid gap-x-6 gap-y-2 rounded-lg border border-(--line) p-3 sm:grid-cols-[auto_1fr]" data-testid="holiday-sync-result">
                        <dt class="text-xs text-muted-color">{{ t('holiday.syncRanAt') }}</dt>
                        <dd class="m-0 text-sm tabular-nums">
                            {{ view.sync.ranBy ? t('holiday.syncRanBy', { at: time.full(view.sync.ranAt), name: view.sync.ranBy }) : t('holiday.syncAuto', { at: time.full(view.sync.ranAt) }) }}
                        </dd>
                        <dt class="text-xs text-muted-color">{{ t('holiday.syncResult') }}</dt>
                        <dd class="m-0"><Tag class="holiday-tag" :value="t(`holiday.outcome.${view.sync.outcome}`)" :severity="OUTCOME_SEVERITY[view.sync.outcome]" /></dd>
                        <dt class="text-xs text-muted-color">{{ t('holiday.syncApplied') }}</dt>
                        <dd class="m-0 text-sm tabular-nums">{{ view.sync.appliedYears.length ? view.sync.appliedYears.join(', ') : t('holiday.syncNone') }}</dd>
                        <dt class="text-xs text-muted-color">{{ t('holiday.syncChanged') }}</dt>
                        <dd class="m-0 text-sm tabular-nums">{{ t('holiday.syncChangedCount', { count: view.sync.changed.toLocaleString(locale) }) }}</dd>
                        <dt class="text-xs text-muted-color">{{ t('holiday.syncFailed') }}</dt>
                        <dd class="m-0 text-sm tabular-nums" :class="view.sync.failedYears.length ? 'text-red-600 dark:text-red-400' : ''">{{ view.sync.failedYears.length ? view.sync.failedYears.join(', ') : t('holiday.syncNone') }}</dd>
                    </dl>

                    <div v-if="canManage">
                        <Button type="button" :label="t('holiday.syncNow')" icon="pi pi-refresh" severity="secondary" variant="outlined" size="small" :loading="working === 'sync'" :disabled="working !== null" data-testid="holiday-sync-now" @click="syncNow" />
                    </div>
                </div>
            </AppGadget>

            <!-- ② 해별 목록 -->
            <AppGadget :title="t('holiday.listSection', { year })" anchor="holiday-list">
                <template #actions>
                    <div class="flex items-center gap-1">
                        <Button v-tooltip.top="t('holiday.prevYear')" type="button" icon="pi pi-chevron-left" text size="small" severity="secondary" :aria-label="t('holiday.prevYear')" :disabled="loading" @click="go(year - 1)" />
                        <span class="min-w-12 text-center text-sm font-semibold tabular-nums" aria-live="polite">{{ year }}</span>
                        <Button v-tooltip.top="t('holiday.nextYear')" type="button" icon="pi pi-chevron-right" text size="small" severity="secondary" :aria-label="t('holiday.nextYear')" :disabled="loading" @click="go(year + 1)" />
                        <Button v-if="year !== thisYear" type="button" :label="t('holiday.thisYear')" size="small" severity="secondary" variant="outlined" :disabled="loading" @click="go(thisYear)" />
                    </div>
                </template>

                <ErrorState v-if="loadError" :title="t('holiday.loadFailed')" :description="loadError.message" retry :retrying="loading" @retry="load(year)" />
                <DataTable v-else :value="view.rows" data-key="id" :loading="loading" :size="prefs.density === 'compact' ? 'small' : undefined" class="min-w-0" data-testid="holiday-table">
                    <template #empty>
                        <EmptyState icon="pi pi-calendar" :title="t('holiday.empty')" :description="t('holiday.emptyHint')" />
                    </template>
                    <Column :header="t('holiday.colDate')" style="width: 11rem">
                        <template #body="{ data }">
                            <span class="tabular-nums" :class="data.source === 'OPERATOR' ? UtilHoliday.COMPANY_CLASS : UtilHoliday.HOLIDAY_CLASS">{{ time.serviceDate(data.date) }}</span>
                            <span class="ml-1 text-xs text-muted-color">({{ weekdayOf(data.date) }})</span>
                        </template>
                    </Column>
                    <Column :header="t('holiday.colName')" style="min-width: 12rem">
                        <template #body="{ data }">
                            <span class="flex flex-wrap items-center gap-1.5">
                                <span>{{ data.name }}</span>
                                <Tag v-if="data.substitute" class="holiday-tag" :value="t('holiday.substitute')" severity="secondary" />
                            </span>
                            <span v-if="data.source === 'OPERATOR' && data.createdBy" class="mt-0.5 block text-xs text-muted-color">{{ t('holiday.addedBy', { name: data.createdBy }) }}</span>
                        </template>
                    </Column>
                    <Column :header="t('holiday.colSource')" style="width: 9rem">
                        <template #body="{ data }">
                            <Tag class="holiday-tag" :value="t(`holiday.source.${data.source}`)" :severity="SOURCE_SEVERITY[data.source as HolidaySource]" />
                        </template>
                    </Column>
                    <Column v-if="canManage" style="width: 6rem" body-class="text-right">
                        <template #body="{ data }">
                            <Button
                                v-if="data.source === 'OPERATOR'"
                                type="button"
                                :label="t('holiday.remove')"
                                icon="pi pi-trash"
                                size="small"
                                severity="danger"
                                variant="text"
                                :aria-label="t('holiday.removeAria', { date: time.serviceDate(data.date), name: data.name })"
                                :loading="working === `remove:${data.id}`"
                                :disabled="working !== null"
                                @click="remove(data)"
                            />
                            <i v-else v-tooltip.left="t('holiday.readOnlyRow')" class="pi pi-lock text-xs text-muted-color" :aria-label="t('holiday.readOnlyRow')" />
                        </template>
                    </Column>
                </DataTable>
            </AppGadget>

            <!-- ③ 회사 휴무일 더하기 -->
            <form v-if="canManage" novalidate @submit.prevent="add">
                <AppGadget :title="t('holiday.addSection')" anchor="holiday-add">
                    <div class="flex max-w-2xl flex-col gap-4">
                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('holiday.addLead') }}</p>
                        <div class="flex flex-col gap-4 sm:flex-row sm:items-start">
                            <LabeledField :label="t('holiday.addDate')" :error="dateError" class="sm:w-48">
                                <template #default="{ id, describedBy, invalid }">
                                    <AppDatePicker v-model="draftDate" :input-id="id" :invalid="invalid" :described-by="describedBy" :disabled="working !== null" @update:model-value="clearField('date')" />
                                </template>
                            </LabeledField>
                            <LabeledField :label="t('holiday.addName')" :hint="t('holiday.addNameHint')" :error="nameError" class="min-w-0 flex-1">
                                <template #default="{ id, describedBy, invalid }">
                                    <InputText
                                        :id="id"
                                        v-model="draftName"
                                        name="name"
                                        autocomplete="off"
                                        :maxlength="NAME_MAX"
                                        :placeholder="t('holiday.addNamePlaceholder')"
                                        :invalid="invalid"
                                        :aria-describedby="describedBy"
                                        aria-required="true"
                                        :disabled="working !== null"
                                        fluid
                                        @update:model-value="clearField('name')"
                                    />
                                </template>
                            </LabeledField>
                        </div>
                        <Message v-if="formError" severity="error" variant="simple" size="small">{{ formError }}</Message>
                        <div class="flex justify-end">
                            <Button type="submit" :label="t('holiday.addSubmit')" icon="pi pi-plus" :loading="working === 'add'" :disabled="working !== null" />
                        </div>
                    </div>
                </AppGadget>
            </form>
        </template>
    </div>
</template>

<script setup lang="ts">
import UtilHoliday from '#layers/display-time/utils/util-holiday';
import type { ApiError } from '~/composables/common/useApiClient';
import type { HolidayRow, HolidaySource, HolidaySyncOutcome, HolidayYearView } from '~/composables/holiday/api/useHolidayApi.types';

type Severity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('holiday.title') });

const api = useHolidayApi();
const source = useHolidaySource();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다 — 템플릿에서 `prefs.density` 로 읽는다
const { prefs } = useLayoutPrefs();

/** 바꾸기(더하기·지우기·지금 받기) 권한 — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('site.manage'));

/** 이름 길이 — 서버 규칙(1~30자)과 같다 */
const NAME_MAX = 30;
const OUTCOME_SEVERITY: Record<HolidaySyncOutcome, Severity> = { OK: 'success', PARTIAL: 'warn', NO_KEY: 'secondary', FAILED: 'danger' };
const SOURCE_SEVERITY: Record<HolidaySource, Severity> = { PUBLIC_DATA: 'info', SEED: 'secondary', OPERATOR: 'contrast' };

const thisYear = new Date().getFullYear();
const year = ref(thisYear);
const view = ref<HolidayYearView | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const working = ref<'sync' | 'add' | `remove:${string}` | null>(null);
/** 늦게 온 앞 해의 응답을 버린다 */
let seq = 0;

async function load(target: number): Promise<void> {
    const mine = ++seq;
    loading.value = true;
    loadError.value = null;
    try {
        const found = await api.year(target);
        if (mine === seq) view.value = found;
    } catch (e) {
        if (mine === seq) loadError.value = toApiError(e);
    } finally {
        if (mine === seq) loading.value = false;
    }
}

function go(target: number): void {
    year.value = target;
    void load(target);
}

/** 날짜 글자의 요일(표시 언어) */
function weekdayOf(ymd: string): string {
    const [y, m, d] = ymd.split('-').map(Number);
    if (!y || !m || !d) return '';
    return new Intl.DateTimeFormat(locale.value, { weekday: 'short', timeZone: 'UTC' }).format(new Date(Date.UTC(y, m - 1, d)));
}

/** 달력 표시 캐시를 다시 받는다 — 바뀐 해들 */
function refreshCalendars(years: readonly number[]): void {
    for (const y of new Set(years)) source.invalidate(y);
}

// ── 지금 받기 ──
async function syncNow(): Promise<void> {
    if (!canManage.value || working.value || !view.value) return;
    working.value = 'sync';
    try {
        const sync = await api.sync();
        if (view.value) view.value = { ...view.value, sync, keyConfigured: sync.outcome === 'NO_KEY' ? false : view.value.keyConfigured };
        if (sync.outcome === 'NO_KEY') toast.show(t('holiday.syncDoneNoKey'));
        else if (sync.outcome === 'FAILED') toast.error(t('holiday.syncDoneFailed'));
        else {
            toast.success(t('holiday.syncDone'));
            refreshCalendars(sync.appliedYears);
            if (sync.appliedYears.includes(year.value)) await load(year.value);
        }
    } catch (e) {
        toast.error(toApiError(e).message);
    } finally {
        working.value = null;
    }
}

// ── 지우기 ──
async function remove(row: HolidayRow): Promise<void> {
    if (!canManage.value || working.value || row.source !== 'OPERATOR') return;
    const date = time.value.serviceDate(row.date);
    const ok = await confirm({ title: t('holiday.removeTitle'), message: t('holiday.removeMessage', { date, name: row.name }), confirmLabel: t('holiday.remove'), tone: 'danger' });
    if (!ok || working.value) return;
    working.value = `remove:${row.id}`;
    try {
        await api.remove(row.id);
        if (view.value) view.value = { ...view.value, rows: view.value.rows.filter((r) => r.id !== row.id) };
        toast.success(t('holiday.removed'));
        refreshCalendars([Number(row.date.slice(0, 4))]);
    } catch (e) {
        const error = toApiError(e);
        toast.error(error.message);
        // 이미 없거나 바꿀 수 없는 줄이면 목록을 서버 값으로 맞춘다
        if (error.code === 'HOLIDAY_NOT_FOUND' || error.code === 'HOLIDAY_NOT_EDITABLE') await load(year.value);
    } finally {
        working.value = null;
    }
}

// ── 회사 휴무일 더하기 ──
const draftDate = ref('');
const draftName = ref('');
const submitted = ref(false);
const fieldErrors = ref<Record<string, string>>({});
const formError = ref('');

const dateError = computed(() => fieldErrors.value.date || (submitted.value && !draftDate.value ? t('holiday.dateRequired') : ''));
const nameError = computed(() => {
    if (fieldErrors.value.name) return fieldErrors.value.name;
    const length = draftName.value.trim().length;
    if (submitted.value && length === 0) return t('holiday.nameRequired');
    return length > NAME_MAX ? t('holiday.nameTooLong') : '';
});

/** 칸을 고치면 그 칸의 서버 문구는 낡았다 — 지운다 */
function clearField(name: 'date' | 'name'): void {
    formError.value = '';
    if (!fieldErrors.value[name]) return;
    const next = { ...fieldErrors.value };
    delete next[name];
    fieldErrors.value = next;
}

async function add(): Promise<void> {
    if (!canManage.value || working.value) return;
    submitted.value = true;
    if (dateError.value || nameError.value) return;
    working.value = 'add';
    formError.value = '';
    fieldErrors.value = {};
    try {
        const created = await api.create({ date: draftDate.value, name: draftName.value.trim() });
        if (view.value && created.date.startsWith(`${view.value.year}-`)) {
            view.value = { ...view.value, rows: [...view.value.rows, created].sort((a, b) => a.date.localeCompare(b.date)) };
        }
        refreshCalendars([Number(created.date.slice(0, 4))]);
        toast.success(t('holiday.added'));
        draftDate.value = '';
        draftName.value = '';
        submitted.value = false;
    } catch (e) {
        const error = toApiError(e);
        const fields = error.fields ?? {};
        const own = { date: fields.date, name: fields.name };
        if (own.date || own.name) fieldErrors.value = Object.fromEntries(Object.entries(own).filter(([, v]) => !!v)) as Record<string, string>;
        else formError.value = error.message;
    } finally {
        working.value = null;
    }
}

onMounted(() => {
    void load(year.value);
});
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.holiday-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
