<template>
    <!-- 미리보기의 한 단계 목록(재귀) — `MenuPreview` 만 쓴다. 폴더는 제목 + 하위(이 사람에게 연결이 살아 있으면 제목에 밑줄 + 고리), 항목은 이름 -->
    <ul class="m-0 flex list-none flex-col p-0" :class="depth > 1 ? 'ml-3 border-l border-(--line) pl-2' : ''">
        <li v-for="entry in nodes" :key="entry.node.key">
            <template v-if="entry.node.kind === 'FOLDER'">
                <div class="flex items-center gap-1.5 px-2 pt-2 pb-1 text-[11px] font-semibold text-muted-color">
                    <span class="truncate" :class="entry.linked ? 'text-color underline decoration-dotted underline-offset-2' : ''">{{ nameOf(entry.node) }}</span>
                    <i v-if="entry.linked" class="pi pi-link text-[9px]" :aria-label="t('menuConfig.folderLinkTag')" />
                    <i v-if="entry.node.collapse !== 'NONE'" :class="entry.node.collapse === 'CLOSED' ? 'pi pi-chevron-right' : 'pi pi-chevron-down'" class="ml-auto text-[9px]" aria-hidden="true" />
                </div>
                <MenuPreviewList :nodes="entry.children" :ctx="ctx" :depth="depth + 1" />
            </template>
            <div v-else class="flex items-center gap-1.5 rounded px-2 py-1 text-[13px] hover:bg-surface-100 dark:hover:bg-surface-800">
                <span class="min-w-0 truncate">{{ nameOf(entry.node) }}</span>
                <i v-if="UtilMenu.isExternal(entry.node)" class="pi pi-external-link ml-auto text-[10px] text-muted-color" :aria-label="t('menuConfig.externalLink')" />
            </div>
        </li>
    </ul>
</template>

<script setup lang="ts">
import type { MenuContext, MenuEditNode, MenuPreviewNode } from '~/utils/util-menu';
import UtilMenu from '~/utils/util-menu';
import UtilMenuMessages from '~/utils/util-menu-messages';

interface Props {
    nodes: MenuPreviewNode[];
    ctx: MenuContext;
    depth: number;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const { localizedName } = useLocalizedName();

const nameOf = (node: MenuEditNode) => UtilMenuMessages.nodeName(t, node, props.ctx, localizedName).text;
</script>
