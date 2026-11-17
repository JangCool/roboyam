<template>
    <!--
        회원 › 회원 칭호 › 주고 뺀 기록(`/members/titles/grants?key=&memberId=&page=`, member-title-contract 「화면」·「규칙」 — 수여·회수는 모두 감사 기록, 회수해도 줄은 남는다).
        거르기: 칭호 · 회원 번호. 표: 칭호 칩(회수한 줄은 흐리게) · 회원 · 기간 · 받은 길(자동·수여·신청 승인) · 준 때·준 사람·사유 · 회수(때·사람·사유) · [회수](`title.manage`, 살아 있는 줄만 — 사유 필수).
        매니저가 준 칭호(신청 승인)도 운영자가 회수할 수 있다. 서버 쪽 쪽 번호는 0부터.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <TitleAdminTabs active="grants" />

        <form class="flex flex-wrap items-end gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('memberTitle.grants.searchLabel')" novalidate @submit.prevent="search">
            <div class="flex flex-col gap-1">
                <label :for="`${uid}-key`" class="text-[12px] font-medium">{{ t('memberTitle.grants.key') }}</label>
                <Select :input-id="`${uid}-key`" v-model="filter.key" :options="keyOptions" option-label="label" option-value="value" size="small" class="w-44" />
            </div>
            <div class="flex flex-col gap-1">
                <label :for="`${uid}-member`" class="text-[12px] font-medium">{{ t('memberTitle.grants.memberId') }}</label>
                <InputText :id="`${uid}-member`" v-model="filter.memberId" type="search" maxlength="40" size="small" class="w-56 font-mono" autocomplete="off" :placeholder="t('memberTitle.grants.memberIdPlaceholder')" />
            </div>
            <Button type="submit" :label="t('common.search')" size="small" :loading="loading" />
        </form>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <DataTable
                :value="page?.items ?? []"
                data-key="grantId"
                lazy
                paginator
                :first="(page?.page ?? 0) * size"
                :rows="size"
                :total-records="page?.total ?? 0"
                :rows-per-page-options="[20, 50, 100]"
                :loading="loading"
                :size="prefs.density === 'compact' ? 'small' : undefined"
                scrollable
                paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                @page="onPage"
            >
                <template #empty>
                    <ErrorState v-if="loadError" :title="t('memberTitle.grants.loadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
                    <div v-else-if="loading" class="h-32" aria-hidden="true" />
                    <EmptyState v-else icon="pi pi-history" :title="t('memberTitle.grants.empty')" />
                </template>
                <Column :header="t('memberTitle.col.title')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <div class="flex flex-col items-start gap-1">
                            <TitleChip :title="data.title" :muted="!data.active" />
                            <span v-if="data.period" class="text-[11px] tabular-nums text-muted-color">{{ UtilTitleMessages.period(t, data.period) }}</span>
                        </div>
                    </template>
                </Column>
                <Column :header="t('memberTitle.col.member')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <NuxtLink v-if="canReadMember" :to="{ path: '/members', query: { memberNo: data.member.memberNo } }" class="text-[13px] font-medium text-(--p-primary-color) hover:underline">{{ data.member.nickname ?? t('memberTitle.withdrawn') }}</NuxtLink>
                        <span v-else class="text-[13px]">{{ data.member.nickname ?? t('memberTitle.withdrawn') }}</span>
                    </template>
                </Column>
                <Column :header="t('memberTitle.col.granted')" style="min-width: 13rem">
                    <template #body="{ data }">
                        <div class="flex flex-col gap-0.5 text-[12px]">
                            <span><Tag :value="t(`memberTitle.source.${data.source}`)" severity="secondary" class="mr-1 text-[10px]!" /><span class="tabular-nums">{{ time.full(data.grantedAt) }}</span></span>
                            <span class="text-muted-color">{{ data.grantedBy ?? t('memberTitle.grants.auto') }}<template v-if="data.reason"> · {{ data.reason }}</template></span>
                        </div>
                    </template>
                </Column>
                <Column :header="t('memberTitle.col.revoked')" style="min-width: 13rem">
                    <template #body="{ data }">
                        <div v-if="!data.active" class="flex flex-col gap-0.5 text-[12px]">
                            <span class="tabular-nums text-red-700 dark:text-red-400">{{ data.revokedAt ? time.full(data.revokedAt) : '—' }}</span>
                            <span class="text-muted-color">{{ data.revokedBy ?? '—' }}<template v-if="data.revokeReason"> · {{ data.revokeReason }}</template></span>
                        </div>
                        <Button v-else-if="canManage" type="button" :label="t('memberTitle.revoke')" icon="pi pi-times" severity="danger" variant="outlined" size="small" :disabled="busy" @click="openRevoke(data)" />
                        <span v-else class="text-[12px] text-muted-color">—</span>
                    </template>
                </Column>
            </DataTable>
        </div>

        <MemberReasonDialog
            v-model:visible="revokeOpen"
            :title="t('memberTitle.revokeTitle')"
            :label="t('memberTitle.revokeReason')"
            :submit-label="t('memberTitle.revoke')"
            submit-icon="pi pi-times"
            danger
            :notice="t('memberTitle.revokeNotice')"
            :min="1"
            :max="UtilTitle.REASON_MAX"
            :busy="busy"
            :error="revokeError"
            :field-error="revokeFieldError"
            @submit="submitRevoke"
        >
            <p v-if="revokeTarget" class="m-0 text-sm">{{ t('memberTitle.revokeText', { name: revokeTarget.member.nickname ?? '', title: revokeTarget.title.label }) }}</p>
        </MemberReasonDialog>
    </div>
</template>

<script setup lang="ts">
import type { AdminTitle, TitleGrantPage, TitleGrantRow } from '~/composables/title/api/useTitleApi.types';
import UtilTitle from '~/utils/util-title';
import UtilTitleMessages from '~/utils/util-title-messages';

const ALL = '__all__';

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const api = useTitleApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { prefs } = useLayoutPrefs();
const { localizedName } = useLocalizedName();
const uid = useId();

const canManage = computed(() => can('title.manage'));
const canReadMember = computed(() => can('member.read'));

/** 주소의 조건 */
const applied = computed(() => ({
    key: typeof route.query.key === 'string' ? route.query.key : '',
    memberId: typeof route.query.memberId === 'string' ? route.query.memberId : '',
    page: Math.max(0, Number(route.query.page ?? 0) || 0),
}));
const size = ref(20);
const filter = reactive({ key: ALL, memberId: '' });

const titles = ref<AdminTitle[]>([]);
const keyOptions = computed(() => [{ value: ALL, label: t('memberTitle.review.allTitles') }, ...titles.value.map((x) => ({ value: x.key, label: localizedName(x.name, x.nameEn) }))]);
void api
    .list()
    .then((list) => (titles.value = list))
    .catch(() => undefined);

const page = ref<TitleGrantPage | null>(null);
const loading = ref(true);
const loadError = ref('');
const busy = ref(false);

let seq = 0;
async function load(): Promise<void> {
    const mine = ++seq;
    loading.value = true;
    loadError.value = '';
    try {
        const res = await api.grants({ key: applied.value.key || undefined, memberId: applied.value.memberId || undefined, page: applied.value.page, size: size.value });
        if (mine !== seq) return;
        page.value = res;
    } catch (e) {
        if (mine !== seq) return;
        loadError.value = errorOf(e).message;
        page.value = null;
    } finally {
        if (mine === seq) loading.value = false;
    }
}

function go(change: { key?: string; memberId?: string; page?: number }): void {
    const key = change.key ?? applied.value.key;
    const memberId = change.memberId ?? applied.value.memberId;
    const p = change.page ?? applied.value.page;
    void router.replace({ path: UtilTitle.PATHS.grants, query: { ...(key ? { key } : {}), ...(memberId ? { memberId } : {}), ...(p > 0 ? { page: String(p) } : {}) } });
}
function search(): void {
    go({ key: filter.key === ALL ? '' : filter.key, memberId: filter.memberId.trim(), page: 0 });
}
function onPage(e: { page: number; rows: number }): void {
    if (e.rows !== size.value) {
        size.value = e.rows;
        if (applied.value.page === 0) void load();
        else go({ page: 0 });
        return;
    }
    go({ page: e.page });
}

watch(
    applied,
    (value) => {
        filter.key = value.key || ALL;
        filter.memberId = value.memberId;
        void load();
    },
    { immediate: true },
);

const revokeOpen = ref(false);
const revokeTarget = ref<TitleGrantRow | null>(null);
const revokeError = ref('');
const revokeFieldError = ref('');
function openRevoke(row: TitleGrantRow): void {
    revokeTarget.value = row;
    revokeError.value = '';
    revokeFieldError.value = '';
    revokeOpen.value = true;
}
async function submitRevoke(reason: string): Promise<void> {
    const row = revokeTarget.value;
    if (!row || busy.value) return;
    busy.value = true;
    try {
        await api.revoke(row.title.key, row.grantId, { reason });
        revokeOpen.value = false;
        toast.success(t('memberTitle.revoked', { name: row.member.nickname ?? '' }));
        await load();
    } catch (e) {
        const error = errorOf(e);
        if (error.fields?.reason) revokeFieldError.value = error.fields.reason;
        else revokeError.value = error.message;
    } finally {
        busy.value = false;
    }
}

useSeoMeta({ title: () => `${t('memberTitle.tab.grants')} · ${t('memberTitle.title')}` });
</script>
