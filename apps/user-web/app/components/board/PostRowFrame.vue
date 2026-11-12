<template>
    <!--
        여러 줄 글 줄의 뼈대 — 칩 위치(post-chip-position-contract)에 따라 칩·숫자·날짜 자리를 정한다. 목록마다 다른 제목·메타 줄 글꼴과 썸네일은 슬롯으로 받는다.
        · `ABOVE`(기본): 윗줄(줄 폭 전체, 썸네일보다 위) = 왼쪽 칩(말머리가 없으면 흐린 「일반」) + 오른쪽 끝 조회·추천 → 아래 칸 = 제목 → 아바타 · 닉네임 · 등급 · 날짜(닉네임 줄에 이어서, 가운데점 구분 — 썸네일은 이 칸 옆).
          윗줄은 늘 있다(칩이 없어도 「일반」 + 숫자).
        · `BEFORE`: 칩은 제목 앞 한 줄 안(기본 슬롯의 `chips` = true — 목록이 `PostRowTitle :chips` 로 넘긴다), 날짜·조회·추천은 메타 줄(`stats` = true).
        · `BELOW`: 제목 → 메타 줄(날짜·조회·추천 포함) → 칩 줄. 칩이 없으면 칩 줄을 그리지 않는다.
        슬롯: 기본(`{ chips, stats, dateInline }` — 제목 줄·메타 줄. `dateInline` 이면 메타 줄의 글쓴이 바로 뒤에 「· 날짜」만) · `start`/`end`(아래 칸 왼쪽·오른쪽 — 썸네일) ·
        `lead`(이 부품이 모르는 앞쪽 칩 — 섞인 목록의 게시판 이름 등. `BEFORE` 면 목록이 제목 앞에 직접 그린다) · `stats-lead`(「제목 위」 윗줄 숫자 앞 — 섞인 목록의 찬반 토론 수)
    -->
    <div class="min-w-0" :data-chip-position="position">
        <!-- 윗줄과 아래 칸(제목·썸네일) 사이는 6px — 썸네일 윗변이 윗줄 숫자에 붙어 보이지 않게(사용자 2026-11-08 「아주 살짝」) -->
        <div v-if="position === 'ABOVE'" class="mb-1.5 flex items-center justify-between gap-2" data-testid="post-row-top">
            <PostRowChips :row="row" :show-prefix="showPrefix" :general="general">
                <template v-if="$slots.lead" #default><slot name="lead" /></template>
            </PostRowChips>
            <!-- 숫자 앞 슬롯(`stats-lead`) — 섞인 목록의 찬반 토론 수(찬·반·중 → 세로 구분선 → 추천·조회, feed-debate-counts-contract) -->
            <!-- `statsBelow` 면 숫자는 이 줄이 아니라 아래 칸 오른쪽 끝(썸네일이 있으면 그 밑) — 모바일, 사용자 결정 2026-11-12 -->
            <!-- 숫자 아래(`statsBelow`) — 날짜는 이 윗줄 오른쪽 끝(썸네일 위, 사용자 결정 2026-11-12 — 닉네임 줄이 찬반·추천·조회로 좁아 날짜가 밀려 내려가던 것) -->
            <PostListDate v-if="statsBelow && dateDisplay" :iso="row.createdAt" :mode="dateDisplay" class="ml-auto shrink-0 text-xs text-muted-color" data-testid="post-row-date-top" />
            <template v-if="!statsBelow">
                <span v-if="$slots['stats-lead']" class="ml-auto inline-flex shrink-0 items-center gap-2">
                    <slot name="stats-lead" />
                    <PostRowStats :row="row" :views="views" :likes="likes" />
                </span>
                <PostRowStats v-else :row="row" :views="views" :likes="likes" class="ml-auto" />
            </template>
        </div>
        <!--
            숫자 아래(`statsBelow` + 「제목 위」): 왼쪽 칸 = 제목(위)·닉네임·날짜(아래 끝), 오른쪽 칸 = 썸네일(위)·추천·조회(아래 끝). 두 칸이 같은 높이로 늘어나 닉네임 줄과 숫자가 같은 선에 선다 —
            썸네일이 있으면 숫자는 그 밑(사용자 결정 2026-11-12). 썸네일이 없으면 이 두 칸을 쓰지 않고 제목이 줄 폭을 다 쓰며, 숫자는 목록이 닉네임 줄 오른쪽 끝에 그린다(기본 슬롯의 `statsEnd`)
        -->
        <div v-if="statsBelow && position === 'ABOVE' && row.thumbnailUrl" class="flex items-stretch gap-3">
            <div class="flex min-w-0 flex-1 flex-col justify-between" :class="bodyClass">
                <slot :chips="false" :stats="false" :date-inline="!dateDisplay" />
            </div>
            <div class="flex shrink-0 flex-col items-end gap-1">
                <slot name="end" />
                <!-- 숫자 줄은 칸 맨 아래(`mt-auto` — 썸네일이 없어도), 높이는 닉네임 줄(아바타 24px)과 같게 맞춰 같은 선에 선다 -->
                <span class="mt-auto inline-flex h-6 items-center gap-2" data-testid="post-row-stats-end">
                    <slot name="stats-lead" />
                    <PostRowStats :row="row" :views="views" :likes="likes" />
                </span>
            </div>
        </div>
        <div v-else class="flex" :class="rowClass">
            <slot name="start" />
            <div class="min-w-0 flex-1" :class="bodyClass">
                <slot :chips="position === 'BEFORE'" :stats="position !== 'ABOVE'" :date-inline="position === 'ABOVE' && !(statsBelow && dateDisplay)" :stats-end="statsBelow && position === 'ABOVE'" />
                <PostRowChips v-if="position === 'BELOW'" :row="row" :show-prefix="showPrefix" class="mt-1">
                    <template v-if="$slots.lead" #default><slot name="lead" /></template>
                </PostRowChips>
            </div>
            <slot name="end" />
        </div>
    </div>
</template>

<script setup lang="ts">
import type { BoardDateDisplay, PostChipPosition } from '~/composables/board/api/useBoardApi.types';
import type { PostRow } from '~/composables/board/api/usePostApi.types';

interface Props {
    row: PostRow;
    /** 칩 위치 — 게시판 목록은 게시판 정보의 `chipPosition`, 섞인 목록은 공개 표시 기본값의 `listChipPosition` */
    position: PostChipPosition;
    /** 「제목 위」에서 추천·조회를 윗줄이 아니라 메타 줄 오른쪽 끝(추천 · 조회 · 날짜)으로 — 기본 슬롯의 `statsEnd` 로 목록이 그린다(모바일 목록, 사용자 결정 2026-11-12) */
    statsBelow?: boolean;
    showPrefix?: boolean;
    /** 날짜 표시 방식 — `statsBelow` 와 함께 주면 날짜를 닉네임 줄이 아니라 윗줄 오른쪽 끝(썸네일 위)에 그린다(모바일 목록, 사용자 결정 2026-11-12) */
    dateDisplay?: BoardDateDisplay;
    /** 「제목 위」 윗줄의 조회 칸 — 원래 메타 줄에 조회 수가 없던 목록은 끈다 */
    views?: boolean;
    /** 「제목 위」 윗줄의 추천 칸 — 원래 메타 줄에 추천 수가 없던 목록은 끈다 */
    likes?: boolean;
    /** 「제목 위」 윗줄에서 말머리가 없을 때 흐린 「일반」 칩 — 앞쪽 칩(게시판 이름)이 늘 있는 섞인 목록은 끈다 */
    general?: boolean;
    /** 아래 칸(제목·메타 + 썸네일)의 정렬·간격 */
    rowClass?: string;
    /** 제목·메타 칸의 추가 클래스(카드형의 세로 배치 등) */
    bodyClass?: string;
}

withDefaults(defineProps<Props>(), {
    showPrefix: true,
    views: true,
    likes: true,
    general: true,
    statsBelow: false,
    rowClass: 'items-start gap-3',
    bodyClass: '',
});
</script>
