<template>
    <!-- 회원 ⋯ 메뉴의 항목 둘 — 「이 사용자 차단 / 차단 해제」·「메모 남기기 / 메모 수정」. 부르는 쪽 시트의 `<ul>` 안에 놓는다 -->
    <template v-if="target">
        <!-- 운영진·운영자는 차단할 수 없어 감춘다(서버도 403) — 이미 차단해 둔 사람이면 풀 수 있게 남긴다 -->
        <li v-if="target.blocked || UtilBlock.canBlock(target.role)">
            <button type="button" :class="ITEM" data-testid="menu-user-block" @click="pick(target.blocked ? 'unblock' : 'block')">
                {{ target.blocked ? t('block.menuUnblock') : t('block.menuBlock') }}
                <small class="ml-1 text-xs text-muted-color">{{ target.blocked ? t('block.menuUnblockHint') : t('block.menuBlockHint') }}</small>
            </button>
        </li>
        <li>
            <button type="button" :class="ITEM" data-testid="menu-user-memo" @click="pick('memo')">
                {{ target.memo ? t('block.menuMemoEdit') : t('block.menuMemoAdd') }}
                <small class="ml-1 text-xs text-muted-color">{{ t('block.menuMemoHint') }}</small>
            </button>
        </li>
    </template>
</template>

<script setup lang="ts">
import type { UserAction, UserActionKind, UserActionTarget } from '~/composables/block/useUserActions';
import UtilBlock from '~/utils/util-block';

/** 회원 ⋯ 메뉴의 항목 — 무엇을 눌렀는지만 `action` 으로 올리고, 하는 일은 부르는 쪽이 `useUserActions().run` 으로 한다. `target` 이 null(탈퇴·나)이면 그리지 않는다 */
interface Props {
    target: UserActionTarget | null;
}

const props = withDefaults(defineProps<Props>(), {});
const emit = defineEmits<{ action: [action: UserAction] }>();
const { t } = useI18n();

function pick(kind: UserActionKind) {
    if (props.target) emit('action', { kind, target: props.target });
}

const ITEM = 'block w-full border-b border-surface px-5 py-3 text-left text-sm hover:bg-surface-100 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary dark:hover:bg-surface-800';
</script>
