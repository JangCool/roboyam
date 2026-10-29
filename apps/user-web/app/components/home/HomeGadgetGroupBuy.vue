<template>
    <!--
        진행 중 공동구매 가젯(`GROUP_BUY`) — 모집 중인 공구를 한 장씩 넘겨 보는 카드(사진 + 마감·품절 표시 · 이름 · 할인율·공구가·정상가 · 리뷰 · 진행 막대 · 남은 수량).
        가젯 `data` 는 `{ items, salesOpen }` — 줄은 회원 공구 목록(`GET /api/group-buys`)과 같고, 서버가 가젯 설정 `sort`(마감 이른 순 기본 · 새로 시작한 순 · 참여 많은 순) 차례로 `count` 개를 준다.
        판매 스위치가 꺼지면(`salesOpen: false`) 공구는 그대로 보이고 위에 「지금은 주문을 받지 않아요」 한 줄을 덧붙인다(목록·상세처럼 막지 않는다 — 주문 단추가 막는다).
        공구가 없으면 `HomeGadgetFrame` 의 빈 상태(가젯 꾸미기 `empty` 그대로 — 기본 배치는 가젯을 숨긴다).
        넘기기는 이전·다음 단추와 점(지금 장은 `aria-current`)이고, 자동으로 넘어가지 않는다. 공구가 하나면 넘기기가 없다. 카드 한 장은 `HomeGadgetGroupBuyCard`.
        한 열(모바일) + 템플릿이 `mobile.groupBuyScroll` 을 고르면 넘기기 대신 가로로 미는 카드 줄(`gadget-scroll-row` — 한 칸 84%, 하나면 한 장).
    -->
    <HomeGadgetFrame :gadget="gadget" :empty="gadget.empty || items.length === 0" empty-icon="pi pi-shopping-cart">
        <p v-if="!salesOpen" class="flex items-center gap-1 pt-1 text-xs text-muted-color" data-testid="home-group-buy-sales-closed">
            <i class="pi pi-info-circle text-[11px]" aria-hidden="true" />{{ t('home.groupBuySalesClosed') }}
        </p>
        <!-- 한 열 — 가로로 미는 카드 줄(다음 장이 조금 보인다, 하나면 한 장 폭) -->
        <ul v-if="scroll" class="gadget-scroll-row" :style="{ '--row-item': items.length > 1 ? '84%' : '100%' }" :aria-label="t('home.scrollRowLabel', { name: gadget.title })" data-testid="home-group-buy">
            <li v-for="item in items" :key="item.id">
                <HomeGadgetGroupBuyCard :item="item" />
            </li>
        </ul>
        <div v-else-if="current" class="pt-1" data-testid="home-group-buy">
            <HomeGadgetGroupBuyCard :item="current" />

            <!-- 넘기기 — 공구가 둘 이상일 때 -->
            <div v-if="items.length > 1" class="mt-2.5 flex items-center justify-center gap-1">
                <button type="button" :class="NAV_BUTTON" :aria-label="t('home.groupBuyPrev')" data-testid="home-group-buy-prev" @click="go(index - 1)">
                    <i class="pi pi-angle-left text-xs" aria-hidden="true" />
                </button>
                <span class="flex items-center">
                    <button
                        v-for="(item, i) in items"
                        :key="item.id"
                        type="button"
                        class="grid size-6 place-items-center rounded-full focus-visible:outline-2 focus-visible:outline-primary"
                        :aria-label="t('home.groupBuyPick', { n: i + 1 })"
                        :aria-current="i === index ? 'true' : undefined"
                        @click="go(i)"
                    >
                        <span class="block size-1.5 rounded-full" :class="i === index ? 'bg-primary' : 'bg-surface-300 dark:bg-surface-600'" aria-hidden="true" />
                    </button>
                </span>
                <button type="button" :class="NAV_BUTTON" :aria-label="t('home.groupBuyNext')" data-testid="home-group-buy-next" @click="go(index + 1)">
                    <i class="pi pi-angle-right text-xs" aria-hidden="true" />
                </button>
                <span class="sr-only" aria-live="polite">{{ t('home.groupBuyPosition', { n: index + 1, total: items.length }) }}</span>
            </div>
        </div>
    </HomeGadgetFrame>
</template>

<script setup lang="ts">
import type { HomeGadget } from '~/composables/home/api/useHomeGadgetApi.types';
import UtilHomeGadget from '~/utils/util-home-gadget';

interface Props {
    gadget: HomeGadget;
}

const props = withDefaults(defineProps<Props>(), {});

const NAV_BUTTON = 'grid size-7 place-items-center rounded-full text-muted-color hover:bg-surface-100 hover:text-color focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800';

const { t } = useI18n();

const items = computed(() => UtilHomeGadget.groupBuyItems(props.gadget.data));
const salesOpen = computed(() => UtilHomeGadget.groupBuySalesOpen(props.gadget.data));
const index = ref(0);
watch(
    () => items.value.length,
    (n) => {
        if (index.value >= n) index.value = 0;
    },
);
const current = computed(() => items.value[index.value] ?? items.value[0] ?? null);

function go(next: number): void {
    const n = items.value.length;
    if (n === 0) return;
    index.value = ((next % n) + n) % n;
}

/** 가로로 미는 카드 줄 — 한 열(모바일)이고 템플릿이 골랐을 때만(넘기기 단추 대신) */
const stacked = useHomeGadgetStacked();
const mobile = useHomeGadgetLookOptions().mobile;
const scroll = computed(() => stacked.value && mobile.groupBuyScroll);
</script>
