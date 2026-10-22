<template>
    <!--
        공개 사이트 메뉴 미리보기 — 보는 사람(비로그인 / 등급별 회원 / 게시판 매니저·스텝)을 골라 **저장 전 트리**를 서버와 같은 규칙(`UtilMenu.preview`)으로 걸러 세로 사이드 모양으로 보인다.
        SIDE 그룹은 위에 모바일 상단 탭 줄도 보인다. 폴더는 제목(접기 표시), 항목은 이름(외부 링크 아이콘).
    -->
    <div class="flex flex-col gap-3">
        <div class="flex flex-col gap-1.5">
            <label :for="`${idBase}-viewer`" class="text-xs font-medium text-muted-color">{{ t('menuConfig.previewViewer') }}</label>
            <Select :input-id="`${idBase}-viewer`" v-model="viewerValue" :options="viewerOptions" option-label="label" option-value="value" size="small" fluid />
        </div>

        <div v-if="side" class="flex flex-col gap-1">
            <span class="text-[11px] font-semibold text-muted-color">{{ t('menuConfig.previewTabs') }}</span>
            <div v-if="tabs.length" class="flex gap-1 overflow-x-auto rounded-md border border-(--line) p-1">
                <span v-for="tab in tabs" :key="tab.key" class="shrink-0 rounded px-2 py-1 text-[11px] whitespace-nowrap" :class="tab === tabs[0] ? 'bg-primary text-primary-contrast' : 'bg-surface-100 dark:bg-surface-800'">{{ nameOf(tab) }}</span>
            </div>
            <p v-else class="m-0 text-[11px] text-muted-color">{{ t('menuConfig.previewNoTabs') }}</p>
        </div>

        <nav class="rounded-lg border border-(--line) bg-surface-50 p-2 dark:bg-surface-950" :aria-label="t('menuConfig.previewTitle')">
            <p v-if="!result.length" class="m-0 px-2 py-6 text-center text-xs text-muted-color">{{ t('menuConfig.previewEmpty') }}</p>
            <MenuPreviewList v-else :nodes="result" :ctx="ctx" :depth="1" />
        </nav>
        <p class="m-0 text-[11px] leading-relaxed text-muted-color">{{ t('menuConfig.previewNote') }}</p>
    </div>
</template>

<script setup lang="ts">
import type { MenuContext, MenuEditItem, MenuEditNode, MenuViewer } from '~/utils/util-menu';
import UtilMenu from '~/utils/util-menu';
import UtilMenuMessages from '~/utils/util-menu-messages';

interface Props {
    /** 저장 전 트리 */
    nodes: MenuEditNode[];
    ctx: MenuContext;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const { localizedName } = useLocalizedName();
const idBase = useId();

const viewerValue = ref('GUEST');

const viewerOptions = computed(() => [
    { value: 'GUEST', label: t('menuConfig.viewerGuest') },
    ...props.ctx.grades.map((g) => ({ value: `GRADE:${g.gradeKey}`, label: t('menuConfig.viewerMember', { grade: g.label }) })),
    { value: 'STAFF', label: t('menuConfig.viewerStaff') },
]);

const viewer = computed<MenuViewer>(() => {
    if (viewerValue.value === 'STAFF') return { kind: 'STAFF' };
    if (viewerValue.value.startsWith('GRADE:')) {
        const grade = props.ctx.grades.find((g) => `GRADE:${g.gradeKey}` === viewerValue.value);
        return { kind: 'MEMBER', level: grade?.level ?? 1 };
    }
    return { kind: 'GUEST' };
});

const side = computed(() => UtilMenu.isSide(props.ctx.placement));
const result = computed(() => UtilMenu.preview(props.nodes, viewer.value, props.ctx));
const tabs = computed(() => (side.value ? UtilMenu.previewTabs(result.value) : []));

const nameOf = (item: MenuEditItem) => UtilMenuMessages.nodeName(t, item, props.ctx, localizedName).text;
</script>
