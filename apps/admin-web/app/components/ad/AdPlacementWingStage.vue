<template>
    <!--
        날개 배너 무대 — 창(바탕) 가운데 본문 상자, 바깥 좌우 여백에 세로 날개(4:15). user-web `AdWings` 짜임:
        날개는 본문 상자 바로 바깥(간격 16px)에 덮여 뜨고 본문 배치를 밀지 않는다(sticky — 스크롤해도 따라온다). 끈 쪽은 여백만 남는다.
        비율은 창 1484px(본문 1100 + 양쪽 192) 기준을 어림했다 — 여백 약 13% · 날개 약 11%.
    -->
    <div class="min-w-0 rounded-md bg-surface-100 p-1.5 dark:bg-surface-900" data-testid="ad-placement-wing-stage">
        <!-- 사이트 머리 줄 -->
        <div class="mb-1.5 h-2.5 rounded-sm bg-surface-200 dark:bg-surface-800" />
        <div class="grid grid-cols-[13%_minmax(0,1fr)_13%] items-start">
            <div class="flex justify-end pr-[8%]">
                <div v-if="left" class="w-[84%]" data-testid="ad-placement-wing-left"><AdPlacementSlot shape="wing" /></div>
            </div>
            <div class="min-w-0">
                <slot />
            </div>
            <div class="flex justify-start pl-[8%]">
                <div v-if="right" class="w-[84%]" data-testid="ad-placement-wing-right"><AdPlacementSlot shape="wing" /></div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
interface Props {
    left?: boolean;
    right?: boolean;
}

withDefaults(defineProps<Props>(), { left: false, right: false });
</script>
