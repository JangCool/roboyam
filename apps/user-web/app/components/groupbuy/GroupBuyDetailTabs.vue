<template>
    <!--
        상품 상세 탭 막대(일반 쇼핑몰 구조 — 11번가·쿠팡·네이버 쇼핑) — 「상품상세 | 상품평 (n) | 상품문의 (n) | 배송/교환/반품 안내」.
        · 탭은 구역을 숨기지 않는다: 같은 화면의 구역(`#detail`…)으로 가는 링크이고, 누르면 부드럽게 스크롤한다(구역의 `scroll-margin-top` 이 붙은 막대 높이만큼 띄운다).
        · 막대는 공용 헤더 바로 아래에 붙는다(`--shell-header-h`). 지금 보이는 구역의 탭에 `aria-current` 와 밑줄(scrollspy — `IntersectionObserver`).
        · 주소 해시(`#reviews` 등)로 들어오면 처음 그릴 때 그 구역으로 간다. 모바일은 네 칸이 꽉 차고, 넘치면 가로로 민다.
        구역 요소는 부르는 쪽이 같은 화면에 둔다(이 막대가 그려질 때 이미 있어야 한다 — 같은 부모 아래 형제).
    -->
    <nav class="group-buy-tabs sticky z-20 -mx-4 border-b border-surface bg-surface-0/95 px-4 backdrop-blur dark:bg-surface-900/95 sm:mx-0 sm:rounded-t-xl sm:px-0" :aria-label="t('groupBuy.tabsLabel')" data-testid="group-buy-tabs">
        <ul class="m-0 flex list-none overflow-x-auto p-0 [scrollbar-width:none]">
            <li v-for="tab in tabs" :key="tab.id" class="flex-1 shrink-0">
                <a
                    :href="`#${tab.id}`"
                    class="flex h-12 min-w-max items-center justify-center gap-1 whitespace-nowrap border-b-2 px-3 text-sm focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
                    :class="active === tab.id ? 'border-primary font-semibold text-primary' : 'border-transparent text-muted-color hover:text-(--p-text-color)'"
                    :aria-current="active === tab.id ? 'location' : undefined"
                    :data-testid="`group-buy-tab-${tab.id}`"
                    @click.prevent="go(tab.id, 'smooth')"
                >
                    {{ tab.label }}<span v-if="tab.count !== null && tab.count !== undefined" class="tabular-nums" :class="active === tab.id ? '' : 'text-muted-color'">({{ tab.count }})</span>
                </a>
            </li>
        </ul>
    </nav>
</template>

<script setup lang="ts">
import type { GroupBuyDetailTab } from '~/components/groupbuy/GroupBuyDetailTabs.types';

const props = defineProps<{ tabs: GroupBuyDetailTab[] }>();
const { t } = useI18n();

const active = ref(props.tabs[0]?.id ?? '');
/** 지금 화면(막대 아래 띠)에 걸친 구역 */
const visible = new Set<string>();
let observer: IntersectionObserver | null = null;
/** 탭을 눌러 스크롤하는 동안은 관찰로 표시를 바꾸지 않는다(지나가는 구역에 표시가 튀지 않게) */
let lockUntil = 0;

/** 붙은 헤더 + 탭 막대 높이(px) — 관찰 띠의 위쪽 */
function topOffset(): number {
    const nav = document.querySelector<HTMLElement>('[data-testid=group-buy-tabs]');
    return nav ? Math.round(nav.getBoundingClientRect().height + parseFloat(getComputedStyle(nav).top || '0')) : 0;
}

function go(id: string, behavior: ScrollBehavior): void {
    const el = document.getElementById(id);
    if (!el) return;
    active.value = id;
    lockUntil = Date.now() + 900;
    el.scrollIntoView({ behavior, block: 'start' });
    // 주소에도 적는다(공유·새로고침) — 라우터를 거치지 않아 다시 스크롤하지 않는다
    history.replaceState(history.state, '', `${location.pathname}${location.search}#${id}`);
}

function pick(): void {
    if (Date.now() < lockUntil) return;
    // 위에서부터 차례로 — 띠에 걸친 첫 구역. 걸친 것이 없으면 첫 구역보다 위(구매 영역)일 때만 첫 탭, 구역 사이 틈이면 그대로 둔다
    const first = props.tabs.find((tab) => visible.has(tab.id));
    if (first) active.value = first.id;
    else if (props.tabs[0] && (document.getElementById(props.tabs[0].id)?.getBoundingClientRect().top ?? 0) > topOffset()) active.value = props.tabs[0].id;
}

onMounted(() => {
    observer = new IntersectionObserver(
        (entries) => {
            for (const e of entries) {
                if (e.isIntersecting) visible.add(e.target.id);
                else visible.delete(e.target.id);
            }
            pick();
        },
        // 막대 바로 아래부터 화면 위쪽 40% 띠만 본다 — 그 띠에 들어온 구역이 「지금 읽는 구역」
        { rootMargin: `-${topOffset() + 1}px 0px -60% 0px` },
    );
    for (const tab of props.tabs) {
        const el = document.getElementById(tab.id);
        if (el) observer.observe(el);
    }
    const hash = decodeURIComponent(location.hash.slice(1));
    if (props.tabs.some((tab) => tab.id === hash)) requestAnimationFrame(() => go(hash, 'auto'));
});

onBeforeUnmount(() => {
    observer?.disconnect();
    observer = null;
});
</script>

<style scoped>
.group-buy-tabs {
    top: var(--shell-header-h);
}
.group-buy-tabs ul::-webkit-scrollbar {
    display: none;
}
</style>
