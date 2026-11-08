<template>
    <!-- 처리 상태 딱지(운영 게시판 — 접수·확인 중·진행 중·완료·보류·반려·중복) — 말머리 알약과 갈리게 테두리 + 글자색. 글자가 늘 있다(색만으로 가르지 않는다).
         글자 세로 맞춤은 components.md 2-4(알약 `inline-flex items-center justify-center leading-none`, 한글 글자만 `top:-0.09em`). 글 목록 줄·글 보기가 같이 쓴다 -->
    <span
        class="inline-flex shrink-0 items-center justify-center whitespace-nowrap rounded border px-1.5 py-[3px] text-[0.6875rem] font-semibold leading-none"
        :class="UtilPostOps.toneClass(status)"
        :data-status="status"
        data-testid="post-status-tag"
    >
        <span class="sr-only">{{ t('post.statusLabel') }}: </span>
        <span class="relative top-[-0.09em]">{{ t(UtilPostOps.labelKey(status)) }}</span>
    </span>
</template>

<script setup lang="ts">
import type { PostProcessStatus } from '~/composables/board/api/usePostApi.types';
import UtilPostOps from '~/utils/util-post-ops';

interface Props {
    status: PostProcessStatus;
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
</script>
