<template>
    <!--
        광고 배너 가젯(`AD` — 02 §5-1: 배너 자리 하나) — 설정 `config.adType`(넓은·띠·네모·작은 배너, 처음 값 넓은 배너)의 후보 중 하나를 `AdSlot` 이 그린다.
        후보는 서버 홈 응답이 아니라 페이지가 `useAdGadgets` 로 채운 `data`(`AdGadgetData`)다. 머리·카드는 공통 껍데기(`HomeGadgetFrame`)의 꾸미기 그대로 — 처음 꾸미기는 머리 숨김 · 카드 없이(평평).
        비었을 때: 「빈 자리 유지」가 꺼져 있으면(처음 값) 격자가 자리째 뺀다. 켜져 있으면 껍데기의 기본 빈 안내 대신 「광고 문의」 안내(`AdInquiry`)를 같은 비율로.
        PC/모바일 노출은 ⚙ 「노출」 탭(가젯 공통). 19금·게시판 제외는 가젯이 아니라 글 중간 광고에서만 한다.
    -->
    <HomeGadgetFrame :gadget="gadget" :empty="false">
        <AdSlot
            :type="adType"
            :ads="data.ads"
            :placement="data.placement"
            :keep-empty="gadget.style.empty.keep"
            :empty-message="gadget.style.empty.message"
            :empty-text="gadget.style.empty.text"
        />
    </HomeGadgetFrame>
</template>

<script setup lang="ts">
import type { HomeGadget } from '~/composables/home/api/useHomeGadgetApi.types';
import UtilAd from '~/utils/util-ad';

interface Props {
    gadget: HomeGadget;
}

const props = withDefaults(defineProps<Props>(), {});

const adType = computed(() => UtilAd.gadgetAdType(props.gadget.config));
const data = computed(() => UtilAd.gadgetData(props.gadget.data));
</script>
