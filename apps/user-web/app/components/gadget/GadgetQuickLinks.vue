<template>
    <!--
        바로가기 가젯(`QUICK_LINKS`) — 둥근 버튼(`PILL`) 또는 밑줄 탭(`TAB`) 한 줄(네이버형 맨 위 「바로가기 줄」). 주소는 `UtilGadget.linkOf` 가 만든다.
        지금 보는 곳과 같은 항목은 `aria-current="page"` 로 눌린 모양. 좁은 화면에서는 가로로 밀어 넘긴다(스크롤바 없음). 메뉴 구성과 별개다(홈 구성이 정한다 — community 결정).
        마우스 올림 효과(설정 `hover`, `main.css`): 지금 보는 곳이 아닌 단추·탭에 옅은 바탕(`gadget-hover-box` — 단추는 둥근 모양 그대로, 탭은 위 모서리만 둥글게). 눌린 항목은 그 모양 그대로.
    -->
    <GadgetFrame :gadget="gadget" :empty="gadget.empty || items.length === 0" empty-icon="pi pi-link">
        <nav :aria-label="gadget.title || t('home.quickLinks')">
            <ul class="no-scrollbar m-0 flex list-none overflow-x-auto p-0" :class="shape === 'TAB' ? 'gap-1 border-b border-surface' : 'gap-2 py-1'" data-testid="home-quick-links">
                <li v-for="item in items" :key="`${item.kind}:${item.value ?? ''}:${item.label}`" class="shrink-0">
                    <NuxtLink
                        :to="UtilGadget.linkOf(item)!"
                        :aria-current="isCurrent(item) ? 'page' : undefined"
                        class="inline-flex min-h-8 items-center whitespace-nowrap text-sm focus-visible:outline-2 focus-visible:outline-primary"
                        :class="linkClass(isCurrent(item))"
                        data-testid="home-quick-link"
                    >
                        {{ item.label }}
                    </NuxtLink>
                </li>
            </ul>
        </nav>
    </GadgetFrame>
</template>

<script setup lang="ts">
import type { Gadget, QuickLinkItem } from '~/composables/gadget/api/useGadgetApi.types';
import UtilGadget from '~/utils/util-gadget';

interface Props {
    gadget: Gadget;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const route = useRoute();
const items = computed(() => UtilGadget.quickLinkItems(props.gadget.data));
const shape = computed(() => UtilGadget.quickLinksConfig(props.gadget.config).shape);

function isCurrent(item: QuickLinkItem): boolean {
    return UtilGadget.linkOf(item) === route.path;
}

/** 둥근 버튼은 테두리 상자(눌리면 주 색상 채움), 밑줄 탭은 밑줄(눌리면 굵게 + 주 색상 밑줄) */
function linkClass(current: boolean): string {
    if (shape.value === 'TAB') return `-mb-px border-b-2 px-3 ${current ? 'border-primary font-bold text-primary' : 'gadget-hover-box rounded-t-md border-transparent text-muted-color hover:text-color'}`;
    return `rounded-full border px-3.5 ${current ? 'border-primary bg-primary font-semibold text-primary-contrast' : 'gadget-hover-box border-hairline-strong bg-card text-muted-color hover:text-color'}`;
}
</script>
