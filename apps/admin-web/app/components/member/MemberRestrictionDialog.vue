<template>
    <!--
        기능 제한 걸기(`sanction.manage`) — 회원 상세 「기능 제한」 구획의 [제한 걸기]가 연다(커머스 0-4, commerce.md 12-3).
        위에서 아래로: 종류(설명 포함 라디오 — 막는 것 / 그래도 되는 것) · 기간(1일·3일·7일·30일·직접 끝 시각·무기한 — 시작은 지금) · 공개 사유(필수 5~200자) · 운영 메모(선택 ~1000자) · 알림 안내.
        같은 종류가 진행 중이면 경고(「걸면 늦게 끝나는 쪽까지 막혀요」) — 막지는 않는다.
        서버 호출은 하지 않는다 — `submit` 으로 요청 본문을 올린다(끝 순간은 누른 순간 기준). 서버 칸 오류(`fieldErrors.type|until|reason|adminMemo`)는 칸 아래, 그 밖은 폼 아래에 서버 문구 그대로. 열 때마다 비운다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :header="t('restriction.createTitle')" :style="{ width: 'min(36rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p v-if="memberName" class="m-0 text-sm">{{ t('restriction.createTarget', { name: memberName }) }}</p>

            <!-- 종류 -->
            <fieldset class="m-0 min-w-0 border-0 p-0">
                <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('restriction.typeLabel') }}</legend>
                <div class="flex flex-col gap-2">
                    <label
                        v-for="option in UtilMemberRestriction.TYPES"
                        :key="option"
                        class="flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-2.5"
                        :class="[type === option ? 'border-primary bg-primary-50 dark:bg-primary-950' : 'border-(--line)', busy ? 'cursor-not-allowed opacity-60' : '']"
                    >
                        <RadioButton v-model="type" :value="option" :name="`${formId}-type`" :disabled="busy" class="mt-0.5" />
                        <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                            <span class="inline-flex items-center gap-1.5 text-sm font-semibold"><i :class="UtilMemberRestriction.typeIcon(option)" aria-hidden="true" />{{ t(`restriction.type.${option}`) }}</span>
                            <span class="text-xs text-muted-color">{{ t('restriction.blocks', { what: t(`restriction.blocksWhat.${option}`) }) }}</span>
                            <span class="text-xs text-muted-color">{{ t('restriction.allows', { what: t(`restriction.allowsWhat.${option}`) }) }}</span>
                        </span>
                    </label>
                </div>
                <p v-if="fieldErrors.type" role="alert" class="m-0 mt-1 text-xs text-red-600 dark:text-red-400">{{ fieldErrors.type }}</p>
            </fieldset>

            <Message v-if="sameActive" severity="warn" size="small" :closable="false" data-testid="restriction-same-active">
                {{ sameActive.until ? t('restriction.sameActive', { until: time.full(sameActive.until) }) : t('restriction.sameActiveForever') }}
            </Message>

            <!-- 기간 -->
            <fieldset class="m-0 min-w-0 border-0 p-0">
                <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('restriction.durationLabel') }}</legend>
                <div class="flex flex-wrap gap-2">
                    <label
                        v-for="option in UtilMemberRestriction.DURATIONS"
                        :key="option"
                        class="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-1.5 text-sm"
                        :class="[duration === option ? 'border-primary bg-primary-50 font-semibold dark:bg-primary-950' : 'border-(--line)', busy ? 'cursor-not-allowed opacity-60' : '']"
                    >
                        <RadioButton v-model="duration" :value="option" :name="`${formId}-duration`" :disabled="busy" />
                        {{ t(`restriction.duration.${option}`) }}
                    </label>
                </div>
                <div v-if="duration === 'CUSTOM'" class="mt-2 flex flex-col gap-1">
                    <label :for="customId" class="text-xs text-muted-color">{{ t('restriction.customUntil') }}</label>
                    <AppDateTimePicker v-model="custom" :input-id="customId" :min="nowIso" :minute-step="10" :invalid="Boolean(untilError)" :disabled="busy" show-zone class="max-w-xs" />
                </div>
                <p v-if="untilError" role="alert" class="m-0 mt-1 text-xs text-red-600 dark:text-red-400">{{ untilError }}</p>
                <p v-else class="m-0 mt-1.5 text-xs text-muted-color tabular-nums">{{ previewText }}</p>
            </fieldset>

            <!-- 공개 사유 — 회원에게 보인다 -->
            <LabeledField :label="t('restriction.reasonLabel')" :hint="t('reason.hint', { min: UtilMemberRestriction.REASON_MIN, max: UtilMemberRestriction.REASON_MAX })" :help="t('restriction.reasonHelp')" :error="reasonError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="reason" name="reason" rows="2" fluid :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" />
                </template>
            </LabeledField>

            <!-- 운영 메모 — 운영자만 -->
            <LabeledField :label="t('restriction.memoLabel')" :hint="t('restriction.memoHint', { max: UtilMemberRestriction.MEMO_MAX })" :help="t('restriction.memoHelp')" :error="memoError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="memo" name="adminMemo" rows="3" fluid :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" />
                </template>
            </LabeledField>

            <p class="m-0 flex items-start gap-2 rounded-lg bg-surface-100 px-3 py-2 text-xs leading-relaxed dark:bg-surface-800">
                <i class="pi pi-bell mt-0.5" aria-hidden="true" />
                <span>{{ t('restriction.notifyNotice') }}</span>
            </p>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('restriction.createSubmit')" icon="pi pi-ban" severity="danger" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { MemberRestriction, MemberRestrictionCreateRequest, MemberRestrictionType } from '~/composables/member/api/useMemberRestrictionApi.types';
import type { RestrictionDuration } from '~/utils/util-member-restriction';
import UtilMemberRestriction from '~/utils/util-member-restriction';

interface Props {
    visible: boolean;
    /** 대상 회원 닉네임 */
    memberName?: string;
    /** 이 회원의 지금 목록 — 같은 종류가 진행 중인지 경고에 쓴다 */
    items?: MemberRestriction[];
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 거절한 칸 문구 — 키 `type`·`until`·`reason`·`adminMemo` */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { memberName: '', items: () => [], busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: MemberRestrictionCreateRequest] }>();

const { t } = useI18n();
const time = useDisplayTime();
const formId = useId();
const customId = useId();

const type = ref<MemberRestrictionType>('GROUP_BUY_BAN');
const duration = ref<RestrictionDuration>('D1');
const custom = ref('');
const reason = ref('');
const memo = ref('');
const submitted = ref(false);
/** 서버 칸 오류를 보일지 — 새 오류가 오면 켜고, 칸을 고치면 그 칸만 끈다 */
const hidden = reactive<Record<string, boolean>>({});
/** 미리보기·고르개의 「지금」 — 열 때와 30초마다 다시 잰다 */
const now = ref(Date.now());
let timer: ReturnType<typeof setInterval> | null = null;

watch(
    () => props.visible,
    (open) => {
        if (timer) clearInterval(timer);
        timer = null;
        if (!open) return;
        type.value = 'GROUP_BUY_BAN';
        duration.value = 'D1';
        custom.value = '';
        reason.value = '';
        memo.value = '';
        submitted.value = false;
        now.value = Date.now();
        timer = setInterval(() => (now.value = Date.now()), 30000);
    },
    { immediate: true },
);
onBeforeUnmount(() => {
    if (timer) clearInterval(timer);
});
watch(
    () => props.fieldErrors,
    () => {
        for (const key of Object.keys(hidden)) delete hidden[key];
    },
);
watch(type, () => (hidden.type = true));
watch([duration, custom], () => (hidden.until = true));
watch(reason, () => (hidden.reason = true));
watch(memo, () => (hidden.adminMemo = true));

const nowIso = computed(() => new Date(now.value).toISOString());
const until = computed(() => UtilMemberRestriction.untilOf(duration.value, custom.value, now.value));
const sameActive = computed(() => UtilMemberRestriction.activeSameType(props.items, type.value));

function server(key: string): string {
    return hidden[key] ? '' : (props.fieldErrors[key] ?? '');
}

const untilError = computed(() => {
    if (server('until')) return server('until');
    if (!submitted.value || duration.value !== 'CUSTOM') return '';
    if (!custom.value) return t('restriction.customRequired');
    if (Date.parse(custom.value) <= now.value) return t('restriction.customPast');
    return '';
});

const reasonError = computed(() => {
    if (server('reason')) return server('reason');
    if (!submitted.value) return '';
    const problem = UtilMemberRestriction.reasonProblem(reason.value);
    if (problem === 'REQUIRED') return t('reason.required');
    if (problem === 'LENGTH') return t('reason.length', { min: UtilMemberRestriction.REASON_MIN, max: UtilMemberRestriction.REASON_MAX });
    return '';
});

const memoError = computed(() => {
    if (server('adminMemo')) return server('adminMemo');
    if (submitted.value && memo.value.trim().length > UtilMemberRestriction.MEMO_MAX) return t('restriction.memoTooLong', { max: UtilMemberRestriction.MEMO_MAX });
    return '';
});

/** 「지금부터 2026.10.24 14:00 까지」 · 「지금부터 해제할 때까지」 */
const previewText = computed(() => {
    if (until.value === null) return t('restriction.previewForever');
    if (!until.value) return '';
    return t('restriction.preview', { until: time.value.full(until.value) });
});

function submit(): void {
    submitted.value = true;
    for (const key of Object.keys(hidden)) hidden[key] = true;
    now.value = Date.now();
    if (untilError.value || reasonError.value || memoError.value || props.busy) return;
    emit('submit', {
        type: type.value,
        until: UtilMemberRestriction.untilOf(duration.value, custom.value, now.value),
        reason: reason.value.trim(),
        adminMemo: memo.value.trim() || null,
    });
}
</script>
