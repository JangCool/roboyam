<template>
    <!--
        메일 종류(목록, `/settings/mail-kinds`, 설정 > 메일) — 서버가 보내는 메일 종류. 계약 `docs/features/message-template-contract.md` 「개정」 2 · 「개정 2」(받는 사람 구분).
        맨 위 「회원 메일 / 운영자 메일」 전환(주소 `?audience=MEMBER|OPERATOR`, 기본 회원) → 그 아래 묶음(계정·보안 메일 / 알림 메일)마다 표 하나. 행을 누르면 종류 상세(`/settings/mail-kinds/[kind]`).
        표: 이름(보안 메일 딱지·설명·코드, 관리자 알림은 영역) · 언어별 쓰는 템플릿 · 특수한 상황 규칙 수 · 사용자 템플릿 수. 알림 메일은 영역 차례(알림 화면과 같다).
        「준비 중」 줄(목업 전용 — `mockPlannedKinds`): 앞으로 생길 회원 메일을 흐리게 보이고 누를 수 없다. 서버 단계에서는 실제 있는 종류만 보인다(없는 기능은 감춘다).
        이 화면은 보기만 한다(바꾸기는 상세 화면). 권한: 화면 `template.read`(메뉴가 거른다) — 403 이면 권한 없음 안내.
        ⚠ MOCK: API 는 메모리 목업이다(`useMailTemplateApi` · `?mock=…` 스위치는 `composables/template/api/mock/mail-template-mock.ts` 머리말).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('mailTemplate.kindsTitle')" :subtitle="t('mailTemplate.kindsSubtitle')" />

        <Message v-if="denied" severity="info" :closable="false" size="small">{{ t('mailTemplate.denied') }}</Message>

        <template v-else>
            <AppGadget v-if="loading && !kinds" :title="t('mailTemplate.kindsTitle')">
                <div class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                    <Skeleton v-for="n in 6" :key="n" height="2.75rem" />
                </div>
            </AppGadget>

            <AppGadget v-else-if="!kinds" :title="t('mailTemplate.kindsTitle')">
                <ErrorState :title="t('mailTemplate.kindsLoadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
            </AppGadget>

            <AppGadget v-else-if="!kinds.length && !planned.length" :title="t('mailTemplate.kindsTitle')">
                <EmptyState icon="pi pi-envelope" :title="t('mailTemplate.kindsEmptyTitle')" :description="t('mailTemplate.kindsEmptyBody')" />
            </AppGadget>

            <template v-else>
                <!-- 받는 사람 전환 -->
                <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
                    <SelectButton v-model="audience" :options="audienceOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('mailTemplate.audienceLabel')" />
                    <p class="m-0 text-xs text-muted-color">{{ audienceHint }}</p>
                </div>

                <!-- 묶음마다 표 -->
                <section v-for="group in groups" :key="group.group" class="flex min-w-0 flex-col gap-2" :aria-labelledby="`${sectionId}-${group.group}`">
                    <div class="flex items-baseline gap-2">
                        <h2 :id="`${sectionId}-${group.group}`" class="m-0 text-sm font-medium">{{ UtilMailTemplateMessages.groupLabel(t, group.group, null) }}</h2>
                        <span class="text-xs text-muted-color tabular-nums">{{ t('mailTemplate.groupCount', { count: group.realCount }) }}</span>
                    </div>
                    <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                        <DataTable :value="group.rows" data-key="kind" row-hover scrollable :size="prefs.density === 'compact' ? 'small' : undefined" :row-class="rowClass" @row-click="onRowClick">
                            <template #empty>
                                <p class="m-0 py-2 text-center text-xs text-muted-color">{{ t('mailTemplate.groupEmpty') }}</p>
                            </template>
                            <Column :header="t('mailTemplate.colKind')" style="min-width: 18rem">
                                <template #body="{ data }">
                                    <div class="flex min-w-0 flex-col gap-0.5">
                                        <div class="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
                                            <NuxtLink v-if="data.view" :to="`/settings/mail-kinds/${data.kind}`" class="text-[13px] font-medium hover:underline">{{ data.name }}</NuxtLink>
                                            <span v-else class="text-[13px] font-medium">{{ data.name }}</span>
                                            <Tag v-if="data.security" v-tooltip.top="t('mailTemplate.securityTip')" :value="t('mailTemplate.securityTag')" icon="pi pi-shield" severity="warn" class="mail-template-tag text-[10px]!" />
                                            <Tag v-if="!data.view" v-tooltip.top="t('mailTemplate.plannedTip')" :value="t('mailTemplate.plannedTag')" icon="pi pi-clock" severity="secondary" class="mail-template-tag text-[10px]!" />
                                        </div>
                                        <span class="block truncate text-[11px] text-muted-color">{{ data.description }}</span>
                                        <span class="flex flex-wrap items-center gap-x-2 text-[10px] text-muted-color">
                                            <code>{{ data.kind }}</code>
                                            <span v-if="data.area">{{ UtilNotificationMessages.areaLabel(t, data.area) }}</span>
                                        </span>
                                    </div>
                                </template>
                            </Column>
                            <Column :header="t('mailTemplate.colUsing')" style="min-width: 14rem">
                                <template #body="{ data }">
                                    <ul v-if="data.view" class="m-0 flex list-none flex-col gap-0.5 p-0 text-[12px]">
                                        <li v-for="locale in LOCALES" :key="locale" class="flex min-w-0 items-center gap-1.5">
                                            <span class="w-14 shrink-0 text-[11px] text-muted-color">{{ UtilMailTemplateMessages.localeLabel(t, locale) }}</span>
                                            <span class="truncate" :class="data.view.selected[locale].builtin ? 'text-muted-color' : ''">{{ data.view.selected[locale].name }}</span>
                                        </li>
                                    </ul>
                                    <span v-else class="text-[12px] text-muted-color" aria-hidden="true">—</span>
                                </template>
                            </Column>
                            <Column :header="t('mailTemplate.colRules')" style="min-width: 6.5rem">
                                <template #body="{ data }">
                                    <span v-if="!data.view" class="text-[12px] text-muted-color" aria-hidden="true">—</span>
                                    <span v-else-if="!data.view.conditions.length" class="text-[12px] text-muted-color">{{ t('mailTemplate.noConditionsShort') }}</span>
                                    <span v-else class="text-[13px] tabular-nums" :class="data.view.ruleCount ? '' : 'text-muted-color'">{{ t('mailTemplate.ruleCount', { count: data.view.ruleCount }) }}</span>
                                </template>
                            </Column>
                            <Column :header="t('mailTemplate.colUserTemplates')" style="min-width: 7rem">
                                <template #body="{ data }">
                                    <span v-if="!data.view" class="text-[12px] text-muted-color" aria-hidden="true">—</span>
                                    <span v-else class="text-[13px] tabular-nums" :class="data.view.templateCount ? '' : 'text-muted-color'">{{ t('mailTemplate.userTemplateCount', { count: data.view.templateCount, max: MAIL_TEMPLATE_LIMIT }) }}</span>
                                </template>
                            </Column>
                        </DataTable>
                    </div>
                </section>

                <div class="flex flex-col gap-1">
                    <p class="m-0 text-xs text-muted-color">{{ t('mailTemplate.kindsNote') }}</p>
                    <!-- MOCK: 「준비 중」 줄은 목업 전용이다 -->
                    <p v-if="hasPlanned" class="m-0 text-xs text-muted-color">{{ t('mailTemplate.kindsPlannedNote') }}</p>
                </div>
            </template>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { MockPlannedMailKind } from '~/composables/template/api/mock/mail-template-mock';
import { mockPlannedKinds } from '~/composables/template/api/mock/mail-template-mock';
import type { MailAudience, MailKindGroup, MailKindView, MailLocale } from '~/composables/template/api/useMailTemplateApi.types';
import { MAIL_AUDIENCES, MAIL_KIND_GROUPS, MAIL_TEMPLATE_LIMIT } from '~/composables/template/api/useMailTemplateApi.types';
import UtilMailTemplate from '~/utils/util-mail-template';
import UtilMailTemplateMessages from '~/utils/util-mail-template-messages';
import UtilNotification from '~/utils/util-notification';
import UtilNotificationMessages from '~/utils/util-notification-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('mailTemplate.kindsTitle') });

const api = useMailTemplateApi();
const route = useRoute();
const router = useRouter();
const toApiError = useApiErrorOf();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다
const { prefs } = useLayoutPrefs();

const LOCALES: readonly MailLocale[] = ['ko', 'en'];
const sectionId = useId();

const kinds = ref<MailKindView[] | null>(null);
/** MOCK: 「준비 중」 종류(목업 전용 — 서버 단계에서 지운다) */
const planned = ref<MockPlannedMailKind[]>([]);
const loading = ref(false);
const loadError = ref('');
/** 읽기 권한이 없다(403) — 오류가 아니라 안내다(`forms.md`) */
const denied = ref(false);

/* ── 받는 사람 전환 — 주소 `?audience=` 에 남긴다(기본 회원) ── */

const audienceOf = (value: unknown): MailAudience => (value === 'OPERATOR' ? 'OPERATOR' : 'MEMBER');
const audience = ref<MailAudience>(audienceOf(route.query.audience));
watch(audience, (value) => {
    if (audienceOf(route.query.audience) === value && route.query.audience) return;
    void router.replace({ query: { ...route.query, audience: value } });
});
// 뒤로 가기·링크로 주소가 바뀌면 따라간다
watch(
    () => route.query.audience,
    (value) => (audience.value = audienceOf(value)),
);

const audienceOptions = computed(() => MAIL_AUDIENCES.map((value) => ({ value, label: UtilMailTemplateMessages.audienceMailLabel(t, value) })));
/** 받는 사람 안내 — 그 받는 사람만의 공통 변수(회원 `siteUrl` · 운영자 `adminUrl`) */
const audienceHint = computed(() =>
    audience.value === 'OPERATOR' ? t('mailTemplate.audienceOperatorHint', { token: UtilMailTemplate.token('adminUrl') }) : t('mailTemplate.audienceMemberHint', { token: UtilMailTemplate.token('siteUrl') }),
);

/* ── 묶음별 줄 ── */

/** 표 한 줄 — 실제 종류(`view`) 또는 「준비 중」(view 없음) */
interface KindRow {
    kind: string;
    name: string;
    description: string;
    area: string | null;
    security: boolean;
    view: MailKindView | null;
}

/** 알림 메일은 영역 차례(알림 화면과 같다), 같은 영역 안은 받은 차례. 「준비 중」은 묶음 맨 뒤 */
const groups = computed(() => {
    const rank = (item: MailKindView) => {
        if (!item.area) return -1;
        const at = UtilNotification.AREA_ORDER.indexOf(item.area);
        return at < 0 ? UtilNotification.AREA_ORDER.length : at;
    };
    return MAIL_KIND_GROUPS.map((group) => {
        const real = (kinds.value ?? [])
            .map((item, index) => ({ item, index }))
            .filter(({ item }) => item.audience === audience.value && item.group === group)
            .sort((a, b) => rank(a.item) - rank(b.item) || a.index - b.index)
            .map(({ item }): KindRow => ({ kind: item.kind, name: item.name, description: item.description, area: item.area, security: item.security, view: item }));
        const soon = planned.value.filter((item) => item.audience === audience.value && item.group === group).map((item): KindRow => ({ kind: item.kind, name: item.name, description: item.description, area: null, security: false, view: null }));
        return { group: group as MailKindGroup, realCount: real.length, rows: [...real, ...soon] };
    });
});
const hasPlanned = computed(() => groups.value.some((group) => group.rows.some((row) => !row.view)));

const rowClass = (row: KindRow) => (row.view ? 'cursor-pointer' : 'mail-kind-planned');

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = '';
    try {
        kinds.value = await api.kinds();
        // MOCK: 「준비 중」 줄 — 서버가 붙으면 이 줄과 `planned` 를 지운다
        planned.value = mockPlannedKinds();
    } catch (e) {
        const error = toApiError(e);
        if (error.status === 403) denied.value = true;
        else loadError.value = error.message;
    } finally {
        loading.value = false;
    }
}

/** 행을 누르면 상세로 — 줄 안의 링크를 누른 것은 그 동작만 한다. 「준비 중」 줄은 누를 수 없다 */
function onRowClick(event: DataTableRowClickEvent): void {
    const row = event.data as KindRow;
    if (!row.view) return;
    const target = event.originalEvent.target as HTMLElement | null;
    if (target?.closest('button, a, input')) return;
    void router.push(`/settings/mail-kinds/${row.kind}`);
}

onMounted(load);
</script>

<style scoped>
/* 뱃지 글자 상하 중앙 + 한글 글꼴 보정(components.md 2-4) */
.mail-template-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
}
.mail-template-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
/* 「준비 중」 줄 — 흐리게, 누를 수 없음 */
:deep(.mail-kind-planned) {
    opacity: 0.55;
    cursor: default;
}
</style>
