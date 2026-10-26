<template>
    <!-- 아바타 카테고리 칩 줄 — 누르면 그 카테고리를 본다(하나만 눌린다). 좁은 화면은 옆으로 밀고, 넓은 화면은 줄을 바꿔 다 보인다.
         이름은 화면 문구(`avatar.category.<id>` — 카탈로그 이름의 「(남/여)」 꼬리표를 쓰지 않는다), 수는 부르는 쪽이 센 값 -->
    <div role="group" :aria-label="label" class="-mx-4 flex gap-1.5 overflow-x-auto whitespace-nowrap px-4 py-1 md:mx-0 md:flex-wrap md:px-0" :data-testid="testid">
        <button
            v-for="category in categories"
            :key="category.id"
            type="button"
            class="inline-flex min-h-8 shrink-0 items-center rounded-full border px-3 py-0.5 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            :class="
                model === category.id
                    ? 'border-surface-900 bg-surface-900 font-semibold text-surface-0 dark:border-surface-100 dark:bg-surface-100 dark:text-surface-900'
                    : 'border-surface bg-surface-0 text-color hover:bg-surface-100 dark:bg-surface-900 dark:hover:bg-surface-800'
            "
            :aria-pressed="model === category.id"
            :data-testid="`${testid}-${category.id}`"
            @click="model = category.id"
        >
            {{ t(UtilAvatar.categoryKey(category.id)) }}<span class="ml-1 text-xs opacity-70">{{ category.count }}</span>
        </button>
    </div>
</template>

<script setup lang="ts">
import type { AvatarCategory } from '~/composables/avatar/api/useAvatarApi.types';
import UtilAvatar from '~/utils/util-avatar';

/** 아바타 카테고리 칩 줄 — 프로필 설정의 고르기(무료 + 산 것)와 포인트 상점 아바타 탭(유료)이 같이 쓴다. 어느 카테고리·수를 보일지는 부르는 쪽이 정한다 */
interface Props {
    categories: AvatarCategory[];
    /** 스크린리더용 이름 */
    label: string;
    testid?: string;
}

withDefaults(defineProps<Props>(), {
    testid: 'avatar-categories',
});

/** 고른 카테고리 ID */
const model = defineModel<string>({ required: true });

const { t } = useI18n();
</script>
