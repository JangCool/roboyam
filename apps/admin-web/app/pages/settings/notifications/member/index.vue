<template>
    <!--
        회원 이벤트(`/settings/notifications/member`, 설정 > 알림·이벤트) — 공개 사이트 회원에게 가는 알림의 일. 계약 `docs/features/message-template-contract.md` 「개정 3」 이벤트 화면. **전부 목업**이다.
        통합 목록: 지금 실제로 일어나는 5종(`MemberNotificationType` — 댓글·답글·포인트 회수·글 숨김·댓글 숨김) + 게시판 기본 설정 「알림」에만 있는 5종(멘션·인기글·운영 처리·스텝 처리·신고 결과)은 「준비 중」(흐리게, 바꿀 수 없음).
        영역(콘텐츠 · 회원 · 안전 …)마다 구획 한 장 + 표. 줄마다: 사용 · 앱 알림 · 이메일 · 회원이 끌 수 있는지(계정·처리 알림은 코드가 잠금) · 쓰는 템플릿 요약(채널마다 한국어 쓸 템플릿) · 「상세」(`/settings/notifications/member/[code]`).
        스위치는 바꾸면 곧바로 저장한다(운영자 이벤트와 같은 방식 — 낙관적 갱신, 거절되면 그 줄만 되돌리고 서버 문구 토스트 8초).
        맨 위 안내: 게시판 기본 설정 › 알림의 이벤트별 칸은 서버 단계에서 이 화면으로 옮긴다(그 탭은 지금 건드리지 않는다 — 링크만).
        권한: 화면 `notification.read`(메뉴가 거른다) · 바꾸기 `notification.manage` · 쓰는 템플릿 칸은 `template.read` 가 있을 때만.
        ⚠ MOCK: `useMemberEventApi`(스위치 `?mock=member-events-error|member-events-save-error|member-events-denied`) · `useNotificationTemplateApi`.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('notification.memberEvents.title')" :subtitle="t('notification.memberEvents.subtitle')">
            <template #actions>
                <Button :label="t('notification.adminReload')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="loading" @click="load()" />
            </template>
        </PageTitle>

        <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('notification.memberEvents.readOnly') }}</Message>

        <Message severity="secondary" :closable="false" size="small">
            <span>{{ t('notification.memberEvents.boardNote') }}</span>
            <NuxtLink v-if="can('board.read')" to="/boards/defaults/notifications" class="ml-2 font-medium underline underline-offset-2">{{ t('notification.memberEvents.boardLink') }}</NuxtLink>
        </Message>

        <div v-if="loading && !events" class="flex flex-col gap-4" aria-busy="true">
            <span class="sr-only">{{ t('common.loading') }}</span>
            <div v-for="n in 3" :key="n" class="rounded-xl border border-(--line) p-5">
                <Skeleton width="30%" height="1rem" class="mb-4" />
                <Skeleton v-for="m in 3" :key="m" height="2.5rem" class="mb-2" />
            </div>
        </div>

        <ErrorState v-else-if="loadError" :title="t('notification.memberEvents.loadFailed')" :description="loadError" retry :retrying="loading" @retry="load()" />

        <EmptyState v-else-if="!events?.length" icon="pi pi-bell-slash" :title="t('notification.memberEvents.emptyTitle')" :description="t('notification.memberEvents.emptyBody')" />

        <template v-else>
            <Message severity="secondary" :closable="false" size="small">{{ t('notification.memberEvents.note') }}</Message>

            <AppGadget v-for="group in groups" :key="group.area" :title="UtilNotificationMessages.areaLabel(t, group.area)" :anchor="UtilNotification.areaAnchor(group.area)">
                <DataTable :value="group.items" data-key="code" :row-class="(row: MemberEventView) => (row.featureReady ? '' : 'opacity-60')" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable>
                    <Column :header="t('notification.colEvent')" style="min-width: 18rem">
                        <template #body="{ data }">
                            <div class="min-w-0">
                                <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                                    <span class="text-[13px] font-medium">{{ data.name }}</span>
                                    <NotificationFlagTag v-if="!data.featureReady" kind="notReady" />
                                </div>
                                <div class="mt-0.5 text-[11px] leading-relaxed text-muted-color">{{ data.description }}</div>
                                <div v-if="data.boardSettingKind" class="mt-0.5 text-[10px] text-muted-color">{{ t('notification.memberEvents.boardKind', { kind: t(`board.notifyKind.${data.boardSettingKind}`) }) }}</div>
                            </div>
                        </template>
                    </Column>

                    <Column :header="t('notification.colRecipient')" style="min-width: 11rem">
                        <template #body="{ data }">
                            <span class="text-[12px] leading-relaxed">{{ data.recipientText }}</span>
                        </template>
                    </Column>

                    <Column :header="t('notification.colEnabled')" style="width: 5.5rem">
                        <template #body="{ data }">
                            <span v-tooltip.top="lockOf(data)" class="inline-flex">
                                <ToggleSwitch :model-value="data.enabled" :disabled="lockOf(data) !== undefined || isSaving(data.code)" :aria-label="t('notification.enabledLabel', { name: data.name })" @update:model-value="(v: boolean) => change(data, { enabled: v })" />
                            </span>
                        </template>
                    </Column>

                    <Column :header="t('notification.memberEvents.colInApp')" style="width: 6rem">
                        <template #body="{ data }">
                            <span v-tooltip.top="lockOf(data)" class="inline-flex">
                                <ToggleSwitch :model-value="data.inApp" :disabled="lockOf(data) !== undefined || isSaving(data.code)" :aria-label="t('notification.memberEvents.inAppLabel', { name: data.name })" @update:model-value="(v: boolean) => change(data, { inApp: v })" />
                            </span>
                        </template>
                    </Column>

                    <Column :header="t('notification.memberEvents.colEmail')" style="width: 6rem">
                        <template #body="{ data }">
                            <span v-tooltip.top="lockOf(data)" class="inline-flex">
                                <ToggleSwitch :model-value="data.email" :disabled="lockOf(data) !== undefined || isSaving(data.code)" :aria-label="t('notification.memberEvents.emailLabel', { name: data.name })" @update:model-value="(v: boolean) => change(data, { email: v })" />
                            </span>
                        </template>
                    </Column>

                    <Column :header="t('notification.memberEvents.colMemberOff')" style="width: 8rem">
                        <template #body="{ data }">
                            <span v-tooltip.top="memberOffLock(data)" class="inline-flex">
                                <ToggleSwitch :model-value="data.memberCanTurnOff" :disabled="memberOffLock(data) !== undefined || isSaving(data.code)" :aria-label="t('notification.memberEvents.memberOffLabel', { name: data.name })" @update:model-value="(v: boolean) => change(data, { memberCanTurnOff: v })" />
                            </span>
                        </template>
                    </Column>

                    <!-- 쓰는 템플릿(목업) — `template.read` 가 없거나 못 받으면 칸을 감춘다. 준비 중 이벤트는 템플릿이 없다(「—」) -->
                    <Column v-if="templateTargets" :header="t('notification.colTemplates')" style="min-width: 14rem">
                        <template #body="{ data }">
                            <TemplateUsageSummary :channels="templateTargets.get(data.code)?.channels ?? null" />
                        </template>
                    </Column>

                    <Column :header="t('notification.colSent')" style="width: 7rem">
                        <template #body="{ data }">
                            <span v-if="data.featureReady" class="text-[12px] tabular-nums">{{ t('notification.sentCount', { n: data.sentLast30Days.toLocaleString() }) }}</span>
                            <span v-else class="text-[12px] text-muted-color" aria-hidden="true">—</span>
                        </template>
                    </Column>

                    <Column :header="t('notification.detail')" style="width: 5.5rem">
                        <template #body="{ data }">
                            <NuxtLink :to="`/settings/notifications/member/${encodeURIComponent(data.code)}`" class="inline-flex items-center gap-1 text-[12px] text-primary hover:underline" :aria-label="t('notification.detailLabel', { name: data.name })">
                                {{ t('notification.detail') }}
                                <i class="pi pi-angle-right text-[10px]" aria-hidden="true" />
                            </NuxtLink>
                        </template>
                    </Column>
                </DataTable>
            </AppGadget>

            <div class="flex flex-col gap-1">
                <p v-if="templateTargets" class="m-0 text-xs text-muted-color">{{ t('notification.templatesNote') }}</p>
                <!-- MOCK: 「준비 중」 줄은 화면 확인용이다(서버 단계에서는 실제로 일어나는 이벤트와 서버가 준 것만 보인다) -->
                <p v-if="hasPlanned" class="m-0 text-xs text-muted-color">{{ t('notification.memberEvents.plannedNote') }}</p>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { MemberEventUpdateRequest, MemberEventView } from '~/composables/notification/api/useMemberEventApi.types';
import { mockTemplateCan } from '~/composables/template/api/mock/notification-template-mock';
import type { TemplateTargetView } from '~/composables/template/api/useNotificationTemplateApi.types';
import UtilNotification from '~/utils/util-notification';
import UtilNotificationMessages from '~/utils/util-notification-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('notification.memberEvents.title') });

const api = useMemberEventApi();
const templateApi = useNotificationTemplateApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const { can } = useAdminPermission();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다
const { prefs } = useLayoutPrefs();
// MOCK: 템플릿 읽기 권한은 목업 스위치(`?mock=template-denied`)로 흉내 낸다 — 서버가 붙으면 `can` 으로 바꾼다
const templateCan = mockTemplateCan(can);

const canManage = computed(() => can('notification.manage'));

/** `undefined` = 아직 안 받았다 · 배열 = 받았다 */
const events = ref<MemberEventView[] | undefined>(undefined);
const loading = ref(false);
const loadError = ref('');
const saving = ref<string[]>([]);
const isSaving = (code: string) => saving.value.includes(code);

const groups = computed(() => UtilNotification.groupByArea(events.value ?? []));
const hasPlanned = computed(() => (events.value ?? []).some((event) => !event.featureReady));

/** 스위치가 잠긴 까닭 — 권한이 먼저. 잠기지 않았으면 undefined */
function lockOf(event: MemberEventView): string | undefined {
    if (!canManage.value) return t('notification.noManageReason');
    if (!event.featureReady) return t('notification.notReadyHint');
    return undefined;
}
/** 「회원이 끌 수 있음」 — 계정·처리 알림은 코드가 잠근다 */
function memberOffLock(event: MemberEventView): string | undefined {
    return lockOf(event) ?? (event.lockReason ? t(`notification.memberEvents.lock${event.lockReason}`) : undefined);
}

/* ── 쓰는 템플릿 요약(목업) ── */

const templateTargets = ref<Map<string, TemplateTargetView> | null>(null);

async function loadTemplates(): Promise<void> {
    if (!templateCan('template.read')) {
        templateTargets.value = null;
        return;
    }
    try {
        const list = await templateApi.targets({ type: 'EVENT', audience: 'MEMBER' });
        templateTargets.value = new Map(list.map((item) => [item.code, item]));
    } catch {
        templateTargets.value = null;
    }
}

async function load(): Promise<void> {
    if (loading.value) return;
    loading.value = true;
    loadError.value = '';
    try {
        const [list] = await Promise.all([api.list(), loadTemplates()]);
        events.value = list;
    } catch (e) {
        loadError.value = toApiError(e).message;
        events.value = undefined;
    } finally {
        loading.value = false;
    }
}

function replace(code: string, next: MemberEventView): void {
    events.value = events.value?.map((event) => (event.code === code ? next : event));
}

/** 한 칸을 바꾸고 곧바로 저장한다 — 낙관적 갱신, 거절되면 그 줄만 되돌리고 서버 문구(칸 문구가 있으면 그것)를 토스트로 */
async function change(event: MemberEventView, patch: Partial<MemberEventUpdateRequest>): Promise<void> {
    if (isSaving(event.code)) return;
    const next: MemberEventUpdateRequest = { enabled: event.enabled, inApp: event.inApp, email: event.email, memberCanTurnOff: event.memberCanTurnOff, ...patch };
    replace(event.code, { ...event, ...next });
    saving.value = [...saving.value, event.code];
    try {
        replace(event.code, await api.update(event.code, next));
        toast.success(t('notification.memberEvents.saved', { name: event.name }));
    } catch (e) {
        replace(event.code, event);
        const failure = toApiError(e);
        toast.error(failure.fields?.memberCanTurnOff ?? failure.message, 8000);
    } finally {
        saving.value = saving.value.filter((code) => code !== event.code);
    }
}

onMounted(() => void load());
</script>
