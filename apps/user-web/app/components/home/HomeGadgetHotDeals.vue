<template>
    <!--
        핫딜 가젯(`HOT_DEALS` — `hotdeal-contract.md` 「가젯 `HOT_DEALS` 설정」) — 진행 중인 핫딜을 가격 줄 작은 목록으로(썸네일 · 배지 · 제목 · 몰 이름 · 할인율 · 딜가 · 정상가).
        줄은 서버가 가젯 설정(`boards`·`sort`·`count`)대로 골라 `data.items` 에 싣는다(컴포넌트는 서버를 부르지 않는다). 줄 모양은 핫딜 목록 줄(`HotdealRowItem compact`)과 같다.
        비면 `HomeGadgetFrame` 의 빈 상태(기본 꾸미기는 영역을 그리지 않는다). 홈·가젯 페이지·게시판 레이아웃이 같이 쓴다.
    -->
    <HomeGadgetFrame :gadget="gadget" :empty="gadget.empty || items.length === 0" empty-icon="pi pi-tag">
        <ul class="m-0 list-none divide-y divide-surface p-0" data-testid="home-hot-deals">
            <li v-for="row in items" :key="row.id">
                <HotdealRowItem :row="row" :new-post-hours="display.newPostHours" compact />
            </li>
        </ul>
    </HomeGadgetFrame>
</template>

<script setup lang="ts">
import type { HomeGadget } from '~/composables/home/api/useHomeGadgetApi.types';
import UtilHomeGadget from '~/utils/util-home-gadget';

interface Props {
    gadget: HomeGadget;
}

const props = withDefaults(defineProps<Props>(), {});

const { display } = useBoardDisplay();
const items = computed(() => UtilHomeGadget.hotDealItems(props.gadget.data));
</script>
