<template>
    <!--
        화면 안의 좌측 서브메뉴 — 한 화면이 여러 갈래로 나뉠 때(내 정보의 일반 …) 쓴다. 카드로 한 번 들어 올려 본문과 층을 가르고, 고른 것은 알약 모양 배경으로 짚는다.
        ⚠ 사이드바 하위 항목과 규칙이 다르다 — 그쪽은 배경을 칠하지 않고 글자만 굵게 한다(층이 여럿이라). 여기는 층이 하나뿐이라 배경으로 짚는 것이 더 잘 읽힌다.
        좁아지면(md 미만) Select 로 바뀐다 — 세로 메뉴가 본문 위에 얹히면 화면의 절반을 먹는다. 부르는 쪽이 신경 쓰지 않아도 되게 여기서 갈아 낀다.
    -->
    <Select
        v-if="!isDesktop"
        :model-value="modelValue"
        :options="items"
        option-label="label"
        option-value="value"
        class="w-full"
        :aria-label="navLabel"
        @update:model-value="(value: string) => emit('update:modelValue', value)"
    >
        <template #value="{ value }">
            <span v-if="picked(value)" class="inline-flex items-center gap-2">
                <i v-if="picked(value)?.icon" :class="picked(value)?.icon" class="text-sm" aria-hidden="true" />
                <span class="text-sm">{{ picked(value)?.label }}</span>
            </span>
        </template>
        <template #option="{ option }">
            <span class="inline-flex items-center gap-2">
                <i v-if="option.icon" :class="option.icon" class="text-sm" aria-hidden="true" />
                <span class="text-sm">{{ option.label }}</span>
            </span>
        </template>
    </Select>

    <!-- 폭은 3단으로 설 때(lg)만 고정한다 — 좁은 화면에서 혼자만 좁은 상자로 남아 옆이 비지 않게 -->
    <nav v-else class="w-full shrink-0 rounded-xl border border-(--line) bg-(--p-content-background) p-3 lg:w-(--nav-w)" :style="{ '--nav-w': `${width}px` }" :aria-label="navLabel">
        <ul class="m-0 flex list-none flex-col gap-0.5 p-0">
            <li v-for="item in items" :key="item.value">
                <button
                    type="button"
                    class="flex min-h-9 w-full cursor-pointer items-center gap-2 rounded-lg border-0 bg-transparent px-2.5 text-left text-sm focus-visible:outline-2 focus-visible:outline-primary"
                    :class="modelValue === item.value ? 'bg-primary-50! font-medium text-primary-700 dark:bg-primary-950! dark:text-primary-300' : 'hover:bg-(--p-content-hover-background)'"
                    :aria-current="modelValue === item.value ? 'page' : undefined"
                    @click="emit('update:modelValue', item.value)"
                >
                    <i v-if="item.icon" :class="item.icon" class="shrink-0 text-sm" aria-hidden="true" />
                    <span class="truncate">{{ item.label }}</span>
                </button>
            </li>
        </ul>
    </nav>
</template>

<script setup lang="ts">
import type { SideNavItem } from '~/components/common/AppSideNav.types';

interface Props {
    modelValue: string;
    items: SideNavItem[];
    ariaLabel?: string;
    /** 데스크톱에서의 폭(px) */
    width?: number;
}

const props = withDefaults(defineProps<Props>(), { ariaLabel: '', width: 224 });
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();

const { t } = useI18n();
const navLabel = computed(() => props.ariaLabel || t('shell.menu'));

/** 데스크톱인가 — 셸의 모바일 경계(md = 768px)와 같다. `true` 로 시작한다: SPA 라 `false` 로 두면 데스크톱에서도 Select 가 한 번 깜빡였다 사라진다 */
const isDesktop = ref(true);
let mq: MediaQueryList | undefined;
const sync = () => {
    if (mq) isDesktop.value = mq.matches;
};
onMounted(() => {
    mq = window.matchMedia('(min-width: 768px)');
    sync();
    mq.addEventListener('change', sync);
});
onBeforeUnmount(() => mq?.removeEventListener('change', sync));

const picked = (value: string) => props.items.find((item) => item.value === value);
</script>
