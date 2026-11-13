<template>
    <!--
        요금 예약 변경 안내 한 줄 — 광고 종류 카드·핫딜 노출 자리 줄·핫딜 글 안내 아래(광고·핫딜 신청). 「6월 22일 (월) 00:00부터 하루 60,000원으로 올라요」.
        오름·내림은 문구(「올라요」·「내려요」)와 화살표 아이콘으로 알린다(색은 모두 같다 — 색만으로 가르지 않는다). 일수만 바뀌면 시계 아이콘.
        `<label>` 안에도 놓이므로 글자 요소(`span`)만 쓴다.
    -->
    <span class="flex items-start gap-1 text-xs font-medium text-primary" data-testid="business-price-change-note">
        <i :class="[ICON[note.direction ?? 'NONE'], 'mt-0.5 text-[0.6875rem]']" aria-hidden="true" />
        <span class="min-w-0">{{ note.text }}</span>
    </span>
</template>

<script setup lang="ts">
import type { BizPriceChangeNote } from '~/utils/util-business-messages';

interface Props {
    note: BizPriceChangeNote;
}

withDefaults(defineProps<Props>(), {});

const ICON: Record<'UP' | 'DOWN' | 'NONE', string> = {
    UP: 'pi pi-arrow-up',
    DOWN: 'pi pi-arrow-down',
    NONE: 'pi pi-clock',
};
</script>
