<template>
    <!-- 신고 — 회원만(비회원은 부르는 화면이 로그인 안내). 사유는 관리자 신고 정책의 사유 코드와 같다. 기타는 설명 필수(가정) -->
    <Dialog :visible="visible" modal :header="target === 'COMMENT' ? t('post.reportCommentTitle') : t('post.reportPostTitle')" :style="{ width: 'min(26rem, calc(100vw - 2rem))' }" :draggable="false" @update:visible="close">
        <form class="flex flex-col gap-3" @submit.prevent="submit">
            <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                <legend class="mb-1 text-sm font-medium">{{ t('post.reportReason') }}</legend>
                <label v-for="item in reasons" :key="item.value" class="flex cursor-pointer items-center gap-2 text-sm">
                    <RadioButton v-model="reason" :value="item.value" :input-id="`${baseId}-${item.value}`" name="report-reason" />
                    <span>{{ item.label }}</span>
                </label>
            </fieldset>
            <LabeledField v-if="reason === 'OTHER'" :label="t('post.reportDetail')" :hint="t('post.maxHint', { max: UtilPost.REPORT_DETAIL_MAX })" :error="detailError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="detail" rows="3" :maxlength="UtilPost.REPORT_DETAIL_MAX" :invalid="invalid" :aria-describedby="describedBy" fluid auto-resize />
                </template>
            </LabeledField>
            <p class="text-xs text-muted-color">{{ t('post.reportNotice') }}</p>
            <Message v-if="error" severity="error" variant="simple" size="small">{{ error }}</Message>
            <div class="flex justify-end gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="close(false)" />
                <Button type="submit" :label="t('post.reportSubmit')" severity="danger" :loading="loading" :disabled="!reason" />
            </div>
        </form>
    </Dialog>
</template>

<script setup lang="ts">
import { REPORT_REASONS, type ReportReason, type ReportTargetType } from '~/composables/report/api/useReportApi.types';
import UtilPost from '~/utils/util-post';

interface Props {
    visible: boolean;
    target?: ReportTargetType;
    loading?: boolean;
    /** 서버 문구(폼 위) */
    error?: string;
    /** 서버 `fields.detail` */
    fieldError?: string;
}

const props = withDefaults(defineProps<Props>(), {
    target: 'POST',
    loading: false,
    error: '',
    fieldError: '',
});

const emit = defineEmits<{
    'update:visible': [value: boolean];
    submit: [value: { reason: ReportReason; detail: string | null }];
}>();

const { t } = useI18n();
const baseId = useId();
const reason = ref<ReportReason | null>(null);
const detail = ref('');
const tried = ref(false);

/** 사유 → 문구 키 */
const REASON_KEY: Record<ReportReason, string> = {
    SPAM_AD: 'post.reasonSpamAd',
    ABUSE: 'post.reasonAbuse',
    OBSCENE: 'post.reasonObscene',
    ILLEGAL_FILMING: 'post.reasonIllegalFilming',
    RIGHTS: 'post.reasonRights',
    OTHER: 'post.reasonOther',
};
const reasons = computed(() => REPORT_REASONS.map((value) => ({ value, label: t(REASON_KEY[value]) })));

const detailError = computed(() => props.fieldError || (tried.value && reason.value === 'OTHER' && !detail.value.trim() ? t('post.reportDetailRequired') : ''));

function reset(): void {
    reason.value = null;
    detail.value = '';
    tried.value = false;
}

function close(value: boolean): void {
    if (!value) reset();
    emit('update:visible', value);
}

function submit(): void {
    tried.value = true;
    if (!reason.value || props.loading) return;
    if (reason.value === 'OTHER' && !detail.value.trim()) return;
    emit('submit', { reason: reason.value, detail: reason.value === 'OTHER' ? detail.value.trim() : null });
}

watch(
    () => props.visible,
    (open) => {
        if (!open) reset();
    },
);
</script>
