<template>
    <!--
        신고 — 회원만(비회원은 부르는 화면이 로그인 안내). 사유는 관리자 신고 정책의 사유 코드와 같다. 기타는 설명 필수(가정).
        대상: 글 · 댓글 · 소모임(소모임 상세 ⋯ 메뉴 · 일정 상세의 오프라인 고지 아래 — 제목·안내가 소모임 문구이고 `summary` 로 대상 한 줄을 보인다).
        · 공구 문의(`GROUP_BUY_QUESTION` — 공구 상세 Q&A, 사유는 글·댓글 체계).
        사유는 대상 체계를 따른다 — 글·댓글 6종 / 소모임 4종(홍보·영업 · 오프라인 위험 · 종목 추천·유료 모집(`stock` 일 때만) · 기타).
    -->
    <Dialog :visible="visible" modal :header="title" :style="{ width: 'min(26rem, calc(100vw - 2rem))' }" :draggable="false" @update:visible="close">
        <form class="flex flex-col gap-3" @submit.prevent="submit">
            <p v-if="summary" class="m-0 break-words rounded-lg border border-surface bg-surface-50 px-2.5 py-1.5 text-xs text-muted-color [overflow-wrap:anywhere] dark:bg-surface-800/60" data-testid="report-target">{{ summary }}</p>
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
            <p class="text-xs text-muted-color">{{ target === 'GROUP' ? t('group.reportNotice') : t('post.reportNotice') }}</p>
            <Message v-if="error" severity="error" variant="simple" size="small">{{ error }}</Message>
            <div class="flex justify-end gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="close(false)" />
                <Button type="submit" :label="t('post.reportSubmit')" severity="danger" :loading="loading" :disabled="!reason" />
            </div>
        </form>
    </Dialog>
</template>

<script setup lang="ts">
import { GROUP_REPORT_REASONS, REPORT_REASONS, type ReportReason, type ReportTargetType } from '~/composables/report/api/useReportApi.types';
import UtilPost from '~/utils/util-post';

interface Props {
    visible: boolean;
    target?: ReportTargetType;
    loading?: boolean;
    /** 서버 문구(폼 위) */
    error?: string;
    /** 서버 `fields.detail` */
    fieldError?: string;
    /** 대상 한 줄(소모임 이름 등) — 비었으면 그리지 않는다 */
    summary?: string;
    /** 소모임 대상 — 투자 고지 소모임(`stockNotice`)이면 「종목 추천·유료 모집」 사유가 더해진다 */
    stock?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    target: 'POST',
    loading: false,
    error: '',
    fieldError: '',
    summary: '',
    stock: false,
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
    PROMOTION: 'group.reasonPromotion',
    OFFLINE_RISK: 'group.reasonOfflineRisk',
    STOCK_RECOMMENDATION: 'group.reasonStockRecommendation',
    OTHER: 'post.reasonOther',
};
/** 대상 → 제목 키 */
const TITLE_KEY: Record<ReportTargetType, string> = { POST: 'post.reportPostTitle', COMMENT: 'post.reportCommentTitle', GROUP: 'group.reportTitle', GROUP_BUY_QUESTION: 'groupBuyQna.reportTitle', GROUP_BUY_REVIEW: 'groupBuyReview.reportTitle' };
const title = computed(() => t(TITLE_KEY[props.target]));
/** 대상의 사유 — 소모임은 소모임 사유(종목 추천은 투자 고지 소모임만), 글·댓글은 글 사유 */
const reasons = computed(() => {
    const list = props.target === 'GROUP' ? GROUP_REPORT_REASONS.filter((value) => value !== 'STOCK_RECOMMENDATION' || props.stock) : REPORT_REASONS;
    return list.map((value) => ({ value, label: t(REASON_KEY[value]) }));
});

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
