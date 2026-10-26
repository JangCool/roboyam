<template>
    <!--
        포인트 상점 › 포인트 상점 설정(`/point-shop/settings`) — 지금은 셀프 환불 기한(1~30일, 기본 7) 한 칸(계약 B절 `point_shop_settings.refund_days`, commerce.md 7-3).
        실제 서버(`usePointShopAdminApi` — `GET/PUT /api/admin/point-shop/settings`)를 부른다.
        권한: 조회 `pointshop.read`(메뉴), 저장 `pointshop.manage`(없으면 저장 줄 대신 읽기 전용 안내, 칸은 `readonly`). 권한 코드는 `util-permission-catalog.ts`.
        저장은 한 번에(PUT) — 떠 있는 저장 줄(`StickySaveBar`, `forms.md` 2-4). 칸은 `InputText` + `inputmode=numeric`(`InputNumber` 는 범위 밖 값을 말없이 고친다).
        범위 검사는 `UtilPointShop`(서버와 같은 1~30) — 틀리면 저장 단추가 꺼진다. 서버가 거절한 칸(400 `fields.refundDays`)은 칸 아래 서버 문구, 칸에 붙지 않는 것은 저장 줄 안.
        **가정(정책 미확정)**: 기한을 바꾸면 그 뒤 구매뿐 아니라 아직 기한 안의 구매에도 적용된다 — 구매 시점 값이 아니라 지금 값으로 판정한다(서버 판정을 따른다). 화면에 안내로 적었다.
        변경 이력 서랍(`components.md` 2-5)은 이력 API 가 계약에 없어 두지 않았다(감사 로그에는 남는다 — 일반 운영).
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('pointShop.settingsTitle')" :subtitle="t('pointShop.settingsSubtitle')" />

        <AppGadget v-if="loading && !saved" :title="t('pointShop.settings.refundSection')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 2" :key="n" height="3.5rem" />
            </div>
        </AppGadget>

        <AppGadget v-else-if="!saved" :title="t('pointShop.settings.refundSection')">
            <ErrorState :code="loadError?.status === 403 ? '403' : '!'" :title="loadError?.status === 403 ? t('pointShop.forbidden') : t('pointShop.settings.loadFailed')" :description="loadError?.message" :retry="loadError?.status !== 403" :retrying="loading" @retry="load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <AppGadget :title="t('pointShop.settings.refundSection')" anchor="point-shop-refund" :changes="dirty ? 1 : 0" :invalid="errorText ? 1 : 0">
                <div class="flex flex-col gap-4">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('pointShop.settings.refundLead') }}</p>

                    <LabeledField :label="t('pointShop.field.refundDays')" :hint="t('pointShop.range', { min: UtilPointShop.REFUND_DAYS.min, max: UtilPointShop.REFUND_DAYS.max })" :error="errorText" :help="t('pointShop.settings.refundHelp')">
                        <template #default="{ id, describedBy, invalid }">
                            <div class="flex flex-wrap items-center gap-2">
                                <InputText
                                    :id="id"
                                    :model-value="refundDays"
                                    name="refundDays"
                                    inputmode="numeric"
                                    autocomplete="off"
                                    :readonly="!canManage || saving"
                                    :invalid="invalid"
                                    :aria-describedby="describedBy"
                                    :aria-required="true"
                                    class="w-20 text-right tabular-nums"
                                    @update:model-value="onInput"
                                />
                                <span class="text-sm text-muted-color">{{ t('pointShop.unitDays') }}</span>
                                <template v-if="UtilPointShop.parseInt(refundDays) !== saved.defaultRefundDays">
                                    <span class="text-xs text-muted-color">{{ t('pointShop.settings.defaultValue', { value: saved.defaultRefundDays }) }}</span>
                                    <Button v-if="canManage" type="button" :label="t('pointShop.settings.useDefault')" icon="pi pi-replay" size="small" severity="secondary" variant="text" class="px-1! py-0!" :disabled="saving" @click="onInput(String(saved.defaultRefundDays))" />
                                </template>
                            </div>
                        </template>
                    </LabeledField>

                    <Message severity="info" :closable="false" size="small">{{ t('pointShop.settings.assumption') }}</Message>

                    <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-xs leading-relaxed text-muted-color">
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointShop.settings.noteRule') }}</span></li>
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointShop.settings.noteNotice') }}</span></li>
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointShop.settings.noteException') }}</span></li>
                    </ul>

                    <p class="m-0 text-xs text-muted-color">
                        {{ saved.updatedAt ? t('pointShop.settings.lastSaved', { at: time.full(saved.updatedAt), by: saved.updatedBy ?? '—' }) : t('pointShop.settings.neverSaved') }}
                    </p>
                </div>
            </AppGadget>

            <StickySaveBar v-if="canManage" submit revertable :dirty="dirty" :saving="saving" :disabled="!canSave" :count="dirty ? 1 : 0" :error-count="errorText ? 1 : 0" :error="formError" @revert="revert" />
            <p v-else class="text-xs text-muted-color">{{ t('pointShop.settings.readOnly') }}</p>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { PointShopSettingsView } from '~/composables/pointshop/api/usePointShopAdminApi.types';
import UtilPointShop from '~/utils/util-point-shop';
import UtilPointShopMessages from '~/utils/util-point-shop-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('pointShop.settingsTitle') });

const api = usePointShopAdminApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();

/** 저장 권한(`util-permission-catalog.ts`) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('pointshop.manage'));

const saved = ref<PointShopSettingsView | null>(null);
const refundDays = ref('');
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');
const serverError = ref('');

const problem = computed(() => UtilPointShop.refundDaysProblem(refundDays.value));
const errorText = computed(() => (problem.value ? UtilPointShopMessages.refundDaysProblem(t, problem.value) : serverError.value));
/** 숫자로 읽어 비교한다(「07」과 「7」은 같다), 읽을 수 없는 글자는 바뀐 것으로 본다 */
const dirty = computed(() => saved.value !== null && UtilPointShop.parseInt(refundDays.value) !== saved.value.refundDays);
const canSave = computed(() => canManage.value && dirty.value && !problem.value);

function onInput(value: string | undefined): void {
    refundDays.value = value ?? '';
    serverError.value = '';
}

onMounted(() => {
    void load();
});

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

function applySaved(view: PointShopSettingsView): void {
    saved.value = view;
    refundDays.value = String(view.refundDays);
    serverError.value = '';
    formError.value = '';
}

function revert(): void {
    if (saved.value) applySaved(saved.value);
}

async function save(): Promise<void> {
    if (saving.value || !canSave.value) return;
    const value = UtilPointShop.parseInt(refundDays.value);
    if (value === null) return;
    saving.value = true;
    formError.value = '';
    serverError.value = '';
    try {
        applySaved(await api.saveSettings({ refundDays: value }));
        toast.success(t('pointShop.settings.saved'));
    } catch (e) {
        const failure = toApiError(e);
        const message = failure.code === 'VALIDATION' ? failure.field('refundDays') : undefined;
        if (message) {
            serverError.value = message;
            formError.value = t('pointShop.saveFieldsRejected');
        } else {
            formError.value = `${t('pointShop.saveFailed')} — ${failure.message}`;
        }
    } finally {
        saving.value = false;
    }
}

onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('pointShop.leaveTitle'), message: t('pointShop.leaveMessage'), confirmLabel: t('pointShop.leaveConfirm'), cancelLabel: t('pointShop.leaveCancel'), tone: 'danger' });
});
</script>
