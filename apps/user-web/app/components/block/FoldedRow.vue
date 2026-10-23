<template>
    <!-- 접히지 않는 줄 — 내용만 그린다 -->
    <slot v-if="!reason" :revealed="false" :hide="hide" />

    <!-- 접힌 줄(표) — 표의 한 줄(`<tr>`)을 통째로 대신한다 -->
    <tr v-else-if="!open && table" class="border-b border-surface" data-testid="folded-row">
        <td :colspan="colspan" class="px-0 py-2">
            <div class="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted-color">
                <i class="pi pi-eye-slash text-xs" aria-hidden="true" />
                <span>{{ UtilBlockMessages.folded(t, reason, kind) }}</span>
                <button ref="showButton" type="button" :class="SHOW_CLASS" :aria-label="UtilBlockMessages.showLabel(t, reason, kind)" data-testid="folded-show" @click="reveal">
                    {{ t('common.show') }}
                </button>
            </div>
        </td>
    </tr>

    <!-- 접힌 줄 — 차단한 사용자·단어의 글·댓글이 한 줄로 남아 목록 위치가 밀리지 않는다. 「보기」를 누르면 그 자리에서 펼친다(다시 불러오지 않는다) -->
    <div v-else-if="!open" v-bind="$attrs" class="flex flex-wrap items-center gap-x-2 gap-y-1 text-muted-color" data-testid="folded-row">
        <i class="pi pi-eye-slash text-xs" aria-hidden="true" />
        <span>{{ UtilBlockMessages.folded(t, reason, kind) }}</span>
        <button ref="showButton" type="button" :class="SHOW_CLASS" :aria-label="UtilBlockMessages.showLabel(t, reason, kind)" data-testid="folded-show" @click="reveal">
            {{ t('common.show') }}
        </button>
    </div>

    <!-- 펼친 줄 — 내용 + 「다시 숨기기」. 표는 내용(슬롯) 안의 칸이 `hide` 로 직접 단추를 둔다 -->
    <template v-else>
        <slot :revealed="true" :hide="hide" />
        <div v-if="!table" :class="hideClass">
            <button ref="hideButton" type="button" :class="HIDE_CLASS" data-testid="folded-hide" @click="hide">{{ t('block.hideAgain') }}</button>
        </div>
    </template>
</template>

<script setup lang="ts">
import type { BlockedBy } from '~/composables/block/api/useBlockApi.types';
import type { FoldKind } from '~/utils/util-block-messages';
import UtilBlockMessages from '~/utils/util-block-messages';

/**
 * 차단한 사용자·단어의 글·댓글을 한 줄로 접는다(community `FoldedRow`). 서버는 내용을 그대로 싣고 `blocked` 로 표시만 하므로 접는 것은 화면의 일이다.
 *  - `reason` 이 null 이면 접지 않는다 — 목록 줄·댓글을 늘 이 컴포넌트로 감싸고 `reason` 만 바꿔 넘긴다.
 *  - 접힘 상태는 **그 줄의 로컬 상태**다(목록을 다시 받으면 다시 접힌다). `table` 이면 `<tr><td colspan>` 이고 「다시 숨기기」는 슬롯 속성(`hide`)으로 슬롯 안에 둔다.
 */
defineOptions({ inheritAttrs: false });

interface Props {
    reason: BlockedBy | null;
    kind: FoldKind;
    table?: boolean;
    colspan?: number;
    /** 펼친 뒤 「다시 숨기기」를 감싸는 칸의 `class`(표가 아닐 때) */
    hideClass?: string;
}

const props = withDefaults(defineProps<Props>(), { table: false, colspan: 1, hideClass: '' });

defineSlots<{ default(props: { revealed: boolean; hide: () => void }): unknown }>();

const { t } = useI18n();
const SHOW_CLASS = 'rounded px-1.5 py-1 text-sm font-medium text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary';
const HIDE_CLASS = 'rounded px-1 py-0.5 text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary';

const open = ref(false);
const showButton = ref<HTMLButtonElement | null>(null);
const hideButton = ref<HTMLButtonElement | null>(null);

// 접는 까닭이 없어지면(차단을 풀었다) 펼침도 잊는다
watch(
    () => props.reason,
    (reason) => {
        if (!reason) open.value = false;
    },
);

/** 누른 단추가 사라지고 반대 단추가 생기므로 키보드 초점을 그리로 옮긴다 */
async function reveal() {
    open.value = true;
    await nextTick();
    hideButton.value?.focus({ preventScroll: true });
}

async function hide() {
    open.value = false;
    await nextTick();
    showButton.value?.focus({ preventScroll: true });
}
</script>
