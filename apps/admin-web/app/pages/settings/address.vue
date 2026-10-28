<template>
    <!--
        주소 검색(`/settings/address`) — 회원 화면 「우편번호 찾기」(user-web 공용 주소 입력 `AddressInput`)의 제공처와 자체 주소 DB. pionworld2 `core.address` 를 옮겼다.
          ① 제공처(`address-provider`) — 다음 우편번호만(기본) · 자체 주소 DB 만 · 다음 먼저(못 불러오면 자체 주소 DB). 다음 스크립트 주소(비우면 서버 기본값 — 도메인을 옮긴 적이 있는 서비스라 코드에 박지 않는다) · 갱신 권고 일수.
             이 구획만 따로 저장한다(구획 안 저장 줄). 자체 주소 DB 를 쓰는 제공처인데 DB 가 없으면 경고만 하고 막지 않는다(회원 화면은 직접 입력으로 넘어간다 — 미확정).
          ② 주소 DB(`address-db`) — 지금 올라 있는 DB(마지막 적재·주소 수·영문 수·파일 이름, 오래되면 「갱신 권고」)와 파일 올리기. 행정안전부 「도로명주소 한글 전체분」 zip(필수)과 「영문 전체분」 zip(선택)을 그대로 올린다.
             [올리기]는 되돌릴 수 없어 확인을 묻는다. 서버는 작업 번호만 바로 주고 적재를 이어 가며(수 분), 화면은 1.5초마다 진행을 묻는다(세기 → 넣기 → 지번·영문 붙이기 → 바꿔 끼우기).
             새 데이터는 스테이징 표에 다 넣은 뒤 맞바꾸므로 적재 중에도 회원 검색은 앞 데이터로 된다. 화면을 떠나도 적재는 이어지고, 다시 열면 돌고 있는 작업을 이어서 보인다. 실패하면 서버 사유를 그대로 보이고 지금 DB 는 그대로다.
          ③ 적재 이력 — 올릴 때마다 한 줄(`address_db_meta`), 최신이 위.
        권한: 조회 `site.read`(메뉴), 저장·올리기 `site.manage` — 없으면 저장 줄·올리기가 사라지고 입력칸은 `readonly`. 감추는 것은 편의이고 막는 것은 서버다.
        ⚠ 목업: `useAddressSettingsApi` 가 아직 `./mock/address-mock.ts` 를 부른다(스위치는 그 파일 머리말).
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('address.title')" :subtitle="t('address.subtitle')" />

        <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('address.readOnly') }}</Message>

        <!-- ① 제공처 -->
        <AppGadget :title="t('address.providerSection')" anchor="address-provider" :changes="changeCount" :invalid="invalidCount">
            <div v-if="loading && !saved" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 3" :key="n" height="3.5rem" />
            </div>

            <ErrorState v-else-if="!saved" :code="loadError?.status === 403 ? '403' : '!'" :title="t('address.loadFailed')" :description="loadError?.message" :retry="loadError?.status !== 403" :retrying="loading" @retry="load" />

            <form v-else class="flex flex-col gap-5" novalidate @submit.prevent="save">
                <fieldset class="m-0 flex flex-col gap-2 border-0 p-0" :aria-describedby="`${uid}-provider-lead`">
                    <legend class="mb-1 text-sm font-medium">{{ t('address.providerLabel') }}</legend>
                    <p :id="`${uid}-provider-lead`" class="m-0 mb-1 text-xs leading-relaxed text-muted-color">{{ t('address.providerLead') }}</p>
                    <label
                        v-for="option in PROVIDERS"
                        :key="option"
                        :for="`${uid}-p-${option}`"
                        class="flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-3 transition"
                        :class="form.provider === option ? 'border-primary bg-primary-50/40 dark:bg-primary-950/30' : 'border-(--line) hover:border-surface-400'"
                    >
                        <RadioButton v-model="form.provider" :input-id="`${uid}-p-${option}`" name="provider" :value="option" :disabled="!canManage || saving" class="mt-0.5" />
                        <span class="flex min-w-0 flex-col gap-0.5">
                            <span class="flex flex-wrap items-center gap-2 text-sm font-medium">
                                {{ t(`address.provider_${option}`) }}
                                <Tag v-if="option === 'DAUM'" :value="t('address.providerDefault')" severity="secondary" class="text-[11px]!" />
                                <Tag v-if="option === saved.provider && option !== form.provider" :value="t('address.providerSaved')" severity="secondary" class="text-[11px]!" />
                            </span>
                            <span class="text-xs leading-relaxed text-muted-color">{{ t(`address.providerDesc_${option}`) }}</span>
                        </span>
                    </label>
                    <Message v-if="needsDbWarning" severity="warn" size="small" :closable="false" role="status">{{ t('address.providerNeedsDb') }}</Message>
                </fieldset>

                <LabeledField :label="t('address.scriptUrlLabel')" :hint="t('address.optional')" :help="t('address.scriptUrlHelp')" :error="scriptUrlError">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText
                            :id="id"
                            v-model="form.scriptUrl"
                            name="scriptUrl"
                            type="url"
                            inputmode="url"
                            autocomplete="off"
                            spellcheck="false"
                            size="small"
                            :placeholder="saved.defaultScriptUrl"
                            :readonly="!canManage || saving"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                            class="w-full max-w-2xl font-mono! text-xs!"
                            @update:model-value="clearServer('scriptUrl')"
                        />
                    </template>
                </LabeledField>

                <LabeledField :label="t('address.staleDaysLabel')" :hint="t('address.staleDaysHint', { min: saved.staleWarnDaysMin, max: saved.staleWarnDaysMax })" :help="t('address.staleDaysHelp')" :error="staleDaysError">
                    <template #default="{ id, describedBy, invalid }">
                        <div class="flex items-center gap-2">
                            <InputText
                                :id="id"
                                v-model="form.staleWarnDays"
                                name="staleWarnDays"
                                inputmode="numeric"
                                autocomplete="off"
                                size="small"
                                :readonly="!canManage || saving"
                                :invalid="invalid"
                                :aria-describedby="describedBy"
                                aria-required="true"
                                class="w-20 text-right tabular-nums"
                                @update:model-value="clearServer('staleWarnDays')"
                            />
                            <span class="text-sm text-muted-color">{{ t('address.days') }}</span>
                        </div>
                    </template>
                </LabeledField>

                <Message v-if="formError" severity="error" variant="simple" size="small">{{ formError }}</Message>

                <div class="flex flex-wrap items-center justify-between gap-2">
                    <p class="m-0 text-xs text-muted-color">
                        {{ saved.updatedAt ? t('address.lastSaved', { at: time.full(saved.updatedAt), by: saved.updatedBy ?? '—' }) : t('address.neverSaved') }}
                    </p>
                    <div v-if="canManage" class="flex flex-wrap items-center gap-2">
                        <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" :disabled="saving || changeCount === 0" @click="revert" />
                        <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="saving" :disabled="changeCount === 0 || hasProblem" />
                    </div>
                </div>
            </form>
        </AppGadget>

        <!-- ② 주소 DB -->
        <AppGadget :title="t('address.dbSection')" anchor="address-db">
            <template v-if="saved" #title-extra>
                <Tag v-if="!saved.db.loadedAt" :value="t('address.dbNever')" severity="secondary" class="text-[11px]!" />
                <Tag v-else-if="saved.db.stale" :value="t('address.dbStale')" icon="pi pi-exclamation-triangle" severity="warn" class="text-[11px]!" />
                <Tag v-else :value="t('address.dbFresh')" icon="pi pi-check" severity="success" class="text-[11px]!" />
            </template>

            <div v-if="loading && !saved" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton height="4rem" />
                <Skeleton height="6rem" />
            </div>
            <ErrorState v-else-if="!saved" :code="loadError?.status === 403 ? '403' : '!'" :title="t('address.loadFailed')" :description="loadError?.message" :retry="loadError?.status !== 403" :retrying="loading" @retry="load" />

            <div v-else class="flex flex-col gap-5">
                <!-- 지금 올라 있는 DB -->
                <dl v-if="saved.db.loadedAt" class="m-0 grid gap-x-6 gap-y-3 rounded-lg border border-(--line) p-3 sm:grid-cols-[auto_1fr]">
                    <dt class="text-xs text-muted-color">{{ t('address.dbLoadedAt') }}</dt>
                    <dd class="m-0 text-sm tabular-nums">
                        {{ time.full(saved.db.loadedAt) }}
                        <span v-if="saved.db.stale" class="ml-1 text-xs text-amber-700 dark:text-amber-400">{{ t('address.dbStaleHint', { days: saved.staleWarnDays }) }}</span>
                    </dd>
                    <dt class="text-xs text-muted-color">{{ t('address.dbEntries') }}</dt>
                    <dd class="m-0 text-sm tabular-nums">
                        {{ t('address.countUnit', { count: (saved.db.entryCount ?? 0).toLocaleString(locale) }) }}
                        <span class="ml-1 text-xs text-muted-color">{{ saved.db.englishCount ? t('address.dbEnglish', { count: saved.db.englishCount.toLocaleString(locale) }) : t('address.dbNoEnglish') }}</span>
                    </dd>
                    <dt class="text-xs text-muted-color">{{ t('address.dbSource') }}</dt>
                    <dd class="m-0 break-all text-sm">{{ saved.db.sourceLabel }}</dd>
                </dl>
                <EmptyState v-else icon="pi pi-database" :title="t('address.dbEmptyTitle')" :description="t('address.dbEmptyDesc')" />

                <!-- 적재 진행 -->
                <section v-if="job" :aria-labelledby="`${uid}-job`" class="flex flex-col gap-3 rounded-lg border p-3" :class="job.failed ? 'border-red-300 dark:border-red-800' : job.done ? 'border-green-300 dark:border-green-800' : 'border-primary'" data-testid="address-import-job">
                    <div class="flex flex-wrap items-center gap-2">
                        <h3 :id="`${uid}-job`" class="m-0 text-sm font-medium">{{ job.done ? t('address.jobDone') : job.failed ? t('address.jobFailed') : t('address.jobRunning') }}</h3>
                        <span class="text-xs text-muted-color">{{ t('address.jobStarted', { at: time.full(job.startedAt), by: job.startedBy ?? '—' }) }}</span>
                        <Button v-if="job.done || job.failed" type="button" icon="pi pi-times" severity="secondary" variant="text" size="small" rounded class="ml-auto" :aria-label="t('common.close')" @click="job = null" />
                    </div>

                    <!-- 단계 — 지금 단계는 굵게, 지난 단계는 확인 표시 -->
                    <ol class="m-0 flex list-none flex-wrap gap-x-4 gap-y-1 p-0 text-xs" :aria-label="t('address.jobSteps')">
                        <li v-for="(step, i) in STEPS" :key="step" class="inline-flex items-center gap-1.5" :class="stepState(i) === 'current' ? 'font-semibold text-color' : stepState(i) === 'past' ? 'text-muted-color' : 'text-surface-400'" :aria-current="stepState(i) === 'current' ? 'step' : undefined">
                            <i class="pi text-[10px]" :class="stepState(i) === 'past' ? 'pi-check' : stepState(i) === 'current' && !job.failed ? 'pi-spin pi-spinner' : stepState(i) === 'current' ? 'pi-times text-red-600' : 'pi-circle'" aria-hidden="true" />
                            {{ t(`address.phase_${step}`) }}
                        </li>
                    </ol>

                    <div v-if="!job.failed" class="flex flex-col gap-1">
                        <ProgressBar :value="percent ?? undefined" :mode="percent === null ? 'indeterminate' : 'determinate'" :show-value="false" class="h-2" :aria-label="t('address.jobProgress')" />
                        <p class="m-0 text-xs tabular-nums text-muted-color" aria-live="polite">
                            <template v-if="job.totalRows < 0">{{ t('address.jobCounting') }}</template>
                            <template v-else>{{ t('address.jobRows', { done: job.processedRows.toLocaleString(locale), total: job.totalRows.toLocaleString(locale), percent: percent ?? 0 }) }}</template>
                        </p>
                    </div>

                    <Message v-if="job.failed" severity="error" size="small" :closable="false" role="alert">{{ job.message ?? t('address.jobFailedUnknown') }}</Message>
                    <Message v-else-if="job.done" severity="success" size="small" :closable="false" role="status">{{ job.message }}</Message>
                    <p v-else class="m-0 text-xs leading-relaxed text-muted-color">{{ t('address.jobKeepGoing') }}</p>
                    <p class="m-0 break-all text-xs text-muted-color">{{ job.fileNames.join(', ') }}</p>
                </section>
                <Message v-if="pollError" severity="error" size="small" :closable="false" role="alert">
                    {{ pollError }}
                    <Button type="button" :label="t('common.retry')" size="small" severity="secondary" variant="text" class="ml-2" @click="resumePolling" />
                </Message>

                <!-- 파일 올리기 -->
                <form v-if="canManage && !running" class="flex flex-col gap-4" novalidate @submit.prevent="startImport">
                    <div class="flex flex-col gap-1">
                        <h3 class="m-0 text-sm font-medium">{{ t('address.uploadTitle') }}</h3>
                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('address.uploadLead') }}</p>
                        <ul class="m-0 mt-1 flex list-none flex-col gap-1 p-0 text-xs leading-relaxed text-muted-color">
                            <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('address.uploadWhere') }}</span></li>
                            <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('address.uploadSwap') }}</span></li>
                            <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('address.uploadLimit') }}</span></li>
                        </ul>
                    </div>

                    <div class="grid gap-4 md:grid-cols-2">
                        <div v-for="slot in FILE_SLOTS" :key="slot" class="flex flex-col gap-1.5">
                            <span :id="`${uid}-${slot}-label`" class="text-sm font-medium">
                                {{ t(`address.file_${slot}`) }}
                                <small class="ml-1 text-xs font-normal text-muted-color">{{ slot === 'ko' ? t('address.required') : t('address.optional') }}</small>
                            </span>
                            <div class="flex min-h-10 items-center gap-2 rounded-lg border border-dashed px-3 py-2" :class="fileErrors[slot] ? 'border-red-400' : 'border-(--line)'">
                                <i class="pi pi-file-export text-muted-color" aria-hidden="true" />
                                <span class="min-w-0 flex-1 truncate text-sm" :class="files[slot] ? '' : 'text-muted-color'">
                                    {{ files[slot] ? `${files[slot]!.name} · ${UtilAddressSettings.megabytes(files[slot]!.size)}MB` : t('address.fileNone') }}
                                </span>
                                <Button type="button" :label="files[slot] ? t('address.fileChange') : t('address.filePick')" size="small" severity="secondary" variant="outlined" :disabled="uploading" :aria-describedby="`${uid}-${slot}-label`" @click="pick(slot)" />
                                <Button v-if="files[slot]" type="button" icon="pi pi-times" size="small" severity="secondary" variant="text" rounded :disabled="uploading" :aria-label="t('address.fileRemove')" @click="setFile(slot, null)" />
                            </div>
                            <p v-if="fileErrors[slot]" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fileErrors[slot] }}</p>
                            <p v-else class="m-0 text-xs text-muted-color">{{ t(`address.fileHelp_${slot}`) }}</p>
                            <input :ref="(el) => (inputs[slot] = el as HTMLInputElement | null)" type="file" :accept="UtilAddressSettings.FILE_ACCEPT" class="hidden" tabindex="-1" :aria-label="t(`address.file_${slot}`)" @change="onFileChange(slot, $event)" />
                        </div>
                    </div>

                    <LabeledField :label="t('address.charsetLabel')" :help="t('address.charsetHelp')">
                        <template #default="{ id, describedBy }">
                            <Select v-model="charset" :input-id="id" :options="CHARSETS" option-label="label" option-value="value" size="small" :disabled="uploading" :aria-describedby="describedBy" class="w-56" />
                        </template>
                    </LabeledField>

                    <Message v-if="uploadError" severity="error" variant="simple" size="small">{{ uploadError }}</Message>

                    <div class="flex justify-end">
                        <Button type="submit" :label="t('address.upload')" icon="pi pi-upload" severity="danger" :loading="uploading" :disabled="!canUpload" data-testid="address-upload" />
                    </div>
                </form>
            </div>
        </AppGadget>

        <!-- ③ 적재 이력 -->
        <AppGadget :title="t('address.historySection')">
            <DataTable
                :value="history?.items ?? []"
                data-key="id"
                lazy
                paginator
                :first="historyPage * HISTORY_SIZE"
                :rows="HISTORY_SIZE"
                :total-records="history?.total ?? 0"
                :loading="historyLoading"
                size="small"
                :always-show-paginator="false"
                paginator-template="PrevPageLink PageLinks NextPageLink"
                @page="(e) => loadHistory(e.page)"
            >
                <template #empty>
                    <ErrorState v-if="historyError" :title="t('address.historyLoadFailed')" :description="historyError.message" retry :retrying="historyLoading" @retry="loadHistory(historyPage)" />
                    <div v-else-if="historyLoading" class="h-24" aria-hidden="true" />
                    <EmptyState v-else icon="pi pi-history" :title="t('address.historyEmpty')" />
                </template>
                <Column field="loadedAt" :header="t('address.colLoadedAt')" style="width: 11rem">
                    <template #body="{ data }"><span class="text-[12px] tabular-nums">{{ time.full(data.loadedAt) }}</span></template>
                </Column>
                <Column field="sourceLabel" :header="t('address.colSource')">
                    <template #body="{ data }"><span class="break-all text-[12px]">{{ data.sourceLabel }}</span></template>
                </Column>
                <Column field="entryCount" :header="t('address.colEntries')" style="width: 8rem" header-class="justify-end" body-class="text-right">
                    <template #body="{ data }"><span class="text-[12px] tabular-nums">{{ data.entryCount.toLocaleString(locale) }}</span></template>
                </Column>
                <Column field="englishCount" :header="t('address.colEnglish')" style="width: 8rem" body-class="text-right">
                    <template #body="{ data }"><span class="text-[12px] tabular-nums">{{ data.englishCount ? data.englishCount.toLocaleString(locale) : '—' }}</span></template>
                </Column>
                <Column field="loadedBy" :header="t('address.colLoadedBy')" style="width: 9rem">
                    <template #body="{ data }"><span class="text-[12px]">{{ data.loadedBy ?? '—' }}</span></template>
                </Column>
            </DataTable>
        </AppGadget>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type {
    AddressDbLoadPage,
    AddressImportCharset,
    AddressImportPhase,
    AddressImportProgress,
    AddressProvider,
    AddressSettingsView,
} from '~/composables/address/api/useAddressSettingsApi.types';
import type { ImportFileProblem, ScriptUrlProblem, StaleDaysProblem } from '~/utils/util-address-settings';
import UtilAddressSettings from '~/utils/util-address-settings';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('address.title') });

const api = useAddressSettingsApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const canManage = computed(() => can('site.manage'));
const uid = useId();

const PROVIDERS: readonly AddressProvider[] = ['DAUM', 'INTERNAL', 'DAUM_FALLBACK_INTERNAL'];
const STEPS: readonly AddressImportPhase[] = ['COUNTING', 'PARSING', 'MERGING', 'SWAPPING'];
const FILE_SLOTS = ['ko', 'en'] as const;
type FileSlot = (typeof FILE_SLOTS)[number];
const HISTORY_SIZE = 10;
const POLL_MS = 1500;
const CHARSETS = computed<{ value: AddressImportCharset; label: string }[]>(() => [
    { value: 'MS949', label: t('address.charsetCp949') },
    { value: 'UTF-8', label: 'UTF-8' },
]);

/* ── ① 제공처 ── */

interface ProviderForm {
    provider: AddressProvider;
    scriptUrl: string;
    staleWarnDays: string;
}

const saved = ref<AddressSettingsView | null>(null);
const form = ref<ProviderForm>({ provider: 'DAUM', scriptUrl: '', staleWarnDays: '' });
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');
const serverErrors = ref<Partial<Record<'scriptUrl' | 'staleWarnDays', string>>>({});

const SCRIPT_URL_KEYS: Record<ScriptUrlProblem, string> = { NOT_HTTPS: 'address.scriptUrlNotHttps', TOO_LONG: 'address.scriptUrlTooLong' };
const STALE_KEYS: Record<StaleDaysProblem, string> = { REQUIRED: 'address.staleDaysRequired', NOT_INTEGER: 'address.staleDaysNotInteger', OUT_OF_RANGE: 'address.staleDaysOutOfRange' };

const scriptUrlProblem = computed(() => UtilAddressSettings.scriptUrlProblem(form.value.scriptUrl));
const staleProblem = computed(() => (saved.value ? UtilAddressSettings.staleDaysProblem(form.value.staleWarnDays, saved.value.staleWarnDaysMin, saved.value.staleWarnDaysMax) : null));
const scriptUrlError = computed(() => (scriptUrlProblem.value ? t(SCRIPT_URL_KEYS[scriptUrlProblem.value], { max: UtilAddressSettings.SCRIPT_URL_MAX }) : (serverErrors.value.scriptUrl ?? '')));
const staleDaysError = computed(() =>
    staleProblem.value && saved.value ? t(STALE_KEYS[staleProblem.value], { min: saved.value.staleWarnDaysMin, max: saved.value.staleWarnDaysMax }) : (serverErrors.value.staleWarnDays ?? ''),
);
const hasProblem = computed(() => scriptUrlProblem.value !== null || staleProblem.value !== null);
const invalidCount = computed(() => (scriptUrlError.value ? 1 : 0) + (staleDaysError.value ? 1 : 0));

const changeCount = computed(() => {
    const s = saved.value;
    if (!s) return 0;
    let n = 0;
    if (form.value.provider !== s.provider) n += 1;
    if (form.value.scriptUrl.trim() !== (s.scriptUrl ?? '')) n += 1;
    if (UtilAddressSettings.parseDays(form.value.staleWarnDays) !== s.staleWarnDays) n += 1;
    return n;
});

/** 자체 주소 DB 를 쓰는 제공처인데 DB 를 올린 적이 없다 */
const needsDbWarning = computed(() => form.value.provider !== 'DAUM' && !saved.value?.db.loadedAt);

function applySaved(view: AddressSettingsView): void {
    saved.value = view;
    form.value = { provider: view.provider, scriptUrl: view.scriptUrl ?? '', staleWarnDays: String(view.staleWarnDays) };
    serverErrors.value = {};
    formError.value = '';
}

function revert(): void {
    if (saved.value) applySaved(saved.value);
}

function clearServer(key: 'scriptUrl' | 'staleWarnDays'): void {
    if (!serverErrors.value[key]) return;
    const { [key]: _removed, ...rest } = serverErrors.value;
    serverErrors.value = rest;
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        applySaved(await api.settings());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

async function save(): Promise<void> {
    const days = UtilAddressSettings.parseDays(form.value.staleWarnDays);
    if (saving.value || changeCount.value === 0 || hasProblem.value || days === null) return;
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        applySaved(await api.saveSettings({ provider: form.value.provider, scriptUrl: form.value.scriptUrl.trim() || null, staleWarnDays: days }));
        toast.success(t('address.saved'));
    } catch (e) {
        const failure = toApiError(e);
        const fields: Partial<Record<'scriptUrl' | 'staleWarnDays', string>> = {};
        if (failure.code === 'VALIDATION') {
            for (const key of ['scriptUrl', 'staleWarnDays'] as const) {
                const message = failure.fields?.[key];
                if (message) fields[key] = message;
            }
        }
        serverErrors.value = fields;
        formError.value = Object.keys(fields).length ? t('address.saveFieldsRejected') : failure.message;
    } finally {
        saving.value = false;
    }
}

/* ── ② 주소 DB 올리기 ── */

const files = ref<Record<FileSlot, File | null>>({ ko: null, en: null });
const fileProblems = ref<Record<FileSlot, ImportFileProblem | null>>({ ko: null, en: null });
const inputs: Record<FileSlot, HTMLInputElement | null> = { ko: null, en: null };
const charset = ref<AddressImportCharset>('MS949');
const uploading = ref(false);
const uploadError = ref('');

const FILE_PROBLEM_KEYS: Record<ImportFileProblem, string> = { NOT_ZIP: 'address.fileNotZip', EMPTY: 'address.fileEmpty', TOO_LARGE: 'address.fileTooLarge' };
const fileErrors = computed<Record<FileSlot, string>>(() => ({
    ko: fileProblems.value.ko ? t(FILE_PROBLEM_KEYS[fileProblems.value.ko], { max: UtilAddressSettings.megabytes(UtilAddressSettings.FILE_MAX_BYTES) }) : '',
    en: fileProblems.value.en ? t(FILE_PROBLEM_KEYS[fileProblems.value.en], { max: UtilAddressSettings.megabytes(UtilAddressSettings.FILE_MAX_BYTES) }) : '',
}));
const canUpload = computed(() => Boolean(files.value.ko) && !fileProblems.value.ko && !fileProblems.value.en && !uploading.value);

function pick(slot: FileSlot): void {
    inputs[slot]?.click();
}

function onFileChange(slot: FileSlot, event: Event): void {
    const input = event.target as HTMLInputElement;
    setFile(slot, input.files?.[0] ?? null);
    // 같은 파일을 다시 골라도 change 가 오게 비운다
    input.value = '';
}

function setFile(slot: FileSlot, file: File | null): void {
    files.value = { ...files.value, [slot]: file };
    fileProblems.value = { ...fileProblems.value, [slot]: file ? UtilAddressSettings.fileProblem(file) : null };
    uploadError.value = '';
}

async function startImport(): Promise<void> {
    const ko = files.value.ko;
    if (!ko || !canUpload.value || !saved.value) return;
    const chosen = [ko, files.value.en].filter((f): f is File => f !== null);
    const current = saved.value.db.entryCount;
    const ok = await confirm({
        title: t('address.uploadConfirmTitle'),
        message: current ? t('address.uploadConfirmReplace', { count: current.toLocaleString(locale.value), files: chosen.map((f) => f.name).join(', ') }) : t('address.uploadConfirmFirst', { files: chosen.map((f) => f.name).join(', ') }),
        confirmLabel: t('address.uploadConfirm'),
        tone: 'danger',
    });
    if (!ok) return;
    uploading.value = true;
    uploadError.value = '';
    try {
        const started = await api.startImport(chosen, charset.value);
        files.value = { ko: null, en: null };
        fileProblems.value = { ko: null, en: null };
        await follow(started.jobId);
    } catch (e) {
        uploadError.value = toApiError(e).message;
    } finally {
        uploading.value = false;
    }
}

/* ── 적재 진행 — 끝나거나 실패할 때까지 1.5초마다 묻는다 ── */

const job = ref<AddressImportProgress | null>(null);
const pollError = ref('');
const running = computed(() => uploading.value || (job.value !== null && !job.value.done && !job.value.failed));
const percent = computed(() => (job.value ? UtilAddressSettings.percent(job.value) : null));
let timer: ReturnType<typeof setTimeout> | undefined;
let alive = true;

function stepState(index: number): 'past' | 'current' | 'future' {
    const j = job.value;
    if (!j) return 'future';
    if (j.done) return 'past';
    const at = STEPS.indexOf(j.phase);
    // 실패는 단계가 FAILED 라 어디서 멈췄는지 모른다 — 진행 줄 수로 어림한다(셌으면 넣기 중, 다 넣었으면 그다음)
    const current = at >= 0 ? at : j.totalRows < 0 ? 0 : j.processedRows < j.totalRows ? 1 : 2;
    return index < current ? 'past' : index === current ? 'current' : 'future';
}

async function follow(jobId: string): Promise<void> {
    clearTimeout(timer);
    pollError.value = '';
    try {
        const progress = await api.importProgress(jobId);
        if (!alive) return;
        job.value = progress;
        if (progress.done) {
            toast.success(t('address.importDone'));
            await refreshDb();
            return;
        }
        if (progress.failed) return;
        timer = setTimeout(() => void follow(jobId), POLL_MS);
    } catch (e) {
        if (alive) pollError.value = toApiError(e).message;
    }
}

function resumePolling(): void {
    if (job.value) void follow(job.value.jobId);
}

/** 적재가 끝나면 DB 상태·이력만 다시 받는다 — 제공처 구획의 고치던 값은 그대로 둔다 */
async function refreshDb(): Promise<void> {
    try {
        const fresh = await api.settings();
        if (saved.value) saved.value = { ...saved.value, db: fresh.db };
    } catch {
        // 상태 줄이 늦을 뿐 — 이력은 아래에서 다시 받는다
    }
    await loadHistory(0);
}

async function loadCurrentJob(): Promise<void> {
    try {
        const current = await api.currentImport();
        if (current && alive) {
            job.value = current;
            if (!current.done && !current.failed) timer = setTimeout(() => void follow(current.jobId), POLL_MS);
        }
    } catch {
        // 돌고 있는 작업을 못 받아도 화면은 쓸 수 있다 — 올리기를 누르면 서버가 409 로 알린다
    }
}

/* ── ③ 적재 이력 ── */

const history = ref<AddressDbLoadPage | null>(null);
const historyPage = ref(0);
const historyLoading = ref(false);
const historyError = ref<ApiError | null>(null);

async function loadHistory(page: number): Promise<void> {
    historyLoading.value = true;
    historyError.value = null;
    try {
        history.value = await api.history(page, HISTORY_SIZE);
        historyPage.value = page;
    } catch (e) {
        historyError.value = toApiError(e);
        history.value = null;
    } finally {
        historyLoading.value = false;
    }
}

onMounted(() => {
    void load();
    void loadCurrentJob();
    void loadHistory(0);
});

onBeforeUnmount(() => {
    alive = false;
    clearTimeout(timer);
});

// 제공처 구획에 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다(적재는 서버에서 이어지니 묻지 않는다)
onBeforeRouteLeave(async () => {
    if (changeCount.value === 0) return true;
    return confirm({ title: t('address.leaveTitle'), message: t('address.leaveMessage'), confirmLabel: t('address.leaveConfirm'), cancelLabel: t('address.leaveCancel'), tone: 'danger' });
});
</script>
