<template>
    <!--
        등급 설정 — **등급 목록**(이름·순서·승급 조건)과 등급 표시(뱃지 색·아이콘), 수동으로 올린 회원의 이후 동작을 정한다. community 관리자 앱의 `grades.vue` 구조를 따르되 등급을 **목록 기반**(4단계 고정이 아님)으로 넓혔다.
        등급은 순서 있는 목록이고 이 화면이 원천이다. 등급 **역할**(그 등급이 무엇을 할 수 있나)은 이 목록에서 자동으로 만들어지고, 역할 관리 › 등급 역할(`/settings/roles/grade`)에서는 권한만 정한다.
        community 와 다른 점: 등급을 추가·삭제·순서 이동할 수 있고(첫 등급 = 기본 등급은 삭제·이동·조건 편집 불가, 이름·뱃지는 편집 가능), 출하 완료글(인기글) 수 칸은 없다(roboyam 에 같은 개념이 아직 없다). 새로고침 단추도 두지 않았다.
        수동으로 올린 회원 옵션·소급 영향 확인·뱃지는 roboyam 정책(2026-10-21 확정)이다.
        구획: 승급 조건(`grade-rules`, 등급 행 — 이름·조건·순서·삭제·추가) · 등급 표시(`grade-badge`, 행마다 미리보기·색·아이콘) · 수동으로 올린 회원(`grade-manual`, 라디오 둘 — 수동으로 올린 회원에게만 적용되는 옵션) · 안내·동작(`grade-notes`).
        **저장은 한 번에**(등급 목록 + 뱃지 + 옵션, PUT 한 번) — [저장]·[되돌리기]·상태 글·저장 실패 문구는 구획 안이 아니라 `<form>` 맨 끝, 페이지 맨 아래에 창 아래로 떠 있는 줄(`StickySaveBar`)에 둔다(`forms.md` 2-4. 구획 안에 두면 "그 구획만 저장된다"로 읽힌다). 서버는 목록을 diff 해서 추가·삭제·순서·수정을 처리한다.
        조건 칸은 글자 입력(`InputText` + `inputmode=numeric`)이다 — PrimeVue `InputNumber` 는 범위를 벗어난 값을 말없이 바꿔 버리고 `aria-describedby` 가 입력칸에 붙지 않아, 칸 아래 오류 문구로 알리려면 글자 입력이 맞다.
        뱃지 색·아이콘 고르개는 **같은 이름의 네이티브 라디오**(`input[type=radio]`)를 시각적으로 숨기고 라벨을 견본으로 그린다 — Tab 으로 묶음에 들어가 화살표로 고르는 키보드 동작을 브라우저가 맡는다(PrimeVue `RadioButton` 은 이름 연결·묶음 이동이 라벨 한 칸 모양에 맞지 않는다).
        **저장 전 영향 확인**: 기준이 하나라도 낮아졌거나 등급을 추가·삭제했거나 옵션을 "계속 자동 승급"으로 바꾸면 미리보기(`api.preview`)로 지금 승급될 인원과 이동할 회원 수를 받아 확인 대화상자로 보이고, 승급·이동이 모두 없으면 확인 없이 저장한다.
        미리보기가 실패하면 저장을 막지 않고 "영향 인원을 확인하지 못했어요. 그래도 저장할까요?" 로 묻는다. 기준을 올리기만 한 저장은 확인 없이 저장하고 토스트로 영향이 없음을 알린다. 시점은 구현 가정이다(활동 조건의 소급은 저장 직후, 일수 조건의 소급은 다음 배치).
        등급 삭제: 단추를 누르면 목록에서만 뺀다(되돌리기로 복구). **저장 시점**에 지우는 등급과 이동할 회원 수를 소급 승급 확인과 **하나의 확인 대화상자**로 묻는다("「단골」 등급을 삭제하면 회원 N명이 「일반」 등급으로 이동돼요. 되돌릴 수 없어요." — 확정 정책: 회원은 바로 아래 등급으로 이동).
        등급 수는 최소 2 · 최대 10단계(확정) — 10개에서는 「등급 추가」가 비활성이고 안내(툴팁·글자)가 나온다.
        권한: 조회 `grade.read`(메뉴), 변경 `grade.manage` — 없으면 저장 줄·추가/삭제/순서 단추가 사라지고(저장 줄 자리에 읽기 전용 안내가 보인다) 입력칸은 `readonly`, 라디오는 `disabled`(서버 값이 그대로 보인다). 감추는 것은 편의이고 막는 것은 서버다.
        저장하지 않은 변경(등급 목록·옵션)이 있는 채 다른 화면으로 가면 확인을 묻는다(`onBeforeRouteLeave`).
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('grade.title')" :subtitle="t('grade.subtitle')" />

        <!-- 읽는 중 -->
        <AppGadget v-if="loading && !saved" :title="t('grade.rulesTitle')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 4" :key="n" height="3.5rem" />
            </div>
        </AppGadget>

        <!-- 읽기 실패 — 서버 문구를 그대로 보이고 다시 시도 -->
        <AppGadget v-else-if="!saved" :title="t('grade.rulesTitle')">
            <ErrorState :title="t('grade.loadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <AppGadget :title="t('grade.rulesTitle')" anchor="grade-rules" :changes="changes.rules" :invalid="ruleErrorCount">
                <p class="mb-4 text-xs leading-relaxed text-muted-color">{{ t('grade.rulesLead') }}</p>

                <ul class="m-0 flex list-none flex-col divide-y divide-(--line) border-y border-(--line) p-0">
                    <li v-for="(row, i) in form" :key="row.id" role="group" :aria-labelledby="`${idBase}-${row.id}-title`" class="flex flex-col gap-3 py-4">
                        <!-- 줄 머리 — 단계 · 기본/새 등급 표시 · 회원 수 · 순서·삭제 단추 -->
                        <div class="flex flex-wrap items-center gap-2">
                            <span :id="`${idBase}-${row.id}-title`" class="text-sm font-medium">{{ t('grade.levelLabel', { level: i + 1 }) }}</span>
                            <Tag v-if="i === 0" :value="t('grade.baseTag')" icon="pi pi-lock" severity="secondary" />
                            <Tag v-else-if="row.gradeKey === null" :value="t('grade.newTag')" severity="info" />
                            <span v-if="row.gradeKey !== null" class="text-xs tabular-nums text-muted-color">{{ t('grade.memberCount', { count: UtilGrade.format(row.memberCount) }) }}</span>
                            <div v-if="canManage && i > 0" class="ml-auto flex items-center gap-0.5">
                                <Button :id="`${idBase}-${row.id}-up`" type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" rounded :aria-label="t('grade.moveUp', { name: nameOf(row) })" :disabled="saving || !UtilGrade.canMove(form, i, -1)" @click="move(i, -1)" />
                                <Button :id="`${idBase}-${row.id}-down`" type="button" icon="pi pi-arrow-down" size="small" severity="secondary" variant="text" rounded :aria-label="t('grade.moveDown', { name: nameOf(row) })" :disabled="saving || !UtilGrade.canMove(form, i, 1)" @click="move(i, 1)" />
                                <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" rounded :aria-label="t('grade.removeGrade', { name: nameOf(row) })" :disabled="saving || !UtilGrade.canRemove(form, i)" @click="removeRow(i)" />
                            </div>
                        </div>

                        <!-- 입력 — 이름 · (기본 등급이 아니면) 가입 후 일수 · 글·댓글 합계 -->
                        <div class="grid gap-x-4 gap-y-3 sm:grid-cols-[repeat(3,minmax(0,16rem))]">
                            <LabeledField :label="t('grade.fieldLabel')" :hint="t('grade.labelHint', { max: UtilGrade.LABEL_MAX })" :error="errorOf(row, 'label')">
                                <template #default="{ id, describedBy, invalid }">
                                    <InputText
                                        :id="id"
                                        :model-value="row.label"
                                        :name="`${row.id}.label`"
                                        autocomplete="off"
                                        :placeholder="t('grade.labelPlaceholder')"
                                        :readonly="!canManage || saving"
                                        :invalid="invalid"
                                        :aria-describedby="describedBy"
                                        :aria-required="true"
                                        fluid
                                        @update:model-value="(value: string | undefined) => onInput(row, 'label', value ?? '')"
                                    />
                                </template>
                            </LabeledField>

                            <template v-if="i > 0">
                                <LabeledField v-for="field in FIELDS" :key="field" :label="t(field === 'minDays' ? 'grade.fieldDays' : 'grade.fieldActivity')" :help="helpOf(field)" :error="errorOf(row, field)">
                                    <template #default="{ id, describedBy, invalid }">
                                        <div class="flex items-center gap-2">
                                            <InputText
                                                :id="id"
                                                :model-value="row[field]"
                                                :name="`${row.id}.${field}`"
                                                inputmode="numeric"
                                                autocomplete="off"
                                                :readonly="!canManage || saving"
                                                :invalid="invalid"
                                                :aria-describedby="describedBy"
                                                :aria-required="true"
                                                class="w-32 text-right tabular-nums"
                                                @update:model-value="(value: string | undefined) => onInput(row, field, value ?? '')"
                                            />
                                            <span class="text-sm text-muted-color">{{ t(field === 'minDays' ? 'grade.unitDays' : 'grade.unitActivity') }}</span>
                                        </div>
                                    </template>
                                </LabeledField>
                            </template>
                            <div v-else class="flex flex-col justify-center gap-1 sm:col-span-2">
                                <p class="flex items-center gap-1.5 text-sm">
                                    <i class="pi pi-lock text-[11px] text-muted-color" aria-hidden="true" />
                                    {{ UtilGradeMessages.condition(t, { minDays: 0, minActivity: 0 }) }}
                                </p>
                                <p class="text-xs leading-relaxed text-muted-color">{{ t('grade.baseNote') }}</p>
                            </div>
                        </div>
                    </li>
                </ul>

                <!-- 추가 — 등급 수는 최소·최대가 있다 -->
                <div v-if="canManage" class="mt-4 flex flex-wrap items-center gap-3">
                    <!-- 비활성 단추는 마우스 이벤트가 없어 툴팁을 감싼 칸에 단다 -->
                    <span v-tooltip.top="form.length >= UtilGrade.MAX_GRADES ? t('grade.addLimit', { max: UtilGrade.MAX_GRADES }) : undefined" class="inline-flex">
                        <Button type="button" :label="t('grade.addGrade')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" :disabled="saving || form.length >= UtilGrade.MAX_GRADES" :aria-description="form.length >= UtilGrade.MAX_GRADES ? t('grade.addLimit', { max: UtilGrade.MAX_GRADES }) : undefined" @click="addRow" />
                    </span>
                    <span class="text-xs tabular-nums text-muted-color">{{ t('grade.gradeCount', { count: form.length, max: UtilGrade.MAX_GRADES }) }}</span>
                    <span v-if="form.length >= UtilGrade.MAX_GRADES" class="text-xs text-muted-color">{{ t('grade.addLimit', { max: UtilGrade.MAX_GRADES }) }}</span>
                    <span v-else-if="form.length <= UtilGrade.MIN_GRADES" class="text-xs text-muted-color">{{ t('grade.removeMinTip', { min: UtilGrade.MIN_GRADES }) }}</span>
                </div>
            </AppGadget>

            <AppGadget :title="t('grade.badgeTitle')" anchor="grade-badge" :changes="changes.badge" :invalid="badgeErrorCount">
                <p class="mb-4 text-xs leading-relaxed text-muted-color">{{ t('grade.badgeLead') }}</p>

                <ul class="m-0 flex list-none flex-col divide-y divide-(--line) border-y border-(--line) p-0">
                    <li v-for="row in form" :key="row.id" class="grid gap-x-4 gap-y-3 py-4 sm:grid-cols-[8rem_minmax(0,1fr)]">
                        <!-- 미리보기 = 실제 사용 모양(같은 `GradeBadge`) -->
                        <div class="flex flex-col gap-1">
                            <span class="text-xs text-muted-color">{{ t('grade.badgePreview') }}</span>
                            <div><GradeBadge :label="nameOf(row)" :color="row.color" :icon="row.icon || null" /></div>
                        </div>

                        <div class="flex min-w-0 flex-col gap-3">
                            <div>
                                <div :id="`${idBase}-${row.id}-color-label`" class="mb-1.5 text-sm font-medium">{{ t('grade.badgeColor') }}</div>
                                <div role="radiogroup" :aria-label="t('grade.badgeColorGroup', { name: nameOf(row) })" class="flex flex-wrap gap-2">
                                    <label v-for="c in UtilGrade.BADGE_COLORS" :key="c.name" class="swatch" :class="{ 'swatch--disabled': !canManage || saving }" :style="UtilGrade.badgeStyle(c.name)">
                                        <input v-model="row.color" type="radio" class="sr-only" :name="`${idBase}-${row.id}-color`" :value="c.name" :disabled="!canManage || saving" />
                                        <i v-if="row.color === c.name" class="pi pi-check text-[10px]" aria-hidden="true" />
                                        <span>{{ t(`grade.color.${c.name}`) }}</span>
                                    </label>
                                </div>
                                <p v-if="serverCellOf(row, 'badgeColor')" role="alert" class="mt-1.5 text-xs text-red-600 dark:text-red-400">{{ serverCellOf(row, 'badgeColor') }}</p>
                            </div>

                            <div>
                                <div :id="`${idBase}-${row.id}-icon-label`" class="mb-1.5 text-sm font-medium">{{ t('grade.badgeIcon') }}</div>
                                <div role="radiogroup" :aria-label="t('grade.badgeIconGroup', { name: nameOf(row) })" class="flex flex-wrap gap-1.5">
                                    <label class="icon-opt" :class="{ 'icon-opt--disabled': !canManage || saving }">
                                        <input v-model="row.icon" type="radio" class="sr-only" :name="`${idBase}-${row.id}-icon`" value="" :disabled="!canManage || saving" :aria-label="t('grade.iconNone')" />
                                        <i class="pi pi-ban" aria-hidden="true" />
                                    </label>
                                    <label v-for="icon in UtilGrade.BADGE_ICONS" :key="icon" class="icon-opt" :class="{ 'icon-opt--disabled': !canManage || saving }">
                                        <input v-model="row.icon" type="radio" class="sr-only" :name="`${idBase}-${row.id}-icon`" :value="icon" :disabled="!canManage || saving" :aria-label="t(`grade.icon.${icon.replace('pi pi-', '')}`)" />
                                        <i :class="icon" aria-hidden="true" />
                                    </label>
                                </div>
                                <p v-if="serverCellOf(row, 'badgeIcon')" role="alert" class="mt-1.5 text-xs text-red-600 dark:text-red-400">{{ serverCellOf(row, 'badgeIcon') }}</p>
                            </div>
                        </div>
                    </li>
                </ul>
            </AppGadget>

            <AppGadget :title="t('grade.manualTitle')" anchor="grade-manual" :changes="policyChanged ? 1 : 0" :invalid="serverPolicyError ? 1 : 0">
                <p class="mb-4 text-xs leading-relaxed text-muted-color">{{ t('grade.manualLead') }}</p>

                <!-- 수동으로 올린 회원의 이후 동작 — 둘 중 하나 -->
                <div role="radiogroup" :aria-label="t('grade.manualTitle')" class="flex flex-col gap-3">
                    <div v-for="value in POLICIES" :key="value" class="flex items-start gap-3">
                        <RadioButton v-model="policy" :input-id="`${idBase}-policy-${value}`" name="manualPolicy" :value="value" :disabled="!canManage || saving" :invalid="Boolean(serverPolicyError)" class="mt-0.5 shrink-0" />
                        <label :for="`${idBase}-policy-${value}`" class="flex min-w-0 flex-col gap-0.5" :class="canManage && !saving ? 'cursor-pointer' : ''">
                            <span class="text-sm font-medium">{{ t(POLICY_TEXT[value].label) }}</span>
                            <span class="text-xs leading-relaxed text-muted-color">{{ t(POLICY_TEXT[value].help) }}</span>
                        </label>
                    </div>
                </div>
                <p v-if="serverPolicyError" role="alert" class="mt-2 text-xs text-red-600 dark:text-red-400">{{ serverPolicyError }}</p>
            </AppGadget>

            <AppGadget :title="t('grade.notesTitle')" anchor="grade-notes">
                <ul class="m-0 flex list-none flex-col gap-2 p-0 text-sm">
                    <li v-for="key in NOTES" :key="key" class="flex items-start gap-2">
                        <i class="pi pi-info-circle mt-1 text-xs text-muted-color" aria-hidden="true" />
                        <span>{{ t(key) }}</span>
                    </li>
                </ul>
            </AppGadget>

            <!-- 저장 줄 — 구획 밖, 모든 구획 아래. 등급 목록·뱃지·옵션을 한 번에 저장한다. 저장 실패(칸에 붙지 않는 것)는 이 줄 안에 선다 -->
            <StickySaveBar v-if="canManage" submit revertable :dirty="dirty" :saving="saving" :disabled="!canSave" :count="changeCount" :error-count="errorCount" :error="formError" @revert="revert" />
            <p v-else class="text-xs text-muted-color">{{ t('grade.readOnlyNote') }}</p>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { Grade, GradeManualPolicy, GradePreviewRequest, GradePreviewResponse, GradesResponse } from '~/composables/grade/api/useGradeApi.types';
import type { GradeCellField, GradeField, GradeFieldErrors, GradeRowForm } from '~/utils/util-grade';
import UtilGrade from '~/utils/util-grade';
import UtilGradeMessages from '~/utils/util-grade-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('grade.title') });

const api = useGradeApi();
const appearance = useGradeAppearance();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const idBase = useId();

/** 이 화면에서 하는 일에 필요한 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('grade.manage'));

const FIELDS = UtilGrade.FIELDS;
const POLICIES = UtilGrade.POLICIES;
/** 옵션 라디오의 문구 키 */
const POLICY_TEXT: Record<GradeManualPolicy, { label: string; help: string }> = {
    CONTINUE_AUTO: { label: 'grade.policyContinueAuto', help: 'grade.policyContinueAutoHelp' },
    STOP_AUTO: { label: 'grade.policyStopAuto', help: 'grade.policyStopAutoHelp' },
};
/** 안내·동작 구획의 문구 키 — 등급 목록이 원천 · 판정 시점(활동 즉시 · 일수 배치) · 올리기만 · 소급 */
const NOTES = ['grade.noteSource', 'grade.noteActivity', 'grade.noteDays', 'grade.noteUpOnly', 'grade.noteRetro'] as const;

/** 서버가 준 등급 목록(저장된 값). `null` = 아직 못 받았다 */
const saved = ref<Grade[] | null>(null);
const savedPolicy = ref<GradeManualPolicy>(UtilGrade.DEFAULT_POLICY);
/** 입력 행 — 목록 순서가 곧 서열이다 */
const form = ref<GradeRowForm[]>([]);
/** 수동으로 올린 회원 옵션의 입력값 */
const policy = ref<GradeManualPolicy>(UtilGrade.DEFAULT_POLICY);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
/** 칸에 붙지 않는 저장 오류 */
const formError = ref('');
/** 서버가 거절한 칸별 문구(행 id → 칸) — 그 칸을 고치면 지운다 */
const serverCells = ref<GradeFieldErrors['cells']>({});
/** 서버가 거절한 옵션 문구 — 옵션을 고치면 지운다 */
const serverPolicyError = ref('');
/** 새 행 번호 — 화면 안 식별자용 */
let newSeq = 0;

const problems = computed(() => UtilGrade.validate(form.value));
const changes = computed(() => (saved.value ? UtilGrade.changes(form.value, saved.value) : { rules: 0, badge: 0 }));
const policyChanged = computed(() => policy.value !== savedPolicy.value);
/** 저장하지 않은 변경 칸 수 — 등급 목록(이름·조건·추가·삭제·순서) + 뱃지 + 옵션 */
const changeCount = computed(() => changes.value.rules + changes.value.badge + (policyChanged.value ? 1 : 0));
const dirty = computed(() => changeCount.value > 0);

/** 서버가 거절한 칸 중 뱃지 칸(색·아이콘)과 나머지(이름·승급 조건)의 수 */
const serverBadgeErrorCount = computed(() => Object.values(serverCells.value).reduce((sum, cells) => sum + Number(Boolean(cells?.badgeColor)) + Number(Boolean(cells?.badgeIcon)), 0));
const serverRuleErrorCount = computed(() => Object.values(serverCells.value).reduce((sum, cells) => sum + Number(Boolean(cells?.label)) + Number(Boolean(cells?.minDays)) + Number(Boolean(cells?.minActivity)), 0));
const ruleErrorCount = computed(() => UtilGrade.problemCount(problems.value) + serverRuleErrorCount.value);
const badgeErrorCount = computed(() => serverBadgeErrorCount.value);
/** 맞지 않는 칸 수 — 화면이 미리 검사한 것 + 서버가 거절한 것 */
const errorCount = computed(() => ruleErrorCount.value + badgeErrorCount.value + (serverPolicyError.value ? 1 : 0));
/** 서버가 거절한 칸은 저장을 막지 않는다 — 고치지 않고 다시 보내 볼 수 있어야 하고, 서버가 다시 판정한다 */
const canSave = computed(() => canManage.value && dirty.value && UtilGrade.problemCount(problems.value) === 0);

watch(policy, () => {
    serverPolicyError.value = '';
});

onMounted(load);

async function load() {
    loading.value = true;
    loadError.value = null;
    try {
        applySaved(await api.load());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

/** 서버가 인정한 값으로 저장된 값과 입력 행을 맞추고, 앱 전체가 공유하는 등급 목록도 새 값으로 올린다 */
function applySaved(res: GradesResponse) {
    saved.value = res.grades;
    savedPolicy.value = res.manualPolicy;
    form.value = UtilGrade.formOf(res.grades);
    policy.value = res.manualPolicy;
    serverCells.value = {};
    serverPolicyError.value = '';
    appearance.set(res.grades);
}

function revert() {
    if (!saved.value) return;
    form.value = UtilGrade.formOf(saved.value);
    policy.value = savedPolicy.value;
    serverCells.value = {};
    serverPolicyError.value = '';
    formError.value = '';
}

/** 행의 표시 이름 — 이름을 비웠으면 "(이름 없는 등급)" */
function nameOf(row: GradeRowForm): string {
    return row.label.trim() || t('grade.unnamedGrade');
}

function helpOf(field: GradeField): string {
    const [min, max] = UtilGrade.limitsOf(field);
    return t(field === 'minDays' ? 'grade.helpDays' : 'grade.helpActivity', { min: UtilGrade.format(min), max: UtilGrade.format(max) });
}

function serverCellOf(row: GradeRowForm, field: GradeCellField): string {
    return serverCells.value[row.id]?.[field] ?? '';
}

/** 칸 아래에 보일 오류 — 화면이 미리 검사한 문제가 먼저, 없으면 서버가 거절한 문구 */
function errorOf(row: GradeRowForm, field: 'label' | GradeField): string {
    const problem = problems.value[row.id]?.[field];
    if (problem) return UtilGradeMessages.problem(t, problem, field, UtilGrade.lowerOf(form.value, row.id, field as GradeField));
    return serverCellOf(row, field);
}

function onInput(row: GradeRowForm, field: 'label' | GradeField, value: string) {
    row[field] = value;
    const cells = serverCells.value[row.id];
    if (!cells?.[field]) return;
    const { [field]: _removed, ...rest } = cells;
    serverCells.value = { ...serverCells.value, [row.id]: rest };
}

/** 등급을 한 칸 위·아래로 옮긴다 — 옮긴 뒤에도 키보드 초점이 그 단추에 남게 한다 */
async function move(index: number, step: -1 | 1) {
    const row = form.value[index];
    if (!row || !UtilGrade.canMove(form.value, index, step)) return;
    form.value = UtilGrade.moved(form.value, index, step);
    await nextTick();
    const at = form.value.findIndex((r) => r.id === row.id);
    const first = document.getElementById(`${idBase}-${row.id}-${step === -1 ? 'up' : 'down'}`) as HTMLButtonElement | null;
    const other = document.getElementById(`${idBase}-${row.id}-${step === -1 ? 'down' : 'up'}`) as HTMLButtonElement | null;
    (first && !first.disabled ? first : other)?.focus();
    void at;
}

/** 등급을 목록에서 뺀다 — 저장해야 반영되고(되돌리기로 복구), 이동할 회원 수는 저장 시점의 확인 대화상자가 보인다 */
function removeRow(index: number) {
    const row = form.value[index];
    if (!row || !UtilGrade.canRemove(form.value, index)) return;
    form.value = form.value.filter((r) => r.id !== row.id);
}

/** 맨 아래에 새 등급을 더하고 이름 칸에 초점을 둔다 */
async function addRow() {
    if (form.value.length >= UtilGrade.MAX_GRADES) return;
    const row = UtilGrade.newRow(form.value, ++newSeq);
    form.value = [...form.value, row];
    await nextTick();
    document.querySelector<HTMLInputElement>(`input[name="${row.id}.label"]`)?.focus();
}

/**
 * 저장 전 영향을 확인한다 — 미리보기로 지금 승급될 인원과 지우는 등급에서 이동할 회원 수를 받아 **한 번에** 묻고, 둘 다 없으면 묻지 않는다.
 * 미리보기가 실패하면 저장을 막지 않고 "그래도 저장할까요?" 로 묻는다.
 * @returns 저장해도 되면 true.
 */
async function confirmImpact(body: GradePreviewRequest, lowered: Record<GradeField, boolean>, manualUnlocked: boolean, added: boolean, removed: boolean): Promise<boolean> {
    const title = t(removed ? 'grade.confirmTitleRemove' : 'grade.confirmTitle');
    let preview: GradePreviewResponse;
    try {
        preview = await api.preview(body);
    } catch {
        return confirm({ title, message: t('grade.previewFailedMessage'), confirmLabel: t('grade.previewFailedConfirm'), cancelLabel: t('common.cancel') });
    }
    if (preview.total === 0 && preview.willMove.length === 0) return true;
    return confirm({
        title,
        message: UtilGradeMessages.impactConfirm(t, preview, lowered, manualUnlocked, added),
        confirmLabel: t(preview.willMove.length ? 'grade.confirmSaveRemove' : 'grade.confirmSave'),
        cancelLabel: t('common.cancel'),
        tone: preview.willMove.length ? 'danger' : 'primary',
    });
}

async function save() {
    if (saving.value || !canSave.value || !saved.value) return;
    const body = UtilGrade.toRequest(form.value, policy.value);
    if (!body) return;
    formError.value = '';
    serverCells.value = {};
    serverPolicyError.value = '';
    saving.value = true;
    // 서버가 거절하면 칸 오류를 붙일 행 — 요청 `grades` 의 차례와 같다
    const sent = [...form.value];
    try {
        // 기준이 낮아졌거나 등급을 추가·삭제했거나 옵션을 "계속 자동 승급"으로 바꾸면 소급 승급·회원 이동이 일어나므로 먼저 영향을 확인한다
        const lowered = UtilGrade.lowered(form.value, saved.value);
        const added = UtilGrade.hasAdded(form.value);
        const removed = UtilGrade.hasRemoved(form.value, saved.value);
        const manualUnlocked = policy.value === 'CONTINUE_AUTO' && savedPolicy.value !== 'CONTINUE_AUTO';
        const affects = lowered.minDays || lowered.minActivity || added || removed || manualUnlocked;
        const raisedOnly = changes.value.rules > 0 && !affects;
        if (affects && !(await confirmImpact(body, lowered, manualUnlocked, added, removed))) return;

        applySaved(await api.save(body));
        toast.success(t(raisedOnly ? 'grade.savedNoImpact' : 'grade.saved'));
    } catch (e) {
        const rejected = UtilGrade.fieldErrorsOf(e, sent);
        if (rejected) {
            serverCells.value = rejected.cells;
            serverPolicyError.value = rejected.policy;
            formError.value = [Object.keys(rejected.cells).length || rejected.policy ? t('grade.saveFieldsRejected') : '', rejected.form].filter(Boolean).join(' ');
        } else {
            formError.value = `${t('grade.saveFailed')} — ${toApiError(e).message}`;
        }
    } finally {
        saving.value = false;
    }
}

// 저장하지 않은 변경(등급 목록·뱃지·옵션)이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('grade.leaveTitle'), message: t('grade.leaveMessage'), confirmLabel: t('grade.leaveConfirm'), cancelLabel: t('grade.leaveCancel'), tone: 'danger' });
});
</script>

<style scoped>
/* 뱃지 색 견본 — 실제 뱃지와 같은 색 변수(`--gb-bg`·`--gb-fg`, 인라인으로 주입). 선택은 체크 아이콘 + 윤곽이라 색만으로 구분하지 않고, 이름 글자도 함께 보인다 */
.swatch {
    display: inline-flex;
    align-items: center;
    gap: 0.3rem;
    padding: 0.25rem 0.75rem;
    border-radius: 9999px;
    background: var(--gb-bg);
    color: var(--gb-fg);
    font-size: 0.75rem;
    font-weight: 600;
    line-height: 1.25;
    cursor: pointer;
}
.swatch:has(input:checked) {
    outline: 2px solid var(--p-text-color);
    outline-offset: 2px;
}
.swatch:has(input:focus-visible) {
    box-shadow: 0 0 0 5px var(--p-focus-ring-color, var(--p-primary-color));
}
.swatch--disabled {
    cursor: default;
}
.swatch:has(input:disabled:not(:checked)) {
    opacity: 0.6;
}

/* 뱃지 아이콘 고르개 — 정사각 단추. 선택은 윤곽 + 바탕 */
.icon-opt {
    display: inline-grid;
    place-items: center;
    width: 2.25rem;
    height: 2.25rem;
    border: 1px solid var(--line);
    border-radius: 0.5rem;
    color: var(--p-text-color);
    cursor: pointer;
}
.icon-opt:hover:not(.icon-opt--disabled) {
    background: var(--p-content-hover-background);
}
.icon-opt:has(input:checked) {
    outline: 2px solid var(--p-text-color);
    outline-offset: 1px;
    background: var(--p-content-hover-background);
}
.icon-opt:has(input:focus-visible) {
    box-shadow: 0 0 0 4px var(--p-focus-ring-color, var(--p-primary-color));
}
.icon-opt--disabled {
    cursor: default;
}
.icon-opt:has(input:disabled:not(:checked)) {
    opacity: 0.6;
}
</style>
