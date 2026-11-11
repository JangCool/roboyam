<template>
    <!-- 경험치 막대 — 이 레벨에서 채운 만큼(grade-level-contract 4절, 확정 시안 ④: 짧은 막대 + 「다음 레벨까지 N%」). `detail` 이면 아래에 경험치·다음 레벨까지 남은 XP -->
    <div data-testid="grade-level-bar">
        <div class="flex items-center gap-2">
            <div
                class="h-[5px] max-w-[11.25rem] flex-1 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700"
                role="progressbar"
                :aria-label="t('grade.levelBarLabel')"
                aria-valuemin="0"
                aria-valuemax="100"
                :aria-valuenow="progress.percent"
            >
                <div class="h-full rounded-full bg-amber-500" :style="{ width: `${progress.percent}%` }" />
            </div>
            <span class="shrink-0 text-[0.6875rem] text-muted-color">{{ t('grade.toNextLevel', { percent: progress.remainingPercent }) }}</span>
        </div>
        <p v-if="detail" class="m-0 mt-1 text-xs text-muted-color tabular-nums" data-testid="grade-level-xp">
            {{ t('grade.xpValue', { xp: progress.xp.toLocaleString() }) }}<template v-if="progress.left > 0"> · {{ t('grade.toNextLevelXp', { next: progress.level + 1, left: progress.left.toLocaleString() }) }}</template>
        </p>
    </div>
</template>

<script setup lang="ts">
import type { LevelProgress } from '~/utils/util-grade';

interface Props {
    /** 레벨 진행(`UtilGrade.levelProgress`) */
    progress: LevelProgress;
    /** 경험치·남은 XP 줄(내 정보·등급 안내) */
    detail?: boolean;
}

withDefaults(defineProps<Props>(), { detail: false });

const { t } = useI18n();
</script>
