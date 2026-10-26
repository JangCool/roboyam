<template>
    <!--
        아바타 그림 동그라미 — 아바타 탭 격자 · 구매 내역 줄 · 편집 서랍 미리보기가 같은 모양으로 쓴다(결정 14).
        그림은 배포 자산 `/avatars/roboyam/<id>.svg` 를 **`<img>` 로만** 그린다(`v-html`·인라인 SVG 금지). 284종이 가로로 조금 넓어 틀 안에서 `object-fit: contain`.
        못 읽으면(없는 ID) 기본 아바타 `basic-default` 로 바꿔 그린다(공개 화면의 대체 이미지와 같다). 이름(`alt`)이 비면 장식으로 숨긴다.
        동그라미 면은 `light-dark()` 라 감싼 상자의 `color-scheme` 을 따른다.
    -->
    <span class="ps-avatar" :class="`ps-avatar--${size}`">
        <img :src="src" :alt="alt" :aria-hidden="alt ? undefined : 'true'" loading="lazy" decoding="async" class="ps-avatar__img" @error="onError" />
    </span>
</template>

<script setup lang="ts">
import UtilPointShop from '~/utils/util-point-shop';

interface Props {
    /** 카탈로그 아바타 ID */
    avatarId: string;
    size?: 'sm' | 'md' | 'lg';
    /** 대체 글자(아바타 이름). 비우면 장식 */
    alt?: string;
}

const props = withDefaults(defineProps<Props>(), { size: 'md', alt: '' });

const failed = ref(false);
watch(
    () => props.avatarId,
    () => (failed.value = false),
);
const src = computed(() => UtilPointShop.avatarUrl(failed.value ? 'basic-default' : props.avatarId));

function onError(): void {
    if (!failed.value) failed.value = true;
}
</script>

<style scoped>
.ps-avatar {
    --ps-avatar: 2.75rem;
    display: inline-grid;
    flex-shrink: 0;
    place-items: center;
    width: var(--ps-avatar);
    height: var(--ps-avatar);
    overflow: hidden;
    border-radius: 9999px;
    background: light-dark(var(--p-surface-100), var(--p-surface-800));
}

.ps-avatar--sm {
    --ps-avatar: 1.75rem;
}

.ps-avatar--lg {
    --ps-avatar: 5rem;
}

.ps-avatar__img {
    width: 100%;
    height: 100%;
    object-fit: contain;
}
</style>
