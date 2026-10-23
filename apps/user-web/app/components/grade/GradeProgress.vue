<template>
    <!-- 다음 등급까지의 진행도 — 조건마다 막대(채운 조건은 초록). 가장 높은 등급이면 「가장 높은 등급이에요」. 자동 승급이 멈춰 있으면 흐리게 그린다 -->
    <div :class="paused ? 'opacity-60' : ''" :data-paused="paused ? 'true' : undefined" data-testid="grade-progress">
        <p v-if="showGrade" class="m-0 flex items-center gap-1.5 text-sm">
            {{ t('grade.mineLabel') }}
            <GradeBadge :grade="grade.grade.code as Grade" :label="grade.grade.label" />
        </p>

        <template v-if="grade.next">
            <p class="m-0 text-sm font-semibold" :class="showGrade ? 'mt-2' : ''" data-testid="grade-next-heading">{{ t('grade.nextHeading', { label: grade.next.label }) }}</p>
            <div v-for="(r, index) in grade.next.requirements" :key="index" class="mt-2" :data-testid="`grade-req-${index}`">
                <div class="flex items-baseline justify-between gap-2 text-xs">
                    <span class="min-w-0 text-muted-color">{{ UtilGradeMessages.requirementLabel(t, r) }}</span>
                    <span class="shrink-0" :class="bars[index]?.met ? 'font-semibold text-green-700 dark:text-green-400' : ''">{{ UtilGradeMessages.progressText(t, r) }}</span>
                </div>
                <div
                    class="mt-1 h-1.5 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700"
                    role="progressbar"
                    :aria-label="UtilGradeMessages.requirementLabel(t, r)"
                    aria-valuemin="0"
                    :aria-valuemax="bars[index]?.required"
                    :aria-valuenow="bars[index]?.shown"
                >
                    <div class="h-full rounded-full transition-[width]" :class="bars[index]?.met ? 'bg-green-500' : 'bg-primary'" :style="{ width: `${bars[index]?.percent ?? 0}%` }" />
                </div>
            </div>
        </template>

        <template v-else>
            <p class="m-0 text-sm font-semibold" :class="showGrade ? 'mt-2' : ''" data-testid="grade-highest">{{ t('grade.highestTitle') }}</p>
            <p class="m-0 mt-1 text-xs text-muted-color">{{ t('grade.highestText') }}</p>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { Grade } from '~/composables/auth/api/useAuthApi.types';
import type { MyGrade } from '~/composables/grade/api/useGradeApi.types';
import UtilGrade from '~/utils/util-grade';
import UtilGradeMessages from '~/utils/util-grade-messages';

/**
 * 등급 진행도 — 그리기만 한다(`GET /api/me/grade` 응답 하나). 등급 안내(`/me/grade`)와 내 프로필이 쓴다.
 * 막대의 현재 값은 서버가 상한 없이 주는 실제 값이라 `UtilGrade.bar` 가 필요 값에서 자른다. `showGrade` 가 false 면 「내 등급 …」 줄을 뺀다(프로필 카드 안).
 */
interface Props {
    grade: MyGrade;
    showGrade?: boolean;
}

const props = withDefaults(defineProps<Props>(), { showGrade: true });

const { t } = useI18n();
const bars = computed(() => (props.grade.next?.requirements ?? []).map((r) => UtilGrade.bar(r)));
const paused = computed(() => UtilGrade.isPaused(props.grade.autoPromotion));
</script>
