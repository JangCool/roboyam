<template>
    <!-- 제재 화면의 안내 상자 — 굵은 첫 문장(`title`) + 설명(`text` 또는 기본 슬롯). 색: info 회색 · warn 노랑 · red 붉음 · ok 초록 (community `DmNotice` 와 같은 모양) -->
    <div :class="[BASE, TONE[tone]]" :role="live || tone === 'red' ? 'alert' : 'note'">
        <p class="m-0 leading-relaxed">
            <b v-if="title" class="font-semibold">{{ title }}</b>
            <template v-if="title && (text || $slots.default)">{{ ' ' }}</template>
            <slot>{{ text }}</slot>
        </p>
    </div>
</template>

<script setup lang="ts">
/** 안내 상자 한 개 — 제재 안내·이의제기 두 화면이 쓴다. 붉은 안내는 스크린리더가 바로 읽도록 `role="alert"`, `live` 로 다른 색에도 켤 수 있다 */
type Tone = 'info' | 'warn' | 'red' | 'ok';

interface Props {
    tone?: Tone;
    title?: string;
    text?: string;
    /** 붉은 안내가 아니어도 바로 읽어 준다 */
    live?: boolean;
}

withDefaults(defineProps<Props>(), {
    tone: 'info',
    title: '',
    text: '',
    live: false,
});

const BASE = 'rounded-lg px-3 py-2.5 text-xs';
const TONE: Record<Tone, string> = {
    info: 'bg-surface-100 text-muted-color dark:bg-surface-800/60',
    warn: 'border border-amber-200 bg-amber-50 text-amber-950 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-100',
    red: 'border border-red-200 bg-red-50 text-red-950 dark:border-red-900 dark:bg-red-950/40 dark:text-red-100',
    ok: 'border border-green-200 bg-green-50 text-green-950 dark:border-green-900 dark:bg-green-950/40 dark:text-green-100',
};
</script>
