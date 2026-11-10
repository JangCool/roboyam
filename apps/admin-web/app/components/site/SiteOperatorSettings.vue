<template>
    <!--
        설정 > 운영·약관 > 운영 주체 화면(`/settings/operator`, 구획 주소 `site-operator`) — 사이트를 운영하는 사람·사업자. 약관 본문의 자리표시(`{{운영자}}` 등 — `UtilTermsPlaceholder`)는 **게시할 때** 이 값으로 바뀌어 저장된다(게시본은 고치지 않으므로 지난 판 글자는 그대로).
          단계 라디오 카드 3개(개인 · 개인사업자 · 법인) → 공통 칸(운영자 이름·연락 이메일·전화·보호책임자·호스팅·메일 발송) → 사업자 단계만 사업자 칸(상호/회사명·대표자·사업자등록번호·법인등록번호(법인)·사업장 주소·통신판매업 신고).
          개인 단계는 애드센스 안내를 보인다. 단계를 바꾸고 저장하면 확인 창(새 약관 게시 필요, 법인은 회원 알림 의무 덧붙임). 아래 전환 이력 표(날짜 · 이전 → 이후 · 처리자).
          사업자 칸은 개인 단계로 바꿔도 입력칸에는 남는다(되돌려 볼 때 지운 듯 보이지 않게) — 보내는 값에서만 빠진다(`UtilSiteOperator.request`).
          개인사업자로 바꿀 때 대표자가 비어 있으면 운영자 이름으로 미리 채운다.
          사업자 칸 아래(저장 줄 위) 「푸터 안내 문구」 구획(2026-11-10) — 공개 푸터 안내 줄(여러 줄 입력, 최대 3줄 · 줄마다 120자, 비우면 숨김)과 보이기(개인 단계에서만 · 늘). 같은 저장 줄로 함께 저장한다.
        서버 API: `useSiteOperatorApi`(`GET·PUT /api/admin/site/operator`). 사업자 정보의 정본이다 — 커머스 설정의 사업자 정보 구획은 겹치는 칸을 여기서 읽어 읽기 전용으로 보인다.
        서버 거절: `fields.*`·`fields["business.*"]` 는 그 칸 아래, 칸 밖(`fields.stage`, 판매 중 개인 단계 전환·신고번호 비우기 `409 COMMERCE_SALES_OPEN`)은 저장 줄 위 한 줄 — 모두 서버 문구 그대로.
        다른 구획과 따로 읽고 저장한다(구획 안 저장 줄 — forms.md 2-4). 권한: 조회 `site.read`, 변경 `site.manage` — 없으면 저장 줄이 사라지고 입력칸은 `readonly`.
    -->
    <AppGadget :title="t('operatorStage.title')" :anchor="ANCHOR" :changes="changeCount" :invalid="invalidCount">
        <div v-if="loading && !saved" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <Skeleton height="4.5rem" width="min(48rem, 100%)" />
            <Skeleton height="2.25rem" width="min(24rem, 100%)" />
            <Skeleton height="2.25rem" width="min(24rem, 100%)" />
        </div>

        <ErrorState v-else-if="!saved" :title="t('operatorStage.loadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />

        <form v-else class="flex flex-col gap-5" novalidate @submit.prevent="save">
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('operatorStage.intro') }}</p>

            <!-- 단계 -->
            <div class="flex flex-col gap-1.5">
                <span :id="`${uid}-stage`" class="text-sm font-medium">{{ t('operatorStage.stageLabel') }}</span>
                <div role="radiogroup" :aria-labelledby="`${uid}-stage`" class="grid gap-2 sm:grid-cols-3">
                    <label
                        v-for="stage in SITE_OPERATOR_STAGES"
                        :key="stage"
                        class="flex items-start gap-2.5 rounded-lg border p-3 transition-colors"
                        :class="[
                            form.stage === stage ? 'border-primary bg-primary-50 dark:bg-primary-950/40' : 'border-(--line)',
                            readonly ? 'cursor-default' : 'cursor-pointer hover:bg-(--p-content-hover-background)',
                        ]"
                    >
                        <RadioButton :model-value="form.stage" :value="stage" :name="`${uid}-stage`" :disabled="readonly" class="mt-0.5" @update:model-value="onStage" />
                        <span class="flex min-w-0 flex-col gap-0.5">
                            <span class="text-sm font-medium">{{ t(`operatorStage.stage.${stage}`) }}</span>
                            <span class="text-xs leading-relaxed text-muted-color">{{ t(`operatorStage.stageHelp.${stage}`) }}</span>
                        </span>
                    </label>
                </div>
                <Message v-if="form.stage === 'INDIVIDUAL'" severity="info" :closable="false" size="small">{{ t('operatorStage.individualNotice') }}</Message>
            </div>

            <!-- 공통 칸 -->
            <div class="flex flex-col gap-4">
                <h3 class="m-0 text-xs font-semibold text-muted-color">{{ t('operatorStage.commonTitle') }}</h3>

                <LabeledField :label="t('operatorStage.operatorName')" :hint="t('operatorStage.lengthHint', { max: UtilSiteOperator.NAME_MAX })" :help="t('operatorStage.operatorNameHelp')" :error="errorOf('operatorName')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText v-model="form.operatorName" :id="id" name="operatorName" autocomplete="name" size="small" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" class="w-full max-w-sm" @update:model-value="clearServer('operatorName')" />
                    </template>
                </LabeledField>

                <LabeledField :label="t('operatorStage.contactEmail')" :error="errorOf('contactEmail')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText v-model="form.contactEmail" :id="id" name="contactEmail" type="email" inputmode="email" autocomplete="email" size="small" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" class="w-full max-w-sm" @update:model-value="clearServer('contactEmail')" />
                    </template>
                </LabeledField>

                <LabeledField :label="t('operatorStage.phone')" :hint="isBusiness ? t('operatorStage.phoneRequired') : t('operatorStage.phoneOptional')" :error="errorOf('phone')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText v-model="form.phone" :id="id" name="phone" type="tel" inputmode="tel" autocomplete="tel" size="small" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" :aria-required="isBusiness" class="w-full max-w-xs" @update:model-value="clearServer('phone')" />
                    </template>
                </LabeledField>

                <div class="flex flex-col gap-3">
                    <div class="flex items-start gap-2.5">
                        <Checkbox v-model="form.privacyOfficerSameAsOperator" binary :input-id="`${uid}-same`" :disabled="readonly" :aria-describedby="`${uid}-same-help`" class="mt-0.5" />
                        <div class="flex min-w-0 flex-col gap-0.5">
                            <label :for="`${uid}-same`" class="text-sm" :class="readonly ? '' : 'cursor-pointer'">{{ t('operatorStage.officerSame') }}</label>
                            <p :id="`${uid}-same-help`" class="m-0 text-xs text-muted-color">{{ t('operatorStage.officerSameHelp') }}</p>
                        </div>
                    </div>
                    <div v-if="!form.privacyOfficerSameAsOperator" class="flex flex-col gap-4 border-l-2 border-(--line) pl-4">
                        <LabeledField :label="t('operatorStage.officerName')" :hint="t('operatorStage.lengthHint', { max: UtilSiteOperator.NAME_MAX })" :error="errorOf('privacyOfficerName')">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText v-model="form.privacyOfficerName" :id="id" name="privacyOfficerName" autocomplete="off" size="small" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" class="w-full max-w-sm" @update:model-value="clearServer('privacyOfficerName')" />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('operatorStage.officerEmail')" :error="errorOf('privacyOfficerEmail')">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText v-model="form.privacyOfficerEmail" :id="id" name="privacyOfficerEmail" type="email" inputmode="email" autocomplete="off" size="small" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" class="w-full max-w-sm" @update:model-value="clearServer('privacyOfficerEmail')" />
                            </template>
                        </LabeledField>
                    </div>
                </div>

                <LabeledField :label="t('operatorStage.hostingProvider')" :hint="t('operatorStage.optional')" :error="errorOf('hostingProvider')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText v-model="form.hostingProvider" :id="id" name="hostingProvider" autocomplete="off" size="small" :placeholder="t('operatorStage.hostingProviderPlaceholder')" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" class="w-full max-w-sm" @update:model-value="clearServer('hostingProvider')" />
                    </template>
                </LabeledField>

                <LabeledField :label="t('operatorStage.mailProvider')" :hint="t('operatorStage.optional')" :error="errorOf('mailProvider')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText v-model="form.mailProvider" :id="id" name="mailProvider" autocomplete="off" size="small" :placeholder="t('operatorStage.mailProviderPlaceholder')" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" class="w-full max-w-sm" @update:model-value="clearServer('mailProvider')" />
                    </template>
                </LabeledField>
            </div>

            <!-- 사업자 칸 — 개인사업자·법인만 -->
            <div v-if="isBusiness" class="flex flex-col gap-4">
                <h3 class="m-0 text-xs font-semibold text-muted-color">{{ t('operatorStage.businessTitle') }}</h3>

                <LabeledField :label="isCorporation ? t('operatorStage.companyName') : t('operatorStage.businessName')" :error="errorOf('businessName')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText v-model="form.businessName" :id="id" name="businessName" autocomplete="organization" size="small" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" class="w-full max-w-sm" @update:model-value="clearServer('businessName')" />
                    </template>
                </LabeledField>

                <LabeledField :label="t('operatorStage.representative')" :error="errorOf('representative')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText v-model="form.representative" :id="id" name="representative" autocomplete="off" size="small" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" class="w-full max-w-sm" @update:model-value="clearServer('representative')" />
                    </template>
                </LabeledField>

                <LabeledField :label="t('operatorStage.registrationNumber')" :help="t('operatorStage.registrationNumberHelp')" :error="errorOf('registrationNumber')">
                    <template #default="{ id, describedBy, invalid }">
                        <!-- 입력할 때마다 3-2-5 로 하이픈을 맞춘다 — 같은 값이어도 입력칸 글자를 맞춘 값으로 되돌린다(글자를 쳐도 남지 않게) -->
                        <InputText
                            :model-value="form.registrationNumber"
                            :id="id"
                            name="registrationNumber"
                            inputmode="numeric"
                            autocomplete="off"
                            size="small"
                            placeholder="123-45-67890"
                            maxlength="12"
                            :readonly="readonly"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                            aria-required="true"
                            class="w-full max-w-[12rem] tabular-nums"
                            @input="onBizNumberInput"
                        />
                    </template>
                </LabeledField>

                <LabeledField v-if="isCorporation" :label="t('operatorStage.corporateNumber')" :hint="t('operatorStage.optional')" :error="errorOf('corporateNumber')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText v-model="form.corporateNumber" :id="id" name="corporateNumber" inputmode="numeric" autocomplete="off" size="small" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" class="w-full max-w-[14rem] tabular-nums" @update:model-value="clearServer('corporateNumber')" />
                    </template>
                </LabeledField>

                <LabeledField :label="t('operatorStage.address')" :hint="t('operatorStage.lengthHint', { max: UtilSiteOperator.ADDRESS_MAX })" :error="errorOf('address')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText v-model="form.address" :id="id" name="address" autocomplete="street-address" size="small" :placeholder="t('operatorStage.addressPlaceholder')" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" class="w-full max-w-xl" @update:model-value="clearServer('address')" />
                    </template>
                </LabeledField>

                <div class="flex flex-col gap-1.5">
                    <div class="grid gap-4 sm:max-w-xl sm:grid-cols-2">
                        <LabeledField :label="t('operatorStage.mailOrderNumber')" :hint="t('operatorStage.optional')" :error="errorOf('mailOrderNumber')">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText v-model="form.mailOrderNumber" :id="id" name="mailOrderNumber" autocomplete="off" size="small" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy ? `${describedBy} ${uid}-mail-help` : `${uid}-mail-help`" fluid @update:model-value="clearServer('mailOrderNumber')" />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('operatorStage.mailOrderAuthority')" :hint="t('operatorStage.optional')" :error="errorOf('mailOrderAuthority')">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText v-model="form.mailOrderAuthority" :id="id" name="mailOrderAuthority" autocomplete="off" size="small" :placeholder="t('operatorStage.mailOrderAuthorityPlaceholder')" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy ? `${describedBy} ${uid}-mail-help` : `${uid}-mail-help`" fluid @update:model-value="clearServer('mailOrderAuthority')" />
                            </template>
                        </LabeledField>
                    </div>
                    <p :id="`${uid}-mail-help`" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('operatorStage.mailOrderHelp') }}</p>
                </div>
            </div>

            <!-- 푸터 「사업자 신청」 링크(사용자 2026-11-10) — 계정 메뉴에서 옮긴 고정 링크(공개 푸터 「고객 지원」 끝)를 켜고 끈다. 기본 켬, 이 화면의 [저장]으로 함께 -->
            <div class="flex flex-col gap-1.5">
                <h3 class="m-0 text-xs font-semibold text-muted-color">{{ t('operatorStage.footerTitle') }}</h3>
                <div class="flex items-center gap-2">
                    <ToggleSwitch v-model="form.footerBusinessApply" :input-id="`${uid}-biz-apply`" :disabled="readonly" data-testid="operator-footer-business-apply" />
                    <label :for="`${uid}-biz-apply`" class="text-sm" :class="readonly ? 'cursor-default' : 'cursor-pointer'">{{ t('operatorStage.footerBusinessApply') }}</label>
                </div>
                <small class="text-xs leading-relaxed text-muted-color">{{ t('operatorStage.footerBusinessApplyHint') }}</small>
            </div>

            <!-- 푸터 안내 문구(2026-11-10) — 공개 푸터의 안내 줄. 한 줄에 한 문장(최대 3줄 · 줄마다 120자), 비우면 숨김. `{사이트}` 는 공개 푸터가 사이트 이름 + 맞춘 조사로 바꾼다. 보이기: 개인 단계에서만 · 늘 -->
            <div class="flex flex-col gap-4">
                <h3 class="m-0 text-xs font-semibold text-muted-color">{{ t('operatorStage.footerNoticeTitle') }}</h3>

                <LabeledField
                    :label="t('operatorStage.footerNotice')"
                    :hint="t('operatorStage.footerNoticeHint', { lines: UtilSiteOperator.NOTICE_LINES_MAX, max: UtilSiteOperator.NOTICE_LINE_MAX })"
                    :help="t('operatorStage.footerNoticeHelp')"
                    :error="errorOf('footerNotice')"
                >
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea v-model="form.footerNotice" :id="id" name="footerNotice" rows="3" auto-resize :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" class="w-full max-w-xl" @update:model-value="clearServer('footerNotice')" />
                    </template>
                </LabeledField>

                <div class="flex flex-col gap-1.5">
                    <span :id="`${uid}-notice-scope`" class="text-sm font-medium">{{ t('operatorStage.footerNoticeScopeLabel') }}</span>
                    <div role="radiogroup" :aria-labelledby="`${uid}-notice-scope`" class="flex flex-wrap gap-x-5 gap-y-2">
                        <label v-for="scope in SITE_FOOTER_NOTICE_SCOPES" :key="scope" class="flex items-center gap-2 text-sm" :class="readonly ? 'cursor-default' : 'cursor-pointer'">
                            <RadioButton v-model="form.footerNoticeScope" :value="scope" :name="`${uid}-notice-scope`" :disabled="readonly" />
                            {{ t(`operatorStage.footerNoticeScope.${scope}`) }}
                        </label>
                    </div>
                </div>
            </div>

            <Message v-if="formError" severity="error" variant="simple" size="small">{{ formError }}</Message>

            <div v-if="canManage" class="flex flex-wrap items-center justify-end gap-2">
                <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" :disabled="saving || changeCount === 0" @click="reset" />
                <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="saving" :disabled="changeCount === 0" />
            </div>

            <!-- 전환 이력 -->
            <div class="flex flex-col gap-2 border-t border-(--line) pt-4">
                <h3 class="m-0 text-xs font-semibold text-muted-color">{{ t('operatorStage.historyTitle') }}</h3>
                <p v-if="!saved.history.length" class="m-0 text-xs text-muted-color">{{ t('operatorStage.historyEmpty') }}</p>
                <table v-else class="w-full max-w-2xl border-collapse text-xs">
                    <thead>
                        <tr class="border-b border-(--line) text-left text-muted-color">
                            <th scope="col" class="py-1.5 pr-4 font-medium">{{ t('operatorStage.colChangedAt') }}</th>
                            <th scope="col" class="py-1.5 pr-4 font-medium">{{ t('operatorStage.colChange') }}</th>
                            <th scope="col" class="py-1.5 font-medium">{{ t('operatorStage.colChangedBy') }}</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="(row, index) in saved.history" :key="`${row.changedAt}-${index}`" class="border-b border-(--line) last:border-b-0">
                            <td class="py-1.5 pr-4 tabular-nums text-muted-color">{{ time.full(row.changedAt) }}</td>
                            <td class="py-1.5 pr-4">{{ t('operatorStage.changeLine', { from: t(`operatorStage.stage.${row.fromStage}`), to: t(`operatorStage.stage.${row.toStage}`) }) }}</td>
                            <td class="py-1.5">{{ row.changedBy ?? t('operatorStage.unknownOperator') }}</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </form>
    </AppGadget>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import { SITE_FOOTER_NOTICE_SCOPES, SITE_OPERATOR_STAGES, type SiteOperatorProfile, type SiteOperatorResponse, type SiteOperatorStage } from '~/composables/site/api/useSiteOperatorApi.types';
import UtilSiteOperator, { type SiteOperatorField, type SiteOperatorForm } from '~/utils/util-site-operator';
import UtilSiteOperatorMessages from '~/utils/util-site-operator-messages';

/** 구획 주소 — `#site-operator` 로 들어오면 읽은 뒤 이 구획으로 옮긴다 */
const ANCHOR = 'site-operator';

const { t } = useI18n();
const api = useSiteOperatorApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const time = useDisplayTime();
const route = useRoute();
const canManage = computed(() => can('site.manage'));

const uid = useId();

const saved = ref<SiteOperatorResponse | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const form = reactive<SiteOperatorForm>(UtilSiteOperator.formOf(emptyProfile()));
/** 저장을 눌러 본 적이 있는가 — 그 전에는 "비어 있음" 문제를 빨갛게 보이지 않는다 */
const tried = ref(false);
/** 서버가 거절한 칸 문구(열쇠는 입력칸 이름) — 그 칸을 고치면 지운다 */
const serverErrors = ref<Partial<Record<SiteOperatorField, string>>>({});
const formError = ref('');

const readonly = computed(() => !canManage.value || saving.value);
const isBusiness = computed(() => UtilSiteOperator.isBusiness(form.stage));
const isCorporation = computed(() => form.stage === 'CORPORATION');

const problems = computed(() => UtilSiteOperator.validate(form));
const changeCount = computed(() => (saved.value ? UtilSiteOperator.changes(saved.value, form) : 0));
/** 보이는 칸의 오류 수 — 구역 내비의 빨강 숫자 */
const invalidCount = computed(() => UtilSiteOperator.FIELDS.filter((field) => errorOf(field)).length);

function emptyProfile(): SiteOperatorProfile {
    return { stage: 'INDIVIDUAL', operatorName: '', contactEmail: '', phone: null, privacyOfficerSameAsOperator: true, privacyOfficerName: null, privacyOfficerEmail: null, hostingProvider: null, mailProvider: null, business: null, footerNotice: null, footerNoticeScope: 'INDIVIDUAL_ONLY', footerBusinessApply: true };
}

/** 칸 아래 한 줄 — 화면이 본 문제(「비었음」은 저장을 눌러 본 뒤부터) → 없으면 서버 문구 그대로 */
function errorOf(field: SiteOperatorField): string {
    const problem = problems.value[field];
    if (problem && (problem !== 'REQUIRED' || tried.value)) return UtilSiteOperatorMessages.problem(t, field, problem, form.stage);
    return serverErrors.value[field] ?? '';
}

function clearServer(field: SiteOperatorField): void {
    if (serverErrors.value[field]) serverErrors.value = { ...serverErrors.value, [field]: '' };
}

/** 단계를 골랐다 — 개인사업자로 바꿀 때 대표자가 비어 있으면 운영자 이름으로 미리 채운다 */
function onStage(stage: SiteOperatorStage): void {
    if (readonly.value) return;
    form.stage = stage;
    if (stage === 'SOLE_PROPRIETOR' && !form.representative.trim()) form.representative = form.operatorName.trim();
}

/** 사업자등록번호 — 칠 때마다 숫자만 남겨 3-2-5 로 끊고, 입력칸 글자도 그 값으로 맞춘다 */
function onBizNumberInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const formatted = UtilSiteOperator.formatBizNumber(input.value);
    if (input.value !== formatted) input.value = formatted;
    form.registrationNumber = formatted;
    clearServer('registrationNumber');
}

function fill(res: SiteOperatorResponse): void {
    saved.value = res;
    Object.assign(form, UtilSiteOperator.formOf(res));
    tried.value = false;
    serverErrors.value = {};
    formError.value = '';
}

function reset(): void {
    if (saved.value) fill(saved.value);
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        fill(await api.get());
        // 약관 화면의 링크(`#site-operator`)로 들어왔으면 읽은 뒤 이 구획으로 — 내용이 늦게 그려져 라우터의 해시 이동이 모자랄 수 있다
        if (route.hash === `#${ANCHOR}`) {
            await nextTick();
            document.getElementById(ANCHOR)?.scrollIntoView({ block: 'start', behavior: 'smooth' });
        }
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

async function save(): Promise<void> {
    if (saving.value || !canManage.value || !saved.value || changeCount.value === 0) return;
    tried.value = true;
    if (Object.keys(problems.value).length > 0) return;
    const from = saved.value.stage;
    const to = form.stage;
    if (from !== to) {
        const ok = await confirm({
            title: t('operatorStage.changeConfirmTitle', { from: t(`operatorStage.stage.${from}`), to: t(`operatorStage.stageTo.${to}`) }),
            message: UtilSiteOperatorMessages.changeMessage(t, to),
            confirmLabel: t('operatorStage.changeConfirmLabel'),
            tone: to === 'CORPORATION' ? 'danger' : 'primary',
        });
        if (!ok) return;
    }
    saving.value = true;
    formError.value = '';
    try {
        // 평범한 객체로 보낸다(`request` 가 새 객체를 만든다 — _frontend-core 2-3)
        fill(await api.save(UtilSiteOperator.request(form)));
        toast.success(t('operatorStage.saved'));
    } catch (e) {
        const error = toApiError(e);
        const next: Partial<Record<SiteOperatorField, string>> = {};
        for (const field of UtilSiteOperator.FIELDS) {
            const message = error.field(UtilSiteOperator.fieldKey(field));
            if (message) next[field] = message;
        }
        serverErrors.value = next;
        // 칸에 붙지 않는 오류만 아래 한 줄에 — 단계(`fields.stage`) · 판매 중이라 막힘(409 COMMERCE_SALES_OPEN) 등, 서버 문구를 그대로 보인다
        const stageError = error.field('stage');
        if (stageError) formError.value = stageError;
        else if (Object.keys(next).length === 0) formError.value = error.message;
    } finally {
        saving.value = false;
    }
}

onBeforeRouteLeave(async () => {
    if (changeCount.value === 0) return true;
    return confirm({ title: t('operatorStage.leaveTitle'), message: t('operatorStage.leaveMessage'), confirmLabel: t('operatorStage.leaveConfirm'), tone: 'danger' });
});

void load();
</script>
