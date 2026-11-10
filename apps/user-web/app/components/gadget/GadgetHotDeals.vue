<template>
    <!--
        핫딜 가젯(`HOT_DEALS` — `hotdeal-contract.md` 「가젯 `HOT_DEALS` 설정」) — 진행 중인 핫딜을 가격 줄 작은 목록으로(썸네일 · 배지 · 제목 · 몰 이름 · 할인율 · 딜가 · 정상가 · 배송비).
        줄은 서버가 가젯 설정(`boards`·`sort`·`count`)대로 골라 `data.items` 에 싣는다(컴포넌트는 서버를 부르지 않는다). 줄 모양은 핫딜 목록 줄(`HotdealRowItem compact`)과 같다.
        목록 아래 「핫딜 더 보기 →」(05 §2-15) — 가젯 머리의 「더보기」 대신 이것 하나만 그린다(틀에는 `more` 를 빼고 넘긴다). 갈 곳은 서버 `data.more.to` 가 있으면 그것,
          그다음 가젯 `more.to`(운영자 꾸미기), 둘 다 없으면 설정 게시판 첫 번째 → 비었으면(모든 핫딜 게시판) 첫 줄의 게시판.
        비면 `GadgetFrame` 의 빈 상태(기본 꾸미기는 영역을 그리지 않는다). 마우스 올림 효과(설정 `hover`)는 줄에 옅은 바탕(`gadget-hover` — 줄 부품 뿌리 링크에 클래스로 넘긴다, `main.css`). 홈·가젯 페이지·게시판 레이아웃이 같이 쓴다.
    -->
    <GadgetFrame :gadget="frameGadget" :empty="gadget.empty || items.length === 0" empty-icon="pi pi-tag">
        <ul class="m-0 list-none divide-y divide-surface p-0" data-testid="home-hot-deals">
            <li v-for="row in items" :key="row.id">
                <HotdealRowItem :row="row" :new-post-hours="display.newPostHours" compact class="gadget-hover" />
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
    </GadgetFrame>
</template>

<script setup lang="ts">
import type { Gadget } from '~/composables/gadget/api/useGadgetApi.types';
import UtilGadget from '~/utils/util-gadget';

interface Props {
    gadget: Gadget;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const { display } = useBoardDisplay();
const items = computed(() => UtilGadget.hotDealItems(props.gadget.data));
/** 틀의 「더보기」는 그리지 않는다 — 목록 아래 「핫딜 더 보기 →」 하나로 */
const frameGadget = computed<Gadget>(() => ({ ...props.gadget, more: null }));
const moreTo = computed(() => {
    if (props.gadget.locked || items.value.length === 0) return '';
    const fromData = UtilGadget.hotDealsMoreTo(props.gadget.data);
    if (fromData) return fromData;
    if (props.gadget.more?.to) return props.gadget.more.to;
    const boards = UtilGadget.hotDealsConfig(props.gadget.config).boards;
    const slug = boards[0] ?? items.value[0]?.board.slug ?? '';
    return slug ? `/boards/${slug}` : '';
});
</script>
