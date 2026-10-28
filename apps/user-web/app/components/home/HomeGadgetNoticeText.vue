<template>
    <!--
        안내 글 가젯(`NOTICE_TEXT`) — 운영자가 적은 평문(HTML 아님, 줄바꿈 가능). 글머리표(`BULLETS`)면 줄마다 한 항목. 글자로만 그린다(`v-html` 금지).
        「더보기」(예: 공지 보기)는 껍데기가 그린다.
    -->
    <HomeGadgetFrame :gadget="gadget" :empty="gadget.empty || text.lines.length === 0" empty-icon="pi pi-info-circle">
        <ul v-if="text.format === 'BULLETS'" class="m-0 list-disc space-y-1 py-2 pl-5 pr-1 text-[0.8125rem] leading-relaxed text-muted-color" data-testid="home-notice">
            <li v-for="(line, i) in text.lines" :key="i">{{ line }}</li>
        </ul>
        <div v-else class="space-y-1 py-2 text-[0.8125rem] leading-relaxed text-muted-color" data-testid="home-notice">
            <p v-for="(line, i) in text.lines" :key="i" class="m-0">{{ line }}</p>
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

const text = computed(() => UtilHomeGadget.noticeText(props.gadget.data, props.gadget.config));
</script>
