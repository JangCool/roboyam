<template>
    <!--
        공개 사이트 메뉴 트리의 한 줄 내용(손잡이는 `MenuTreeEditor`) — 폴더/항목 아이콘 · 이름(항목 이름이 비면 대상 이름을 흐리게) · 연결 유형 + 대상 ·
        보이는 대상 · 숨김 · 모바일 탭 · 자동 추가 · 접기 · 상태 경고 · [항목 넣기](폴더) [수정] [삭제]. 숨김 폴더 안의 줄은 흐리게 그린다(방문자에게 안 보인다).
        줄의 문제(화면 검사·서버 입력 오류)는 줄 아래에 빨간 글자로 선다.
    -->
    <div class="flex min-w-0 flex-col gap-1" :class="dimmed ? 'opacity-55' : ''">
        <div class="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
            <i :class="node.kind === 'FOLDER' ? 'pi pi-folder' : (typeDef?.icon ?? 'pi pi-question-circle')" class="shrink-0 text-[13px] text-muted-color" aria-hidden="true" />
            <span class="min-w-0 truncate text-[13px]" :class="[name.following ? 'italic text-muted-color' : 'font-medium', !node.visible ? 'line-through decoration-surface-400' : '']">{{ name.text }}</span>

            <template v-if="node.kind === 'ITEM'">
                <MenuTag :value="typeLabel" :severity="status === 'UNKNOWN_TYPE' ? 'warn' : 'secondary'" />
                <span v-if="targetText" class="inline-flex min-w-0 items-center gap-1 text-[11px] text-muted-color">
                    <code class="truncate">{{ targetText }}</code>
                    <i v-if="external" class="pi pi-external-link text-[10px]" :aria-label="t('menuConfig.externalLink')" />
                </span>
                <MenuTag v-if="node.newTab && (node.targetType === 'URL' || node.targetType === 'PATH')" :value="t('menuConfig.tag.newTab')" />
            </template>

            <MenuTag v-if="side && top && node.divider !== 'NONE'" :value="t(`menuConfig.dividerTag.${node.divider}`)" icon="pi pi-grip-lines-vertical" />
            <MenuTag v-if="node.audience !== 'ALL'" :value="audienceText" severity="info" icon="pi pi-eye" />
            <MenuTag v-if="!node.visible" :value="t('menuConfig.tag.hidden')" icon="pi pi-eye-slash" />
            <MenuTag v-if="node.kind === 'ITEM' && side && node.mobileTab" :value="t('menuConfig.tag.mobileTab')" severity="contrast" icon="pi pi-mobile" />
            <template v-if="node.kind === 'FOLDER'">
                <MenuTag v-if="side && node.autoAdd" :value="t('menuConfig.tag.autoAdd')" severity="success" icon="pi pi-bolt" />
                <MenuTag v-if="node.collapse !== 'NONE'" :value="t(`menuConfig.collapseTag.${node.collapse}`)" />
                <span class="text-[11px] text-muted-color">{{ t('menuConfig.childCount', { n: node.children.length }) }}</span>
            </template>
            <MenuTag v-if="status !== 'OK'" :value="t(`menuConfig.status.${status}`)" :severity="status === 'TARGET_MISSING' ? 'danger' : 'warn'" icon="pi pi-exclamation-triangle" />

            <div v-if="!readonly" class="ml-auto flex shrink-0 items-center gap-0.5">
                <Button v-if="node.kind === 'FOLDER'" type="button" icon="pi pi-plus" size="small" severity="secondary" variant="text" rounded :disabled="!canAddInside" :aria-label="t('menuConfig.addInside', { name: name.text })" v-tooltip.top="t('menuConfig.addInside', { name: name.text })" @click="emit('addInside')" />
                <Button type="button" icon="pi pi-pencil" size="small" severity="secondary" variant="text" rounded :disabled="status === 'UNKNOWN_TYPE'" :aria-label="t('menuConfig.editNode', { name: name.text })" v-tooltip.top="status === 'UNKNOWN_TYPE' ? t('menuConfig.unknownTypeLocked') : t('common.edit')" @click="emit('edit')" />
                <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" rounded :aria-label="t('menuConfig.removeNode', { name: name.text })" v-tooltip.top="t('common.delete')" @click="emit('remove')" />
            </div>
        </div>
        <p v-if="node.kind === 'ITEM' && side && top && node.description" class="m-0 truncate pl-5 text-[11px] text-muted-color" :title="node.description">{{ node.description }}</p>
        <p v-if="status !== 'OK' && statusHint" class="m-0 pl-5 text-[11px] text-amber-700 dark:text-amber-400">{{ statusHint }}</p>
        <ul v-if="errors.length" class="m-0 list-none pl-5" role="alert">
            <li v-for="(message, i) in errors" :key="i" class="text-[11px] text-red-600 dark:text-red-400">{{ message }}</li>
        </ul>
    </div>
</template>

<script setup lang="ts">
import type { MenuContext, MenuEditNode } from '~/utils/util-menu';
import UtilMenu from '~/utils/util-menu';
import UtilMenuMessages from '~/utils/util-menu-messages';

interface Props {
    node: MenuEditNode;
    ctx: MenuContext;
    /** 숨김 폴더 안(방문자에게 안 보인다) */
    dimmed?: boolean;
    /** 이 줄의 문제 문구 */
    errors?: string[];
    readonly?: boolean;
    /** 폴더 안에 항목을 더 넣을 수 있다(단계·노드 수 한도) */
    canAddInside?: boolean;
    /** 최상위 줄(구분선 딱지는 최상위만) */
    top?: boolean;
}

const props = withDefaults(defineProps<Props>(), { dimmed: false, errors: () => [], readonly: false, canAddInside: true, top: false });
const emit = defineEmits<{ (e: 'edit'): void; (e: 'remove'): void; (e: 'addInside'): void }>();

const { t } = useI18n();
const { localizedName } = useLocalizedName();

const side = computed(() => UtilMenu.isSide(props.ctx.placement));
const name = computed(() => UtilMenuMessages.nodeName(t, props.node, props.ctx, localizedName));
const typeDef = computed(() => (props.node.kind === 'ITEM' ? UtilMenu.typeOf(props.node.targetType) : undefined));
const typeLabel = computed(() => (typeDef.value ? t(typeDef.value.labelKey) : t('menuConfig.unknownType', { type: props.node.kind === 'ITEM' ? props.node.targetType : '' })));
const status = computed(() => (props.node.kind === 'ITEM' ? UtilMenu.itemStatus(props.node, props.ctx) : 'OK'));
const statusHint = computed(() => (status.value === 'OK' ? '' : t(`menuConfig.statusHint.${status.value}`)));
const targetText = computed(() => (props.node.kind === 'ITEM' ? UtilMenu.targetText(props.node, props.ctx) : ''));
const external = computed(() => props.node.kind === 'ITEM' && UtilMenu.isExternal(props.node));
const audienceText = computed(() => UtilMenuMessages.audience(t, props.node.audience, props.node.minGrade, props.ctx.grades));
</script>
