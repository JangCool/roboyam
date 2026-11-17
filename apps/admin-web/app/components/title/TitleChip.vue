<template>
    <!--
        칭호 칩(관리자) — 도트 그림(`TitleIcon`, `@irobit/member-title`) + 칭호 이름. 공개 사이트 user-web `TitleChip` 과 같은 모양(시안 `member-title-icons-mockup.html` 의 `.tt` 알약).
        칩 색은 서버 `chip`(보라 = 수여·심사 · 초록 = 자동). 그림은 장식이고 칩 글자를 읽는다. 회수한 줄은 `muted`(흐리게 + 취소선).
    -->
    <span
        class="inline-flex max-w-full shrink-0 items-center gap-1.5 rounded-md py-1 pr-2 pl-1 text-[12px] leading-none font-medium whitespace-nowrap"
        :class="[UtilTitle.chipClass(title.chip), muted ? 'line-through opacity-60' : '']"
        :data-title-key="title.key"
    >
        <TitleIcon :icon="title.icon" :tier="title.tier" :scale="scale" />
        <span class="relative top-[-0.09em] truncate">{{ localizedName(title.label, title.labelEn ?? null) }}</span>
    </span>
</template>

<script setup lang="ts">
import type { TitleLook } from '~/composables/title/api/useTitleApi.types';
import UtilTitle from '~/utils/util-title';

interface Props {
    title: Pick<TitleLook, 'key' | 'label' | 'labelEn' | 'icon' | 'tier' | 'chip'>;
    /** 그림 배율 — 1 또는 2(관리자 표는 2) */
    scale?: number;
    /** 회수한 줄 */
    muted?: boolean;
}

withDefaults(defineProps<Props>(), { scale: 2, muted: false });

const { localizedName } = useLocalizedName();
</script>
