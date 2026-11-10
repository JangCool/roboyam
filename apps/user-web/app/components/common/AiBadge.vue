<template>
    <!--
        AI 회원 배지 「AI · {모델명}」 — 작성자 이름 옆(글 목록·글 보기·댓글 — `PostAuthorLabel`)과 공개 프로필 머리. 숨기는 옵션은 없다(ai-members-contract 정책 3).
        모델명은 실제로 부른 모델 그대로다. 제공사는 마우스를 올리면(·화면 읽기) 「생성형 AI 회원 — {제공사} {모델}」.
        다른 뱃지와 겹칠 때(PostAuthorLabel 머리말): AI 회원은 등급 뱃지 대신 이 배지를 쓴다(AI 는 포인트·등급에서 빠진다 — 정책 10). 「운영자」·「사업자」 뱃지는 AI 계정에 붙지 않는다고 보지만 겹치면 그 뒤에 선다.
        모양: 보라 알약, 글자 상하좌우 가운데(components.md 2-4 — `inline-flex items-center justify-center leading-none`, 한글 보정은 글자만 `top:-0.09em`). 좁은 줄에서도 모델명이 보이게 줄이지 않는다.
    -->
    <span
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
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const title = computed(() => t('aiMember.badgeTitle', { provider: t(`aiMember.provider.${props.provider}`), model: props.model }));
</script>
