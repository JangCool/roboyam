<template>
    <!--
        봉인된 익명 작성자(작성자 칸 `sealed` — anonymous-board-contract 정책 8) — 자물쇠 + 「익명 · 작성자 봉인됨」. 닉네임·회원 ID·IP 는 서버가 싣지 않는다. 게시글 관리·신고 처리의 목록·서랍이 쓴다.
        `label` 을 주면 그 글자를 보인다(게시판 제재 반복 검토의 봉인 줄 — 「익명 글 작성자(봉인)」).
    -->
    <span class="inline-flex min-w-0 items-center gap-1 text-muted-color" :title="t('anonymousAuthor.sealedTitle')" data-testid="author-sealed">
        <i class="pi pi-lock shrink-0 text-[11px]" aria-hidden="true" />
        <span class="truncate">{{ label || t('anonymousAuthor.sealed') }}</span>
    </span>
</template>

<script setup lang="ts">
interface Props {
    /** 보일 글자 — 비우면 「익명 · 작성자 봉인됨」 */
    label?: string;
}

withDefaults(defineProps<Props>(), { label: '' });

const { t } = useI18n();
</script>
