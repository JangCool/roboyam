<template>
    <!--
        관리자 사이트 메뉴 미리보기 — 운영자 역할(권한 묶음)을 골라 「이 역할이 보는 메뉴」를 **저장 전 구성**으로 보인다.
        거르기는 `UtilAdminMenuConfig.preview`(숨김·권한 없음을 빼고 빈 묶음·하위 메뉴를 뺀다 — `UtilAdminMenu.visible` 과 같은 규칙). 그리는 모양은 `RoleMenuPreview` 와 같은 줄 모양이다.
    -->
    <div class="flex flex-col gap-3">
        <div class="flex flex-col gap-1.5">
            <label :for="`${idBase}-viewer`" class="text-xs font-medium text-muted-color">{{ t('menuConfig.previewRole') }}</label>
            <Select :input-id="`${idBase}-viewer`" v-model="viewerValue" :options="viewers" option-label="label" option-value="value" filter size="small" fluid />
            <p v-if="rolesNote" class="m-0 text-[11px] text-muted-color">{{ rolesNote }}</p>
        </div>

        <nav class="rounded-lg border border-(--line) bg-surface-50 p-2 dark:bg-surface-950" :aria-label="t('menuConfig.previewTitle')">
            <p v-if="!result.length" class="m-0 px-2 py-6 text-center text-xs text-muted-color">{{ t('menuConfig.previewEmpty') }}</p>
            <ul v-else class="m-0 flex list-none flex-col gap-2 p-0">
                <li v-for="entry in result" :key="entry.node.key">
                    <template v-if="entry.node.kind === 'SECTION'">
                        <h3 class="mb-1 flex items-center gap-1.5 px-2 text-xs font-semibold">
                            <i v-if="entry.node.icon" :class="entry.node.icon" class="text-[11px]" aria-hidden="true" />
                            {{ nameOf(entry.node) }}
                        </h3>
                        <ul class="m-0 flex list-none flex-col p-0">
                            <AdminMenuPreviewItem v-for="child in entry.children" :key="child.node.key" :entry="child" />
                        </ul>
                    </template>
                    <ul v-else class="m-0 list-none p-0">
                        <AdminMenuPreviewItem :entry="entry" />
                    </ul>
                </li>
            </ul>
        </nav>
    </div>
</template>

<script setup lang="ts">
import type { AdminMenuEditNode } from '~/utils/util-admin-menu-config';
import UtilAdminMenuConfig from '~/utils/util-admin-menu-config';
import UtilMenuMessages from '~/utils/util-menu-messages';

interface Props {
    /** 한 모드의 저장 전 구성 */
    nodes: AdminMenuEditNode[];
    /** 고를 수 있는 보는 사람 — `codes` 가 null 이면 모든 권한(최고 관리자) */
    viewers: { value: string; label: string; codes: string[] | null }[];
    /** 역할 목록을 못 읽었을 때 등의 안내 */
    rolesNote?: string;
}

const props = withDefaults(defineProps<Props>(), { rolesNote: '' });

const { t } = useI18n();
const { localizedName } = useLocalizedName();
const idBase = useId();

const viewerValue = ref(props.viewers[0]?.value ?? '');
watch(
    () => props.viewers,
    (list) => {
        if (!list.some((v) => v.value === viewerValue.value)) viewerValue.value = list[0]?.value ?? '';
    },
);

const result = computed(() => {
    const viewer = props.viewers.find((v) => v.value === viewerValue.value);
    const owned = viewer?.codes ? new Set(viewer.codes) : null;
    return UtilAdminMenuConfig.preview(props.nodes, (code) => owned === null || owned.has(code));
});

const nameOf = (node: AdminMenuEditNode) => UtilMenuMessages.adminNodeName(t, node, localizedName).text;
</script>
