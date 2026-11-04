<template>
    <!--
        제재 단계 고르개 — 현황(경고 n/2 · 진행 중 · 다음 단계) → 막힘 안내 → 단계 선택지(경고 → 영구 정지) → 건너뛰기 근거 확인 → 영구 정지 닉네임 확인.
        [제재하기] 대화창(`SanctionIssueDialog`)과 신고 처리 대화창의 「작성자 제재 곁들이기」(`ReportActionDialog`)가 같이 쓴다. 서버 호출은 하지 않는다.
        선택지 상태는 서버(`preview.options[].state`)를 따른다 — `NEXT` 권장 · `SKIP` 건너뛰기(`sanction.ban` 이 없으면 비활성, 고르면 근거 확인 필수) · `LOWER`·`BLOCKED` 비활성.
        영구 정지는 다음 단계여도 `sanction.ban` 이 있어야 하고(그것만 있어도 된다) 회원 닉네임을 그대로 적어야 한다(`confirmNickname`). 서버 칸 오류(`kind`·`skipStep`·`confirmNickname`)는 해당 칸 아래.
        `tried` 가 켜지면(보내 보았다) 모자란 것을 칸 아래에 보인다.
    -->
    <div class="flex flex-col gap-3">
        <SanctionPreviewSummary v-if="showSummary" :preview="preview" />

        <Message v-if="preview.blockedReason" :severity="preview.blockedReason === 'ALREADY_BANNED' ? 'error' : 'warn'" size="small" :closable="false" icon="pi pi-info-circle">
            {{ UtilSanctionMessages.blocked(t, preview.blockedReason) }}
        </Message>
        <Message v-if="!UtilSanction.canIssue(perm)" severity="secondary" size="small" :closable="false" icon="pi pi-lock">{{ t('sanction.noManage') }}</Message>

        <fieldset class="m-0 flex flex-col gap-1.5 border-0 p-0" :aria-describedby="kindError ? `${uid}-kind-error` : undefined">
            <legend class="mb-1.5 text-sm font-medium">{{ t('sanction.stepLabel') }}</legend>
            <label
                v-for="choice in choices"
                :key="choice.option.kind"
                :for="`${uid}-${choice.option.kind}`"
                class="flex items-center gap-2.5 rounded-lg border px-3 py-2 text-sm"
                :class="[
                    choice.enabled ? 'cursor-pointer hover:bg-(--p-content-hover-background)' : 'cursor-not-allowed opacity-60',
                    model.kind === choice.option.kind ? 'border-primary bg-primary-50/60 dark:bg-primary-950/40' : 'border-(--line)',
                ]"
            >
                <RadioButton v-model="kindModel" :input-id="`${uid}-${choice.option.kind}`" :name="`${uid}-kind`" :value="choice.option.kind" :disabled="disabled || !choice.enabled" />
                <SanctionTag type="kind" :kind="choice.option.kind" :warning-no="choice.option.kind === 'WARNING' ? choice.option.rank : null" />
                <span class="flex min-w-0 flex-1 flex-wrap items-center gap-x-2 gap-y-0.5">
                    <b v-if="choice.option.state === 'NEXT'" class="text-xs font-semibold text-primary">{{ t('sanction.choiceState.NEXT') }}</b>
                    <span v-else-if="choice.option.state === 'SKIP'" class="inline-flex items-center gap-1 text-xs text-red-600 dark:text-red-400"><i class="pi pi-forward text-[10px]" aria-hidden="true" />{{ t('sanction.choiceState.SKIP') }}</span>
                    <span v-if="!choice.enabled" class="text-xs text-muted-color">{{ UtilSanctionMessages.choiceNote(t, choice) }}</span>
                </span>
                <i v-if="choice.option.requiresBan" v-tooltip.left="t('sanction.requiresBan')" class="pi pi-key text-xs text-muted-color" :aria-label="t('sanction.requiresBan')" />
            </label>
            <p v-if="kindError" :id="`${uid}-kind-error`" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ kindError }}</p>
        </fieldset>

        <!-- 단계 건너뛰기 — 근거 확인 필수(`skipStep`) -->
        <div v-if="isSkip" class="flex flex-col gap-1 rounded-lg border border-red-200 bg-red-50/60 px-3 py-2 dark:border-red-900 dark:bg-red-950/30">
            <div class="flex items-start gap-2">
                <Checkbox v-model="skipModel" binary :input-id="`${uid}-skip`" :disabled="disabled" :invalid="Boolean(skipError)" :aria-describedby="skipError ? `${uid}-skip-error` : undefined" />
                <label :for="`${uid}-skip`" class="flex flex-col text-sm">
                    {{ t('sanction.skipConfirm') }}
                    <span class="text-xs leading-relaxed text-muted-color">{{ t('sanction.skipHelp') }}</span>
                </label>
            </div>
            <p v-if="skipError" :id="`${uid}-skip-error`" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ skipError }}</p>
        </div>

        <!-- 영구 정지 — 회원 닉네임을 그대로 적어야 한다(`confirmNickname`) -->
        <LabeledField v-if="model.kind === 'BAN'" :label="t('sanction.confirmNicknameLabel', { name: preview.user.nickname })" :help="t('sanction.confirmNicknameHelp')" :error="nicknameError">
            <template #default="{ id, describedBy, invalid }">
                <InputText :id="id" v-model="nicknameModel" autocomplete="off" :disabled="disabled" :invalid="invalid" :aria-describedby="describedBy" fluid />
            </template>
        </LabeledField>
    </div>
</template>

<script setup lang="ts">
import type { SanctionKind, SanctionPreview } from '~/composables/sanction/api/useSanctionApi.types';
import type { SanctionStepValue } from '~/composables/sanction/useSanctionIssue.types';
import UtilSanction from '~/utils/util-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

const model = defineModel<SanctionStepValue>({ required: true });

interface Props {
    preview: SanctionPreview;
    perm: { canManage: boolean; canBan: boolean };
    /** 서버 칸 오류(`kind`·`skipStep`·`confirmNickname`) */
    fieldErrors?: Record<string, string>;
    /** 보내 보았다 — 모자란 것을 보인다 */
    tried?: boolean;
    disabled?: boolean;
    /** 위에 현황 요약을 그린다 */
    showSummary?: boolean;
}

const props = withDefaults(defineProps<Props>(), { fieldErrors: () => ({}), tried: false, disabled: false, showSummary: true });

const { t } = useI18n();
const uid = useId();

const choices = computed(() => UtilSanction.choices(props.preview, props.perm));
const isSkip = computed(() => UtilSanction.isSkip(props.preview, model.value.kind));

const kindModel = computed<SanctionKind | null>({
    get: () => model.value.kind,
    set: (kind) => (model.value = { ...model.value, kind, skipConfirmed: false, confirmNickname: kind === 'BAN' ? model.value.confirmNickname : '' }),
});
const skipModel = computed<boolean>({
    get: () => model.value.skipConfirmed,
    set: (skipConfirmed) => (model.value = { ...model.value, skipConfirmed }),
});
const nicknameModel = computed<string>({
    get: () => model.value.confirmNickname,
    set: (confirmNickname) => (model.value = { ...model.value, confirmNickname }),
});

const kindError = computed(() => props.fieldErrors.kind || (props.tried && !model.value.kind ? t('sanction.stepRequired') : ''));
const skipError = computed(() => props.fieldErrors.skipStep || (props.tried && isSkip.value && !model.value.skipConfirmed ? t('sanction.skipRequired') : ''));
const nicknameError = computed(() => {
    if (props.fieldErrors.confirmNickname) return props.fieldErrors.confirmNickname;
    if (!props.tried || model.value.kind !== 'BAN') return '';
    return model.value.confirmNickname.trim() === props.preview.user.nickname ? '' : t('sanction.confirmNicknameMismatch');
});
</script>
