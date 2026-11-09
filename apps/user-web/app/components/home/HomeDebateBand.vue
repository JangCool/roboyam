<template>
    <!--
        하이라이트 사진 안 위쪽 폭 전체 띠 — 찬반 토론 글의 입장 수(feed-debate-counts-contract, 사용자 2026-11-10 시안). `HomeThumb` 의 기본 슬롯에 넣는다
        (사진 칸이 `overflow-hidden` 이라 띠의 위 모서리가 사진 모서리를 따른다). 띠는 테마와 무관하게 반투명 검정 + 밝은 글자.
        · `size="lg"`(큰 카드): 가운데 모아 「찬 0 · 반 3 · 중 3」(이름표 포함, 14px). 다 보이니 툴팁은 없다.
        · `size="sm"`(작은 목록 썸네일): 사진 오른쪽 위 모서리 작은 라벨에 색 숫자만(10px, 99 넘으면 「99+」 — 사용자 2026-11-10 최종: 사진을 덜 가리게). 전체 수 툴팁 「찬성 128 · 반대 97 · 중립 15」 —
          데스크톱은 마우스를 올리면, 모바일(터치)은 띠를 누르면(줄 전체가 글 링크라 띠의 누름은 글로 가지 않게 막는다 — 다른 곳을 누르면 닫힌다), 키보드는 초점(`:focus-visible`)으로.
          마우스로 띠를 누르면 줄과 같이 글로 간다. 툴팁은 PrimeVue `Popover`(body 에 붙어 사진 칸에 잘리지 않는다)
    -->
    <span
        v-if="size === 'lg'"
        class="absolute inset-x-0 top-0 flex justify-center bg-black/62 px-2 py-1.5 text-sm leading-tight"
        data-testid="home-debate-band"
        ><FeedDebateCounts :debate="debate" tone="band" dots
    /></span>
    <template v-else>
        <span
            ref="band"
            class="absolute right-[3px] top-[3px] inline-flex cursor-default rounded-[3px] bg-black/60 px-1 text-[0.625rem] leading-[14px] focus-visible:outline-2 focus-visible:outline-white"
            tabindex="0"
            data-testid="home-debate-band"
            @pointerdown="pointer = $event.pointerType"
            @pointerenter="onEnter"
            @pointerleave="onLeave"
            @click="onClick"
            @focus="onFocus"
            @blur="hide"
            ><FeedDebateCounts :debate="debate" tone="band" :labels="false" cap
        /></span>
        <Popover ref="popover" :pt="{ content: { class: 'px-2.5! py-1.5! text-xs whitespace-nowrap tabular-nums' } }">
            <span role="tooltip" data-testid="home-debate-tip">{{ tip }}</span>
        </Popover>
    </template>
</template>

<script setup lang="ts">
import type { PostFeedDebate } from '~/composables/board/api/usePostApi.types';
import UtilFeedDebate from '~/utils/util-feed-debate';

interface Props {
    debate: PostFeedDebate;
    size?: 'lg' | 'sm';
}

const props = withDefaults(defineProps<Props>(), { size: 'sm' });

const { t } = useI18n();

/** PrimeVue `Popover` 의 열고 닫기 — 바깥 누름·Esc 로 닫는 것은 Popover 가 맡는다 */
interface PopoverHandle {
    show: (event: Event, target?: HTMLElement) => void;
    hide: () => void;
    toggle: (event: Event, target?: HTMLElement) => void;
}

const band = ref<HTMLElement | null>(null);
const popover = ref<PopoverHandle | null>(null);
/** 마지막으로 띠를 누른 입력 종류(`mouse`·`touch`·`pen`) — 마우스 누름만 글로 보낸다 */
const pointer = ref('');
const tip = computed(() => UtilFeedDebate.tip(props.debate, t));

function show(event: Event) {
    if (band.value) popover.value?.show(event, band.value);
}

function hide() {
    popover.value?.hide();
}

function onEnter(event: PointerEvent) {
    if (event.pointerType === 'mouse') show(event);
}

function onLeave(event: PointerEvent) {
    if (event.pointerType === 'mouse') hide();
}

/** 터치·펜으로 누르면 글로 가지 않고 툴팁만 열고 닫는다 */
function onClick(event: MouseEvent) {
    if (pointer.value === 'mouse' || pointer.value === '') return;
    event.preventDefault();
    event.stopPropagation();
    if (band.value) popover.value?.toggle(event, band.value);
}

/** 키보드 초점일 때만 연다(터치로 누를 때 생기는 초점은 누름이 맡는다) */
function onFocus(event: FocusEvent) {
    if (band.value?.matches(':focus-visible')) show(event);
}
</script>
