<template>
    <!-- 메뉴 검색 — 이동할 수 있는 메뉴(`UtilAdminMenu.ITEMS` 의 화면 항목 — 묶음 아래 항목 포함, 묶음 제목 자체는 갈 곳이 없어 제외)만 찾는다. 모드(일반 메뉴·설정 메뉴)와 무관하게 모든 메뉴를 찾고, 고르면 이동하며 메뉴 모드는 주소를 따라 바뀐다(`useAppMenu`). 묶음이 없는 설정 메뉴 항목(일반)은 소속 자리에 "설정"을 보인다. 칸에 붙은 목록에서 고르면 그 화면으로 간다. Ctrl+K(맥은 ⌘K)로 어디서나 칸에 초점을 준다 -->
    <div class="relative w-full min-w-0 max-w-md">
        <i class="pi pi-search pointer-events-none absolute left-3 top-1/2 z-1 -translate-y-1/2 text-sm text-muted-color" aria-hidden="true" />
        <AutoComplete
            v-model="picked"
            :suggestions="suggestions"
            option-label="label"
            :input-id="inputId"
            :placeholder="t('shell.searchPlaceholder')"
            :aria-label="t('shell.searchPlaceholder')"
            :min-length="0"
            complete-on-focus
            auto-option-focus
            fluid
            input-class="pl-9! pr-16!"
            @complete="onComplete"
            @option-select="onSelect"
        >
            <template #option="{ option }">
                <span class="flex min-w-0 items-center gap-2.5">
                    <i v-if="option.icon" :class="[option.icon, 'text-sm text-muted-color']" aria-hidden="true" />
                    <span class="truncate">{{ option.label }}</span>
                    <span v-if="option.group" class="ml-auto shrink-0 text-xs text-muted-color">{{ option.group }}</span>
                </span>
            </template>
            <template #empty>{{ t('shell.searchEmpty') }}</template>
        </AutoComplete>
        <kbd class="app-kbd pointer-events-none absolute right-3 top-1/2 hidden -translate-y-1/2 rounded px-1.5 py-px font-sans text-xs text-muted-color md:block" aria-hidden="true">{{ shortcutLabel }}</kbd>
    </div>
</template>

<script setup lang="ts">
import UtilAdminMenu, { type AdminMenuItem } from '~/utils/util-admin-menu';

interface Props {
    /** 화면에 나타나자마자 칸에 초점을 준다(모바일에서 검색을 열었을 때) */
    autofocus?: boolean;
}

interface SearchOption {
    key: string;
    label: string;
    icon?: string;
    to: string;
    /** 속한 묶음 이름(`배치와 로그`) — 같은 이름이 여러 곳일 때 어느 쪽인지 알리는 맥락. 낱개 항목이면 빈 글자 */
    group: string;
}

const props = withDefaults(defineProps<Props>(), { autofocus: false });
const emit = defineEmits<{ done: [] }>();

const { t } = useI18n();
const router = useRouter();
const inputId = useId();
const { can } = useAdminPermission();

const picked = ref<SearchOption | null>(null);
const suggestions = ref<SearchOption[]>([]);

/** 맥이면 ⌘K, 아니면 Ctrl K — 안내 글자가 실제 손가락과 달라선 안 된다 */
const shortcutLabel = computed(() => (/Mac|iPhone|iPad/.test(navigator.platform) ? '⌘K' : 'Ctrl K'));

/**
 * 화면 항목(하위가 있으면 하위의 잎)을 검색 대상으로 — 맥락(`group`)은 모드·묶음·부모 이름을 ` › ` 로 이은 경로다(예: `설정 › 운영자와 권한 › 역할 관리`). 이름은 잎의 이름(`회원 역할`)이라 검색어가 잎에 맞는다.
 * @param {AdminMenuItem} item - 화면 항목.
 * @param {string[]} trail - 이 항목 위의 이름들(빈 글자는 뺀다).
 * @returns {SearchOption[]} 이동할 수 있는 잎 항목들.
 */
function leafOptions(item: AdminMenuItem, trail: string[]): SearchOption[] {
    if (item.children) return item.children.flatMap((child) => leafOptions(child, [...trail, t(item.labelKey)]));
    return [{ key: item.key, label: t(item.labelKey), icon: item.icon, to: item.to, group: trail.filter(Boolean).join(' › ') }];
}

/** 지금 언어의 메뉴 이름으로 만든 검색 대상 — 언어가 바뀌면 다시 계산된다 */
const options = computed<SearchOption[]>(() =>
    UtilAdminMenu.visible(can).flatMap((node) =>
        UtilAdminMenu.isGroup(node)
            ? node.items.flatMap((item) => leafOptions(item, [UtilAdminMenu.modeOf(node) === 'settings' ? t('shell.settings') : '', t(node.labelKey)]))
            : leafOptions(node, [UtilAdminMenu.modeOf(node) === 'settings' ? t('shell.settings') : '']),
    ),
);

function onComplete(event: { query: string }) {
    const term = event.query.trim().toLowerCase();
    suggestions.value = term ? options.value.filter((option) => option.label.toLowerCase().includes(term)) : options.value;
}

async function onSelect(event: { value: SearchOption }) {
    await router.push(event.value.to);
    picked.value = null; // 칸을 비운다 — 다음에 다시 찾을 수 있게
    emit('done');
}

function focusInput() {
    const input = document.getElementById(inputId) as HTMLInputElement | null;
    input?.focus();
    input?.select();
}

function onShortcut(event: KeyboardEvent) {
    if (event.key.toLowerCase() !== 'k' || !(event.ctrlKey || event.metaKey) || event.shiftKey || event.altKey) return;
    event.preventDefault(); // 브라우저의 기본 동작(주소창 검색 등)을 막는다
    focusInput();
}

onMounted(() => {
    window.addEventListener('keydown', onShortcut);
    if (props.autofocus) nextTick(focusInput);
});
onBeforeUnmount(() => window.removeEventListener('keydown', onShortcut));
</script>
