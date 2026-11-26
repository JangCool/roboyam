<template>
    <!--
        글 보기의 AI 연애상담소 칸(ai-love-counsel-contract 「화면」 2·3) — 본문 바로 다음(찬반 토론 자리와 같은 상자 모양). 「AI 상담소」 말머리 글만 페이지가 그린다.
          안내 한 줄(사연 쓴 사람이면 「…골라 주세요」, 남이면 「사연을 쓴 분이 … 골라요」) → 「AI 상담사 답변 n」 → 상담사마다 답(댓글 부품 `CommentItem` 그대로 — 이름 옆 AI 배지 「AI · 모델」 + 상담 성향 칩).
          아직 쓰는 중인 상담사는 「답을 쓰고 있어요」 자리(시한 `answerDueAt` 이 지나면 「조금 늦어지고 있어요」). 고른 답은 「가장 도움 된 조언」 + 초록 테두리, 고르기 전이면 사연 쓴 사람에게 「이 조언 고르기」.
          회원 답변·상담 경고는 아래 댓글 카드(`CommentSection` 의 `counsel`)가 그린다 — 차례: 사연 → 안내 → AI 답 → 회원 답 → 경고.
        위험 사연(`RISK`): AI 답·안내 대신 도움 안내 카드 — 「지금 많이 힘드신 것 같아요 …」 + 자살예방 상담 109 · 여성 폭력 상담 1366(전화 걸기 링크) · 둘 다 24시간.
        AI 답에는 답글을 달지 않는다(회원 답변은 아래 댓글로 — 화면 가정). 추천·신고는 댓글과 같다(`like`·`report` 이벤트 → 페이지).
    -->
    <section :aria-labelledby="headingId" class="my-2 flex flex-col gap-3 rounded-xl border border-surface bg-surface-50 p-3.5 dark:bg-surface-900/60" data-testid="counsel-panel">
        <div v-if="state === 'loading'" class="flex flex-col gap-3" role="status" :aria-label="t('counsel.loading')">
            <div v-for="n in 3" :key="n">
                <div class="h-3 w-32 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                <div class="mt-2 h-3.5 w-11/12 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            </div>
        </div>
        <ErrorState v-else-if="state === 'error'" :title="t('counsel.loadFailed')" :description="errorMessage" retry @retry="$emit('retry')" />

        <!-- 위험 사연 — AI 는 답하지 않고 도움 안내만 -->
        <div v-else-if="counsel?.status === 'RISK'" role="note" class="flex flex-col gap-2.5 rounded-lg border border-sky-200 bg-sky-50 px-3.5 py-3 text-sky-950 dark:border-sky-900 dark:bg-sky-950/40 dark:text-sky-100" data-testid="counsel-risk">
            <h2 :id="headingId" class="m-0 flex items-center gap-1.5 text-[0.9375rem] font-semibold">
                <i class="pi pi-heart text-sm" aria-hidden="true" />{{ t('counsel.riskTitle') }}
            </h2>
            <p class="m-0 text-sm leading-relaxed">{{ t('counsel.riskText') }}</p>
            <div class="flex flex-wrap items-center gap-2">
                <a
                    v-for="line in HELP_LINES"
                    :key="line.tel"
                    :href="`tel:${line.tel}`"
                    class="inline-flex min-h-9 items-center gap-1.5 rounded-full border border-sky-300 bg-surface-0 px-3 text-sm font-semibold text-sky-900 hover:bg-sky-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:border-sky-800 dark:bg-surface-900 dark:text-sky-100 dark:hover:bg-sky-900/50"
                    :aria-label="t('counsel.riskCall', { name: t(line.key) })"
                    :data-testid="`counsel-risk-${line.tel}`"
                >
                    <i class="pi pi-phone text-xs" aria-hidden="true" />{{ t(line.key) }}
                </a>
                <span class="text-xs text-sky-800 dark:text-sky-300">{{ t('counsel.risk24h') }}</span>
            </div>
        </div>

        <template v-else-if="counsel">
            <p class="m-0 flex items-start gap-1.5 text-[0.8125rem] leading-relaxed text-muted-color" data-testid="counsel-guide">
                <i class="pi pi-sparkles mt-[0.2rem] shrink-0 text-xs text-violet-600 dark:text-violet-300" aria-hidden="true" />
                <span>{{ mine ? t('counsel.guide') : t('counsel.guideReader') }}</span>
            </p>

            <h2 :id="headingId" class="m-0 text-[0.9375rem] font-semibold">
                {{ t('counsel.aiTitle') }} <span class="text-primary tabular-nums">{{ answeredCount }}</span>
            </h2>

            <ul class="m-0 flex list-none flex-col gap-2 p-0">
                <li
                    v-for="c in counsel.counselors"
                    :key="c.counselorId"
                    class="rounded-lg border bg-surface-0 px-3 dark:bg-surface-900"
                    :class="c.comment && c.comment.id === counsel.pickedCommentId ? 'border-green-500 dark:border-green-600' : 'border-surface'"
                    :data-testid="`counsel-answer-${c.counselorId}`"
                >
                    <CommentItem
                        v-if="c.comment"
                        :comment="c.comment"
                        :report-enabled="reportEnabled"
                        @like="$emit('like', c.comment)"
                        @report="$emit('report', c.comment)"
                    >
                        <template #badges>
                            <span class="inline-flex shrink-0 items-center justify-center whitespace-nowrap rounded border border-surface px-1.5 py-[3px] text-[0.6875rem] leading-none text-muted-color">
                                <span class="chip-label">{{ c.style }}</span>
                            </span>
                            <CounselPickedBadge v-if="c.comment.id === counsel.pickedCommentId" />
                        </template>
                        <template v-if="counsel.canPick && !counsel.pickedCommentId" #after>
                            <CounselPickButton :busy="pickBusyId === c.comment.id" :disabled="Boolean(pickBusyId)" @pick="$emit('pick', c.comment)" />
                        </template>
                    </CommentItem>
                    <!-- 아직 쓰는 중 — 상담사 이름·성향 + 「답을 쓰고 있어요」 -->
                    <div v-else class="flex flex-wrap items-center gap-x-1.5 gap-y-1 py-3 text-xs text-muted-color" data-testid="counsel-writing">
                        <span class="font-semibold text-color">{{ c.name }}</span>
                        <AiBadge :provider="c.ai.provider" :model="c.ai.model" plain />
                        <span class="inline-flex shrink-0 items-center justify-center whitespace-nowrap rounded border border-surface px-1.5 py-[3px] text-[0.6875rem] leading-none">
                            <span class="chip-label">{{ c.style }}</span>
                        </span>
                        <span aria-hidden="true">·</span>
                        <span class="inline-flex items-center gap-1"><i class="pi pi-spinner pi-spin text-[0.6875rem]" aria-hidden="true" />{{ t('counsel.writing') }}</span>
                    </div>
                </li>
            </ul>

            <p v-if="late" class="m-0 text-xs text-muted-color" data-testid="counsel-late">{{ t('counsel.late') }}</p>
        </template>
    </section>
</template>

<script setup lang="ts">
import type { CommentView } from '~/composables/board/api/useCommentApi.types';
import type { PostCounselView } from '~/composables/counsel/api/useCounselApi.types';
import type { CounselLoadState } from '~/composables/counsel/usePostCounsel';

interface Props {
    counsel: PostCounselView | null;
    state: CounselLoadState;
    errorMessage?: string;
    /** 보는 사람이 사연을 쓴 사람 — 안내 문구를 고른다(고르기 단추는 `counsel.canPick`) */
    mine?: boolean;
    /** 신고 단추(게시판 `reportEnabled`) */
    reportEnabled?: boolean;
    /** 고르는 중인 댓글 ID */
    pickBusyId?: string;
}

const props = withDefaults(defineProps<Props>(), { errorMessage: '', mine: false, reportEnabled: false, pickBusyId: '' });

defineEmits<{
    pick: [comment: CommentView];
    like: [comment: CommentView];
    report: [comment: CommentView];
    retry: [];
}>();

/** 도움 전화(ai-love-counsel-contract 「화면」 3 — 번호는 고정, 이름만 번역) */
const HELP_LINES = [
    { tel: '109', key: 'counsel.riskSuicide' },
    { tel: '1366', key: 'counsel.riskWomen' },
] as const;

const { t } = useI18n();
const headingId = useId();

const answeredCount = computed(() => props.counsel?.counselors.filter((c) => c.comment).length ?? 0);
/** 시한(사연 + 10분)이 지났는데 아직 쓰는 상담사가 있다 — 화면을 연 때 기준(목업은 시계를 돌리지 않는다) */
const late = computed(() => {
    const c = props.counsel;
    if (!c?.answerDueAt || !c.counselors.some((x) => !x.comment)) return false;
    return Date.parse(c.answerDueAt) < Date.now();
});
</script>
