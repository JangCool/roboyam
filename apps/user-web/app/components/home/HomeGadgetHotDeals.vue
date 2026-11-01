<template>
    <!--
        핫딜 가젯(`HOT_DEALS` — `hotdeal-contract.md` 「가젯 `HOT_DEALS` 설정」) — 진행 중인 핫딜을 가격 줄 작은 목록으로(썸네일 · 배지 · 제목 · 몰 이름 · 할인율 · 딜가 · 정상가 · 배송비).
        줄은 서버가 가젯 설정(`boards`·`sort`·`count`)대로 골라 `data.items` 에 싣는다(컴포넌트는 서버를 부르지 않는다). 줄 모양은 핫딜 목록 줄(`HotdealRowItem compact`)과 같다.
        목록 아래 「핫딜 더 보기 →」(05 §2-15) — 가젯 머리의 「더보기」 대신 이것 하나만 그린다(틀에는 `more` 를 빼고 넘긴다). 갈 곳은 서버 `more.to` 가 있으면 그것,
          없으면 설정 게시판 첫 번째(하나면 그 게시판, 여럿이면 첫 번째) → 비었으면(모든 핫딜 게시판) 첫 줄의 게시판(가정 — 「첫 핫딜 게시판」은 서버가 `more.to` 로 준다).
        비면 `HomeGadgetFrame` 의 빈 상태(기본 꾸미기는 영역을 그리지 않는다). 홈·가젯 페이지·게시판 레이아웃이 같이 쓴다.
    -->
    <HomeGadgetFrame :gadget="frameGadget" :empty="gadget.empty || items.length === 0" empty-icon="pi pi-tag">
        <ul class="m-0 list-none divide-y divide-surface p-0" data-testid="home-hot-deals">
            <li v-for="row in items" :key="row.id">
                <HotdealRowItem :row="row" :new-post-hours="display.newPostHours" compact />
            </li>
        </ul>
        <NuxtLink
            v-if="moreTo"
            :to="moreTo"
            class="mt-1 flex items-center justify-end gap-1 rounded py-1.5 text-[0.8125rem] font-medium text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary"
            data-testid="home-hot-deals-more"
        >
            {{ gadget.more?.label || t('hotdeal.gadgetMore') }}<i class="pi pi-arrow-right text-[0.6875rem]" aria-hidden="true" />
        </NuxtLink>
    </HomeGadgetFrame>
</template>

<script setup lang="ts">
import type { HomeGadget } from '~/composables/home/api/useHomeGadgetApi.types';
import UtilHomeGadget from '~/utils/util-home-gadget';

interface Props {
    gadget: HomeGadget;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const { display } = useBoardDisplay();
const items = computed(() => UtilHomeGadget.hotDealItems(props.gadget.data));
/** 틀의 「더보기」는 그리지 않는다 — 목록 아래 「핫딜 더 보기 →」 하나로 */
const frameGadget = computed<HomeGadget>(() => ({ ...props.gadget, more: null }));
const moreTo = computed(() => {
    if (props.gadget.locked || items.value.length === 0) return '';
    if (props.gadget.more?.to) return props.gadget.more.to;
    const boards = UtilHomeGadget.hotDealsConfig(props.gadget.config).boards;
    const slug = boards[0] ?? items.value[0]?.board.slug ?? '';
    return slug ? `/boards/${slug}` : '';
});
</script>
