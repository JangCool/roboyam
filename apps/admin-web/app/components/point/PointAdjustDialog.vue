<template>
    <!--
        포인트 수동 조정 대화상자(`point.adjust`) — community `MemberPointAdjustSheet` 를 옮기고 **회원 고르기**를 더했다(community 는 회원 상세에서만 열려 대상이 정해져 있었다).
        위에서 아래로: 회원 찾기(닉네임·이메일·회원번호) + 후보 목록(지금 잔액) · 방향(지급/차감) · 포인트(1~100,000) · 사유(1~100자, 회원 내역에 공개) · 처리 후 예상 잔액.
        차감이 잔액보다 크면 "0P 까지만 차감돼요(실제 −N P)" 경고가 예상 칸에 선다(서버가 0P 바닥으로 자른다). 차감이면 회원에게 알림이 간다는 안내 한 줄.
        서버 호출은 하지 않는다(`components.md`) — 후보 찾기는 `search` 로 올리고(부모가 `usePointApi.memberCandidates`), 값은 `submit` 으로 올린다. 확인 대화상자·저장·토스트는 부모 몫이다.
        서버 오류는 해당 칸 아래(`fieldErrors.delta|reason`) 또는 폼 아래(`error`)에 서버 문구 그대로 보인다. 칸을 고치면 그 칸의 서버 문구는 숨긴다.
        커머스 1-1: 조정은 **적립(무상) 포인트만** — 금액 1~100,000, 문턱(`largeThreshold` — 부모가 정책에서 읽어 준다, 0 = 모든 조정) 초과는 `point.adjust.large`(`canLarge`)가 있으면 주의 안내(감사 기록 돈 관련 5년), 없으면 칸 오류로 막는다.
        문턱을 모르면(`null`) 미리 판정하지 않는다 — 서버 403 `POINT_ADJUST_LARGE_REQUIRED` 문구가 금액 칸 아래(`fieldErrors.delta`)에 선다.
        차감 예상은 적립 잔액(`freeBalance`) 기준 0P 바닥이고, 충전 포인트가 있는 회원은 잔액을 「적립 N · 충전 N」 으로 보인다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="t('point.adjust')" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <!-- 회원 고르기 -->
            <div class="flex flex-col gap-1.5">
                <label :for="searchId" class="text-sm font-medium">{{ t('point.adjustMember') }}</label>
                <IconField>
                    <InputIcon class="pi pi-search" aria-hidden="true" />
                    <InputText :id="searchId" v-model="query" type="search" maxlength="100" autocomplete="off" :placeholder="t('point.adjustMemberSearch')" :disabled="busy" fluid @keydown.enter.prevent="emitSearch(true)" />
                </IconField>

                <div role="radiogroup" :aria-label="t('point.adjustMemberList')" :aria-busy="searching" :aria-invalid="Boolean(memberError)" :aria-describedby="memberError ? memberErrorId : undefined" class="max-h-52 overflow-y-auto rounded-lg border border-(--line)">
                    <div v-if="searching && !candidates.length" class="flex flex-col gap-2 p-3" role="status" :aria-label="t('common.loading')">
                        <Skeleton v-for="n in 3" :key="n" height="2rem" />
                    </div>
                    <p v-else-if="!candidates.length" class="px-3 py-4 text-center text-sm text-muted-color">{{ t('point.adjustMemberEmpty') }}</p>
                    <template v-else>
                    <label
                        v-for="c in candidates"
                        :key="c.memberId"
                        class="flex cursor-pointer items-center gap-3 border-b border-(--line) px-3 py-2 last:border-b-0 hover:bg-(--p-content-hover-background)"
                        :class="{ 'bg-(--p-content-hover-background)': selected?.memberId === c.memberId, 'cursor-not-allowed opacity-60': busy }"
                    >
                        <RadioButton :model-value="selected?.memberId" :value="c.memberId" :name="`${formId}-member`" :disabled="busy" @update:model-value="select(c)" />
                        <span class="flex min-w-0 flex-1 flex-col">
                            <span class="truncate text-sm font-medium">{{ c.nickname }}</span>
                            <span class="truncate text-[11px] text-muted-color">
                                <code class="tabular-nums">{{ c.memberId }}</code>
                                <template v-if="c.email"> · {{ c.email }}</template>
                            </span>
                        </span>
                        <span class="shrink-0 text-xs tabular-nums text-muted-color">{{ UtilPointMessages.candidateBalance(t, c, locale) }}</span>
                    </label>
                    </template>
                </div>
                <p v-if="memberError" :id="memberErrorId" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ memberError }}</p>
                <!-- 고른 회원 — 찾기 결과가 바뀌어도 선택은 남는다 -->
                <p v-if="selected" class="flex flex-wrap items-center gap-x-2 text-xs">
                    <span class="text-muted-color">{{ t('point.adjustSelected') }}</span>
                    <span class="font-medium">{{ selected.nickname }}</span>
                    <span class="tabular-nums text-muted-color">{{ UtilPointMessages.candidateBalance(t, selected, locale) }}</span>
                </p>
            </div>

            <!-- 방향 -->
            <fieldset class="m-0 min-w-0 border-0 p-0">
                <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('point.adjustMode') }}</legend>
                <div class="flex flex-wrap gap-2">
                    <label
                        v-for="option in UtilPoint.ADJUST_MODES"
                        :key="option"
                        class="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm"
                        :class="[mode === option ? 'border-primary bg-primary-50 font-semibold dark:bg-primary-950' : 'border-(--line)', busy ? 'cursor-not-allowed opacity-60' : '']"
                    >
                        <RadioButton v-model="mode" :value="option" :name="`${formId}-mode`" :disabled="busy" />
                        {{ t(`point.mode.${option}`) }}
                    </label>
                </div>
            </fieldset>

            <!-- 포인트 — 범위를 넘은 값을 칸이 몰래 고치지 않게 글자 입력이다 -->
            <LabeledField :label="t('point.adjustAmount')" :hint="amountHint" :error="amountError">
                <template #default="{ id, describedBy, invalid }">
                    <div class="flex items-center gap-2">
                        <InputText :id="id" v-model="amountText" name="amount" inputmode="numeric" autocomplete="off" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" class="w-36 text-right tabular-nums" />
                        <span class="text-sm text-muted-color">{{ t('point.unit.point') }}</span>
                    </div>
                </template>
            </LabeledField>
            <!-- 문턱 초과 — 권한이 있으면 주의 안내(권한이 없으면 위 칸 오류로 막힌다) -->
            <p v-if="largeNotice" class="-mt-2 flex items-start gap-1.5 text-xs text-amber-700 dark:text-amber-400" role="status">
                <i class="pi pi-exclamation-triangle mt-0.5 text-xs" aria-hidden="true" />
                {{ t('point.adjustLargeNotice') }}
            </p>

            <!-- 사유 — 회원에게 공개된다 -->
            <LabeledField :label="t('point.adjustReason')" :hint="t('point.adjustReasonHint')" :error="reasonError" :help="t('point.adjustPublic')">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="reason" name="reason" rows="2" auto-resize :placeholder="t('point.adjustReasonPlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" fluid />
                </template>
                <template #aside>
                    <small class="shrink-0 text-xs tabular-nums" :class="reasonLength > UtilPoint.REASON_MAX ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">
                        {{ t('point.reasonCounter', { length: reasonLength, max: UtilPoint.REASON_MAX }) }}
                    </small>
                </template>
            </LabeledField>

            <!-- 처리 후 예상 — 회원과 금액이 정해졌을 때만 -->
            <div v-if="preview && selected" class="rounded-lg border border-(--line) px-3 py-2" role="status">
                <p class="text-xs text-muted-color">{{ t('point.previewTitle') }}</p>
                <p class="mt-0.5 text-sm font-semibold tabular-nums">{{ t('point.previewLine', { before: selected.balance.toLocaleString(locale), after: (selected.balance + preview.applied).toLocaleString(locale) }) }}</p>
                <!-- 충전 포인트가 있으면 적립 쪽 변화도 — 조정은 적립 포인트만 바꾼다 -->
                <p v-if="selected.paidBalance !== 0" class="mt-0.5 text-xs tabular-nums text-muted-color">{{ t('point.previewFreeLine', { before: selected.freeBalance.toLocaleString(locale), after: preview.after.toLocaleString(locale) }) }}</p>
                <p v-if="preview.clipped" class="mt-1 flex items-start gap-1.5 text-xs text-amber-700 dark:text-amber-400">
                    <i class="pi pi-exclamation-triangle mt-0.5 text-xs" aria-hidden="true" />
                    {{ t('point.previewClipped', { applied: Math.abs(preview.applied).toLocaleString(locale) }) }}
                </p>
            </div>

            <p class="flex items-start gap-1.5 text-xs text-muted-color">
                <i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" />
                {{ t('point.adjustFreeOnly') }}
            </p>

            <p v-if="mode === 'DEDUCT'" class="flex items-start gap-1.5 text-xs text-muted-color">
                <i class="pi pi-bell mt-0.5 text-xs" aria-hidden="true" />
                {{ t('point.adjustNotify') }}
            </p>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('point.adjustSubmit')" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { PointAdjustSubmit } from '~/components/point/PointAdjustDialog.types';
import type { PointMemberCandidate } from '~/composables/point/api/usePointApi.types';
import UtilPoint, { type PointAdjustMode } from '~/utils/util-point';
import UtilPointMessages from '~/utils/util-point-messages';

interface Props {
    visible: boolean;
    /** 회원 후보(부모가 `search` 를 받아 채운다) */
    candidates?: PointMemberCandidate[];
    /** 후보를 찾는 중 */
    searching?: boolean;
    /** 보내는 중 — 입력·단추가 잠긴다 */
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 칸마다 준 오류(`fields.delta`·`fields.reason`) */
    fieldErrors?: Record<string, string>;
    /** 고액 조정 권한(`point.adjust.large`) — 없으면 문턱을 넘는 금액을 칸 오류로 막는다 */
    canLarge?: boolean;
    /** 고액 조정 문턱(정책 `adjustLargeThreshold`, 0 = 모든 조정) — 모르면 null(미리 판정하지 않고 서버에 맡긴다) */
    largeThreshold?: number | null;
}

const props = withDefaults(defineProps<Props>(), { candidates: () => [], searching: false, busy: false, error: '', fieldErrors: () => ({}), canLarge: false, largeThreshold: null });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; search: [query: string]; submit: [payload: PointAdjustSubmit] }>();

const { t, locale } = useI18n();
const formId = useId();
const searchId = useId();
const memberErrorId = useId();

const query = ref('');
const selected = ref<PointMemberCandidate | null>(null);
const mode = ref<PointAdjustMode>('GRANT');
const amountText = ref('');
const reason = ref('');
/** [조정하기]를 한 번 눌렀다 — 그 뒤로는 빈 칸도 오류로 보인다 */
const submitted = ref(false);
/** 서버 문구를 숨긴 칸 — 그 칸을 고쳤다. 부모가 새 오류를 주면 다시 보인다 */
const dismissed = reactive({ delta: false, reason: false });

/** 열 때마다 빈 칸·지급으로 시작한다 — 지난번에 적다 만 것이 남아 있으면 잘못 조정하기 쉽다 */
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        query.value = '';
        selected.value = null;
        mode.value = 'GRANT';
        amountText.value = '';
        reason.value = '';
        submitted.value = false;
        emitSearch(true);
    },
    { immediate: true },
);
watch(
    () => props.fieldErrors,
    () => {
        dismissed.delta = false;
        dismissed.reason = false;
    },
);
watch([amountText, mode], () => (dismissed.delta = true));
watch(reason, () => (dismissed.reason = true));

// 찾기 — 글자를 멈추고 0.3초 뒤에 올린다(Enter 는 바로)
let searchTimer: ReturnType<typeof setTimeout> | null = null;
watch(query, () => emitSearch(false));
function emitSearch(now: boolean): void {
    if (searchTimer) clearTimeout(searchTimer);
    searchTimer = null;
    if (now) emit('search', query.value.trim());
    else searchTimer = setTimeout(() => emit('search', query.value.trim()), 300);
}
onBeforeUnmount(() => {
    if (searchTimer) clearTimeout(searchTimer);
});

/** 고른 회원의 잔액은 새 후보 목록의 값으로 맞춘다(조정 뒤 다시 열었을 때 등) */
watch(
    () => props.candidates,
    (list) => {
        const fresh = selected.value ? list.find((c) => c.memberId === selected.value?.memberId) : undefined;
        if (fresh) selected.value = fresh;
    },
);

function select(candidate: PointMemberCandidate): void {
    selected.value = candidate;
}

const reasonLength = computed(() => UtilPoint.reasonLength(reason.value));
const amountHint = computed(() => t('point.range', { min: UtilPoint.AMOUNT_MIN.toLocaleString(locale.value), max: UtilPoint.AMOUNT_MAX.toLocaleString(locale.value) }));

const memberError = computed(() => (submitted.value && !selected.value ? t('point.adjustMemberRequired') : ''));
const amountError = computed(() => {
    // 비어 있는 칸은 [조정하기]를 누르기 전에는 탓하지 않는다
    const problem = UtilPoint.amountProblem(amountText.value);
    if (problem && (submitted.value || problem !== 'REQUIRED')) return UtilPointMessages.amountProblem(t, problem, locale.value);
    // 고액 조정 권한이 없으면 문턱을 넘는 금액은 보내지 않는다(서버도 403 `POINT_ADJUST_LARGE_REQUIRED`)
    if (!problem && largeBlocked.value && props.largeThreshold !== null) return UtilPointMessages.largeRequired(t, props.largeThreshold, locale.value);
    return dismissed.delta ? '' : (props.fieldErrors.delta ?? '');
});
/** 문턱 초과 — 권한이 없으면 막고, 있으면 주의 안내만(문턱을 모르면 둘 다 아니다) */
const isLarge = computed(() => UtilPoint.isLarge(amountText.value, props.largeThreshold));
const largeBlocked = computed(() => isLarge.value && !props.canLarge);
const largeNotice = computed(() => isLarge.value && props.canLarge && !UtilPoint.amountProblem(amountText.value));
const reasonError = computed(() => {
    const problem = UtilPoint.reasonProblem(reason.value);
    if (problem && (submitted.value || problem !== 'REQUIRED')) return UtilPointMessages.reasonProblem(t, problem);
    return dismissed.reason ? '' : (props.fieldErrors.reason ?? '');
});

const delta = computed(() => UtilPoint.toDelta(mode.value, amountText.value));
/** 처리 후 예상 — 조정은 적립 포인트만 바꾸므로 적립 잔액 기준(`after` 는 적립 잔액, 합계는 `balance + applied`) */
const preview = computed(() => (selected.value && delta.value !== null ? UtilPoint.preview(selected.value.freeBalance, delta.value) : null));

function submit(): void {
    submitted.value = true;
    if (!selected.value || delta.value === null || largeBlocked.value || UtilPoint.reasonProblem(reason.value)) return;
    emit('submit', { member: selected.value, mode: mode.value, amount: Math.abs(delta.value), delta: delta.value, reason: UtilPoint.reasonText(reason.value) });
}
</script>
