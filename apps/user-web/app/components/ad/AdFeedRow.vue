<template>
    <!--
        게시판 목록 안 한 줄 광고(인피드 — `TEXT_LINE` 후보 하나, 목록 줄 사이) — **말 그대로 한 줄**: 「광고」 꼬리표 + 문구(40자, 넘치면 말줄임). 둘째 줄(광고주·메타)은 두지 않는다.
        글꼴·좌우 여백은 그 목록의 글 줄에 맞추고(`variant`), 높이는 한 줄이다(여러 줄짜리 글 줄 — 다음식 헤드라인·네이버식 기사형 — 보다 낮다):
          · `table`    기본 목록 데스크톱 표 한 줄(`<tr>`) — 제목 칸 = 꼬리표 + 문구, 작성자 칸 = 광고주 이름(있을 때만, 작은 흐린 글씨), 날짜·조회·추천 칸은 비운다. 광고주 이름은 이 모양에만 보인다
          · `compact`  기본 목록 모바일 · `headline` 다음식·로보얌 · `article` 네이버식 — 꼬리표 + 문구 한 줄(광고주 이름 없음)
          · `card`     카드형 — 카드 한 칸 대신 **두 열을 가로지르는 얇은 한 줄**(카드 칸이면 한 줄 규칙과 맞지 않아서). 감싸는 `li` 가 `md:col-span-2`
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
            class="group flex min-w-0 items-center gap-1.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
            :class="LINE[variant]"
            @click="onClick"
            @auxclick="onClick"
        >
            <PointShopTag tone="neutral">{{ t('ad.mark') }}</PointShopTag>
            <span class="min-w-0 truncate group-hover:underline">{{ text }}</span>
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

/** 한 줄의 좌우 여백·위아래 여백·글꼴 — 그 목록의 글 줄 링크에 맞춘다(`BoardPostList` 모바일 · `DaumPostList` · `NaverPostList` · `BoardPostCards`) */
const LINE: Record<Exclude<FeedVariant, 'table'>, string> = {
    compact: 'px-4 py-2.5 text-sm leading-snug',
    headline: 'py-2.5 text-[15.5px] leading-[23px]',
    article: 'py-3 text-[15.5px] leading-[23px]',
    card: 'px-3 py-2 text-sm leading-snug',
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
