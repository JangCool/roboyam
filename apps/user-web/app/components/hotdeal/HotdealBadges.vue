<template>
    <!--
        딜 배지 — 제휴 · 종료 · 기간 종료 · 의심(「품절 알림 N」). 목록 줄(제목 앞)·카드·가젯·글 보기 딜 카드가 같이 쓴다.
        글자 세로 맞춤은 components.md 2-4(알약 `inline-flex items-center justify-center leading-none` + 글자 `top:-0.09em`).
    -->
    <span v-if="items.length" class="inline-flex flex-wrap items-center gap-1 align-[1px]" data-testid="hotdeal-badges">
        <span v-for="item in items" :key="item.key" class="inline-flex items-center justify-center rounded px-1.5 py-1 text-[0.6875rem] font-semibold leading-none" :class="item.tone" :data-badge="item.key">
            <span class="relative top-[-0.09em]">{{ item.label }}</span>
        </span>
    </span>
</template>

<script setup lang="ts">
import type { PostDeal } from '~/composables/hotdeal/api/useHotdealApi.types';

interface Props {
    deal: Pick<PostDeal, 'affiliate' | 'view' | 'endRequests'>;
    /** 의심 배지(「품절 알림 N」)를 보인다 — 글 보기 딜 카드는 위 안내 줄이 따로 말해서 끈다 */
    flagged?: boolean;
}

const props = withDefaults(defineProps<Props>(), { flagged: true });

const { t } = useI18n();

const TONE = {
    affiliate: 'bg-sky-100 text-sky-800 dark:bg-sky-900/50 dark:text-sky-200',
    over: 'bg-surface-200 text-surface-700 dark:bg-surface-700 dark:text-surface-100',
    flagged: 'bg-amber-100 text-amber-800 dark:bg-amber-900/50 dark:text-amber-200',
} as const;

const items = computed(() => {
    const d = props.deal;
    const out: { key: string; label: string; tone: string }[] = [];
    if (d.view === 'ENDED') out.push({ key: 'ended', label: t('hotdeal.badgeEnded'), tone: TONE.over });
    else if (d.view === 'EXPIRED') out.push({ key: 'expired', label: t('hotdeal.badgeExpired'), tone: TONE.over });
    else if (d.view === 'FLAGGED' && props.flagged) out.push({ key: 'flagged', label: t('hotdeal.badgeFlagged', { n: d.endRequests.total }), tone: TONE.flagged });
    if (d.affiliate) out.push({ key: 'affiliate', label: t('hotdeal.badgeAffiliate'), tone: TONE.affiliate });
    return out;
});
</script>
