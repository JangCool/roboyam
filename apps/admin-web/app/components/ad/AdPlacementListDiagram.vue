<template>
    <!--
        게시판 목록 도식 — user-web `BoardPostList`: 공지·고정 줄(다른 바탕)이 맨 위, 그 아래 일반 글. 한 줄 광고는 일반 글 `adAfter` 번째 줄 바로 뒤(공지·고정은 세지 않는다).
        일반 글 줄에는 차례 번호를 붙여 몇 번째 뒤인지 보이게 하고, 긴 목록은 사이를 「⋯ N줄」로 접는다(`UtilAd.previewFeedRows`).
    -->
    <div class="min-w-0 overflow-hidden rounded-md border border-(--line) bg-(--p-content-background)" data-testid="ad-placement-list-diagram">
        <!-- 게시판 머리 -->
        <div class="flex items-center gap-1.5 border-b border-(--line) px-2 py-1.5">
            <span v-if="adult" class="inline-flex size-3.5 shrink-0 items-center justify-center rounded-full bg-surface-400 text-[0.5rem] font-bold leading-none text-surface-0 dark:bg-surface-500" aria-hidden="true">19</span>
            <span class="block h-2 w-1/3 rounded-full bg-surface-300 dark:bg-surface-600" />
        </div>
        <ul class="m-0 list-none p-0">
            <!-- 공지 · 고정 -->
            <li v-for="kind in pinnedKinds" :key="kind" :class="[row, 'bg-surface-100 dark:bg-surface-800']">
                <span class="inline-flex w-7 shrink-0 items-center gap-0.5 text-[0.5625rem] leading-none text-muted-color"><i class="pi pi-thumbtack text-[0.5rem]" aria-hidden="true" />{{ t(`ad.previewPinnedKind.${kind}`) }}</span>
                <span :class="[bar, kind === 'NOTICE' ? 'w-1/2' : 'w-2/5']" />
            </li>
            <template v-for="(item, i) in items" :key="item.kind === 'row' ? `r${item.n}` : `g${i}`">
                <li v-if="item.kind === 'gap'" class="border-b border-(--line) last:border-b-0 px-2 py-0.5 text-center text-[0.5625rem] leading-tight text-muted-color">{{ t('ad.previewRowsFolded', { n: item.count }) }}</li>
                <template v-else>
                    <li :class="row">
                        <span class="w-7 shrink-0 text-right text-[0.5625rem] leading-none tabular-nums text-muted-color">{{ item.n }}</span>
                        <span :class="[bar, widths[item.n % widths.length]]" />
                    </li>
                    <li v-if="adAfter === item.n" class="border-b border-(--line) last:border-b-0 px-1.5 py-1" data-testid="ad-placement-list-ad">
                        <AdPlacementSlot shape="line" />
                    </li>
                </template>
            </template>
        </ul>
    </div>
</template>

<script setup lang="ts">
import UtilAd from '~/utils/util-ad';

interface Props {
    /** 이 쪽의 일반 글 수 */
    rows: number;
    /** 광고가 붙는 일반 글 차례(1부터) — null 이면 광고 줄 없음 */
    adAfter?: number | null;
    /** 19금 게시판 표시 */
    adult?: boolean;
    /** 접지 않고 다 두는 줄 수 */
    keepAll?: number;
}

const props = withDefaults(defineProps<Props>(), { adAfter: null, adult: false, keepAll: 8 });
const { t } = useI18n();

const pinnedKinds = ['NOTICE', 'PINNED'] as const;
const row = 'flex items-center gap-1.5 border-b border-(--line) px-2 py-1 last:border-b-0';
const bar = 'block h-1.5 rounded-full bg-surface-200 dark:bg-surface-700';
const widths = ['w-3/5', 'w-1/2', 'w-2/3', 'w-2/5', 'w-1/2'];

const items = computed(() => UtilAd.previewFeedRows(props.rows, props.adAfter, props.keepAll));
</script>
