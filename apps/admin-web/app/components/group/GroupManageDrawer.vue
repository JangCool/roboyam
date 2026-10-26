<template>
    <!--
        소모임 상세 서랍(community `GroupManageDrawer`) — 목록의 줄을 누르면 연다. 열리는 쪽은 설정의 「서랍 위치」(`prefs.drawerPosition`)를 따른다(회원 상세 서랍과 같다).
        구획 넷: ① 기본 정보 ② 사람(모임장·매니저(읽기 전용)·멤버·모임장이 가진 소모임) ③ 활동 ④ 운영 조치. 앞의 셋은 읽기 전용이고 ④ 만 조치(비활성화·다시 열기 — 부르는 쪽이 대화상자를 연다)를 한다.
        머리(이름·상태)는 목록 줄(`summary`)로 먼저 그리고, 상세(`detail`)는 부르는 쪽이 받아 내려 준다 — 받는 동안 뼈대, 못 받으면 서랍 안 오류(없는 소모임 404 는 다시 시도 없이 안내).
        코드 값(ACTIVE·OPEN·APPROVAL …)은 화면에 나오지 않는다 — 이름으로 푼다. 시각은 `useDisplayTime()` 으로만 그린다. 서버를 부르지 않는다(`components.md`).
        신고(`report.read` 일 때만 링크): 활동의 신고 수 옆 「신고 보기 ›」 → 신고 관리 `/reports?groupId={소모임 ID}&status=ALL`(그 소모임 사건 — 서버 `groupId` 거르기),
          비활성 기록의 사건 번호 → `/reports?report={사건 ID}&status=ALL`(그 사건 서랍이 열린다). 권한이 없으면 번호만 보인다.
        community 와 다른 점: 「공개 화면 보기」(관리자 앱이 공개 사이트 주소를 모른다)·「회원 조회 ›」(회원 관리에 주소로 여는 서랍이 없다) 링크를 뺐다.
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '46rem', maxWidth: '100vw' }">
        <template #header>
            <div v-if="detail || summary" class="flex min-w-0 flex-1 flex-col gap-1">
                <div class="flex min-w-0 flex-wrap items-center gap-2">
                    <h2 :id="titleId" class="min-w-0 truncate text-base font-semibold">{{ detail?.name ?? summary?.name }}</h2>
                    <GroupTag :value="t(`group.status.${status}`)" :severity="status === 'INACTIVE' ? 'danger' : 'success'" />
                </div>
                <p class="text-[11px] text-muted-color">{{ t('group.drawerNote') }}</p>
            </div>
        </template>

        <ErrorState v-if="error" :title="t('group.detailLoadFailed')" :description="error.status === 404 ? t('group.detailNotFound') : error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />

        <div v-else-if="!detail" class="flex flex-col gap-3 py-2" role="status" :aria-label="t('common.loading')" aria-busy="true">
            <Skeleton width="40%" height="1.25rem" />
            <Skeleton v-for="n in 6" :key="n" height="1rem" :width="n % 2 ? '85%' : '65%'" />
        </div>

        <div v-else class="flex min-w-0 flex-col gap-5">
            <!-- 비활성 띠 — 언제·무엇 때문에 비활성이 됐는지 한 줄 -->
            <Message v-if="inactive" severity="error" :closable="false" size="small">{{ bandText }}</Message>

            <!-- ① 기본 정보 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-basic`">
                <h3 :id="`${titleId}-basic`" class="text-sm font-semibold">{{ t('group.sec.basic') }}</h3>
                <p class="m-0 text-xs text-muted-color">{{ t('group.basicHelp') }}</p>
                <dl class="m-0 flex flex-col">
                    <div :class="ROW">
                        <dt :class="DT">{{ t('group.field.description') }}</dt>
                        <dd :class="DD" class="whitespace-pre-line">{{ detail.description || '—' }}</dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('group.field.category') }}</dt>
                        <dd :class="DD"><span :class="detail.category ? '' : 'text-muted-color'">{{ detail.category?.name ?? t('group.categoryNone') }}</span></dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('group.field.board') }}</dt>
                        <dd :class="DD">
                            {{ detail.board.name }}
                            <NuxtLink v-if="canBoard" :to="`/boards/${encodeURIComponent(detail.board.slug)}`" class="ml-1 whitespace-nowrap text-primary hover:underline">{{ t('group.boardSettingsLink') }} ›</NuxtLink>
                        </dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('group.field.visibility') }}</dt>
                        <dd :class="DD">
                            {{ t(`group.visibility.${detail.visibility}`) }}
                            <small class="block text-xs text-muted-color">{{ visibilityChangedText }}</small>
                        </dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('group.field.join') }}</dt>
                        <dd :class="DD">{{ UtilGroupMessages.joinLine(t, detail.joinType, detail.recruitState) }}</dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('group.field.capacity') }}</dt>
                        <dd :class="DD">{{ t('group.capacityLine', { capacity: detail.capacity, members: detail.memberCount }) }}</dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('group.field.offline') }}</dt>
                        <dd :class="DD">{{ detail.offline ? t('common.yes') : t('common.no') }}</dd>
                    </div>
                    <div v-if="detail.stockNotice" :class="ROW">
                        <dt :class="DT">{{ t('group.field.stock') }}</dt>
                        <dd :class="DD">{{ t('group.stockAgreed') }}</dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('group.field.chat') }}</dt>
                        <dd :class="DD">{{ UtilGroupMessages.chatLine(t, detail.chat) }}</dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('group.field.opened') }}</dt>
                        <dd :class="DD" :title="time.full(detail.openedAt)">{{ openedText }}</dd>
                    </div>
                </dl>
            </section>

            <!-- ② 사람 — 매니저 줄은 읽기 전용(지정·해임·맡은 일 바꾸기는 모임장의 일) -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-people`">
                <h3 :id="`${titleId}-people`" class="text-sm font-semibold">{{ t('group.sec.people') }}</h3>
                <p class="m-0 text-xs text-muted-color">{{ t('group.managersHelp') }}</p>
                <dl class="m-0 flex flex-col">
                    <div :class="ROW">
                        <dt :class="DT">{{ t('group.field.leader') }}</dt>
                        <dd :class="DD"><GroupPersonLabel :person="detail.leader" bold /></dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('group.field.managers') }}</dt>
                        <dd :class="DD">
                            <p class="m-0">{{ t('group.managerSummary', { count: managers.length, limit: detail.managerLimit }) }}</p>
                            <ul v-if="managers.length" class="m-0 mt-2 flex list-none flex-col gap-2 p-0" :aria-label="t('group.managersListLabel')">
                                <li v-for="(manager, index) in managers" :key="manager.user.id ?? `m${index}`" class="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
                                    <GroupPersonLabel :person="manager.user" bold />
                                    <span class="inline-flex flex-wrap items-center gap-1" role="group" :aria-label="t('group.managerPermissionsLabel')">
                                        <GroupTag v-for="key in UtilGroup.knownPermissions(manager.permissions)" :key="key" :value="t(`group.managerPermission.${key}`)" />
                                    </span>
                                    <span v-if="manager.appointedAt" class="text-xs text-muted-color" :title="time.full(manager.appointedAt)">{{ t('group.appointed', { date: time.date(manager.appointedAt) }) }}</span>
                                </li>
                            </ul>
                            <p v-else class="m-0 mt-1 text-muted-color">{{ t('group.managersEmpty') }}</p>
                        </dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('group.field.members') }}</dt>
                        <dd :class="DD">{{ t('group.memberCount', { n: detail.memberCount }) }}</dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('group.field.leaderLoad') }}</dt>
                        <dd :class="DD">
                            {{ UtilGroupMessages.leaderLoadLine(t, detail.leaderLoad) }}
                            <small v-if="detail.leaderLoad.inactive > 0" class="mt-1 block text-xs text-muted-color">{{ t('group.leaderLoadNote') }}</small>
                        </dd>
                    </div>
                </dl>
            </section>

            <!-- ③ 활동 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-activity`">
                <h3 :id="`${titleId}-activity`" class="text-sm font-semibold">{{ t('group.sec.activity') }}</h3>
                <dl class="m-0 flex flex-col">
                    <div :class="ROW">
                        <dt :class="DT">{{ t('group.field.posts') }}</dt>
                        <dd :class="DD">{{ t('group.postsLine', { posts: detail.postCount, public: detail.publicPostCount }) }}</dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('group.field.activity') }}</dt>
                        <dd :class="DD" :title="time.full(detail.lastActivityAt)">{{ time.detail(detail.lastActivityAt) || '—' }}</dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('group.field.reports') }}</dt>
                        <dd :class="DD">
                            {{ t('group.reportsLine', { n: detail.reportCaseCount }) }}
                            <NuxtLink v-if="canReport" :to="reportsLink" class="ml-1 whitespace-nowrap text-primary hover:underline">{{ t('group.reportsLink') }} ›</NuxtLink>
                        </dd>
                    </div>
                </dl>
            </section>

            <!-- ④ 운영 조치 — 모임장에게 알림이 간다. 처리 권한이 없으면 단추를 막고 이유를 보인다 -->
            <section class="flex flex-col gap-3 rounded-xl border border-red-200 p-3 dark:border-red-900/60" :aria-labelledby="`${titleId}-actions`">
                <div class="flex flex-wrap items-center gap-2">
                    <h3 :id="`${titleId}-actions`" class="text-sm font-semibold">{{ t('group.sec.actions') }}</h3>
                    <GroupTag :value="t('group.actionNotify')" severity="warn" icon="pi pi-bell" />
                </div>

                <!-- 비활성 기록 — 마지막으로 비활성이 된 때의 것. 다시 열어도 남는다 -->
                <div v-if="detail.deactivation" class="flex flex-col gap-1 rounded-lg bg-surface-50 p-3 dark:bg-surface-800">
                    <h4 class="text-[13px] font-semibold">{{ t('group.record.title') }}</h4>
                    <dl class="m-0 flex flex-col">
                        <div :class="ROW">
                            <dt :class="DT">{{ t('group.record.at') }}</dt>
                            <dd :class="DD" :title="time.full(detail.deactivation.at)">{{ time.detail(detail.deactivation.at) || t('group.record.unknown') }}</dd>
                        </div>
                        <div :class="ROW">
                            <dt :class="DT">{{ t('group.record.by') }}</dt>
                            <dd :class="DD">
                                <GroupPersonLabel v-if="detail.deactivation.by" :person="detail.deactivation.by" />
                                <span v-else class="text-muted-color">{{ t('group.record.unknown') }}</span>
                            </dd>
                        </div>
                        <div :class="ROW">
                            <dt :class="DT">{{ t('group.record.reason') }}</dt>
                            <dd :class="DD" class="whitespace-pre-line">{{ detail.deactivation.reason || t('group.record.unknown') }}</dd>
                        </div>
                        <div v-if="detail.deactivation.reportCase" :class="ROW">
                            <dt :class="DT">{{ t('group.record.case') }}</dt>
                            <dd :class="DD">
                                <NuxtLink
                                    v-if="canReport"
                                    :to="{ path: '/reports', query: { report: detail.deactivation.reportCase.id, status: 'ALL' } }"
                                    class="text-primary hover:underline"
                                >
                                    {{ t('group.caseNo', { no: detail.deactivation.reportCase.no }) }} {{ t('group.reportCaseLink') }} ›
                                </NuxtLink>
                                <template v-else>{{ t('group.caseNo', { no: detail.deactivation.reportCase.no }) }}</template>
                            </dd>
                        </div>
                        <template v-if="detail.deactivation.reactivatedAt">
                            <div :class="ROW">
                                <dt :class="DT">{{ t('group.record.reactivated') }}</dt>
                                <dd :class="DD" :title="time.full(detail.deactivation.reactivatedAt)">{{ time.detail(detail.deactivation.reactivatedAt) }}</dd>
                            </div>
                            <div v-if="detail.deactivation.reactivatedBy" :class="ROW">
                                <dt :class="DT">{{ t('group.record.reactivatedBy') }}</dt>
                                <dd :class="DD"><GroupPersonLabel :person="detail.deactivation.reactivatedBy" /></dd>
                            </div>
                            <div v-if="detail.deactivation.memo" :class="ROW">
                                <dt :class="DT">{{ t('group.record.memo') }}</dt>
                                <dd :class="DD" class="whitespace-pre-line">{{ detail.deactivation.memo }}</dd>
                            </div>
                        </template>
                    </dl>
                </div>

                <!-- 조치 — 열림이면 [비활성화…], 비활성이면 [다시 열기…] -->
                <div class="flex flex-col gap-1.5">
                    <p class="m-0 text-sm font-medium">{{ inactive ? t('group.reactivateTitle') : t('group.deactivateTitle') }}</p>
                    <div>
                        <Button v-if="inactive" type="button" :label="t('group.reactivateButton')" icon="pi pi-refresh" size="small" :disabled="!canWrite || busy" :title="canWrite ? undefined : t('group.noWriteReason')" @click="emit('reactivate')" />
                        <Button v-else type="button" :label="t('group.deactivateButton')" icon="pi pi-ban" size="small" severity="danger" variant="outlined" :disabled="!canWrite || busy" :title="canWrite ? undefined : t('group.noWriteReason')" @click="emit('deactivate')" />
                    </div>
                    <p v-if="!canWrite" class="m-0 text-xs text-muted-color">{{ t('group.noWriteReason') }}</p>
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ inactive ? t('group.reactivateHelp') : t('group.deactivateHelp') }}</p>
                </div>
            </section>
        </div>
    </Drawer>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupDetail, GroupRow } from '~/composables/group/api/useGroupAdminApi.types';
import UtilGroup from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 목록에서 누른 줄 — 상세를 받는 동안 머리를 채운다 */
    summary: GroupRow | null;
    detail: GroupDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    /** 조치가 가는 중 */
    busy?: boolean;
    /** 처리 권한(`group.manage`) */
    canWrite?: boolean;
    /** 게시판 설정 링크를 보일까(`board.read`) */
    canBoard?: boolean;
    /** 신고 관리 링크를 보일까(`report.read`) */
    canReport?: boolean;
}

const props = withDefaults(defineProps<Props>(), { loading: false, error: null, busy: false, canWrite: false, canBoard: false, canReport: false });
const emit = defineEmits<{ deactivate: []; reactivate: []; retry: [] }>();

const { t } = useI18n();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const titleId = useId();

/** 한 항목 줄 — 이름(고정 폭) + 값. 좁은 폭에서는 위아래로 쌓인다 */
const ROW = 'grid grid-cols-1 gap-x-3 gap-y-0.5 border-b border-(--line) py-2 last:border-b-0 sm:grid-cols-[9rem_minmax(0,1fr)]';
const DT = 'text-[13px] text-muted-color';
const DD = 'm-0 min-w-0 text-[13px] [overflow-wrap:anywhere]';

const status = computed(() => props.detail?.status ?? props.summary?.status ?? 'ACTIVE');
const inactive = computed(() => status.value === 'INACTIVE');
const managers = computed(() => props.detail?.managers ?? []);

const openedText = computed(() => {
    const d = props.detail;
    if (!d) return '';
    return UtilGroupMessages.openedLine(t, time.value.date(d.openedAt), d.reviewer?.nickname ?? '', d.creator?.nickname ?? '');
});

const visibilityChangedText = computed(() => {
    const d = props.detail;
    if (!d) return '';
    const at = d.visibilityChangedAt;
    return UtilGroupMessages.visibilityChangedLine(t, at ? time.value.date(at) : '', at ? (d.visibilityChangedBy?.nickname ?? null) : null);
});

/** 「신고 보기 ›」 — 이 소모임을 대상으로 한 신고 사건(모든 상태, 서버 `groupId` 거르기) */
const reportsLink = computed(() => ({ path: '/reports', query: { groupId: props.detail?.id ?? '', status: 'ALL' } }));

const bandText = computed(() => {
    const record = props.detail?.deactivation;
    return UtilGroupMessages.band(t, time.value.date(record?.at ?? props.summary?.deactivatedAt ?? null), record?.reportCase?.no ?? null);
});
</script>
