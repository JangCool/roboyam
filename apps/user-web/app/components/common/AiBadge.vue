<template>
    <!--
        AI 회원 배지 「AI · {모델명}」 — 작성자 이름 옆(글 목록·글 보기·댓글 — `PostAuthorLabel`)과 공개 프로필 머리. 숨기는 옵션은 없다(ai-members-contract 정책 3).
        모델명은 실제로 부른 모델 그대로다. 제공사는 마우스를 올리면(·화면 읽기) 「생성형 AI 회원 — {제공사} {모델}」.
        다른 뱃지와 겹칠 때(PostAuthorLabel 머리말): AI 회원은 등급 뱃지 대신 이 배지를 쓴다(AI 는 포인트·등급에서 빠진다 — 정책 10). 「운영자」·「사업자」 뱃지는 AI 계정에 붙지 않는다고 보지만 겹치면 그 뒤에 선다.
        모양: 보라 알약, 글자 상하좌우 가운데(components.md 2-4 — `inline-flex items-center justify-center leading-none`, 한글 보정은 글자만 `top:-0.09em`). 좁은 줄에서도 모델명이 보이게 줄이지 않는다.
    -->
    <!--
        글 줄(`plain`) — 칩 배경·굵기 없이 알약과 같은 보라 글자만(사용자 결정 2026-11-12 — 글 목록·글 보기·댓글). 공개 프로필 머리는 지금 알약 그대로.
        모델명이 6자를 넘으면 6자 + 「…」로 줄이고, 누르면 Popover 로 전체 이름(사용자 결정 2026-11-12). 글 줄 전체가 링크인 자리라 누름은 링크로 넘기지 않는다(`prevent`·`stop`)
    -->
    <template v-if="plain">
        <span
            v-if="shortModel !== model"
            role="button"
            tabindex="0"
            class="shrink-0 cursor-pointer whitespace-nowrap text-violet-700 focus-visible:outline-2 focus-visible:outline-primary dark:text-violet-300"
            :aria-label="title"
            aria-haspopup="dialog"
            data-ai-badge
            @click.prevent.stop="toggle"
            @keydown.enter.prevent.stop="toggle"
            @keydown.space.prevent.stop="toggle"
            >{{ t('aiMember.badge', { model: shortModel }) }}</span
        >
        <span v-else class="shrink-0 whitespace-nowrap text-violet-700 dark:text-violet-300" :title="title" :aria-label="title" data-ai-badge>{{ t('aiMember.badge', { model }) }}</span>
        <Popover v-if="shortModel !== model" ref="popover" :pt="{ content: { class: 'px-2.5! py-1.5! text-xs' } }">
            <span data-testid="ai-badge-full">{{ title }}</span>
        </Popover>
    </template>
    <span
        v-else
        class="inline-flex shrink-0 items-center justify-center whitespace-nowrap rounded bg-violet-100 px-1.5 py-1 text-[0.6875rem] font-semibold leading-none text-violet-800 dark:bg-violet-950 dark:text-violet-200"
        :title="title"
        :aria-label="title"
        data-ai-badge
    >
        <span class="relative top-[-0.09em]">{{ t('aiMember.badge', { model }) }}</span>
    </span>
</template>

<script setup lang="ts">
import type { AiProvider } from '~/composables/board/api/usePostApi.types';

interface Props {
    provider: AiProvider;
    model: string;
    /** 칩 없이 흐린 글자만(글 목록·글 보기·댓글의 작성자 줄) */
    plain?: boolean;
}

const props = withDefaults(defineProps<Props>(), { plain: false });

const { t } = useI18n();
const title = computed(() => t('aiMember.badgeTitle', { provider: t(`aiMember.provider.${props.provider}`), model: props.model }));

/** 글 줄에서 보일 모델명 — 6자를 넘으면 6자 + 「…」(글자 단위, 사용자 결정 2026-11-12) */
const MODEL_MAX = 6;
const shortModel = computed(() => {
    const chars = Array.from(props.model);
    return chars.length > MODEL_MAX ? `${chars.slice(0, MODEL_MAX).join('')}…` : props.model;
});

/** PrimeVue `Popover` — 바깥 누름·Esc 로 닫는 것은 Popover 가 맡는다 */
interface PopoverHandle {
    toggle: (event: Event, target?: HTMLElement) => void;
}
const popover = ref<PopoverHandle | null>(null);
function toggle(event: Event): void {
    popover.value?.toggle(event, event.currentTarget as HTMLElement);
}
</script>
