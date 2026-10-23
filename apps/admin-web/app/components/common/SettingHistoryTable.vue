<template>
    <!--
        설정 변경 이력 표 — 소셜 로그인 설정(`/settings/social-login`)·메일 서버 설정(`/settings/mail`)이 같이 쓴다. 서버는 부르지 않고 쪽 넘김을 이벤트로 알린다(상태는 `useSettingHistory`).
        최신이 위(서버 순서 그대로), 한 쪽씩 받는다(`lazy` — 감사 로그 표와 같은 쪽 넘김). 줄마다: 시각 · 출처 딱지(설치/관리자) · 작업 · (소셜만) 공급자 · 운영자 · 바뀐 칸 「칸 이름: 전 → 후」.
        비밀 값(시크릿·비밀번호)은 값 없이 「입력함」·「바꿈」·「지움」만 보인다(`UtilSettingHistoryMessages`). 지워진 운영자는 서버가 준 대체 이름을 흐리게 보인다.
    -->
    <DataTable
        :value="items"
        data-key="id"
        lazy
        paginator
        :first="first"
        :rows="pageSize"
        :total-records="total"
        :rows-per-page-options="[10, 20, 50]"
        :loading="loading && loaded"
        :size="compact ? 'small' : undefined"
        :current-page-report-template="totalTemplate"
        paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport"
        @page="emit('page', $event)"
    >
        <template #empty>
            <div v-if="loading && !loaded" class="flex flex-col gap-2 py-2" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 3" :key="n" height="2.25rem" />
            </div>
            <ErrorState v-else-if="error" :title="t('settingHistory.loadFailed')" :description="error.message" retry :retrying="loading" @retry="emit('retry')" />
            <EmptyState v-else icon="pi pi-history" :title="t('settingHistory.emptyTitle')" :description="t('settingHistory.emptyHint')" />
        </template>

        <Column field="changedAt" :header="t('settingHistory.changedAt')" style="width: 11rem">
            <template #body="{ data }">
                <span class="text-[12px] tabular-nums">{{ time.full(data.changedAt) }}</span>
            </template>
        </Column>

        <Column :header="t('settingHistory.sourceHeader')" style="width: 9rem">
            <template #body="{ data }">
                <span class="inline-flex flex-wrap items-center gap-1.5">
                    <Tag class="setting-history-tag" :value="UtilSettingHistoryMessages.source(t, data.source)" :severity="data.source === 'INSTALL' ? 'info' : 'secondary'" />
                    <span class="text-[12px] text-muted-color">{{ UtilSettingHistoryMessages.action(t, data.action) }}</span>
                </span>
            </template>
        </Column>

        <Column v-if="showProvider" :header="t('settingHistory.provider')" style="width: 7rem">
            <template #body="{ data }">
                <span class="text-[13px]">{{ data.providerLabel ?? data.provider ?? '—' }}</span>
            </template>
        </Column>

        <Column :header="t('settingHistory.operator')" style="width: 10rem">
            <template #body="{ data }">
                <span class="text-[13px]" :class="data.operatorDeleted ? 'text-muted-color italic' : ''" :title="data.operatorId ?? undefined">{{ data.operatorName }}</span>
            </template>
        </Column>

        <Column :header="t('settingHistory.changes')">
            <template #body="{ data }">
                <ul v-if="data.changes.length" class="m-0 flex list-none flex-col gap-0.5 p-0">
                    <li v-for="change in data.changes" :key="change.field" class="text-[12px] leading-relaxed [overflow-wrap:anywhere]">
                        <span class="font-medium">{{ UtilSettingHistoryMessages.fieldLabel(t, te, change) }}</span>:
                        <span class="text-muted-color">{{ UtilSettingHistoryMessages.value(t, change.field, change.before) }}</span>
                        <span> → </span>
                        <span>{{ UtilSettingHistoryMessages.value(t, change.field, change.after) }}</span>
                    </li>
                </ul>
                <span v-else class="text-[12px] text-muted-color">—</span>
            </template>
        </Column>
    </DataTable>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { SettingHistoryItem } from '~/composables/common/useSettingHistory.types';
import UtilSettingHistoryMessages from '~/utils/util-setting-history-messages';

interface Props {
    items: SettingHistoryItem[];
    total: number;
    /** 첫 줄의 순번(PrimeVue `first`) */
    first: number;
    pageSize: number;
    loading?: boolean;
    /** 한 번이라도 받았는가 — 처음에는 뼈대만 그린다 */
    loaded?: boolean;
    error?: ApiError | null;
    /** 공급자 열을 보일까(소셜 로그인 이력) */
    showProvider?: boolean;
    /** 조밀하게(`prefs.density === 'compact'`) */
    compact?: boolean;
}

withDefaults(defineProps<Props>(), { loading: false, loaded: false, error: null, showProvider: false, compact: false });

const emit = defineEmits<{ page: [event: { first: number; rows: number }]; retry: [] }>();

const { t, te } = useI18n();
const time = useDisplayTime();

/** 쪽 안내 — `{totalRecords}` 는 PrimeVue 가 채우는 자리라 `{count}` 를 그 이름으로 바꿔 넘긴다 */
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.setting-history-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
