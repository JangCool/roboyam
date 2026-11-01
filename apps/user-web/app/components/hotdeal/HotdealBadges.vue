<template>
    <!--
        딜 배지 — 목록 줄(제목 앞)·카드·갤러리·가젯·글 보기 딜 카드가 같이 쓴다. 차례(05 §2-2·§2-13):
          제휴·수수료(첫 부분 표시) → 상태(종료 / 품절 / 기간 종료 / 품절 알림 N) → 재고 얼마 안 남음 → 마감 임박(「N시간 남음」·「내일 마감」, 48시간 안) → HOT(서버 `hot`)
          → 링크 바뀜 · 가격 수정 N회(D3).
        끝난 딜은 상태 배지만(임박·HOT·재고 얼마 안 남음은 뺀다). 품절로 끝난 딜은 「종료」 대신 「품절」.
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
import UtilHotdeal from '~/utils/util-hotdeal';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';

interface Props {
    deal: Pick<PostDeal, 'affiliate' | 'view' | 'endRequests' | 'stockState' | 'endsAt' | 'hot' | 'revision'>;
    /** 의심 배지(「품절 알림 N」)를 보인다 — 글 보기 딜 카드는 위 안내 줄이 따로 말해서 끈다 */
    flagged?: boolean;
    /** 수정 이력 배지(링크 바뀜·가격 수정 N회)를 보인다 — 가젯은 좁아서 끈다 */
    revision?: boolean;
}

const props = withDefaults(defineProps<Props>(), { flagged: true, revision: true });

const { t } = useI18n();

const TONE = {
    affiliate: 'bg-sky-100 text-sky-800 dark:bg-sky-900/50 dark:text-sky-200',
    over: 'bg-surface-200 text-surface-700 dark:bg-surface-700 dark:text-surface-100',
    flagged: 'bg-amber-100 text-amber-800 dark:bg-amber-900/50 dark:text-amber-200',
    low: 'bg-orange-100 text-orange-800 dark:bg-orange-900/50 dark:text-orange-200',
    soon: 'bg-rose-100 text-rose-800 dark:bg-rose-900/50 dark:text-rose-200',
    hot: 'bg-red-600 text-white dark:bg-red-500',
    revision: 'border border-surface text-muted-color',
} as const;

const items = computed(() => {
    const d = props.deal;
    const out: { key: string; label: string; tone: string }[] = [];
    if (d.affiliate) out.push({ key: 'affiliate', label: t('hotdeal.badgeAffiliate'), tone: TONE.affiliate });
    const over = UtilHotdeal.isOver(d.view);
    if (d.view === 'ENDED') out.push(d.stockState === 'SOLD_OUT' ? { key: 'sold-out', label: t('hotdeal.badgeSoldOut'), tone: TONE.over } : { key: 'ended', label: t('hotdeal.badgeEnded'), tone: TONE.over });
    else if (d.view === 'EXPIRED') out.push({ key: 'expired', label: t('hotdeal.badgeExpired'), tone: TONE.over });
    else if (d.view === 'FLAGGED' && props.flagged) out.push({ key: 'flagged', label: t('hotdeal.badgeFlagged', { n: d.endRequests.total }), tone: TONE.flagged });
    if (!over) {
        if (d.stockState === 'LOW') out.push({ key: 'low', label: t('hotdeal.badgeLow'), tone: TONE.low });
        const soon = UtilHotdeal.endingSoon(d, Date.now());
        if (soon) out.push({ key: 'ending', label: UtilHotdealMessages.endingSoon(t, soon), tone: TONE.soon });
        if (d.hot) out.push({ key: 'hot', label: t('hotdeal.badgeHot'), tone: TONE.hot });
    }
    if (props.revision && d.revision.linkChanged) out.push({ key: 'link-changed', label: t('hotdeal.badgeLinkChanged'), tone: TONE.revision });
    if (props.revision && d.revision.priceEdits > 0) out.push({ key: 'price-edited', label: t('hotdeal.badgePriceEdited', { n: d.revision.priceEdits }), tone: TONE.revision });
    return out;
});
</script>
