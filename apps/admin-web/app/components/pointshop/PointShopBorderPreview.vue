<template>
    <!--
        프로필 테두리 미리보기 — 이니셜 동그라미 둘레의 그라데이션 고리(바탕색 틈 하나). 품목 관리 표의 미리보기 조각과 편집 서랍의 미리보기가 같은 모양으로 쓴다.
        고리 값은 `UtilPointShop.BORDER_GRADIENTS`(user-web 사본과 같아야 한다). 키가 없거나 모르는 키면 고리 없이 동그라미만 — 자리 크기는 같게 둔다(줄이 흔들리지 않게).
        틈 색(`gapColor`)은 고리 뒤의 바탕색이다 — 라이트·다크 바탕을 나란히 보이는 미리보기는 그 바탕(`#ffffff`·`#202125`)을 넘기고, 표에서는 카드 바탕을 쓴다.
        동그라미 면·글자 색은 `light-dark()` 라 감싼 상자의 `color-scheme` 을 따른다. 장식이라 스크린리더에는 `label` 만 읽힌다.
    -->
    <span class="ps-ring" :class="`ps-ring--${size}`" :style="ringStyle" role="img" :aria-label="label || undefined" :aria-hidden="label ? undefined : 'true'">
        <span class="ps-ring__gap">
            <span class="ps-ring__face">{{ initial }}</span>
        </span>
    </span>
</template>

<script setup lang="ts">
import UtilPointShop from '~/utils/util-point-shop';

interface Props {
    /** 팔레트 키(`GOLD` …). 없으면 고리 없이 */
    borderStyle?: string | null;
    /** 동그라미 안 글자(닉네임 첫 글자) */
    initial?: string;
    size?: 'sm' | 'md' | 'lg';
    /** 고리와 동그라미 사이 틈의 색 — 비우면 카드 바탕 */
    gapColor?: string;
    /** 스크린리더 이름. 비우면 장식으로 숨긴다 */
    label?: string;
}

const props = withDefaults(defineProps<Props>(), { borderStyle: null, initial: '', size: 'md', gapColor: '', label: '' });

const ringStyle = computed(() => ({
    '--ps-ring': UtilPointShop.borderGradient(props.borderStyle) ?? 'transparent',
    '--ps-gap': props.gapColor || 'var(--p-content-background)',
}));
</script>

<style scoped>
.ps-ring {
    --ps-face: 2.5rem;
    --ps-ring-width: 3px;
    --ps-gap-width: 2px;
    display: inline-grid;
    flex-shrink: 0;
    place-items: center;
    border-radius: 9999px;
    background: var(--ps-ring);
    padding: var(--ps-ring-width);
}

.ps-ring--sm {
    --ps-face: 1.5rem;
    --ps-ring-width: 2px;
    --ps-gap-width: 1.5px;
}

.ps-ring--lg {
    --ps-face: 4.5rem;
    --ps-ring-width: 4px;
    --ps-gap-width: 3px;
}

.ps-ring__gap {
    display: grid;
    place-items: center;
    border-radius: 9999px;
    background: var(--ps-gap);
    padding: var(--ps-gap-width);
}

.ps-ring__face {
    display: grid;
    place-items: center;
    width: var(--ps-face);
    height: var(--ps-face);
    border-radius: 9999px;
    background: light-dark(var(--p-surface-200), var(--p-surface-700));
    color: light-dark(var(--p-surface-700), var(--p-surface-100));
    font-size: calc(var(--ps-face) * 0.42);
    font-weight: 600;
    line-height: 1;
}
</style>
