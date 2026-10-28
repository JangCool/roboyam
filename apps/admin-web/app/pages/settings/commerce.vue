<template>
    <!--
        커머스 설정(`/settings/commerce`) — 판매·결제에 공통으로 쓰는 설정(`docs/domain/commerce.md` 13-5). 구획: ① 요청 제한 ② 판매 스위치·오픈 체크리스트 ③ 사업자 정보 ④ 배송비 정책 ⑤ 택배사 ⑥ 공구 설정
        (② ~ ⑥ 은 커머스 2b — `components/commerce/Commerce*Section.vue`, 지금 목업). **구획마다 따로 저장한다**(구획마다 자기 API — 구획 맨 아래 저장 줄 `SaveBar`, `forms.md` 2-4).
        요청 제한(12-4): 회원 한 명이 1분 동안 보낼 수 있는 돈 관련 요청 수(공구 참여 · 충전 요청 · 상점 구매 · 환불 신청, 차례 고정). 0~100 정수, 0 = 제한 없음. 넘으면 서버가 429 + 몇 초 뒤 가능한지 알린다.
        권한 `commerce.settings`(메뉴 권한이 없으면 셸이 이 화면을 그리지 않는다) — 판매 **켜기**만 최고 관리자. 막는 것은 서버(403)다.
        요청 제한 저장은 한 번에(PUT, 네 종류 모두) — 그 구획 맨 아래 저장 줄. 칸은 `InputText` + `inputmode=numeric`(`InputNumber` 는 범위 밖 값을 말없이 고친다).
        범위 검사는 `UtilCommerce`(서버와 같은 범위) — 하나라도 틀리면 저장 단추가 꺼진다. 서버가 거절한 칸(400 `VALIDATION` `fields.limits.<코드>`)은 그 칸 아래에 서버 문구 그대로, 칸에 붙지 않는 것은 저장 줄 안에 보인다.
        바꾼 값은 서버가 감사(전→후)에 남기고, 서버들은 설정 사본을 15초 안에 따라온다.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('commerce.settingsTitle')" :subtitle="t('commerce.settingsSubtitle')" />

        <!-- 읽는 중 -->
        <AppGadget v-if="loading && !saved" :title="t('commerce.limitsSection')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 4" :key="n" height="3.5rem" />
            </div>
        </AppGadget>

        <!-- 읽기 실패 — 서버 문구를 그대로 보이고 다시 시도 -->
        <AppGadget v-else-if="!saved" :title="t('commerce.limitsSection')">
            <ErrorState :code="loadError?.status === 403 ? '403' : '!'" :title="t('commerce.loadFailed')" :description="loadError?.message" :retry="loadError?.status !== 403" :retrying="loading" @retry="load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <!-- ① 요청 제한 -->
            <AppGadget :title="t('commerce.limitsSection')" anchor="commerce-request-limits" :changes="changed.length" :invalid="errorCount">
                <div class="flex flex-col gap-4">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('commerce.limitsLead') }}</p>

                    <Message severity="info" :closable="false" size="small">{{ t('commerce.limitsNotReady') }}</Message>

                    <ul class="m-0 flex list-none flex-col divide-y divide-(--line) rounded-lg border border-(--line) p-0">
                        <li v-for="action in UtilCommerce.ACTIONS" :key="action" class="flex flex-col gap-2 px-3 py-3 sm:flex-row sm:items-start sm:justify-between sm:gap-4" :data-changed="changed.includes(action) ? '' : undefined">
                            <div class="min-w-0 flex-1">
                                <label :for="idOf(action)" class="text-sm font-medium">{{ t(`commerce.actionName.${action}`) }}</label>
                                <p :id="`${idOf(action)}-desc`" class="m-0 mt-0.5 text-xs leading-relaxed text-muted-color">{{ t(`commerce.actionDesc.${action}`) }}</p>
                            </div>

                            <div class="flex flex-col gap-1 sm:items-end">
                                <div class="flex items-center gap-2">
                                    <Tag v-if="UtilCommerce.parse(form[action]) === 0" :value="t('commerce.unlimited')" icon="pi pi-minus-circle" severity="warn" />
                                    <InputText
                                        :id="idOf(action)"
                                        :model-value="form[action]"
                                        :name="`limits.${action}`"
                                        inputmode="numeric"
                                        autocomplete="off"
                                        :readonly="saving"
                                        :invalid="!!errorOf(action)"
                                        :aria-describedby="`${idOf(action)}-desc ${idOf(action)}-msg`"
                                        :aria-required="true"
                                        class="w-20 text-right tabular-nums"
                                        @update:model-value="(value: string | undefined) => onInput(action, value ?? '')"
                                    />
                                    <span class="whitespace-nowrap text-sm text-muted-color">{{ t('commerce.perMinute') }}</span>
                                </div>

                                <!-- 칸 아래 — 오류(화면 검사 · 서버 문구) / 바뀐 줄 표시 / 기본값과 다르면 「기본값 N」 + 되돌리기 -->
                                <div :id="`${idOf(action)}-msg`" aria-live="polite" class="flex flex-col gap-0.5 text-xs leading-relaxed sm:items-end">
                                    <p v-if="errorOf(action)" role="alert" class="m-0 text-red-600 dark:text-red-400">{{ errorOf(action) }}</p>
                                    <p v-else-if="changed.includes(action)" class="m-0 text-amber-700 dark:text-amber-400">{{ t('commerce.changedFrom', { value: savedOf(action) }) }}</p>
                                    <div v-if="defaultOf(action) !== null && UtilCommerce.parse(form[action]) !== defaultOf(action)" class="flex items-center gap-1 text-muted-color">
                                        <span>{{ t('commerce.defaultValue', { value: defaultOf(action) }) }}</span>
                                        <Button type="button" :label="t('commerce.useDefault')" icon="pi pi-replay" size="small" severity="secondary" variant="text" class="px-1! py-0!" :disabled="saving" @click="applyDefault(action)" />
                                    </div>
                                </div>
                            </div>
                        </li>
                    </ul>

                    <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-xs leading-relaxed text-muted-color">
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('commerce.noteApply') }}</span></li>
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('commerce.noteWindow') }}</span></li>
                    </ul>

                    <p class="m-0 text-xs text-muted-color">
                        {{ saved.updatedAt ? t('commerce.lastSaved', { at: time.full(saved.updatedAt), by: saved.updatedBy ?? '—' }) : t('commerce.neverSaved') }}
                    </p>

                    <!-- 구획마다 따로 저장하는 화면이 됐으므로(2b) 이 구획도 구획 맨 아래 저장 줄 — 동작은 그대로(PUT 한 번, 네 종류 모두) -->
                    <SaveBar :dirty="dirty" :saving="saving" :disabled="!canSave" :count="changed.length" :error-count="errorCount" :error="formError" @save="save" @revert="revert" />
                </div>
            </AppGadget>
        </form>

        <!-- ② ~ ⑥ 커머스 2b(공구) 구획 — 구획마다 따로 저장한다 -->
        <CommerceSalesSwitchSection ref="salesSection" can-edit />
        <CommerceBusinessInfoSection can-edit @dirty="(d: boolean) => setDirty('business', d)" @saved="salesSection?.reload()" />
        <CommerceShippingPolicySection can-edit @dirty="(d: boolean) => setDirty('policy', d)" />
        <CommerceCarrierSection can-edit @dirty="(d: boolean) => setDirty('carrier', d)" @saved="salesSection?.reload()" />
        <CommerceGroupBuySettingsSection can-edit @dirty="(d: boolean) => setDirty('groupBuy', d)" />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { RequestLimitAction, RequestLimitsView } from '~/composables/commerce/api/useCommerceSettingsApi.types';
import type { RequestLimitForm, RequestLimitProblem } from '~/utils/util-commerce';
import UtilCommerce from '~/utils/util-commerce';

const { t } = useI18n();
useSeoMeta({ title: () => t('commerce.settingsTitle') });

const api = useCommerceSettingsApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();

/** 줄마다의 입력칸 id — `useId()` 는 setup 에서 한 번만 부르고 코드를 붙인다 */
const baseId = useId();
const idOf = (action: RequestLimitAction) => `${baseId}-${action}`;

const saved = ref<RequestLimitsView | null>(null);
const form = ref<RequestLimitForm>({ GROUPBUY_JOIN: '', POINT_CHARGE: '', POINT_SHOP_PURCHASE: '', REFUND_REQUEST: '' });
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');
const serverErrors = ref<Partial<Record<RequestLimitAction, string>>>({});

const PROBLEM_KEYS: Record<RequestLimitProblem, string> = {
    REQUIRED: 'commerce.limitRequired',
    NOT_INTEGER: 'commerce.limitNotInteger',
    OUT_OF_RANGE: 'commerce.limitOutOfRange',
};

/** 화면이 미리 알리는 문제(판정은 서버) */
const problems = computed(() => {
    const out: Partial<Record<RequestLimitAction, RequestLimitProblem>> = {};
    for (const action of UtilCommerce.ACTIONS) {
        const problem = UtilCommerce.limitProblem(form.value[action]);
        if (problem) out[action] = problem;
    }
    return out;
});

function savedOf(action: RequestLimitAction): number | null {
    return saved.value?.items.find((item) => item.action === action)?.limitPerMinute ?? null;
}

function defaultOf(action: RequestLimitAction): number | null {
    return saved.value?.items.find((item) => item.action === action)?.defaultLimit ?? null;
}

/** 저장된 값과 다른 줄 — 숫자로 읽어 비교한다(「05」와 「5」는 같다), 읽을 수 없는 글자는 바뀐 것으로 본다 */
const changed = computed(() => (saved.value ? UtilCommerce.ACTIONS.filter((action) => UtilCommerce.parse(form.value[action]) !== savedOf(action)) : []));
const dirty = computed(() => changed.value.length > 0);
const errorCount = computed(() => UtilCommerce.ACTIONS.filter((action) => problems.value[action] || serverErrors.value[action]).length);
const canSave = computed(() => dirty.value && Object.keys(problems.value).length === 0);

function errorOf(action: RequestLimitAction): string {
    const problem = problems.value[action];
    if (problem) return t(PROBLEM_KEYS[problem]);
    return serverErrors.value[action] ?? '';
}

function onInput(action: RequestLimitAction, value: string): void {
    form.value = { ...form.value, [action]: value };
    clearServer(action);
}

function applyDefault(action: RequestLimitAction): void {
    const value = defaultOf(action);
    if (value !== null) onInput(action, String(value));
}

function clearServer(action: RequestLimitAction): void {
    if (!serverErrors.value[action]) return;
    const { [action]: _removed, ...rest } = serverErrors.value;
    serverErrors.value = rest;
}

onMounted(() => {
    void load();
});

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        applySaved(await api.requestLimits());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

function applySaved(view: RequestLimitsView): void {
    saved.value = view;
    form.value = UtilCommerce.formOf(view.items);
    serverErrors.value = {};
    formError.value = '';
}

function revert(): void {
    if (saved.value) applySaved(saved.value);
}

async function save(): Promise<void> {
    if (saving.value || !canSave.value) return;
    const limits = UtilCommerce.toLimits(form.value);
    if (!limits) return;

    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        applySaved(await api.saveRequestLimits({ limits }));
        toast.success(t('commerce.limitsSaved'));
    } catch (e) {
        const failure = toApiError(e);
        const fields: Partial<Record<RequestLimitAction, string>> = {};
        if (failure.code === 'VALIDATION') {
            for (const action of UtilCommerce.ACTIONS) {
                const message = failure.field(`limits.${action}`);
                if (message) fields[action] = message;
            }
        }
        serverErrors.value = fields;
        // 칸에 붙인 것이 있으면 저장 줄에는 「칸 아래를 보라」만, 칸에 붙지 않는 오류는 서버 문구 그대로
        formError.value = Object.keys(fields).length ? t('commerce.saveFieldsRejected') : failure.message;
    } finally {
        saving.value = false;
    }
}

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
// 2b 구획들 — 판매 스위치는 다른 구획 저장 뒤 자동 조건을 다시 읽는다. 바뀐 값이 남은 구획은 떠날 때 묻는다
const salesSection = ref<{ reload: () => Promise<void> } | null>(null);
const sectionDirty = ref<Record<string, boolean>>({});
function setDirty(key: string, value: boolean): void {
    sectionDirty.value = { ...sectionDirty.value, [key]: value };
}

onBeforeRouteLeave(async () => {
    if (!dirty.value && !Object.values(sectionDirty.value).some(Boolean)) return true;
    return confirm({ title: t('commerce.leaveTitle'), message: t('commerce.leaveMessage'), confirmLabel: t('commerce.leaveConfirm'), cancelLabel: t('commerce.leaveCancel'), tone: 'danger' });
});
</script>
