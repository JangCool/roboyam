<template>
    <!--
        공공데이터포털 설정(`/settings/public-data`) — 공공데이터포털 서비스 키 하나를 넣고 지운다(국세청 사업자 상태조회·진위확인이 같이 쓴다). 값은 **절대 보이지 않고**
        설정 여부·어디서 왔는지(화면 / 배포 환경 변수)·마지막 변경만 보인다. 포트원 설정(`/settings/portone`)과 같은 규칙이다.
        구획: ① 사용처(국세청 사업자 조회 — 지금 쓰는 구현, 읽기 전용) ② 서비스 키(입력·저장·지우기) ③ 연결 시험(저장된 키로 실제 국세청 상태조회 한 번)
        ④ API 주소(받는 주소가 바뀔 때를 위해 — 사용자 결정 2026-11-05): 국세청 사업자 상태 조회 주소 · 공휴일 받기(특일 정보) 주소·요청 글자 틀·날짜/이름 칸 이름.
           칸마다 「기본값 / 직접 넣음」 딱지, 입력칸 흐린 글자가 기본값, 빈 칸으로 저장하면 기본값. 묶음마다 저장 · 「기본값으로 되돌리기」(확인) — `site.secret`. 특일 정보 연결 시험(`site.read`)과 휴일 관리 링크.
        - 서버에 암호화 키가 없으면 맨 위 경고 — 저장할 수 없다(지우기는 된다).
        - 권한: 화면 `site.read`(메뉴가 거른다) · 저장·지우기 `site.secret` — 없으면 읽기 전용 안내와 상태만. 연결 시험은 `site.read`.
        - 저장하지 않은 입력이 있는데 다른 화면으로 가면 묻는다. 지우기는 확인을 묻는다.
        서버 API: `usePublicDataSettingsApi`(서버 `PublicDataSettingsController`).
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('publicData.title')" :subtitle="t('publicData.subtitle')" />

        <Message v-if="!canSecret" severity="info" :closable="false" size="small">{{ t('publicData.readOnly') }}</Message>

        <div v-if="loading && !view" class="flex flex-col gap-4" role="status" :aria-label="t('common.loading')">
            <AppGadget v-for="n in 3" :key="n" :title="t('common.loading')">
                <div class="flex flex-col gap-3">
                    <Skeleton height="0.75rem" width="60%" />
                    <Skeleton height="2.25rem" width="min(36rem, 100%)" />
                </div>
            </AppGadget>
        </div>

        <AppGadget v-else-if="!view" :title="t('publicData.title')">
            <ErrorState :title="t('publicData.loadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load()" />
        </AppGadget>

        <template v-else>
            <Message v-if="!view.cryptoKeyConfigured" severity="warn" :closable="false" size="small">
                <b>{{ t('publicData.cryptoMissingTitle') }}</b>
                <span class="mt-0.5 block">{{ t('publicData.cryptoMissingText') }}</span>
            </Message>

            <!-- ① 사용처 — 읽기 전용 -->
            <AppGadget :title="t('publicData.usageTitle')" anchor="public-data-usage">
                <div class="flex flex-col gap-3">
                    <div class="flex flex-wrap items-center gap-2">
                        <span class="text-sm">{{ t('publicData.usageNts') }}</span>
                        <Tag
                            class="public-data-tag"
                            :value="t(`publicData.provider.${view.ntsProvider}`)"
                            :icon="view.ntsProvider === 'ODCLOUD' ? 'pi pi-building-columns' : 'pi pi-wrench'"
                            :severity="view.ntsProvider === 'ODCLOUD' ? 'info' : 'warn'"
                            data-testid="public-data-provider"
                        />
                    </div>
                    <Message v-if="view.ntsProvider === 'FAKE'" severity="warn" :closable="false" size="small">{{ t('publicData.providerFake') }}</Message>
                    <Message v-else-if="view.serviceKey.source === 'NONE'" severity="error" :closable="false" size="small" data-testid="public-data-no-key">
                        <b>{{ t('publicData.noKeyTitle') }}</b>
                        <span class="mt-0.5 block">{{ t('publicData.noKeyText') }}</span>
                    </Message>
                </div>
            </AppGadget>

            <!-- ② 서비스 키 — 카드 하나가 form 하나 -->
            <form novalidate @submit.prevent="save">
                <AppGadget :title="t('publicData.keyTitle')" anchor="public-data-key" :changes="dirty ? 1 : 0" :invalid="keyError ? 1 : 0">
                    <template #actions>
                        <Tag class="public-data-tag shrink-0" :value="t(`publicData.source.${view.serviceKey.source}`)" :severity="view.serviceKey.source === 'NONE' ? 'warn' : 'success'" data-testid="public-data-source" />
                    </template>
                    <div class="flex max-w-xl flex-col gap-4">
                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('publicData.keyHelp') }}</p>

                        <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                            <span class="text-sm font-medium">{{ t('publicData.field') }}</span>
                            <span class="inline-flex items-center gap-1 text-xs" :class="view.serviceKey.source !== 'NONE' ? 'text-green-700 dark:text-green-400' : 'text-muted-color'">
                                <i :class="view.serviceKey.source !== 'NONE' ? 'pi pi-check-circle' : 'pi pi-circle'" aria-hidden="true" />
                                {{ view.serviceKey.source !== 'NONE' ? t('publicData.configured') : t('publicData.notConfigured') }}
                            </span>
                            <span v-if="view.serviceKey.updatedAt" class="text-xs text-muted-color tabular-nums">
                                · {{ view.serviceKey.updatedBy ? t('publicData.updatedBy', { at: time.full(view.serviceKey.updatedAt), name: view.serviceKey.updatedBy }) : t('publicData.updatedAt', { at: time.full(view.serviceKey.updatedAt) }) }}
                            </span>
                            <Button
                                v-if="canSecret && view.serviceKey.source === 'SCREEN'"
                                type="button"
                                class="ml-auto"
                                :label="t('publicData.remove')"
                                icon="pi pi-trash"
                                size="small"
                                severity="danger"
                                variant="text"
                                :loading="working === 'remove'"
                                :disabled="working !== null"
                                @click="remove"
                            />
                        </div>
                        <Message v-if="view.serviceKey.source === 'ENV'" severity="info" :closable="false" size="small">{{ t('publicData.envNotice') }}</Message>

                        <NotificationSecretField
                            v-if="canSecret"
                            v-model="keyInput"
                            :label="view.serviceKey.source === 'NONE' ? t('publicData.value') : t('publicData.newValue')"
                            :saved="false"
                            :help="view.serviceKey.source === 'NONE' ? t('publicData.valueHelp') : t('publicData.keepHint')"
                            :error="keyError"
                            :disabled="working !== null || !view.cryptoKeyConfigured"
                        />

                        <Message v-if="formError" severity="error" variant="simple" size="small">{{ formError }}</Message>

                        <div v-if="canSecret" class="flex flex-wrap items-center justify-end gap-2">
                            <span class="mr-auto text-xs text-muted-color">{{ dirty ? t('publicData.changes') : t('publicData.noChanges') }}</span>
                            <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" :disabled="working !== null || !dirty" @click="keyInput = ''" />
                            <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="working === 'save'" :disabled="working !== null || !dirty || !view.cryptoKeyConfigured" />
                        </div>
                    </div>
                </AppGadget>
            </form>

            <!-- ③ 연결 시험 -->
            <AppGadget :title="t('publicData.testTitle')" anchor="public-data-test">
                <div class="flex max-w-xl flex-col gap-3">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('publicData.testHelp') }}</p>
                    <Message v-if="view.lastTest" :severity="view.lastTest.result === 'OK' ? 'success' : 'error'" :closable="false" size="small" data-testid="public-data-test-result">
                        <b>{{ t(`publicData.testResult.${view.lastTest.result}`) }}</b>
                        <span class="mt-0.5 block text-xs tabular-nums">{{ view.lastTest.testedBy ? t('publicData.testedBy', { at: time.full(view.lastTest.testedAt), name: view.lastTest.testedBy }) : t('publicData.testedAt', { at: time.full(view.lastTest.testedAt) }) }}</span>
                    </Message>
                    <div>
                        <Button type="button" :label="t('publicData.testRun')" icon="pi pi-bolt" severity="secondary" variant="outlined" :loading="working === 'test'" :disabled="working !== null || dirty || epDirty.nts" @click="test" />
                        <span v-if="dirty" class="ml-2 text-xs text-muted-color">{{ t('publicData.testSaveFirst') }}</span>
                        <span v-else-if="epDirty.nts" class="ml-2 text-xs text-muted-color">{{ t('publicData.epTestSaveFirst') }}</span>
                    </div>
                </div>
            </AppGadget>

            <!-- ④ API 주소 — 묶음마다 저장·기본값으로 되돌리기(`site.secret`). 빈 칸은 기본값이다 -->
            <AppGadget :title="t('publicData.epTitle')" anchor="public-data-endpoints" :changes="(epDirty.nts ? 1 : 0) + (epDirty.holiday ? 1 : 0)">
                <div class="flex max-w-2xl flex-col gap-6">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('publicData.epLead') }}</p>

                    <form v-for="kind in EP_KINDS" :key="kind" class="flex flex-col gap-4" :class="kind === 'holiday' ? 'border-t border-(--line) pt-6' : ''" novalidate :data-testid="`public-data-ep-${kind}`" @submit.prevent="saveEndpoint(kind)">
                        <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                            <h3 class="m-0 text-sm font-semibold">{{ t(`publicData.epGroup.${kind}`) }}</h3>
                            <span v-if="view.endpoints[kind].updatedAt" class="text-xs text-muted-color tabular-nums">
                                · {{ view.endpoints[kind].updatedBy ? t('publicData.updatedBy', { at: time.full(view.endpoints[kind].updatedAt!), name: view.endpoints[kind].updatedBy }) : t('publicData.updatedAt', { at: time.full(view.endpoints[kind].updatedAt!) }) }}
                            </span>
                        </div>
                        <p v-if="kind === 'holiday'" class="m-0 text-xs leading-relaxed text-muted-color">
                            {{ t('publicData.epHolidayLead') }}
                            <NuxtLink to="/settings/holidays" class="font-medium text-primary underline">{{ t('publicData.epHolidaysLink') }}</NuxtLink>
                        </p>

                        <LabeledField
                            v-for="field in EP_FIELDS[kind]"
                            :key="field"
                            :label="t(`publicData.epField.${field}`)"
                            :help="epHelp(field)"
                            :error="epErrors[kind][field]"
                        >
                            <template #default="{ id, describedBy, invalid }">
                                <div class="flex items-center gap-2">
                                    <InputText
                                        :id="id"
                                        v-model="epDraft[kind][field]"
                                        :name="field"
                                        :type="field === 'url' ? 'url' : 'text'"
                                        :inputmode="field === 'url' ? 'url' : undefined"
                                        autocomplete="off"
                                        spellcheck="false"
                                        size="small"
                                        :placeholder="epField(kind, field).defaultValue"
                                        :readonly="!canSecret || working !== null"
                                        :invalid="invalid"
                                        :aria-describedby="describedBy"
                                        class="min-w-0 flex-1 font-mono! text-xs!"
                                        @update:model-value="clearEpError(kind, field)"
                                    />
                                    <Tag
                                        class="public-data-tag shrink-0"
                                        :value="epField(kind, field).custom ? t('publicData.epCustom') : t('publicData.epDefaultTag')"
                                        :severity="epField(kind, field).custom ? 'info' : 'secondary'"
                                        :data-testid="`public-data-ep-${kind}-${field}-tag`"
                                    />
                                </div>
                            </template>
                        </LabeledField>

                        <Message v-if="epFormError[kind]" severity="error" variant="simple" size="small">{{ epFormError[kind] }}</Message>

                        <div v-if="canSecret" class="flex flex-wrap items-center justify-end gap-2">
                            <span class="mr-auto text-xs text-muted-color">{{ epDirty[kind] ? t('publicData.changes') : t('publicData.noChanges') }}</span>
                            <Button
                                type="button"
                                :label="t('publicData.epReset')"
                                icon="pi pi-undo"
                                severity="secondary"
                                variant="text"
                                size="small"
                                :loading="working === `ep-reset-${kind}`"
                                :disabled="working !== null || !epHasCustom(kind)"
                                @click="resetEndpoint(kind)"
                            />
                            <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" :disabled="working !== null || !epDirty[kind]" @click="fillEndpointDraft(kind)" />
                            <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="working === `ep-save-${kind}`" :disabled="working !== null || !epDirty[kind]" />
                        </div>

                        <!-- 특일 정보 연결 시험 — 국세청 연결 시험과 같은 모양 -->
                        <div v-if="kind === 'holiday'" class="flex flex-col gap-3 border-t border-(--line) pt-4">
                            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('publicData.epHolidayTestHelp') }}</p>
                            <Message v-if="view.endpoints.holiday.lastTest" :severity="view.endpoints.holiday.lastTest.result === 'OK' ? 'success' : 'error'" :closable="false" size="small" data-testid="public-data-holiday-test-result">
                                <b>{{ t(`publicData.epHolidayTestResult.${view.endpoints.holiday.lastTest.result}`) }}</b>
                                <span class="mt-0.5 block text-xs tabular-nums">{{ view.endpoints.holiday.lastTest.testedBy ? t('publicData.testedBy', { at: time.full(view.endpoints.holiday.lastTest.testedAt), name: view.endpoints.holiday.lastTest.testedBy }) : t('publicData.testedAt', { at: time.full(view.endpoints.holiday.lastTest.testedAt) }) }}</span>
                            </Message>
                            <div>
                                <Button type="button" :label="t('publicData.testRun')" icon="pi pi-bolt" severity="secondary" variant="outlined" :loading="working === 'holiday-test'" :disabled="working !== null || dirty || epDirty.holiday" data-testid="public-data-holiday-test" @click="holidayTest" />
                                <span v-if="dirty" class="ml-2 text-xs text-muted-color">{{ t('publicData.testSaveFirst') }}</span>
                                <span v-else-if="epDirty.holiday" class="ml-2 text-xs text-muted-color">{{ t('publicData.epTestSaveFirst') }}</span>
                            </div>
                        </div>
                    </form>
                </div>
            </AppGadget>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { PublicDataEndpointField, PublicDataEndpointKind, PublicDataEndpoints, PublicDataSettingsView } from '~/composables/publicdata/api/usePublicDataSettingsApi.types';

/** API 주소 칸 이름 — 서버 칸 오류 키와 같다 */
type EpField = 'url' | 'queryTemplate' | 'dateField' | 'nameField';

const EP_KINDS: readonly PublicDataEndpointKind[] = ['nts', 'holiday'];
const EP_FIELDS: Record<PublicDataEndpointKind, readonly EpField[]> = { nts: ['url'], holiday: ['url', 'queryTemplate', 'dateField', 'nameField'] };
/** 요청 글자 틀의 자리 — 문구의 `{…}` 와 섞이지 않게 값으로 넘긴다 */
const TEMPLATE_SLOTS = '{serviceKey} {year} {month} {pageNo} {numOfRows}';
const TEMPLATE_REQUIRED = '{serviceKey} {year} {month}';
const EMPTY_FIELD: PublicDataEndpointField = { value: '', defaultValue: '', custom: false };

function blankDraft(): Record<EpField, string> {
    return { url: '', queryTemplate: '', dateField: '', nameField: '' };
}

const { t } = useI18n();
useSeoMeta({ title: () => t('publicData.title') });

const api = usePublicDataSettingsApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();

/** 저장·지우기 권한 — 숨기는 것은 편의이고 막는 것은 서버다 */
const canSecret = computed(() => can('site.secret'));

const view = ref<PublicDataSettingsView | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
/** 입력한 새 키 — 이 화면의 메모리에만 있고 저장에 성공하면 비운다 */
const keyInput = ref('');
const keyError = ref('');
const formError = ref('');
const working = ref<'save' | 'remove' | 'test' | 'holiday-test' | `ep-save-${PublicDataEndpointKind}` | `ep-reset-${PublicDataEndpointKind}` | null>(null);

const dirty = computed(() => keyInput.value.trim().length > 0);
watch(keyInput, () => (keyError.value = ''));

// ── API 주소 — 입력칸은 직접 넣은 값(기본값을 쓰는 중이면 비움 — 흐린 글자가 기본값), 빈 칸으로 저장하면 기본값으로 돌아간다 ──
const epDraft = ref<Record<PublicDataEndpointKind, Record<EpField, string>>>({ nts: blankDraft(), holiday: blankDraft() });
const epErrors = ref<Record<PublicDataEndpointKind, Partial<Record<EpField, string>>>>({ nts: {}, holiday: {} });
const epFormError = ref<Record<PublicDataEndpointKind, string>>({ nts: '', holiday: '' });

function epField(kind: PublicDataEndpointKind, field: EpField): PublicDataEndpointField {
    const group = view.value?.endpoints?.[kind] as unknown as Partial<Record<EpField, PublicDataEndpointField>> | undefined;
    return group?.[field] ?? EMPTY_FIELD;
}

/** 저장된 값 기준의 입력칸 글자 */
function savedText(kind: PublicDataEndpointKind, field: EpField): string {
    const f = epField(kind, field);
    return f.custom ? f.value : '';
}

const epDirty = computed<Record<PublicDataEndpointKind, boolean>>(() => ({
    nts: EP_FIELDS.nts.some((f) => epDraft.value.nts[f].trim() !== savedText('nts', f)),
    holiday: EP_FIELDS.holiday.some((f) => epDraft.value.holiday[f].trim() !== savedText('holiday', f)),
}));

function epHasCustom(kind: PublicDataEndpointKind): boolean {
    return EP_FIELDS[kind].some((f) => epField(kind, f).custom);
}

function epHelp(field: EpField): string {
    if (field === 'url') return t('publicData.epUrlHelp');
    if (field === 'queryTemplate') return t('publicData.epTemplateHelp', { slots: TEMPLATE_SLOTS, required: TEMPLATE_REQUIRED });
    return t('publicData.epFieldNameHelp');
}

/** 입력칸을 저장된 값으로 채운다(되돌리기 · 처음 받았을 때 · 저장한 뒤) */
function fillEndpointDraft(kind: PublicDataEndpointKind): void {
    const next = blankDraft();
    for (const f of EP_FIELDS[kind]) next[f] = savedText(kind, f);
    epDraft.value = { ...epDraft.value, [kind]: next };
    epErrors.value = { ...epErrors.value, [kind]: {} };
    epFormError.value = { ...epFormError.value, [kind]: '' };
}

function clearEpError(kind: PublicDataEndpointKind, field: EpField): void {
    epFormError.value = { ...epFormError.value, [kind]: '' };
    if (!epErrors.value[kind][field]) return;
    const next = { ...epErrors.value[kind] };
    delete next[field];
    epErrors.value = { ...epErrors.value, [kind]: next };
}

function applyEndpoints(endpoints: PublicDataEndpoints, kind: PublicDataEndpointKind): void {
    if (view.value) view.value = { ...view.value, endpoints };
    fillEndpointDraft(kind);
}

async function saveEndpoint(kind: PublicDataEndpointKind): Promise<void> {
    if (!canSecret.value || working.value || !view.value || !epDirty.value[kind]) return;
    const draft = epDraft.value[kind];
    working.value = `ep-save-${kind}`;
    epErrors.value = { ...epErrors.value, [kind]: {} };
    epFormError.value = { ...epFormError.value, [kind]: '' };
    try {
        const endpoints =
            kind === 'nts'
                ? await api.saveNtsEndpoint({ url: draft.url.trim() })
                : await api.saveHolidayEndpoint({ url: draft.url.trim(), queryTemplate: draft.queryTemplate.trim(), dateField: draft.dateField.trim(), nameField: draft.nameField.trim() });
        applyEndpoints(endpoints, kind);
        toast.success(t('publicData.epSaved'));
    } catch (e) {
        const error = toApiError(e);
        const own: Partial<Record<EpField, string>> = {};
        for (const f of EP_FIELDS[kind]) {
            const message = error.field(f);
            if (message) own[f] = message;
        }
        if (Object.keys(own).length) epErrors.value = { ...epErrors.value, [kind]: own };
        else epFormError.value = { ...epFormError.value, [kind]: error.message };
    } finally {
        working.value = null;
    }
}

async function resetEndpoint(kind: PublicDataEndpointKind): Promise<void> {
    if (!canSecret.value || working.value) return;
    const ok = await confirm({ title: t('publicData.epResetTitle'), message: t('publicData.epResetMessage', { name: t(`publicData.epGroup.${kind}`) }), confirmLabel: t('publicData.epReset'), tone: 'danger' });
    if (!ok || working.value) return;
    working.value = `ep-reset-${kind}`;
    epFormError.value = { ...epFormError.value, [kind]: '' };
    try {
        applyEndpoints(await api.resetEndpoint(kind), kind);
        toast.success(t('publicData.epResetDone'));
    } catch (e) {
        epFormError.value = { ...epFormError.value, [kind]: toApiError(e).message };
    } finally {
        working.value = null;
    }
}

async function holidayTest(): Promise<void> {
    if (working.value || !view.value) return;
    working.value = 'holiday-test';
    try {
        const lastTest = await api.holidayTest();
        if (view.value) view.value = { ...view.value, endpoints: { ...view.value.endpoints, holiday: { ...view.value.endpoints.holiday, lastTest } } };
    } catch (e) {
        toast.error(toApiError(e).message);
    } finally {
        working.value = null;
    }
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        view.value = await api.get();
        for (const kind of EP_KINDS) fillEndpointDraft(kind);
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

async function save(): Promise<void> {
    if (!canSecret.value || working.value || !view.value || !dirty.value) return;
    const value = keyInput.value.trim();
    if (/\s/.test(value)) {
        keyError.value = t('publicData.noSpaces');
        return;
    }
    working.value = 'save';
    formError.value = '';
    try {
        view.value = await api.save({ serviceKey: value });
        keyInput.value = '';
        toast.success(t('publicData.saved'));
    } catch (e) {
        const error = toApiError(e);
        const field = error.field('serviceKey');
        if (field) keyError.value = field;
        else {
            if (error.code === 'CRYPTO_KEY_MISSING' && view.value) view.value = { ...view.value, cryptoKeyConfigured: false };
            formError.value = error.message;
        }
    } finally {
        working.value = null;
    }
}

async function remove(): Promise<void> {
    if (working.value) return;
    const ok = await confirm({ title: t('publicData.removeTitle'), message: t('publicData.removeMessage'), confirmLabel: t('publicData.remove'), tone: 'danger' });
    if (!ok || working.value) return;
    working.value = 'remove';
    formError.value = '';
    try {
        await api.removeServiceKey();
        toast.success(t('publicData.removed'));
        view.value = await api.get();
    } catch (e) {
        formError.value = toApiError(e).message;
    } finally {
        working.value = null;
    }
}

async function test(): Promise<void> {
    if (working.value || !view.value) return;
    working.value = 'test';
    try {
        const lastTest = await api.test();
        view.value = { ...view.value, lastTest };
    } catch (e) {
        toast.error(toApiError(e).message);
    } finally {
        working.value = null;
    }
}

// 저장하지 않은 입력이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => {
    if (!dirty.value && !epDirty.value.nts && !epDirty.value.holiday) return true;
    return confirm({ title: t('publicData.leaveTitle'), message: t('publicData.leaveMessage'), confirmLabel: t('publicData.leaveConfirm'), tone: 'danger' });
});

onMounted(() => {
    void load();
});
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.public-data-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
