<template>
    <!--
        가로로 미는 메뉴 줄 하나(데스크톱 상단 메뉴들이 같이 쓴다 — `AppTopMenu`·`DaumMenuBar`(GNB·LNB)·`NaverMenuBar`·`AppLogoLinks`). 줄 내용은 기본 슬롯으로 받아 스크롤 상자 안에 그대로 놓는다.
        브라우저 스크롤바는 늘 숨긴다(`no-scrollbar` — OS 설정에 따라 높이·겹침이 달라 줄 높이를 지킬 수 없다). 넘칠 때만 운영자가 고른 표시(`mode`)를 직접 그린다:
          SCROLLBAR  줄 아래 예약 자리(`--scroll-reserve`)에 얇은 트랙 + 손잡이(폭·위치 = 보이는 폭/전체 폭, 끌어서 넘기기·트랙 누르면 한 쪽). 넘치지 않으면 자리만 비어 있다.
                     자리는 줄 안 아래쪽 여백을 쓴다 — 줄 높이 안에 빈 여백이 없는 막대는 `reserve` 로 그만큼 처음부터 줄 아래에 덧붙인다(넘침과 무관하게 같은 높이).
          ARROWS     줄 양끝에 바탕색에서 투명으로 사라지는 그라데이션 화살표 단추(줄 폭의 80% 씩 부드럽게). 끝에 닿은 쪽 단추는 숨는다. 글자 위에 겹치므로 줄 높이는 그대로다.
        두 방식 모두 손가락·트랙패드 밀기는 그대로이고, 모바일(md 미만)은 표시 없이 손가락 밀기만이다. 키보드는 Tab 으로 칸을 돌며 넘어가고(초점 칸이 단추에 가리지 않게 민다) 단추는 Tab 순서에서 뺀다.
        색은 역할 변수(`--scroll-fade-bg`·`--scroll-track`·`--scroll-thumb`·`--scroll-arrow-fg` …, `main.css`)라 놓인 면(카드·주 색상 바)이 채운다.
        활성 칸 가운데로 끌어오기는 부모가 `useScrollRowRef()` 의 `scroller` 로 `useCenterActive` 를 잇는다(이 부품이 스크롤 상자를 내놓는다).
    -->
    <div ref="root" class="relative min-w-0" :class="reserve && mode === 'SCROLLBAR' ? 'md:pb-[var(--scroll-reserve)]' : ''" :data-scroll-mode="mode">
        <div ref="scroller" class="no-scrollbar relative overflow-x-auto" :class="scrollerClass" @focusin="onFocusIn">
            <slot />
        </div>

        <!-- 얇은 스크롤 — 그림일 뿐이라 보조기기에는 숨긴다(칸은 Tab·손가락으로도 넘어간다) -->
        <div
            v-if="mode === 'SCROLLBAR' && overflowing"
            class="scroll-row-track max-md:hidden"
            :data-dragging="dragging ? 'true' : undefined"
            aria-hidden="true"
            data-testid="scroll-row-track"
            @pointerdown="onTrackPointerDown"
        >
            <span class="scroll-row-thumb" :style="{ width: `${thumbWidth}px`, transform: `translateX(${thumbLeft}px)` }" @pointerdown.stop="onThumbPointerDown" />
        </div>

        <template v-if="mode === 'ARROWS' && overflowing">
            <button v-if="!atStart" type="button" tabindex="-1" class="scroll-row-arrow max-md:hidden" data-edge="start" :aria-label="t('shell.scrollMenuLeft')" data-testid="scroll-row-prev" @click="page(-1)">
                <i class="pi pi-angle-left" aria-hidden="true" />
            </button>
            <button v-if="!atEnd" type="button" tabindex="-1" class="scroll-row-arrow max-md:hidden" data-edge="end" :aria-label="t('shell.scrollMenuRight')" data-testid="scroll-row-next" @click="page(1)">
                <i class="pi pi-angle-right" aria-hidden="true" />
            </button>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { MenuOverflow } from '~/utils/util-menu-position';

interface Props {
    /** 넘칠 때의 표시 — 줄 아래 얇은 스크롤 · 양끝 화살표 단추(운영자 설정 > 테마 기본, 셸이 넘긴다) */
    mode: MenuOverflow;
    /** 스크롤 상자에 줄 클래스(높이·정렬·안쪽 여백 …) — 상자에는 늘 `no-scrollbar relative overflow-x-auto` 가 붙는다 */
    scrollerClass?: string;
    /** 줄 높이 안에 빈 아래 여백이 없는 줄인가(활성 밑줄이 줄 바닥에 붙는 막대) — 그렇다면 `SCROLLBAR` 일 때 예약 자리를 줄 아래에 덧붙인다(데스크톱) */
    reserve?: boolean;
}
withDefaults(defineProps<Props>(), { scrollerClass: '', reserve: false });

const { t } = useI18n();
const root = ref<HTMLElement | null>(null);
const scroller = ref<HTMLElement | null>(null);

/** 화살표 단추가 덮는 폭(px, `--scroll-arrow-w`) — 키보드 초점 칸을 그만큼 안쪽에 보이게 한다. 단추가 없는 방식이면 0 */
function edgeInset(): number {
    if (!root.value || root.value.dataset.scrollMode !== 'ARROWS') return 0;
    return Number.parseFloat(getComputedStyle(root.value).getPropertyValue('--scroll-arrow-w')) || 0;
}

const { overflowing, atStart, atEnd, thumbWidth, thumbLeft, dragging, page, onThumbPointerDown, onTrackPointerDown, onFocusIn } = useScrollOverflow(scroller, { edgeInset });

defineExpose({ scroller });
</script>
