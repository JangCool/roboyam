<template>
    <!--
        게시판 매니저·스텝의 핫딜 종료·재개 사유(사용자 결정 2026-11-01) — `end` 는 사유 필수(1~200자), `reopen` 은 선택(0~200자). 글자 수는 앞뒤 공백을 뗀 글자 단위(서버와 같다).
        종료 사유는 작성자 알림에 그대로 실린다. 보내기는 부르는 쪽(딜 카드)이 한다 — `submit`(앞뒤 공백을 뗀 사유, 재개에서 비면 빈 글자)을 올린다. 열 때마다 빈 입력으로 시작한다.
        모양은 같은 카드의 품절 알리기 대화창(`HotdealEndRequestDialog`)과 같은 Dialog, 사유 칸·글자 수는 소모임 거절 시트(`GroupPublishRejectSheet`)와 같은 `LabeledField` + 오른쪽 글자 수.
    -->
    <Dialog :visible="visible" modal :header="title" :style="{ width: 'min(26rem, calc(100vw - 2rem))' }" :draggable="false" @update:visible="close">
        <form class="flex flex-col gap-3" novalidate data-testid="hotdeal-moderate-dialog" :data-mode="mode" @submit.prevent="submit">
            <p class="m-0 text-sm leading-relaxed text-muted-color">{{ mode === 'end' ? t('hotdeal.moderateEndText') : t('hotdeal.moderateReopenText') }}</p>
            <LabeledField :label="t('hotdeal.moderateReasonLabel')" :hint="mode === 'end' ? t('hotdeal.moderateReasonRequired') : t('hotdeal.moderateReasonOptional')" :error="shownError">
                <template #aside>
                    <small class="text-xs tabular-nums" :class="tooLong ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'" data-testid="hotdeal-moderate-counter">
                        {{ t('hotdeal.moderateCounter', { n: length, max: UtilHotdeal.MODERATE_REASON_MAX }) }}
                    </small>
                </template>
                <template #default="{ id: fieldId, describedBy, invalid }">
                    <Textarea
                        :id="fieldId"
                        v-model="reason"
                        name="reason"
                        rows="3"
                        fluid
                        class="resize-none"
                        :placeholder="mode === 'end' ? t('hotdeal.moderateEndPlaceholder') : t('hotdeal.moderateReopenPlaceholder')"
                        :invalid="invalid"
                        :disabled="loading"
                        :aria-describedby="describedBy"
                        :aria-required="mode === 'end'"
                        data-testid="hotdeal-moderate-reason"
                    />
                </template>
            </LabeledField>
            <Message v-if="error" severity="error" variant="simple" size="small">{{ error }}</Message>
            <div class="flex justify-end gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="loading" @click="close(false)" />
                <Button
                    type="submit"
                    :label="mode === 'end' ? t('hotdeal.moderateEnd') : t('hotdeal.reopen')"
                    :severity="mode === 'end' ? 'danger' : undefined"
                    :loading="loading"
                    :disabled="!canSubmit"
                    data-testid="hotdeal-moderate-submit"
                />
            </div>
        </form>
    </Dialog>
</template>

<script setup lang="ts">
import UtilHotdeal from '~/utils/util-hotdeal';

interface Props {
    visible: boolean;
    /** `end` 종료(사유 필수) · `reopen` 다시 진행 중으로(사유 선택) */
    mode: 'end' | 'reopen';
    loading?: boolean;
    /** 칸에 붙지 않는 서버 문구 */
    error?: string;
    /** 사유 칸 서버 오류(`400 fields.reason`) */
    fieldError?: string;
}

const props = withDefaults(defineProps<Props>(), { loading: false, error: '', fieldError: '' });
const emit = defineEmits<{
    'update:visible': [value: boolean];
    submit: [reason: string];
}>();

const { t } = useI18n();
const reason = ref('');

const title = computed(() => (props.mode === 'end' ? t('hotdeal.moderateEndTitle') : t('hotdeal.moderateReopenTitle')));
const length = computed(() => UtilHotdeal.reasonLength(reason.value));
const tooLong = computed(() => length.value > UtilHotdeal.MODERATE_REASON_MAX);
const shownError = computed(() => (tooLong.value ? t('hotdeal.moderateReasonTooLong', { max: UtilHotdeal.MODERATE_REASON_MAX }) : props.fieldError));
const canSubmit = computed(() => !props.loading && !tooLong.value && (props.mode === 'reopen' || length.value > 0));

function close(value: boolean): void {
    if (!value) reason.value = '';
    emit('update:visible', value);
}

function submit(): void {
    if (!canSubmit.value) return;
    emit('submit', reason.value.trim());
}

watch(
    () => props.visible,
    (open) => {
        if (open) reason.value = '';
    },
);
</script>
