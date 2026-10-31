<template>
    <!--
        게시판 목록 안 한 줄 광고(인피드 — `TEXT_LINE` 후보 하나, 목록 줄 사이) — 그 목록의 글 줄과 **같은 높이·글꼴**로 그린다(`variant`):
          · `table`    기본 목록 데스크톱 표 한 줄(`<tr>`) — 제목 칸 = 「광고」 꼬리표 + 문구, 작성자 칸 = 광고주 이름(없으면 빈칸), 날짜·조회·추천 칸은 비운다
          · `compact`  기본 목록 모바일 두 줄 · `headline` 다음식·로보얌 헤드라인 줄 · `article` 네이버식 기사형 줄 — 제목 자리 = 꼬리표 + 문구(40자), 메타 줄 = 광고주 이름(없으면 빈 줄 높이만)
          · `card`     카드형 한 칸 — 사진 자리에 확성기 아이콘, 제목 자리에 꼬리표 + 문구
        글 번호를 차지하지 않는다(이 앱의 목록에는 번호 칸이 없어 꼬리표가 제목 앞에 선다). 링크는 새 탭 + `UtilAd.REL`, 노출(`BOARD_LIST`)은 절반 이상 보였을 때 한 번, 누르면 클릭을 남긴다.
        감싸는 줄(`li` — 선·여백)은 목록이 그리고, 이 부품은 줄 안쪽만 그린다(`table` 만 `<tr>` 째).
    -->
    <tr v-if="variant === 'table'" ref="root" class="border-b border-surface hover:bg-surface-50 dark:hover:bg-surface-900" data-testid="ad-feed-row">
        <td class="py-2.5 pr-3">
            <a :href="ad.linkUrl" target="_blank" :rel="UtilAd.REL" class="flex min-w-0 items-center gap-1.5 hover:underline focus-visible:outline-2 focus-visible:outline-primary" @click="onClick" @auxclick="onClick">
                <PointShopTag tone="neutral">{{ t('ad.mark') }}</PointShopTag>
                <span class="min-w-0 truncate">{{ text }}</span>
                <span class="sr-only">{{ t('ad.newTab') }}</span>
            </a>
        </td>
        <td class="truncate py-2.5 text-xs text-muted-color">{{ ad.advertiserName ?? '' }}</td>
        <td class="py-2.5" />
        <td class="py-2.5" />
        <td class="py-2.5 pr-1" />
    </tr>
    <div v-else ref="root" data-testid="ad-feed-row" :data-variant="variant">
        <a
            :href="ad.linkUrl"
            target="_blank"
            :rel="UtilAd.REL"
            class="group flex items-start focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
            :class="LINK[variant]"
            @click="onClick"
            @auxclick="onClick"
        >
            <span v-if="variant === 'card'" class="grid aspect-[4/3] w-28 shrink-0 place-items-center rounded-md bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true">
                <i class="pi pi-megaphone" />
            </span>
            <span class="flex min-w-0 flex-1 flex-col">
                <span class="flex min-w-0 items-center gap-1.5" :class="TITLE[variant]">
                    <PointShopTag tone="neutral">{{ t('ad.mark') }}</PointShopTag>
                    <span class="min-w-0 group-hover:underline" :class="variant === 'compact' || variant === 'card' ? 'line-clamp-2' : 'truncate'">{{ text }}</span>
                </span>
                <span class="block truncate text-muted-color" :class="META[variant]">{{ ad.advertiserName ?? '' }}</span>
            </span>
            <span class="sr-only">{{ t('ad.newTab') }}</span>
        </a>
    </div>
</template>

<script setup lang="ts">
import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import UtilAd from '~/utils/util-ad';

type FeedVariant = 'table' | 'compact' | 'headline' | 'article' | 'card';

interface Props {
    ad: PublicAd;
    /** 어느 목록 줄 모양에 맞추나 */
    variant: FeedVariant;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const tracking = usePublicAds();

/** 줄 안 여백·높이 — 그 목록의 글 줄 링크와 같다(`BoardPostList` 모바일 · `DaumPostList` · `NaverPostList` · `BoardPostCards`) */
const LINK: Record<Exclude<FeedVariant, 'table'>, string> = {
    compact: 'gap-3 px-4 py-2.5',
    headline: 'gap-3 py-2.5',
    article: 'gap-3.5 py-3 md:py-3.5',
    card: 'gap-3 p-3',
};
/** 제목 자리 글꼴 */
const TITLE: Record<Exclude<FeedVariant, 'table'>, string> = {
    compact: 'text-sm leading-snug',
    headline: 'text-[15.5px] font-normal leading-[23px]',
    article: 'text-[15.5px] font-normal leading-[23px]',
    card: 'text-sm font-medium leading-snug',
};
/** 메타 줄 — 광고주 이름(없어도 줄 높이는 남겨 글 줄과 같은 높이) */
const META: Record<Exclude<FeedVariant, 'table'>, string> = {
    compact: 'mt-1 min-h-4 text-xs',
    headline: 'mt-[3px] min-h-[18px] text-[12.5px] leading-[18px]',
    article: 'min-h-[18px] pt-1.5 text-[12.5px] leading-[18px]',
    card: 'mt-auto min-h-4 pt-1 text-xs',
};

const text = computed(() => UtilAd.clipText(props.ad.text));

function onClick(): void {
    tracking.click(props.ad, 'BOARD_LIST');
}

// 노출 — 절반 이상 보였을 때 한 번
const root = ref<HTMLElement | null>(null);
useAdImpressions(
    root,
    () => [props.ad],
    () => 'BOARD_LIST',
);
</script>
