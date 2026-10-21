<template>
    <!--
        「이 역할이 보는 메뉴」 — 주어진 권한만 가졌을 때 남는 메뉴를 `UtilAdminMenu.visible` 로 계산해 보여 준다(사이드바·검색과 같은 계산이다). 읽기 전용이다.
        권한 때문에 보이는 메뉴에는 열쇠를 붙이고(필요한 권한 코드를 툴팁·읽기 글자로), 권한이 안 걸린 메뉴(대시보드)는 누구나 보므로 표시하지 않는다 — 거의 모든 줄에 같은 표시가 붙으면 무엇이 다른지 안 보인다.
        역할 관리 화면은 저장된 권한으로, 역할 수정 다이얼로그는 아직 저장하지 않은 선택으로 부른다(저장해야 확인할 수 있으면 미리보기가 아니다).
    -->
    <div>
        <div class="flex flex-col" :class="compact ? 'gap-3' : 'gap-5'">
            <section v-for="group in groups.list" :key="group.key" :aria-labelledby="`${idBase}-${group.key}`">
                <h3 :id="`${idBase}-${group.key}`" class="mb-1.5 flex items-center gap-1.5 text-xs font-semibold">
                    <i :class="group.icon" class="text-[11px]" aria-hidden="true" />
                    {{ group.label }}
                </h3>
                <ul class="m-0 flex list-none flex-col p-0">
                    <li v-for="item in group.items" :key="item.key" class="flex min-h-7 items-center gap-2 text-xs leading-relaxed">
                        <i v-if="item.icon" :class="item.icon" class="shrink-0 text-[11px] text-muted-color" aria-hidden="true" />
                        <span class="truncate" :class="item.permission ? 'text-primary' : 'text-muted-color'">{{ item.label }}</span>
                        <span v-if="item.permission" v-tooltip.top="t('role.menuNeeds', { permission: item.permission })" class="ml-auto inline-flex shrink-0 items-center" role="img" :aria-label="t('role.menuNeeds', { permission: item.permission })">
                            <i class="pi pi-key text-[10px] text-primary" aria-hidden="true" />
                        </span>
                    </li>
                </ul>
            </section>
            <!-- 권한이 걸린 메뉴가 하나도 안 남았다면 이유를 말해 준다 -->
            <p v-if="!hasGated" class="text-xs leading-relaxed text-muted-color">
                <strong>{{ t('role.menuNone') }}</strong> {{ t('role.menuNoneHint') }}
            </p>
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilAdminMenu from '~/utils/util-admin-menu';

interface Props {
    /** 이 권한 코드들만 가졌다고 보고 계산한다 */
    permissions: string[];
    compact?: boolean;
}

const props = withDefaults(defineProps<Props>(), { compact: false });

const { t } = useI18n();
const idBase = useId();

/** 보이는 메뉴를 묶음별로 — 낱개 항목(대시보드)은 「공통」 한 덩어리로 모은다 */
const groups = computed(() => {
    const owned = new Set(props.permissions);
    const nodes = UtilAdminMenu.visible((code) => owned.has(code));
    const out: { key: string; icon: string; label: string; items: { key: string; icon?: string; label: string; permission?: string }[] }[] = [];
    const common: { key: string; icon?: string; label: string; permission?: string }[] = [];
    for (const node of nodes) {
        if (UtilAdminMenu.isGroup(node)) {
            out.push({ key: node.key, icon: node.icon, label: t(node.labelKey), items: UtilAdminMenu.leaves(node.items).map((item) => ({ key: item.key, icon: item.icon, label: t(item.labelKey), permission: item.permission })) });
        } else {
            common.push({ key: node.key, icon: node.icon, label: t(node.labelKey), permission: node.permission });
        }
    }
    const gated = out.length > 0;
    if (common.length) out.unshift({ key: 'common', icon: 'pi pi-th-large', label: t('role.menuCommon'), items: common });
    return { list: out, gated };
});
const hasGated = computed(() => groups.value.gated);
</script>
