<template>
    <!--
        소모임 설정(`/group-settings`) — community 관리자 앱 `group-settings.vue` 를 옮겼다. 소모임에 걸리는 사이트 전체 설정이 모이는 자리이고 구획은 넷이다(구획마다 따로 저장 — `forms.md` 2-4):
          ① 개설 규칙(개설 방식·최소 등급·1인 상한) ② 매니저(기능 켜기·기본 인원·최대 인원) ③ 공개 범위(기능 켜기·공개 요청 규칙 다섯) ④ 운영 한도(재신청·정원·지금 뜨는 소모임·일정 여덟).
        구획의 상태·저장은 `Group*RuleSection` 이 각자 한다 — 이 화면은 틀·분류 안내·**변경 이력 서랍**(제목 옆 단추, `components.md` 2-5)·**이탈 가드**(구획들의 바뀐 칸 수의 합)만 맡는다.
        이력은 서랍을 처음 열 때 받고, 저장한 뒤에는 이미 받은 적이 있을 때만 첫 쪽부터 다시 받는다. 구획 거르기가 있다.
        권한: 화면 `group.read` · 저장 `group.settings`(community 는 최고 관리자 전용 — 로보얌은 권한 코드로 위임한다. 없으면 입력이 잠기고 저장 줄이 없다).
        community 와 다른 점: 「소모임의 이름」 구획(용어 설정 `GROUPS` — 로보얌에 용어 설정이 없다)은 옮기지 않았다. 변경 이력(설정 이력 API)은 로보얌에서 더했다.
        서버 호출은 `useGroupSettingsApi`(구획 넷 + 변경 이력).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('group.settingsTitle')" :subtitle="t('group.settingsSubtitle')">
            <template #actions>
                <Button :label="t('settingHistory.open')" icon="pi pi-history" severity="secondary" outlined size="small" @click="openHistory" />
            </template>
        </PageTitle>

        <Message v-if="!canEdit" severity="info" :closable="false" size="small">{{ t('group.settingsReadOnly') }}</Message>

        <p class="m-0 text-xs text-muted-color">
            {{ t('group.settingsCategoryNote') }}
            <NuxtLink :to="UtilGroup.CATEGORIES_PATH" class="font-medium whitespace-nowrap text-primary hover:underline">{{ t('group.categoriesLink') }} ›</NuxtLink>
        </p>

        <GroupCreationRuleSection :can-edit="canEdit" @changes="(n) => (changes.creation = n)" @saved="onSaved" />
        <GroupManagerRuleSection :can-edit="canEdit" @changes="(n) => (changes.manager = n)" @saved="onSaved" />
        <GroupVisibilityRuleSection :can-edit="canEdit" @changes="(n) => (changes.visibility = n)" @saved="onSaved" />
        <GroupPolicyRuleSection :can-edit="canEdit" @changes="(n) => (changes.policy = n)" @saved="onSaved" />

        <!-- 변경 이력 — 제목 옆 단추가 오른쪽 서랍으로 연다(최신이 위, 구획 거르기) -->
        <SettingHistoryDrawer v-model:visible="historyOpen" :hint="t('group.historyHint')">
            <template #filters>
                <Select v-model="sectionFilter" :options="sectionOptions" option-label="label" option-value="value" :aria-label="t('group.historySectionLabel')" class="w-44" @change="history.reloadFirst()" />
            </template>
            <SettingHistoryTable
                :items="history.rows.value"
                :total="history.total.value"
                :first="history.first.value"
                :page-size="history.size.value"
                :loading="history.loading.value"
                :loaded="history.loaded.value"
                :error="history.error.value"
                :compact="prefs.density === 'compact'"
                @page="history.onPage"
                @retry="history.load"
            />
        </SettingHistoryDrawer>
    </div>
</template>

<script setup lang="ts">
import type { GroupSettingSection } from '~/composables/group/api/useGroupSettingsApi.types';
import UtilGroup from '~/utils/util-group';

const SECTIONS: readonly GroupSettingSection[] = ['CREATION', 'MANAGER', 'VISIBILITY', 'POLICY'];

const { t } = useI18n();
useSeoMeta({ title: () => t('group.settingsTitle') });

const api = useGroupSettingsApi();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const { prefs } = useLayoutPrefs();

/** 저장할 수 있나(정의: util-permission-catalog.ts) */
const canEdit = computed(() => can('group.settings'));

/** 구획마다 저장하지 않은 칸 수 — 이탈 가드가 합을 본다 */
const changes = reactive({ creation: 0, manager: 0, visibility: 0, policy: 0 });
const unsaved = computed(() => changes.creation + changes.manager + changes.visibility + changes.policy);

/* ── 변경 이력 ── */

const historyOpen = ref(false);
const sectionFilter = ref<GroupSettingSection | null>(null);
const sectionOptions = computed(() => [{ value: null, label: t('group.historySectionAll') }, ...SECTIONS.map((value) => ({ value, label: t(`group.historySection.${value}`) }))]);
const history = useSettingHistory((page, size) => api.history({ section: sectionFilter.value, page, size }));

/** 서랍을 처음 열 때 받는다 */
function openHistory(): void {
    historyOpen.value = true;
    if (!history.loaded.value && !history.loading.value) void history.load();
}

/** 구획을 저장했다 — 이력을 이미 받은 적이 있을 때만 첫 쪽부터 다시 */
function onSaved(): void {
    if (history.loaded.value) void history.reloadFirst();
}

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => {
    if (unsaved.value === 0) return true;
    return confirm({ title: t('group.leaveTitle'), message: t('group.leaveBody', { n: unsaved.value }), confirmLabel: t('group.leaveConfirm'), cancelLabel: t('group.leaveCancel'), tone: 'danger' });
});
</script>
