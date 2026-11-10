<template>
    <!--
        회원 제재 현황 요약 — 경고 n/2 · 진행 중인 정지(끝나는 시각) · 다음 단계(막혔으면 까닭). 서버 현황(`SanctionPreview`)을 그대로 그린다(다음 단계는 서버가 계산한다).
        제재하기 대화창·회원 상세 제재 구획·신고 서랍 작성자 카드가 같은 모양으로 쓴다. 오른쪽 아래 `actions` 칸에 부르는 쪽 단추·링크를 둔다.
        봉인 제재(익명 글로 받은 제재, anonymous-board-contract): 회원 단위 현황은 그 줄을 이력에서 빼서 준다 — `sealedCount > 0` 이면 경고 횟수 옆 「(봉인 n건 포함)」 + 아래 「이력에 보이지 않아요」 한 줄,
        `active.sealed` 면 진행 중 줄 아래 「봉인된 제재로 정지됐어요 · 작성자 열람으로만」(자물쇠). 익명 글 작성자 현황(`user.sealed`)에는 두 안내를 붙이지 않는다.
    -->
    <div class="flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-2.5">
        <dl class="m-0 grid gap-x-4 gap-y-1.5 text-[13px]" :class="compact ? 'grid-cols-[max-content_minmax(0,1fr)]' : 'grid-cols-[max-content_minmax(0,1fr)] sm:grid-cols-[max-content_minmax(0,1fr)_max-content_minmax(0,1fr)]'">
            <dt class="text-muted-color">{{ t('sanction.summaryWarnings') }}</dt>
            <dd class="m-0 flex items-center gap-2">
                <span class="tabular-nums font-medium" :class="preview.warningCount >= UtilSanction.WARNING_LIMIT ? 'text-red-600 dark:text-red-400' : ''">{{ UtilSanctionMessages.warningCount(t, preview.warningCount) }}<span v-if="sealedCount > 0" class="font-normal text-muted-color">{{ t('sanction.sealedIncluded', { n: sealedCount }) }}</span></span>
                <span class="flex gap-0.5" aria-hidden="true">
                    <i v-for="n in UtilSanction.WARNING_LIMIT" :key="n" class="block h-1.5 w-4 rounded-full" :class="n <= preview.warningCount ? 'bg-amber-500' : 'bg-surface-200 dark:bg-surface-700'" />
                </span>
            </dd>
            <dt class="text-muted-color">{{ t('sanction.summaryActive') }}</dt>
            <dd class="m-0 flex min-w-0 flex-col gap-1">
                <span class="flex min-w-0 flex-wrap items-center gap-1.5">
                    <template v-if="preview.active">
                        <SanctionTag type="kind" :kind="preview.active.kind" />
                        <span v-if="preview.active.endsAt" class="text-xs text-muted-color tabular-nums">{{ t('sanction.endsAt', { at: time.full(preview.active.endsAt) }) }}</span>
                    </template>
                    <span v-else class="text-muted-color">{{ t('sanction.none') }}</span>
                </span>
                <span v-if="activeSealed" class="inline-flex items-start gap-1 text-xs text-muted-color" data-testid="sanction-active-sealed">
                    <i class="pi pi-lock mt-0.5 shrink-0 text-[10px]" aria-hidden="true" />{{ t('sanction.activeSealed') }}
                </span>
            </dd>
            <dt class="text-muted-color">{{ t('sanction.summaryNext') }}</dt>
            <dd class="m-0 flex min-w-0 flex-wrap items-center gap-1.5" :class="compact ? '' : 'sm:col-span-3'">
                <SanctionTag v-if="preview.next && !preview.blockedReason" type="kind" :kind="preview.next.kind" :warning-no="preview.next.kind === 'WARNING' ? preview.next.rank : null" />
                <span v-else class="text-xs" :class="preview.blockedReason === 'ALREADY_BANNED' ? 'text-muted-color' : 'text-red-600 dark:text-red-400'">{{ UtilSanctionMessages.nextStep(t, preview) }}</span>
            </dd>
        </dl>
        <p v-if="historyHidden" class="m-0 flex items-center gap-1 text-xs text-muted-color" data-testid="sanction-sealed-hidden">
            <i class="pi pi-lock shrink-0 text-[10px]" aria-hidden="true" />{{ t('sanction.sealedHistoryHidden') }}
        </p>
        <div v-if="$slots.actions" class="flex flex-wrap items-center justify-end gap-2 border-t border-(--line) pt-2">
            <slot name="actions" />
        </div>
    </div>
</template>

<script setup lang="ts">
import type { SanctionPreview } from '~/composables/sanction/api/useSanctionApi.types';
import UtilSanction from '~/utils/util-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

interface Props {
    preview: SanctionPreview;
    /** 좁은 자리(신고 서랍 작성자 카드)는 한 단 */
    compact?: boolean;
}

const props = withDefaults(defineProps<Props>(), { compact: false });

const { t } = useI18n();
const time = useDisplayTime();

/** 횟수에 든 봉인 제재(익명 글로 받은 제재) 수 — 서버 값 그대로, 옛 응답은 0 */
const sealedCount = computed(() => props.preview.sealedCount ?? 0);
/**
 * 「이력에 보이지 않아요」·「봉인된 제재로 정지됐어요」 — 회원 단위 현황(이력에서 봉인 줄을 뺀다)에서만.
 * 익명 글 작성자 현황(`user.sealed`)은 그 자체가 봉인이라 이 안내가 맞지 않는다
 */
const historyHidden = computed(() => sealedCount.value > 0 && !props.preview.user.sealed);
const activeSealed = computed(() => Boolean(props.preview.active?.sealed) && !props.preview.user.sealed);
</script>
