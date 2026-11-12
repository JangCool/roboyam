<template>
    <!--
        닉네임을 누르면 뜨는 회원 메뉴(사용자 시안 확정 2026-11-12 — docs/features/author-menu-contract.md).
        머리글 = 줄이지 않은 전체 닉네임 · 활동 보기(공개 프로필, 작성글 탭) · 쪽지 보내기 · 메모 남기기/수정 · ─ · 차단하기/차단 해제(빨강).
        · 내 닉네임·비회원이 볼 때: 활동 보기만. · 운영자 회원 계정: 메모·차단 없음(이미 차단해 둔 사람이면 해제는 남긴다).
        글 줄 전체가 링크인 자리에서도 쓰므로 누름은 링크로 넘기지 않는다(`prevent`·`stop`). 메뉴는 body 에 붙는 PrimeVue `Menu`(처음 누를 때 만든다 — 목록 줄마다 미리 만들지 않게).
        class·style 은 보이는 이름에 붙는다.
    -->
    <span
        v-bind="$attrs"
        role="button"
        tabindex="0"
        class="cursor-pointer whitespace-nowrap hover:underline focus-visible:outline-2 focus-visible:outline-primary"
        :aria-label="t('board.authorMenu.label', { name })"
        aria-haspopup="menu"
        data-testid="author-menu-trigger"
        @click.prevent.stop="toggle"
        @keydown.enter.prevent.stop="toggle"
        @keydown.space.prevent.stop="toggle"
        >{{ shown }}</span
    >
    <Menu v-if="ready" ref="menu" :model="items" popup :pt="{ root: { class: 'min-w-44' } }" data-testid="author-menu">
        <template #start>
            <p class="m-0 max-w-60 break-all border-b border-surface px-3 pb-2 pt-1.5 text-sm font-semibold text-color" data-testid="author-menu-name">{{ name }}</p>
        </template>
        <template #item="{ item, props: link }">
            <a v-bind="link.action" class="flex items-center gap-2 px-3 py-2 text-sm" :class="item.danger ? 'text-red-600 dark:text-red-400' : ''" :data-testid="item.testid">
                <i :class="[item.icon, item.danger ? '' : 'text-muted-color']" class="text-sm" aria-hidden="true" />
                <span>{{ item.label }}</span>
            </a>
        </template>
    </Menu>
</template>

<script setup lang="ts">
import type { Role } from '~/composables/auth/api/useAuthApi.types';
import UtilBlock from '~/utils/util-block';
import UtilDm from '~/utils/util-dm';
import UtilProfile from '~/utils/util-profile';

defineOptions({ inheritAttrs: false });

interface Props {
    memberId: string;
    /** 전체 닉네임 — 메뉴 머리글 */
    name: string;
    /** 줄에 보일 이름(6자로 줄인 것 등) — 없으면 전체 닉네임 */
    display?: string | null;
    role?: Role | null;
    /** 운영자 회원 계정 — 메모·차단을 감춘다 */
    operator?: boolean;
    /** 서버가 준 내 메모 */
    memo?: string | null;
    /** 서버가 준 「내가 차단한 회원」 — 목록 줄·댓글의 `blocked === 'USER'` */
    blocked?: boolean;
}

const props = withDefaults(defineProps<Props>(), { display: null, role: null, operator: false, memo: null, blocked: false });

const { t } = useI18n();
const { user } = useAuth();
const actions = useUserActions();

const shown = computed(() => props.display ?? props.name);

interface AuthorMenuItem {
    label?: string;
    icon?: string;
    separator?: boolean;
    danger?: boolean;
    testid?: string;
    command?: () => void;
}

/** 메뉴 항목 — 열 때마다 지금 값(메모·차단을 방금 바꾼 것 포함)으로 다시 만든다 */
const items = computed<AuthorMenuItem[]>(() => {
    const list: AuthorMenuItem[] = [
        { label: t('board.authorMenu.activity'), icon: 'pi pi-user', testid: 'author-menu-activity', command: () => void navigateTo(UtilProfile.path(props.memberId)) },
    ];
    // 나 자신(targetOf 가 null)·비회원이 볼 때는 활동 보기만
    const target = user.value ? actions.targetOf({ id: props.memberId, nickname: props.name, role: props.role, memo: props.memo }, props.blocked) : null;
    if (!target) return list;
    list.push({ label: t('board.authorMenu.message'), icon: 'pi pi-envelope', testid: 'author-menu-message', command: () => void navigateTo(UtilDm.newPath(props.memberId)) });
    if (!props.operator) {
        list.push({
            label: target.memo ? t('block.menuMemoEdit') : t('block.menuMemoAdd'),
            icon: 'pi pi-pencil',
            testid: 'author-menu-memo',
            command: () => actions.openMemo(target),
        });
    }
    if (target.blocked || (!props.operator && UtilBlock.canBlock(target.role))) {
        list.push({ separator: true });
        list.push({
            label: target.blocked ? t('block.menuUnblock') : t('board.authorMenu.block'),
            icon: 'pi pi-ban',
            danger: !target.blocked,
            testid: 'author-menu-block',
            command: () => void (target.blocked ? actions.unblock(target) : actions.block(target)),
        });
    }
    return list;
});

/** PrimeVue `Menu` — 바깥 누름·Esc 로 닫는 것은 Menu 가 맡는다 */
interface MenuHandle {
    toggle: (event: Event, target?: HTMLElement) => void;
}
const ready = ref(false);
const menu = ref<MenuHandle | null>(null);
async function toggle(event: Event): Promise<void> {
    const target = event.currentTarget as HTMLElement;
    if (!ready.value) {
        ready.value = true;
        await nextTick();
    }
    // 처음 만들 때는 nextTick 뒤라 currentTarget 이 비어 있다 — 잡아 둔 요소로 위치를 잡는다
    menu.value?.toggle(event, target);
}
</script>
