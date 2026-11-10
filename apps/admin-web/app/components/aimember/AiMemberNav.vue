<template>
    <!--
        AI 회원 네 화면(계정 · 검수 대기함 · 설정 · 활동 기록) 사이를 오가는 줄 — 제목 아래에 둔다.
        관리자 메뉴에 아직 등록하지 않아(서버 `AdminScreenRegistry`·권한이 필요 — 목업 단계) 화면끼리 이 줄로 잇는다. 메뉴에 묶음으로 올리면 지워도 된다.
        대기함 옆 숫자는 부르는 쪽이 준 대기 건수(모르면 그리지 않는다).
    -->
    <nav :aria-label="t('aiMember.nav.label')" class="flex flex-wrap gap-1 border-b border-(--line)">
        <NuxtLink
            v-for="item in items"
            :key="item.to"
            :to="item.to"
            class="-mb-px inline-flex items-center gap-1.5 border-b-2 px-3 py-2 text-sm"
            :class="item.active ? 'border-primary font-semibold text-primary' : 'border-transparent text-muted-color hover:text-color'"
            :aria-current="item.active ? 'page' : undefined"
        >
            <i :class="item.icon" class="text-xs" aria-hidden="true" />
            <span>{{ item.label }}</span>
            <Badge v-if="item.count" :value="item.count" severity="warn" size="small" />
        </NuxtLink>
    </nav>
</template>

<script setup lang="ts">
import UtilAiMember from '~/utils/util-ai-member';

interface Props {
    /** 검수 대기 건수 — 모르면 null */
    pending?: number | null;
}

const props = withDefaults(defineProps<Props>(), { pending: null });

const { t } = useI18n();
const route = useRoute();

const items = computed(() =>
    [
        { to: UtilAiMember.LIST_PATH, icon: 'pi pi-users', label: t('aiMember.nav.accounts'), count: 0 },
        { to: UtilAiMember.DRAFTS_PATH, icon: 'pi pi-inbox', label: t('aiMember.nav.drafts'), count: props.pending ?? 0 },
        { to: UtilAiMember.SETTINGS_PATH, icon: 'pi pi-cog', label: t('aiMember.nav.settings'), count: 0 },
        { to: UtilAiMember.LOGS_PATH, icon: 'pi pi-history', label: t('aiMember.nav.logs'), count: 0 },
    ].map((item) => ({ ...item, active: route.path === item.to })),
);
</script>
