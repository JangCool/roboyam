<template>
    <!-- 가젯의 사진 한 장 — 주소가 있으면 그림(비율을 지키고 잘라 채움), 없거나 못 불러오면 같은 크기의 빈 썸네일(`EmptyThumb` — 일반 글은 회색 면 + 사이트 이름, 뉴스 글은 남색 면 + 신문 아이콘, 사용자 확정 시안 2026-11-16).
         글자(`letter`)를 주면 빈 썸네일 대신 게시판 색 옅은 면 + 같은 색 글자(게시판 이름 첫 글자 — 목록형 최신글, 사용자 2026-11-19 「C. 게시판 색 + 첫 글자」를 옅게, 색이 없는 게시판은 주 색상).
         장식이라 대체 글자는 비운다(옆에 제목이 있다) -->
    <span class="gadget-thumb @container relative block overflow-hidden bg-surface-100 dark:bg-surface-800" :style="{ aspectRatio: ratio }" data-testid="home-thumb">
        <img v-if="src && !broken" :src="src" alt="" class="absolute inset-0 size-full object-cover" loading="lazy" decoding="async" @error="broken = true" />
        <!-- 게시판 목록 이모지(관리자 게시판 기본 정보, 사용자 2026-11-19 결정) — 제 색 그대로, 바탕은 글자 칸과 같은 옅은 게시판 색. 없으면 아래 첫 글자 칸 -->
        <span v-else-if="emoji" class="absolute inset-0 grid place-items-center text-2xl leading-none" :style="letterStyle" aria-hidden="true" data-testid="home-thumb-emoji">{{ emoji }}</span>
        <!-- 글자도 옅게(사용자 2026-11-19 「글자도 너무 강조하지 말고」) — 보통 굵기 한 단계 아래 + 글자만 60% -->
        <span v-else-if="letter" class="absolute inset-0 grid place-items-center text-lg font-semibold leading-none" :style="letterStyle" aria-hidden="true" data-testid="home-thumb-letter">
            <span class="opacity-60">{{ letter }}</span>
        </span>
        <EmptyThumb v-else :kind="variant === 'default' ? 'post' : variant" class="absolute inset-0" data-testid="home-thumb-empty" />
        <slot />
    </span>
</template>

<script setup lang="ts">
import UtilBoard from '~/utils/util-board';

interface Props {
    /** 그림 주소 — 없으면 빈 썸네일 */
    src?: string | null;
    /** CSS `aspect-ratio`(예: `16 / 10`) */
    ratio?: string;
    /** 빈 썸네일 모양 — 일반 글(`default`) · 뉴스 게시판 글(`news` — `UtilBoard.isNews`) */
    variant?: 'default' | 'news' | 'groupBuy';
    /** 사진이 없을 때 빈 썸네일 대신 그릴 글자(게시판 이름 첫 글자 — `UtilBoard.iconText`) — 없으면 빈 썸네일 */
    letter?: string | null;
    /** 글자 면의 색(게시판 표시색) — 없으면 주 색상 */
    letterColor?: string | null;
    /** 사진이 없을 때 그릴 게시판 이모지 — 글자보다 먼저 */
    emoji?: string | null;
}

const props = withDefaults(defineProps<Props>(), { src: null, ratio: '16 / 10', variant: 'default', letter: null, letterColor: null, emoji: null });

const broken = ref(false);
watch(
    () => props.src,
    () => {
        broken.value = false;
    },
);

/**
 * 글자 면 — 진한 색 면은 너무 튀어(사용자 2026-11-19 「농도 낮춰」) 말머리 알약과 같은 옅은 면: 그 색 14% 바탕 + 그 바탕 위에서 읽히게 보정한 같은 색 글자(`UtilBoard.tagStyle`).
 * 표시색이 없으면 주 색상으로 같은 식
 */
const PRIMARY_LETTER = { color: 'var(--p-primary-color)', background: 'color-mix(in srgb, var(--p-primary-color) 14%, var(--p-content-background))' };
const letterStyle = computed(() => UtilBoard.tagStyle(props.letterColor) ?? PRIMARY_LETTER);
</script>
