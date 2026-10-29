<template>
    <!--
        공구 문의 상세 서랍 — Q&A 대기열에서 줄을 누르면 연다(`?question=ID`). 계약 「Q&A 규칙」(2b-6).
        구획: ① 문의(공구·회원·시각·비밀글/자동 비밀글·작성자가 고침/지움·보관 만료) ② 답변(쓰기·고치기) ③ 답변 수정 이력 ④ 열린 신고(사유별 수) ⑤ 숨김(사유·누가·언제) + 숨기기/숨김 풀기.
        서버는 부르지 않는다 — 동작은 emit 으로 올린다.
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '40rem', maxWidth: '100vw' }">
        <template #header>
            <div v-if="head" class="flex min-w-0 flex-1 flex-col gap-1">
                <div class="flex min-w-0 flex-wrap items-center gap-2">
                    <h2 :id="titleId" class="min-w-0 truncate text-base font-semibold">{{ t('groupBuyQna.secQuestion') }} #{{ head.id }}</h2>
                    <Tag :value="t(`groupBuyQna.status.${head.status}`)" :severity="head.status === 'ANSWERED' ? 'success' : head.overdue ? 'danger' : 'warn'" />
                    <Tag v-if="head.hidden" :value="t('groupBuyQna.hidden')" severity="secondary" icon="pi pi-eye-slash" />
                    <Tag v-if="head.deleted" :value="t('groupBuyQna.deleted')" severity="secondary" icon="pi pi-trash" />
                </div>
                <span class="truncate text-[12px] text-muted-color">{{ head.groupBuy.title }} · {{ t('groupBuyQna.roundN', { n: head.groupBuy.roundNo }) }} · {{ head.member.nickname }}</span>
            </div>
        </template>

        <ErrorState v-if="error" :title="t('groupBuyQna.detailFailed')" :description="error.message" :code="error.status === 404 ? '404' : error.status === 403 ? '403' : '!'" :retry="error.status !== 404 && error.status !== 403" :retrying="loading" @retry="emit('retry')" />
        <div v-else-if="!detail" class="flex flex-col gap-3 py-2" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 5" :key="n" height="1rem" :width="n % 2 ? '85%' : '65%'" />
        </div>

        <div v-else class="flex min-w-0 flex-col gap-6">
            <!-- ① 문의 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-q`">
                <h3 :id="`${titleId}-q`" class="text-sm font-semibold">{{ t('groupBuyQna.secQuestion') }}</h3>
                <div class="flex flex-wrap items-center gap-2 text-[12px] text-muted-color">
                    <span class="tabular-nums">{{ time.shortDateTime(detail.createdAt) }}</span>
                    <span v-if="detail.updatedAt">· {{ t('groupBuyQna.memberEdited', { at: time.shortDateTime(detail.updatedAt) }) }}</span>
                    <Tag v-if="detail.secret" :value="t('groupBuyQna.secret')" severity="secondary" icon="pi pi-lock" />
                    <Tag v-if="detail.forcedSecret" v-tooltip.top="t('groupBuyQna.forcedSecretHelp')" :value="t('groupBuyQna.forcedSecret')" severity="warn" icon="pi pi-shield" />
                </div>
                <p class="m-0 whitespace-pre-line rounded-lg bg-(--p-content-hover-background) px-3 py-2 text-[13px] leading-relaxed [overflow-wrap:anywhere]">{{ detail.body }}</p>
                <p v-if="detail.deletedAt" class="m-0 text-xs text-muted-color">{{ t('groupBuyQna.deletedAt', { at: time.shortDateTime(detail.deletedAt) }) }}</p>
                <p class="m-0 text-xs text-muted-color">{{ t('groupBuyQna.retainUntil', { date: detail.retainUntil }) }}</p>
            </section>

            <!-- ② 답변 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-a`">
                <h3 :id="`${titleId}-a`" class="text-sm font-semibold">{{ t('groupBuyQna.secAnswer') }}</h3>
                <GroupBuyQnaAnswerPanel
                    :key="`${detail.id}-${detail.answer?.updatedAt ?? detail.answer?.answeredAt ?? 'none'}`"
                    :answer="detail.answer"
                    :can-answer="canManage && !detail.deleted"
                    :blocked-text="detail.deleted ? t('groupBuyQna.answerDeletedBlocked') : ''"
                    :busy="busy === 'answer'"
                    :error="answerError"
                    :field-error="answerFieldError"
                    @submit="(body) => emit('answer', body)"
                />
            </section>

            <!-- ③ 답변 수정 이력 -->
            <section v-if="detail.answer" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-r`">
                <h3 :id="`${titleId}-r`" class="text-sm font-semibold">{{ t('groupBuyQna.secRevisions') }}</h3>
                <p v-if="detail.revisions.length === 0" class="m-0 text-[13px] text-muted-color">{{ t('groupBuyQna.noRevisions') }}</p>
                <ol v-else class="m-0 flex list-none flex-col gap-1 p-0">
                    <li v-for="r in detail.revisions" :key="r.id" class="flex flex-col gap-0.5 rounded-lg border border-(--line) px-3 py-2">
                        <span class="whitespace-pre-line text-[13px] text-muted-color line-through decoration-1 [overflow-wrap:anywhere]">{{ r.body }}</span>
                        <span class="text-[11px] text-muted-color">{{ t('groupBuyQna.revisionLine', { name: r.writtenBy.name, at: time.shortDateTime(r.writtenAt), by: r.replacedBy.name, replacedAt: time.shortDateTime(r.replacedAt) }) }}</span>
                    </li>
                </ol>
            </section>

            <!-- ④ 열린 신고 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-rp`">
                <h3 :id="`${titleId}-rp`" class="text-sm font-semibold">{{ t('groupBuyQna.secReports') }}</h3>
                <p v-if="detail.reports.length === 0" class="m-0 text-[13px] text-muted-color">{{ t('groupBuyQna.noReports') }}</p>
                <template v-else>
                    <div class="flex flex-wrap gap-1.5">
                        <Tag v-for="r in detail.reports" :key="r.reason" :value="`${t(`board.reason.${r.reason}`)} ${r.count}`" severity="danger" />
                    </div>
                    <p class="m-0 text-xs text-muted-color">{{ t('groupBuyQna.reportsHelp') }}</p>
                </template>
            </section>

            <!-- ⑤ 숨김 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-h`">
                <h3 :id="`${titleId}-h`" class="text-sm font-semibold">{{ t('groupBuyQna.secHidden') }}</h3>
                <div v-if="detail.hiddenInfo" class="flex flex-col gap-0.5 rounded-lg border border-(--line) px-3 py-2 text-[13px]">
                    <span class="[overflow-wrap:anywhere]">{{ detail.hiddenInfo.reason }}</span>
                    <span class="text-[11px] text-muted-color">{{ t('groupBuyQna.hiddenLine', { name: detail.hiddenInfo.hiddenBy.name, at: time.shortDateTime(detail.hiddenInfo.hiddenAt) }) }}</span>
                </div>
                <div v-if="canManage && !detail.deleted">
                    <Button v-if="detail.hidden" type="button" size="small" severity="secondary" variant="outlined" icon="pi pi-eye" :label="t('groupBuyQna.actUnhide')" :disabled="busy !== null" @click="emit('unhide')" />
                    <Button v-else type="button" size="small" severity="danger" variant="outlined" icon="pi pi-eye-slash" :label="t('groupBuyQna.actHide')" :disabled="busy !== null" @click="emit('hide')" />
                </div>
            </section>
        </div>
    </Drawer>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupBuyQnaDetail, GroupBuyQnaRow } from '~/composables/groupbuy/api/useGroupBuyQnaAdminApi.types';

interface Props {
    summary: GroupBuyQnaRow | null;
    detail: GroupBuyQnaDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    /** 진행 중인 동작 */
    busy?: 'answer' | 'hide' | null;
    /** `commerce.order.manage` — 답변·숨김 */
    canManage?: boolean;
    answerError?: string;
    answerFieldError?: string;
}

const props = withDefaults(defineProps<Props>(), { loading: false, error: null, busy: null, canManage: false, answerError: '', answerFieldError: '' });
const emit = defineEmits<{ retry: []; answer: [body: string]; hide: []; unhide: [] }>();
const open = defineModel<boolean>('visible', { required: true });

const { t } = useI18n();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const titleId = useId();

const head = computed(() => props.detail ?? props.summary);
</script>
