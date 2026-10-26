<template>
    <!--
        프로필 동그라미 — 아바타 그림(`src`, 없으면 이니셜) + (있으면) 프로필 테두리. 공개 프로필 머리(큰 것)·글 보기 머리의 작성자(작은 것)·포인트 상점 미리보기·내 아이템·프로필 설정이 같이 쓴다.
        테두리 = 그라데이션 고리(`UtilPointShop.BORDERS` 의 값) 안쪽에 바탕색 틈 하나를 두고 동그라미를 앉힌다 — 두 테마에서 고리가 동그라미와 붙어 보이지 않게.
        테두리가 있어도 없어도 바깥 크기는 같다(줄 높이가 바뀌지 않는다). 모르는 테두리 키는 테두리 없이 그린다.
        아바타는 **`<img>` 로만**(`v-html`·인라인 SVG 금지), 틀 안에서 `object-contain`(284종이 가로로 조금 넓다). 못 불러오면 `basic-default`, 그것도 못 불러오면 이니셜.
    -->
    <span class="relative inline-grid shrink-0 place-items-center rounded-full" :class="SIZE[size].box" :style="ringStyle" :data-border="border ? borderStyle : undefined" :aria-hidden="alt ? undefined : 'true'">
        <span class="grid size-full place-items-center rounded-full" :class="border ? [SIZE[size].gap, GAP_BG[surface]] : ''">
            <img
                v-if="shown"
                :src="shown"
                :alt="alt"
                class="size-full rounded-full bg-surface-100 object-contain dark:bg-surface-800"
                :loading="lazy ? 'lazy' : undefined"
                decoding="async"
                @error="onError"
            />
            <span v-else class="grid size-full place-items-center rounded-full bg-surface-100 font-bold text-muted-color dark:bg-surface-800" :class="SIZE[size].text">{{ initial }}</span>
        </span>
    </span>
</template>

<script setup lang="ts">
import UtilAvatar from '~/utils/util-avatar';
import UtilPointShop from '~/utils/util-point-shop';

/** 크기 — `xs` 글 보기 머리 작성자 · `sm` 상점 카드·격자 · `md` 내 정보·내 아이템 · `lg` 공개 프로필 머리 · `xl` 상점 아바타 미리보기 */
type AvatarSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl';
/** 동그라미가 놓인 바탕 — 틈 색을 그 바탕에 맞춘다(`page` 화면 바탕 · `card` 카드 안) */
type AvatarSurface = 'page' | 'card';

interface Props {
    /** 닉네임 첫 글자 — 그림이 없을 때(비회원) */
    initial?: string;
    /** 아바타 그림 주소 — 없으면 이니셜 */
    src?: string | null;
    /** 대체 글 — 비면 장식(옆에 이름이 있다) */
    alt?: string;
    /** 테두리 팔레트 키 — 없으면 테두리 없음 */
    borderStyle?: string | null;
    size?: AvatarSize;
    surface?: AvatarSurface;
    /** 화면 밖이면 나중에 받는다(격자) */
    lazy?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    initial: '',
    src: null,
    alt: '',
    borderStyle: null,
    size: 'lg',
    surface: 'page',
    lazy: false,
});

/** 크기별 상자·고리 두께(바깥 여백)·틈·글자 */
const SIZE: Record<AvatarSize, { box: string; ring: string; gap: string; text: string }> = {
    xs: { box: 'size-6', ring: '2px', gap: 'p-px', text: 'text-[0.625rem]' },
    sm: { box: 'size-10', ring: '2px', gap: 'p-0.5', text: 'text-sm' },
    md: { box: 'size-12', ring: '3px', gap: 'p-0.5', text: 'text-lg' },
    lg: { box: 'size-[4.5rem]', ring: '4px', gap: 'p-[3px]', text: 'text-2xl' },
    xl: { box: 'size-28', ring: '5px', gap: 'p-1', text: 'text-4xl' },
};

const GAP_BG: Record<AvatarSurface, string> = {
    page: 'bg-surface-50 dark:bg-surface-950',
    card: 'bg-surface-0 dark:bg-surface-900',
};

const border = computed(() => UtilPointShop.border(props.borderStyle));
/** 고리 — 바깥 상자의 바탕을 그라데이션으로 칠하고 안쪽 여백만큼 보이게 한다 */
const ringStyle = computed(() => (border.value ? { background: border.value.gradient, padding: SIZE[props.size].ring } : undefined));

/** 그림을 못 불러왔다 — 주소가 바뀌면 처음부터 */
const failed = ref(false);
const fallbackFailed = ref(false);
watch(
    () => props.src,
    () => {
        failed.value = false;
        fallbackFailed.value = false;
    },
);

/** 지금 그릴 주소 — 받은 주소 > 기본 아바타 > 없음(이니셜). 그림 주소가 없으면(비회원) 이니셜 */
const shown = computed<string | null>(() => {
    if (!props.src) return null;
    if (props.src !== UtilAvatar.FALLBACK_URL && !failed.value) return props.src;
    return fallbackFailed.value ? null : UtilAvatar.FALLBACK_URL;
});

function onError() {
    if (shown.value === UtilAvatar.FALLBACK_URL) fallbackFailed.value = true;
    else failed.value = true;
}
</script>
