<template>
    <!--
        로보티콘 격자(보기 전용) — 실제 GIF 를 그 크기(`size`)로 움직이게 그리고, 아래에 이름표를 적는다. 심사 서랍·팩 서랍이 같이 쓴다.
        바탕은 화면 테마가 아니라 `dark` 로 고른다(밝은/어두운 글 바탕에서 어떻게 보이는지 보려고 일부러 고정 색).
        칸 표시: 왼쪽 위 = 새 판 비교(새로 · 그림 바뀜 · 이름표 바뀜 · 빠짐), 오른쪽 위 = 규격 검사(어긋남 빨강 · 권장 넘음 주황). 번호는 팩 안 차례(1부터).
    -->
    <ul class="m-0 grid list-none gap-2 p-0" :style="{ gridTemplateColumns: `repeat(auto-fill, minmax(${size + 18}px, 1fr))` }">
        <li v-for="(item, index) in items" :key="item.id" class="flex min-w-0 flex-col items-center gap-1" :class="{ 'opacity-50': removed }">
            <div
                class="relative grid place-items-center rounded-lg border"
                :class="[dark ? 'border-neutral-700 bg-neutral-900' : 'border-neutral-200 bg-white', markRing(item.id)]"
                :style="{ width: `${size + 12}px`, height: `${size + 12}px` }"
            >
                <img :src="item.imageUrl" :alt="item.alt" :width="size" :height="size" loading="lazy" decoding="async" class="block" :style="{ width: `${size}px`, height: `${size}px` }" />
                <span v-if="markOf(item.id)" class="absolute -top-1.5 -left-1.5 inline-flex items-center justify-center rounded-full px-1.5 py-0.5 text-[10px] leading-none font-semibold text-white" :class="markClass(item.id)">
                    <span class="relative -top-[0.09em]">{{ t(`emoticon.diff.mark.${markOf(item.id)}`) }}</span>
                </span>
                <span
                    v-if="levels[item.id]"
                    class="absolute -top-1.5 -right-1.5 inline-grid size-5 place-items-center rounded-full text-white"
                    :class="levels[item.id] === 'error' ? 'bg-red-600' : 'bg-amber-500'"
                    :title="levels[item.id] === 'error' ? t('emoticon.spec.levelError') : t('emoticon.spec.levelWarn')"
                >
                    <i class="pi pi-exclamation-triangle text-[10px]" aria-hidden="true" />
                    <span class="sr-only">{{ levels[item.id] === 'error' ? t('emoticon.spec.levelError') : t('emoticon.spec.levelWarn') }}</span>
                </span>
            </div>
            <span class="flex max-w-full min-w-0 items-baseline gap-1 text-[11px]">
                <span class="shrink-0 tabular-nums text-muted-color">{{ index + 1 }}</span>
                <span class="truncate" :title="item.alt">{{ item.alt }}</span>
            </span>
        </li>
    </ul>
</template>

<script setup lang="ts">
import type { EmoticonItemView } from '~/composables/emoticon/api/useEmoticonAdminApi.types';
import type { EmoticonDiffMark, EmoticonSpecLevel } from '~/utils/util-emoticon';

interface Props {
    items: EmoticonItemView[];
    /** 그림 한 변(px) */
    size?: number;
    /** 어두운 바탕 */
    dark?: boolean;
    /** 새 판 비교 표시(아이디 → 표시) */
    marks?: Record<string, EmoticonDiffMark>;
    /** 규격 검사 수준(아이디 → 수준) */
    levels?: Record<string, EmoticonSpecLevel>;
    /** 빠진 것들(옛 판) — 흐리게, 칸마다 「빠짐」 */
    removed?: boolean;
}

const props = withDefaults(defineProps<Props>(), { size: 100, dark: false, marks: () => ({}), levels: () => ({}), removed: false });

const { t } = useI18n();

function markOf(id: string): EmoticonDiffMark | 'removed' | null {
    if (props.removed) return 'removed';
    return props.marks[id] ?? null;
}

function markClass(id: string): string {
    const mark = markOf(id);
    if (mark === 'added') return 'bg-green-600';
    if (mark === 'removed') return 'bg-neutral-500';
    return 'bg-sky-600';
}

function markRing(id: string): string {
    const mark = markOf(id);
    if (mark === 'added') return 'ring-2 ring-green-500/60';
    if (mark === 'changedImage' || mark === 'changedAlt') return 'ring-2 ring-sky-500/60';
    return '';
}
</script>
