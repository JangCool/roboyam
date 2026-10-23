<template>
    <!-- 관리자 메뉴 미리보기의 화면 항목 한 줄(하위 메뉴면 하위 화면까지) — `AdminMenuPreview` 만 쓴다. 권한이 걸린 줄은 열쇠를 붙인다(`RoleMenuPreview` 와 같다) -->
    <li>
        <div class="flex min-h-7 items-center gap-2 px-2 text-xs leading-relaxed">
            <i v-if="entry.node.icon" :class="entry.node.icon" class="shrink-0 text-[11px] text-muted-color" aria-hidden="true" />
            <span class="truncate" :class="permission ? 'text-primary' : ''">{{ name }}</span>
            <i v-if="entry.node.targetType === 'URL'" class="pi pi-external-link text-[9px] text-muted-color" :aria-label="t('menuConfig.externalLink')" />
            <i v-if="entry.node.kind === 'FOLDER'" class="pi pi-angle-down text-[10px] text-muted-color" aria-hidden="true" />
            <span v-if="permissionLabel" v-tooltip.top="t('role.menuNeeds', { permission: permissionLabel })" class="ml-auto inline-flex shrink-0 items-center" role="img" :aria-label="t('role.menuNeeds', { permission: permissionLabel })">
                <i class="pi pi-key text-[10px] text-primary" aria-hidden="true" />
            </span>
        </div>
        <ul v-if="entry.children.length" class="m-0 ml-4 list-none border-l border-(--line) p-0 pl-1">
            <AdminMenuPreviewItem v-for="child in entry.children" :key="child.node.key" :entry="child" />
        </ul>
    </li>
</template>

<script setup lang="ts">
import type { AdminMenuPreviewNode } from '~/utils/util-admin-menu-config';
import UtilAdminMenuConfig from '~/utils/util-admin-menu-config';
import UtilMenuMessages from '~/utils/util-menu-messages';

interface Props {
    entry: AdminMenuPreviewNode;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const { localizedName } = useLocalizedName();

const name = computed(() => UtilMenuMessages.adminNodeName(t, props.entry.node, localizedName).text);
const permission = computed(() => UtilAdminMenuConfig.permissionOf(props.entry.node));
const permissionLabel = computed(() => (permission.value ? UtilMenuMessages.adminPermissionLabel(t, permission.value) : null));
</script>
