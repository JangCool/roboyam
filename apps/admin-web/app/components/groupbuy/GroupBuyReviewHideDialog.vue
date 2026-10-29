<template>
    <!--
        리뷰 숨김 대화상자 — 사유 코드(욕설·개인정보·광고·무관한 내용·기타)로만 숨긴다. 낮은 별점은 사유에 없다(표시광고법·공정위 소비자 리뷰 지침).
        메모: 기타는 필수 5~200자, 그 밖은 선택(200자까지). 서버는 부르지 않는다(`submit`). 부르는 쪽이 열 때마다 `:key` 를 바꿔 비운다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="t('groupBuyReview.hideTitle')" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p class="m-0 text-sm leading-relaxed text-muted-color">{{ t('groupBuyReview.hideBody') }}</p>
            <Message severity="warn" size="small" :closable="false">{{ t('groupBuyReview.lowRatingNote') }}</Message>

            <LabeledField :label="t('groupBuyReview.hideReasonLabel')" :error="reasonError">
                <template #default="{ id, describedBy, invalid }">
                    <Select v-model="reasonCode" :input-id="id" :options="options" option-label="label" option-value="value" :placeholder="t('groupBuyReview.hideReasonPlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>

            <LabeledField :label="t('groupBuyReview.hideMemoLabel')" :hint="t('groupBuyReview.hideMemoHint')" :error="memoError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="memo" rows="3" auto-resize :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
                <template #aside>
                    <small class="shrink-0 text-xs tabular-nums" :class="memoLength > 200 ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">{{ memoLength }} / 200</small>
                </template>
            </LabeledField>

            <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color"><i class="pi pi-history mt-0.5 text-xs" aria-hidden="true" />{{ t('groupBuyReview.hideAudit') }}</p>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('groupBuyReview.actHide')" severity="danger" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { GroupBuyReviewHideReason, GroupBuyReviewHideRequest } from '~/composables/groupbuy/api/useGroupBuyReviewAdminApi.types';
import { GROUP_BUY_REVIEW_HIDE_REASONS } from '~/composables/groupbuy/api/useGroupBuyReviewAdminApi.types';

interface Props {
    visible: boolean;
    busy?: boolean;
    error?: string;
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [value: GroupBuyReviewHideRequest] }>();

const { t } = useI18n();
const formId = useId();
const options = computed(() => GROUP_BUY_REVIEW_HIDE_REASONS.map((value) => ({ value, label: t(`groupBuyReview.hideReason.${value}`) })));
const reasonCode = ref<GroupBuyReviewHideReason | null>(null);
const memo = ref('');
const tried = ref(false);
const memoLength = computed(() => memo.value.trim().length);

const reasonError = computed(() => props.fieldErrors.reasonCode || (tried.value && !reasonCode.value ? t('groupBuyReview.hideReasonRequired') : ''));
const memoError = computed(() => {
    if (props.fieldErrors.memo) return props.fieldErrors.memo;
    if (!tried.value) return '';
    if (memoLength.value > 200) return t('groupBuyReview.hideMemoTooLong');
    if (reasonCode.value === 'OTHER' && memoLength.value < 5) return t('groupBuyReview.hideMemoRequired');
    return '';
});

function submit(): void {
    tried.value = true;
    if (!reasonCode.value || reasonError.value || memoError.value) return;
    emit('submit', { reasonCode: reasonCode.value, memo: memo.value.trim() || null });
}
</script>
