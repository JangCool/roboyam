<template>
    <!-- 게시판 모음을 한 카드에 — 기본 테마는 카드가 보이지 않고(역할 변수 기본값), 카드 테마는 흰 카드다(`main.css` `content-card`) -->
    <div class="content-card" data-testid="boards-hub-card" data-cache-area="board.catalog menu.public">
        <header>
            <h1 class="text-lg font-bold">{{ t('feed.hubTitle') }}</h1>
        </header>

        <!-- 최근 방문 · 메뉴 그룹별 게시판 줄(오른쪽 `›`) — 전체 화면 메뉴(`/menu`)와 같은 부품 -->
        <BoardHubBody />
    </div>
</template>

<script setup lang="ts">
/**
 * 게시판 모음 — `/boards`(community `pages/boards/index.vue`). 메뉴 BUILTIN `BOARDS_HUB` 가 이곳이다.
 * 그룹·순서·이름은 메뉴 구성(`GET /api/menu` — 실제)이 정한다. 줄 아래 작은 글씨(말머리 이름·투자 고지)와 최근 방문은 게시판 목록(`GET /api/boards` — 실제)에서 읽는다(`BoardHubBody`).
 * 메뉴를 못 받으면 오류(다시 시도). 모바일 하단 탭에서는 빠졌고(2026-11-11) 메뉴 항목·데스크톱으로 들어온다 — 모바일 메뉴는 `/menu`.
 */
const { t } = useI18n();

const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('feed.hubTitle'), description: () => t('feed.hubSeoDescription', { site: siteLabel.value }) });
</script>
