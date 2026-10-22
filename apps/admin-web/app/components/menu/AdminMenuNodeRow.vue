<template>
    <!--
        관리자 사이트 메뉴 트리의 한 줄 내용 — 아이콘 · 이름(덮어쓰지 않았으면 기본 이름을 흐리게) · 종류(묶음/하위 메뉴/화면/외부 링크) · 주소 ·
        권한(읽기 전용 열쇠 딱지 — 권한은 화면이 정한다) · 숨김 · 상태(없는 화면) · [다른 메뉴로 옮기기](최상위만) [수정] [삭제].
    -->
    <div class="flex min-w-0 flex-col gap-1" :class="dimmed ? 'opacity-55' : ''">
        <div class="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
            <i :class="iconClass" class="w-4 shrink-0 text-center text-[13px] text-muted-color" aria-hidden="true" />
            <span class="min-w-0 truncate text-[13px]" :class="[name.following ? 'text-muted-color' : 'font-medium', node.kind === 'SECTION' ? 'font-semibold' : '', !node.visible ? 'line-through decoration-surface-400' : '']">{{ name.text }}</span>
            <MenuTag :value="kindLabel" :severity="node.kind === 'SECTION' ? 'info' : 'secondary'" />
            <code v-if="address" class="inline-flex min-w-0 items-center gap-1 truncate text-[11px] text-muted-color">
                {{ address }}
                <i v-if="node.targetType === 'URL'" class="pi pi-external-link text-[10px]" :aria-label="t('menuConfig.externalLink')" />
            </code>
            <span v-if="permission" v-tooltip.top="t('menuConfig.permissionFixed')" class="inline-flex">
                <MenuTag :value="permission" icon="pi pi-key" severity="contrast" />
            </span>
            <MenuTag v-else-if="node.kind === 'SCREEN'" :value="t('menuConfig.permissionNone')" />
            <MenuTag v-if="!node.visible" :value="t('menuConfig.tag.hidden')" icon="pi pi-eye-slash" />
            <MenuTag v-if="missing" :value="t('menuConfig.status.TARGET_MISSING')" severity="danger" icon="pi pi-exclamation-triangle" />
            <span v-if="node.children" class="text-[11px] text-muted-color">{{ t('menuConfig.childCount', { n: node.children.length }) }}</span>

            <div v-if="!readonly" class="ml-auto flex shrink-0 items-center gap-0.5">
                <Button v-if="topLevel" type="button" icon="pi pi-arrow-right-arrow-left" size="small" severity="secondary" variant="text" rounded :aria-label="switchLabel" v-tooltip.top="switchLabel" @click="emit('switchMode')" />
                <Button type="button" icon="pi pi-pencil" size="small" severity="secondary" variant="text" rounded :aria-label="t('menuConfig.editNode', { name: name.text })" v-tooltip.top="t('common.edit')" @click="emit('edit')" />
                <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" rounded :aria-label="t('menuConfig.removeNode', { name: name.text })" v-tooltip.top="t('common.delete')" @click="emit('remove')" />
            </div>
        </div>
        <ul v-if="errors.length" class="m-0 list-none pl-6" role="alert">
            <li v-for="(message, i) in errors" :key="i" class="text-[11px] text-red-600 dark:text-red-400">{{ message }}</li>
        </ul>
    </div>
</template>

<script setup lang="ts">
import type { AdminMenuMode } from '~/utils/util-admin-menu';
import type { AdminMenuEditNode } from '~/utils/util-admin-menu-config';
import UtilAdminMenuConfig from '~/utils/util-admin-menu-config';
import UtilMenu from '~/utils/util-menu';
import UtilMenuMessages from '~/utils/util-menu-messages';

interface Props {
    node: AdminMenuEditNode;
    /** 지금 보는 모드 — 「설정 메뉴로 옮기기」 글자 */
    mode: AdminMenuMode;
    /** 최상위 노드(다른 모드로 옮길 수 있다) */
    topLevel?: boolean;
    /** 숨김 묶음 안 */
    dimmed?: boolean;
    errors?: string[];
    readonly?: boolean;
}

const props = withDefaults(defineProps<Props>(), { topLevel: false, dimmed: false, errors: () => [], readonly: false });
const emit = defineEmits<{ (e: 'edit'): void; (e: 'remove'): void; (e: 'switchMode'): void }>();

const { t } = useI18n();
const { localizedName } = useLocalizedName();

const name = computed(() => UtilMenuMessages.adminNodeName(t, props.node, localizedName));
const screen = computed(() => (props.node.kind === 'SCREEN' && props.node.targetType === 'ADMIN_SCREEN' ? UtilAdminMenuConfig.screenOf(props.node.targetKey) : undefined));
const missing = computed(() => props.node.kind === 'SCREEN' && props.node.targetType === 'ADMIN_SCREEN' && !screen.value);
const permission = computed(() => UtilAdminMenuConfig.permissionOf(props.node));
const iconClass = computed(() => props.node.icon || (props.node.kind === 'SECTION' ? 'pi pi-folder' : props.node.kind === 'FOLDER' ? 'pi pi-angle-down' : 'pi pi-circle-fill text-[5px]!'));
const kindLabel = computed(() => (props.node.kind === 'SCREEN' ? t(`menuConfig.adminType.${props.node.targetType ?? 'ADMIN_SCREEN'}`) : t(`menuConfig.adminKind.${props.node.kind}`)));
const address = computed(() => {
    if (props.node.kind !== 'SCREEN') return '';
    if (props.node.targetType === 'URL') return UtilMenu.hostOf(props.node.href);
    return screen.value?.to ?? props.node.targetKey ?? '';
});
const switchLabel = computed(() => t(props.mode === 'normal' ? 'menuConfig.toSettingsMode' : 'menuConfig.toNormalMode'));
</script>
