<template>
    <!-- 품목 미리보기 조각 — 상점 카드·내 아이템 줄. 닉네임 색: 내 닉네임을 그 색으로(지금 테마 바탕) · 테두리: 내 아바타에 그 고리 · 아바타: 그 그림 · 변경권(미리보기 없음): 아이콘 -->
    <span class="grid shrink-0 place-items-center overflow-hidden rounded-lg bg-surface-50 dark:bg-surface-950" :class="compact ? 'size-12' : 'h-16 w-full'" aria-hidden="true">
        <span v-if="kind === 'NICKNAME_COLOR' && colorCss" class="max-w-full truncate px-1.5 font-bold" :class="compact ? 'text-xs' : 'text-sm'" :style="{ color: colorCss }">{{ compact ? initialText : nickname }}</span>
        <ProfileBorderAvatar v-else-if="kind === 'PROFILE_BORDER'" :initial="initialText" :src="myAvatarSrc" :border-style="borderStyle" :size="compact ? 'sm' : 'md'" surface="page" />
        <ProfileBorderAvatar v-else-if="kind === 'PROFILE_AVATAR'" :initial="initialText" :src="avatarSrc" :size="compact ? 'sm' : 'md'" surface="page" lazy />
        <i v-else-if="kind === 'NICKNAME_CHANGE_TICKET'" class="pi pi-ticket text-primary" :class="compact ? 'text-lg' : 'text-2xl'" />
        <i v-else class="pi pi-gift text-muted-color" :class="compact ? 'text-lg' : 'text-2xl'" />
    </span>
</template>

<script setup lang="ts">
import UtilColor from '~/utils/util-color';
import UtilPointShop from '~/utils/util-point-shop';

interface Props {
    /** 품목 종류 */
    kind: string;
    /** 닉네임 색(원색) */
    color?: string | null;
    /** 테두리 키 */
    borderStyle?: string | null;
    /** 아바타 품목의 그림 주소 */
    avatarSrc?: string | null;
    /** 내 아바타 그림 주소 — 테두리 미리보기의 동그라미 */
    myAvatarSrc?: string | null;
    /** 내 닉네임 — 미리보기 글자 */
    nickname?: string;
    /** 작은 정사각형(내 아이템 줄) — 아니면 카드 폭 가득 */
    compact?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    color: null,
    borderStyle: null,
    avatarSrc: null,
    myAvatarSrc: null,
    nickname: '',
    compact: false,
});

/** 대비 보정한 두 테마 색(`light-dark()`) — 작성자 표시와 같은 규칙(`UtilColor.cssPair`) */
const colorCss = computed(() => UtilColor.cssPair(props.color));
const initialText = computed(() => UtilPointShop.initial(props.nickname) || '·');
</script>
