<template>
    <!--
        배너 광고 자리 하나(02 §7-7) — 넓은·띠·네모·작은 배너. 받은 후보(`ads`) 중 비중 가중 무작위로 1건을 골라 그린다(`UtilAd.pick`).
          · 틀: 유형별 비율 고정(`UtilAd.frameOf` — 1200×300 = 4:1 · 1200×100 = 12:1 · 300×250 = 6:5 · 300×100 = 3:1), 네모·작은 배너는 300px 까지(칸 시작선에 붙이고, 칸이 360px 이하면 칸을 채운다 — `widthClass`). 이미지가 늦게 와도 자리가 흔들리지 않는다.
          · 「광고」 표시는 늘 배너 위 오른쪽 위에. 이미지 `alt` = 대체 글, 이미지를 못 읽으면 대체 글을 틀 안에 글자로.
          · 링크: 새 탭 + `rel="sponsored nofollow noopener"`(`UtilAd.REL` — noreferrer 는 붙이지 않는다, 근거는 거기). `href` 는 광고 주소 그대로이고 누르면 클릭을 `sendBeacon` 으로 남긴다.
          · 노출: 화면에 절반 이상 보였을 때 한 번(IntersectionObserver) — 같은 광고·같은 소재 버전이면 이 자리에서 다시 세지 않는다.
          · 받는 중(`ads` null): 같은 비율의 빈 틀 · 후보 없음: `keepEmpty` 면 「광고 문의」 안내(`AdInquiry`), 아니면 아무것도 그리지 않는다(자리를 접는 것은 부르는 쪽 — 가젯은 격자가 자리째 뺀다).
        고르기는 클라이언트에서 한다(이 앱은 `ssr:false` SPA — 서버 그리기와 어긋날 일이 없다). 후보가 새로 와도(사본 갱신) 보이던 광고가 아직 있으면 바꾸지 않는다.
        색은 토큰·테마 역할 변수만(모서리 `--gadget-card-radius` — 테마 카드 모양을 따른다). 「광고」 표시만 이미지 위에서 읽히게 어두운 반투명 바탕(사업자 미리보기와 같은 모양).
    -->
    <div v-if="ads === null || current || keepEmpty" ref="root" class="w-full min-w-0" :class="[widthClass, align === 'center' ? 'mx-auto' : '']" :data-ad-type="type" data-testid="ad-slot">
        <div v-if="ads === null" class="w-full animate-pulse rounded-(--gadget-card-radius) bg-surface-100 dark:bg-surface-800" :style="{ aspectRatio: frame.ratio }" role="status" :aria-label="t('ad.loading')" />
        <a
            v-else-if="current"
            :href="current.linkUrl"
            target="_blank"
            :rel="UtilAd.REL"
            class="relative block w-full overflow-hidden rounded-(--gadget-card-radius) bg-surface-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:bg-surface-800"
            :style="{ aspectRatio: frame.ratio }"
            data-testid="ad-banner"
            @click="onClick"
            @auxclick="onClick"
        >
            <img v-if="!imageFailed && current.imageUrl" :src="current.imageUrl" :alt="current.altText" class="block size-full object-cover" loading="lazy" decoding="async" @error="imageFailed = true" />
            <span v-else class="grid size-full place-items-center px-3 text-center text-sm text-muted-color">{{ current.altText }}</span>
            <span class="absolute right-1.5 top-1.5 inline-flex items-center justify-center rounded bg-black/60 px-1.5 py-1 text-[0.625rem] font-semibold leading-none text-white" data-testid="ad-mark">
                <span class="relative top-[-0.09em]">{{ t('ad.mark') }}</span>
            </span>
            <span class="sr-only">{{ t('ad.newTab') }}</span>
        </a>
        <AdInquiry v-else :message="emptyMessage" :text="emptyText" :ratio="frame.ratio" />
    </div>
</template>

<script setup lang="ts">
import type { ImageAdType, PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import type { AdPlacement } from '~/composables/ad/usePublicAds.types';
import UtilAd from '~/utils/util-ad';

interface Props {
    /** 배너 유형 — 틀 비율 */
    type: ImageAdType;
    /** 이 유형의 후보 — null 이면 받는 중 */
    ads: PublicAd[] | null;
    /** 어디서 보였나(집계) */
    placement: AdPlacement;
    /** 비었을 때 자리를 남기고 「광고 문의」를 보이는가 */
    keepEmpty?: boolean;
    /** 최대 폭이 있는 배너(네모·작은 배너 300px)를 칸 안 어디에 두나 — 'start' 칸 시작선(가젯 — 다른 가젯 가장자리와 맞게) · 'center' 가운데(글 보기) */
    align?: 'start' | 'center';
    /** 남긴 자리에 안내를 보이는가(끄면 빈 틀) */
    emptyMessage?: boolean;
    /** 운영자가 적은 안내 문구 */
    emptyText?: string;
}

const props = withDefaults(defineProps<Props>(), { keepEmpty: false, align: 'start', emptyMessage: true, emptyText: '' });

const { t } = useI18n();
const tracking = usePublicAds();

const frame = computed(() => UtilAd.frameOf(props.type));
/**
 * 폭 — 넓은·띠 배너는 칸을 채운다. 네모·작은 배너(최대 300px)는 칸이 360px 이하로 좁으면(오른쪽 열 등) 칸을 채우고, 넓으면 300px(`align` 쪽에 붙임).
 * 칸 폭은 가젯 껍데기 본문의 컨테이너(`@container`)로 잰다 — 컨테이너가 없는 곳(글 보기)은 300px 그대로. 날개 배너는 160px 고정.
 */
const widthClass = computed(() => {
    if (props.type === 'WING_BANNER') return 'max-w-[160px]';
    return frame.value.maxWidth === '100%' ? '' : 'max-w-[300px] @max-[22.5rem]:max-w-none';
});
const current = ref<PublicAd | null>(null);
const imageFailed = ref(false);

// 고르기 — 후보가 바뀔 때만(보이던 광고가 아직 있으면 그대로)
watch(
    () => props.ads,
    (list) => {
        if (!list) {
            current.value = null;
            return;
        }
        if (UtilAd.stillIn(current.value, list)) return;
        current.value = UtilAd.pick(list);
        imageFailed.value = false;
    },
    { immediate: true },
);

function onClick(): void {
    if (current.value) tracking.click(current.value, props.placement);
}

// 노출 — 절반 이상 보였을 때 한 번
const root = ref<HTMLElement | null>(null);
useAdImpressions(
    root,
    () => (current.value ? [current.value] : []),
    () => props.placement,
);
</script>
