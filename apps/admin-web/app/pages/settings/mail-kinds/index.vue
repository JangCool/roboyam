<template>
    <!--
        메일 종류(목록, `/settings/mail-kinds`, 설정 > 메일) — 서버가 보내는 메일 종류를 묶음(시스템 메일 / 관리자 알림 · 영역)별로 보이고, 행을 누르면 종류 상세(`/settings/mail-kinds/[kind]`)로 간다.
        계약 `docs/features/message-template-contract.md` 「개정」 2. 표: 묶음 · 이름(보안 메일 딱지·코드·설명) · 언어별 쓰는 템플릿 · 특수한 상황 규칙 수 · 사용자 템플릿 수.
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

            <AppGadget v-else-if="!kinds.length" :title="t('mailTemplate.kindsTitle')">
                <EmptyState icon="pi pi-envelope" :title="t('mailTemplate.kindsEmptyTitle')" :description="t('mailTemplate.kindsEmptyBody')" />
            </AppGadget>

            <template v-else>
                <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                    <DataTable :value="rows" data-key="kind" row-hover scrollable :size="prefs.density === 'compact' ? 'small' : undefined" :row-class="() => 'cursor-pointer'" @row-click="onRowClick">
                        <Column :header="t('mailTemplate.colGroup')" style="min-width: 9rem">
                            <template #body="{ data }">
                                <span class="text-[12px] text-muted-color">{{ UtilMailTemplateMessages.groupLabel(t, data.group, data.area) }}</span>
                            </template>
                        </Column>
                        <Column :header="t('mailTemplate.colKind')" style="min-width: 16rem">
                            <template #body="{ data }">
                                <div class="flex min-w-0 flex-col gap-0.5">
                                    <div class="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
                                        <NuxtLink :to="`/settings/mail-kinds/${data.kind}`" class="text-[13px] font-medium hover:underline">{{ data.name }}</NuxtLink>
                                        <Tag v-if="data.security" v-tooltip.top="t('mailTemplate.securityTip')" :value="t('mailTemplate.securityTag')" icon="pi pi-shield" severity="warn" class="mail-template-tag text-[10px]!" />
                                    </div>
                                    <span class="block truncate text-[11px] text-muted-color">{{ data.description }}</span>
                                    <code class="text-[10px] text-muted-color">{{ data.kind }}</code>
                                </div>
                            </template>
                        </Column>
                        <Column :header="t('mailTemplate.colUsing')" style="min-width: 14rem">
                            <template #body="{ data }">
                                <ul class="m-0 flex list-none flex-col gap-0.5 p-0 text-[12px]">
                                    <li v-for="locale in LOCALES" :key="locale" class="flex min-w-0 items-center gap-1.5">
                                        <span class="w-14 shrink-0 text-[11px] text-muted-color">{{ UtilMailTemplateMessages.localeLabel(t, locale) }}</span>
                                        <span class="truncate" :class="data.selected[locale].builtin ? 'text-muted-color' : ''">{{ data.selected[locale].name }}</span>
                                    </li>
                                </ul>
                            </template>
                        </Column>
                        <Column :header="t('mailTemplate.colRules')" style="min-width: 6.5rem">
                            <template #body="{ data }">
                                <span v-if="!data.conditions.length" class="text-[12px] text-muted-color">{{ t('mailTemplate.noConditionsShort') }}</span>
                                <span v-else class="text-[13px] tabular-nums" :class="data.ruleCount ? '' : 'text-muted-color'">{{ t('mailTemplate.ruleCount', { count: data.ruleCount }) }}</span>
                            </template>
                        </Column>
                        <Column :header="t('mailTemplate.colUserTemplates')" style="min-width: 7rem">
                            <template #body="{ data }">
                                <span class="text-[13px] tabular-nums" :class="data.templateCount ? '' : 'text-muted-color'">{{ t('mailTemplate.userTemplateCount', { count: data.templateCount, max: MAIL_TEMPLATE_LIMIT }) }}</span>
                            </template>
                        </Column>
                    </DataTable>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('mailTemplate.kindsNote') }}</p>
            </template>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { MailKindView, MailLocale } from '~/composables/template/api/useMailTemplateApi.types';
import { MAIL_TEMPLATE_LIMIT } from '~/composables/template/api/useMailTemplateApi.types';
import UtilMailTemplateMessages from '~/utils/util-mail-template-messages';
import UtilNotification from '~/utils/util-notification';

const { t } = useI18n();
useSeoMeta({ title: () => t('mailTemplate.kindsTitle') });

const api = useMailTemplateApi();
const router = useRouter();
const toApiError = useApiErrorOf();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다
const { prefs } = useLayoutPrefs();

const LOCALES: readonly MailLocale[] = ['ko', 'en'];

const kinds = ref<MailKindView[] | null>(null);
const loading = ref(false);
const loadError = ref('');
/** 읽기 권한이 없다(403) — 오류가 아니라 안내다(`forms.md`) */
const denied = ref(false);

/** 묶음 차례 — 시스템 메일, 그다음 관리자 알림(영역 차례는 알림 화면과 같다). 묶음 안은 받은 차례 */
const rows = computed(() => {
    const list = kinds.value ?? [];
    const rank = (item: MailKindView) => {
        if (item.group === 'SYSTEM') return -1;
        const at = UtilNotification.AREA_ORDER.indexOf(item.area ?? '');
        return at < 0 ? UtilNotification.AREA_ORDER.length : at;
    };
    return list.map((item, index) => ({ item, index })).sort((a, b) => rank(a.item) - rank(b.item) || a.index - b.index).map(({ item }) => item);
});

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = '';
    try {
        kinds.value = await api.kinds();
    } catch (e) {
        const error = toApiError(e);
        if (error.status === 403) denied.value = true;
        else loadError.value = error.message;
    } finally {
        loading.value = false;
    }
}

/** 행을 누르면 상세로 — 줄 안의 링크를 누른 것은 그 동작만 한다 */
function onRowClick(event: DataTableRowClickEvent): void {
    const target = event.originalEvent.target as HTMLElement | null;
    if (target?.closest('button, a, input')) return;
    void router.push(`/settings/mail-kinds/${(event.data as MailKindView).kind}`);
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
</style>
