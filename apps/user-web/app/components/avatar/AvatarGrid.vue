<template>
    <!-- 아바타 격자 — 칸 하나가 라디오 단추 하나다(그림 + 이름 + 작은 글). 눌린 칸은 고리와 체크 표시로 보인다(색만으로 가르지 않는다).
         그림은 `<img>`(지연 로딩, `object-contain`). 좁은 화면 4열 · 태블릿 6열 · 넓은 화면 8열 -->
    <div role="radiogroup" :aria-label="label" class="grid grid-cols-4 gap-x-1 gap-y-2 sm:grid-cols-6 lg:grid-cols-8" :data-testid="testid">
        <button
            v-for="cell in cells"
            :key="cell.id"
            type="button"
            role="radio"
            :aria-checked="cell.active"
            :aria-label="cell.ariaLabel"
            :disabled="disabled"
            class="relative flex min-h-[4.75rem] min-w-0 flex-col items-center gap-1 rounded-lg px-0.5 py-1.5 focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary disabled:opacity-50"
            :class="[cell.active ? 'bg-primary-50 ring-2 ring-primary dark:bg-primary-950/40' : 'hover:bg-surface-100 dark:hover:bg-surface-800', cell.dim ? 'opacity-60' : '']"
            :data-testid="`avatar-option-${cell.id}`"
            @click="$emit('choose', cell.id)"
        >
            <ProfileBorderAvatar :src="cell.url" size="sm" lazy :class="busyId === cell.id ? 'opacity-50' : ''" />
            <span class="w-full truncate text-center text-xs leading-tight" :class="cell.active ? 'font-semibold' : 'text-muted-color'">{{ cell.name }}</span>
            <span v-if="cell.caption" class="w-full truncate text-center text-[0.6875rem] leading-tight text-muted-color">{{ cell.caption }}</span>
            <i v-if="cell.active" class="pi pi-check absolute right-1 top-1 grid size-4 place-items-center rounded-full bg-primary text-[0.5rem] text-primary-contrast" aria-hidden="true" />
        </button>
    </div>
</template>

<script setup lang="ts">
import type { AvatarGridCell } from '~/components/avatar/AvatarGrid.types';

/**
 * 아바타 격자 — 그리기만 한다. 무엇을 보일지(카테고리·무료 + 산 것 / 유료)는 부르는 쪽이 칸(`AvatarGridCell`)으로 만들고, 누르면 `choose` 로 아바타 ID 를 올린다(요청·확인은 부르는 쪽).
 * 프로필 설정의 고르기와 포인트 상점 아바타 탭이 같이 쓴다.
 */
interface Props {
    cells: AvatarGridCell[];
    /** 스크린리더용 이름 — 지금 보는 카테고리 이름 */
    label: string;
    disabled?: boolean;
    /** 지금 저장하는 중인 칸(흐리게) */
    busyId?: string | null;
    testid?: string;
}

withDefaults(defineProps<Props>(), {
    disabled: false,
    busyId: null,
    testid: 'avatar-grid',
});

defineEmits<{ choose: [id: string] }>();
</script>
