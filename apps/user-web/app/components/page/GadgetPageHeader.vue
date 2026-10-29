<template>
    <!--
        가젯 페이지 머리 — 격자 위에 늘 붙는 고정 머리(사용자 결정 2026-10-29): 이름(h1) · 설명 한 줄 · 글쓰기 단추(운영자가 켰을 때).
        글쓰기는 기준 폴더의 첫 게시판을 골라 둔 채 `/write?board=` 로 간다(게시판이 없으면 `/write` — 글쓰기 화면이 고르게 한다).
        로그인·쓸 수 있는 게시판 판정은 글쓰기 화면·서버가 한다(비회원 글쓰기 게시판도 있어 여기서 막지 않는다 — 게시판 머리의 단추처럼 막힌 까닭을 미리 보이지는 않는다).
        모양은 게시판 머리와 같은 글자 크기·간격이다.
    -->
    <header class="flex items-start gap-3" data-testid="gadget-page-header">
        <div class="min-w-0 flex-1">
            <h1 class="break-words text-lg font-bold leading-tight" data-testid="gadget-page-title">{{ name }}</h1>
            <p v-if="description" class="mt-0.5 text-xs text-muted-color">{{ description }}</p>
        </div>
        <Button v-if="writeButton" type="button" :label="t('shell.write')" icon="pi pi-pencil" size="small" data-testid="gadget-page-write" @click="onWrite" />
    </header>
</template>

<script setup lang="ts">
interface Props {
    name: string;
    description: string;
    writeButton: boolean;
    /** 글쓰기에서 골라 둘 게시판 slug(없으면 null) */
    writeBoard: string | null;
}
const props = defineProps<Props>();

const { t } = useI18n();

function onWrite(): void {
    const target = props.writeBoard ? { path: '/write', query: { board: props.writeBoard } } : { path: '/write' };
    void navigateTo(target);
}
</script>
