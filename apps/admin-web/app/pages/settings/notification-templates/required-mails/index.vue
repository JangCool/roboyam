<template>
    <!--
        필수 안내 메일(목록, `/settings/notification-templates/required-mails`, 설정 > 알림·이벤트 > 알림 템플릿) — 이벤트가 아닌 꼭 가는 메일(회원 이메일 인증 · 운영자 초대 · 임시 비밀번호 · 재설정 승인번호 · 메일 서버 시험).
        계약 `docs/features/message-template-contract.md` 「개정 3」 메뉴. 받는 사람이 끌 수 없고 채널은 이메일뿐이다. 개정 전 「메일 종류」의 계정·보안 메일 묶음을 옮겼다.
        받는 사람을 고르지 않는다 — 한 화면에 「운영자에게 가는 메일」 · 「회원에게 가는 메일」 구획(운영자 먼저)으로 나눠 보인다(종류가 적어 메뉴로 나누지 않는다).
        구획마다 표: 이름(보안 메일 · 끌 수 없음 딱지 · 설명 · 코드) · 언어별 쓰는 템플릿 · 특수 상황 규칙 수 · 사용자 템플릿 수.
        행을 누르면 상세(`/settings/notification-templates/required-mails/[kind]`). 이 화면은 보기만 한다. 권한: 화면 `template.read`(메뉴가 거른다) — 403 이면 권한 없음 안내.
        ⚠ MOCK: API 는 메모리 목업이다(`useNotificationTemplateApi` · `?mock=…` 스위치는 `composables/template/api/mock/notification-template-mock.ts` 머리말).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('notificationTemplate.requiredTitle')" :subtitle="t('notificationTemplate.requiredSubtitle')" />

        <Message v-if="denied" severity="info" :closable="false" size="small">{{ t('notificationTemplate.denied') }}</Message>

        <template v-else>
            <AppGadget v-if="loading && !targets" :title="t('notificationTemplate.requiredTitle')">
                <div class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                    <Skeleton v-for="n in 5" :key="n" height="2.75rem" />
                </div>
            </AppGadget>

            <AppGadget v-else-if="!targets" :title="t('notificationTemplate.requiredTitle')">
                <ErrorState :title="t('notificationTemplate.requiredLoadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
            </AppGadget>

            <AppGadget v-else-if="!targets.length" :title="t('notificationTemplate.requiredTitle')">
                <EmptyState icon="pi pi-envelope" :title="t('notificationTemplate.requiredEmptyTitle')" :description="t('notificationTemplate.requiredEmptyBody')" />
            </AppGadget>

            <template v-else>
                <!-- 받는 사람마다 구획 하나(운영자 먼저 — 메뉴 차례와 같다). 종류가 적어 메뉴로 나누지 않는다 -->
                <AppGadget v-for="section in sections" :key="section.audience" :title="section.title">
                    <template #title-extra>
                        <span class="text-xs tabular-nums text-muted-color">{{ section.items.length }}</span>
                    </template>
                    <EmptyState v-if="!section.items.length" icon="pi pi-envelope" :title="t('notificationTemplate.requiredSectionEmpty')" />
                    <DataTable v-else :value="section.items" data-key="code" row-hover scrollable :size="prefs.density === 'compact' ? 'small' : undefined" :row-class="() => 'cursor-pointer'" @row-click="onRowClick">
                        <Column :header="t('notificationTemplate.colRequiredMail')" style="min-width: 18rem">
                            <template #body="{ data }">
                                <div class="flex min-w-0 flex-col gap-0.5">
                                    <div class="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
                                        <NuxtLink :to="detailPath(data)" class="text-[13px] font-medium hover:underline">{{ data.name }}</NuxtLink>
                                        <Tag v-if="data.security" v-tooltip.top="t('notificationTemplate.securityTip')" :value="t('notificationTemplate.securityTag')" icon="pi pi-shield" severity="warn" class="mail-template-tag text-[10px]!" />
                                        <Tag v-tooltip.top="t('notificationTemplate.alwaysSentTip')" :value="t('notificationTemplate.alwaysSentTag')" icon="pi pi-lock" severity="secondary" class="mail-template-tag text-[10px]!" />
                                    </div>
                                    <span class="block truncate text-[11px] text-muted-color">{{ data.description }}</span>
                                    <code class="text-[10px] text-muted-color">{{ data.code }}</code>
                                </div>
                            </template>
                        </Column>
                        <Column :header="t('notificationTemplate.colUsing')" style="min-width: 14rem">
                            <template #body="{ data }">
                                <ul v-if="emailOf(data)" class="m-0 flex list-none flex-col gap-0.5 p-0 text-[12px]">
                                    <li v-for="locale in TEMPLATE_LOCALES" :key="locale" class="flex min-w-0 items-center gap-1.5">
                                        <span class="w-14 shrink-0 text-[11px] text-muted-color">{{ UtilNotificationTemplateMessages.localeLabel(t, locale) }}</span>
                                        <span class="truncate" :class="emailOf(data)?.selected[locale].builtin ? 'text-muted-color' : ''">{{ emailOf(data)?.selected[locale].name }}</span>
                                    </li>
                                </ul>
                            </template>
                        </Column>
                        <Column :header="t('notificationTemplate.colRules')" style="min-width: 6.5rem">
                            <template #body="{ data }">
                                <span v-if="!emailOf(data)?.conditions.length" class="text-[12px] text-muted-color">{{ t('notificationTemplate.noConditionsShort') }}</span>
                                <span v-else class="text-[13px] tabular-nums" :class="emailOf(data)?.ruleCount ? '' : 'text-muted-color'">{{ t('notificationTemplate.ruleCount', { count: emailOf(data)?.ruleCount ?? 0 }) }}</span>
                            </template>
                        </Column>
                        <Column :header="t('notificationTemplate.colUserTemplates')" style="min-width: 7rem">
                            <template #body="{ data }">
                                <span class="text-[13px] tabular-nums" :class="emailOf(data)?.templateCount ? '' : 'text-muted-color'">{{ t('notificationTemplate.userTemplateCount', { count: emailOf(data)?.templateCount ?? 0, max: TEMPLATE_LIMIT }) }}</span>
                            </template>
                        </Column>
                    </DataTable>
                </AppGadget>
                <p class="m-0 text-xs text-muted-color">{{ t('notificationTemplate.requiredNote') }}</p>
            </template>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { TemplateAudience, TemplateTargetView } from '~/composables/template/api/useNotificationTemplateApi.types';
import { TEMPLATE_LIMIT, TEMPLATE_LOCALES } from '~/composables/template/api/useNotificationTemplateApi.types';
import UtilNotificationTemplateMessages from '~/utils/util-notification-template-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('notificationTemplate.requiredTitle') });

const api = useNotificationTemplateApi();
const router = useRouter();
const toApiError = useApiErrorOf();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다
const { prefs } = useLayoutPrefs();

const targets = ref<TemplateTargetView[] | null>(null);
const loading = ref(false);
const loadError = ref('');
/** 읽기 권한이 없다(403) — 오류가 아니라 안내다(`forms.md`) */
const denied = ref(false);

/* ── 구획 — 받는 사람마다(운영자 먼저 — 메뉴 「운영자 템플릿 · 회원 템플릿」·이벤트와 같은 차례), 구획 안은 받은 차례 ── */

const SECTION_AUDIENCES: readonly TemplateAudience[] = ['OPERATOR', 'MEMBER'];
const sections = computed(() =>
    SECTION_AUDIENCES.map((audience) => ({
        audience,
        title: audience === 'MEMBER' ? t('notificationTemplate.requiredMemberSection') : t('notificationTemplate.requiredOperatorSection'),
        items: (targets.value ?? []).filter((item) => item.audience === audience),
    })),
);

/** 필수 안내 메일의 채널은 이메일 하나다 */
const emailOf = (item: TemplateTargetView) => item.channels.find((channel) => channel.channel === 'EMAIL');
const detailPath = (item: TemplateTargetView) => UtilNotificationTemplateMessages.targetPath(item);

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = '';
    try {
        targets.value = await api.targets({ type: 'REQUIRED_MAIL' });
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
    void router.push(detailPath(event.data as TemplateTargetView));
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
