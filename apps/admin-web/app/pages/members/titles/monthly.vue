<template>
    <!--
        회원 › 회원 칭호 › 이달의 작가 뽑기(`/members/titles/monthly?month=YYYY-MM`, member-title-contract 「작가 칭호 설정」·「구현 결정」 3).
        · 달 고르기(최근 13달, 기본 지난달) → 후보(`GET /api/admin/titles/monthly-candidates`) = 그 달 우수 글의 글쓴이 + 글 관리 ⋯ 「이달의 작가 후보에 넣기」로 넣은 글쓴이.
        · 이미 받은 사람(그 달) · 남은 자리 = 한 달 최대 인원 − 받은 사람. 후보를 골라 [수여] → 확인 → 한 명씩 `POST /api/admin/titles/monthly-writer/grants { memberId, period: 달, reason }`.
          이달의 작가 횟수가 기준에 닿으면 서버가 같은 처리에서 명예 작가를 바로 준다(안내 한 줄). 받은 사람의 [회수]는 사유 필수.
        · 후보 글은 글 관리(`/posts?post=`)로 잇는다. 권한: 보기 `title.read` · 수여·회수 `title.manage`.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <TitleAdminTabs active="monthly" />

        <div class="flex flex-wrap items-center gap-3">
            <label :for="monthId" class="text-[13px] font-medium">{{ t('memberTitle.monthly.month') }}</label>
            <Select :input-id="monthId" :model-value="month" :options="monthOptions" option-label="label" option-value="value" size="small" class="w-40" @update:model-value="onMonth" />
            <span v-if="data" class="text-[13px] text-muted-color" aria-live="polite">{{ t('memberTitle.monthly.slots', { awarded: data.awarded.length, max: data.max }) }}</span>
            <Button v-if="canManage && data" type="button" :label="t('memberTitle.monthly.award', { count: selected.length })" icon="pi pi-star" size="small" class="ml-auto" :disabled="selected.length === 0 || busy" :loading="busy" data-testid="title-monthly-award" @click="award" />
        </div>

        <ErrorState v-if="loadError" :title="t('memberTitle.monthly.loadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
        <div v-else-if="loading && !data" class="flex flex-col gap-3" aria-hidden="true">
            <Skeleton height="4rem" />
            <Skeleton height="8rem" />
        </div>
        <template v-else-if="data">
            <!-- 이미 받은 사람 -->
            <AppGadget :title="t('memberTitle.monthly.awardedTitle', { month: UtilTitleMessages.period(t, data.month) })">
                <EmptyState v-if="data.awarded.length === 0" icon="pi pi-star" :title="t('memberTitle.monthly.awardedEmpty')" />
                <ul v-else class="m-0 flex list-none flex-wrap gap-2 p-0">
                    <li v-for="row in data.awarded" :key="row.id" class="flex items-center gap-2 rounded-lg border border-(--line) py-1.5 pr-1.5 pl-2.5">
                        <TitleIcon icon="pen" :scale="2" />
                        <span class="text-[13px] font-medium">{{ row.member.nickname ?? t('memberTitle.withdrawn') }}</span>
                        <Button v-if="canManage" type="button" icon="pi pi-times" severity="secondary" variant="text" size="small" rounded :aria-label="t('memberTitle.revokeOf', { name: row.member.nickname ?? '' })" @click="openRevoke(row)" />
                    </li>
                </ul>
                <p class="m-0 mt-3 flex items-start gap-2 text-xs leading-relaxed text-muted-color">
                    <i class="pi pi-info-circle mt-0.5" aria-hidden="true" />
                    <span>{{ t('memberTitle.monthly.honoraryNotice') }}</span>
                </p>
            </AppGadget>

            <!-- 후보 -->
            <AppGadget :title="t('memberTitle.monthly.candidatesTitle', { count: data.candidates.length })">
                <EmptyState v-if="data.candidates.length === 0" icon="pi pi-users" :title="t('memberTitle.monthly.candidatesEmpty')" :description="t('memberTitle.monthly.candidatesEmptyHint')" />
                <ul v-else class="m-0 flex list-none flex-col p-0">
                    <li v-for="c in data.candidates" :key="c.member.id" class="flex items-start gap-3 border-b border-(--line) py-3 last:border-b-0">
                        <Checkbox
                            v-if="canManage"
                            :model-value="selected.includes(c.member.id)"
                            binary
                            :input-id="`${monthId}-${c.member.id}`"
                            :disabled="c.awarded || (!selected.includes(c.member.id) && remaining <= selected.length)"
                            class="mt-0.5"
                            @update:model-value="toggle(c.member.id)"
                        />
                        <div class="min-w-0 flex-1">
                            <label :for="`${monthId}-${c.member.id}`" class="flex flex-wrap items-center gap-2 text-sm font-semibold">
                                {{ c.member.nickname ?? t('memberTitle.withdrawn') }}
                                <Tag v-if="c.awarded" :value="t('memberTitle.monthly.alreadyAwarded')" severity="success" class="text-[11px]!" />
                            </label>
                            <ul class="m-0 mt-1 flex list-none flex-col gap-0.5 p-0 text-[12px]">
                                <li v-for="post in c.posts" :key="post.id" class="flex flex-wrap items-center gap-1.5">
                                    <NuxtLink :to="{ path: '/posts', query: { post: post.id } }" class="min-w-0 truncate text-(--p-primary-color) hover:underline">{{ post.title }}</NuxtLink>
                                    <Tag v-if="post.featured" :value="t('memberTitle.monthly.featured')" icon="pi pi-star-fill" severity="warn" class="text-[10px]!" />
                                    <Tag v-if="post.manual" :value="t('memberTitle.monthly.manual')" severity="secondary" class="text-[10px]!" />
                                </li>
                            </ul>
                        </div>
                    </li>
                </ul>
            </AppGadget>
        </template>

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
            <p v-if="revokeTarget" class="m-0 text-sm">{{ t('memberTitle.revokeText', { name: revokeTarget.member.nickname ?? '', title: t('memberTitle.monthly.titleName', { month: UtilTitleMessages.period(t, data?.month ?? '') }) }) }}</p>
        </MemberReasonDialog>
    </div>
</template>

<script setup lang="ts">
import type { MonthlyCandidates, TitleGrantRow } from '~/composables/title/api/useTitleApi.types';
import UtilTitle from '~/utils/util-title';
import UtilTitleMessages from '~/utils/util-title-messages';

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const api = useTitleApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();
const monthId = useId();

const canManage = computed(() => can('title.manage'));
const months = UtilTitle.recentMonths(new Date(), 13);
const monthOptions = computed(() => months.map((value) => ({ value, label: UtilTitleMessages.period(t, value) })));
/** 주소의 달 — 없거나 모양이 틀리면 지난달 */
const month = computed(() => {
    const q = route.query.month;
    return typeof q === 'string' && /^\d{4}-\d{2}$/.test(q) ? q : months[1]!;
});

const data = ref<MonthlyCandidates | null>(null);
const loading = ref(true);
const loadError = ref('');
const busy = ref(false);
const selected = ref<string[]>([]);
/** 남은 자리 */
const remaining = computed(() => Math.max(0, (data.value?.max ?? 0) - (data.value?.awarded.length ?? 0)));

let seq = 0;
async function load(): Promise<void> {
    const mine = ++seq;
    loading.value = true;
    loadError.value = '';
    try {
        const res = await api.monthlyCandidates(month.value);
        if (mine !== seq) return;
        data.value = res;
        selected.value = [];
    } catch (e) {
        if (mine !== seq) return;
        loadError.value = errorOf(e).message;
    } finally {
        if (mine === seq) loading.value = false;
    }
}

function onMonth(value: string | null): void {
    if (value) void router.replace({ path: UtilTitle.PATHS.monthly, query: { month: value } });
}
watch(month, () => void load(), { immediate: true });

function toggle(id: string): void {
    selected.value = selected.value.includes(id) ? selected.value.filter((x) => x !== id) : [...selected.value, id];
}

/** 고른 후보에게 차례로 수여 — 하나가 실패하면 멈추고 알린 뒤 다시 받는다(앞에서 준 것은 그대로) */
async function award(): Promise<void> {
    const current = data.value;
    if (!current || busy.value || selected.value.length === 0) return;
    const names = current.candidates.filter((c) => selected.value.includes(c.member.id)).map((c) => c.member.nickname ?? c.member.id);
    const label = UtilTitleMessages.period(t, current.month);
    const ok = await confirm({ title: t('memberTitle.monthly.confirmTitle', { month: label }), message: t('memberTitle.monthly.confirmText', { names: names.join(', '), count: names.length }), confirmLabel: t('memberTitle.monthly.award', { count: names.length }) });
    if (!ok) return;
    busy.value = true;
    let done = 0;
    try {
        let honorary = 0;
        for (const memberId of selected.value) {
            const granted = await api.grant(UtilTitle.MONTHLY_KEY, { memberId, period: current.month, reason: t('memberTitle.monthly.reason', { month: label }) });
            done += 1;
            if (granted.honorary) honorary += 1;
        }
        toast.success(honorary > 0 ? t('memberTitle.monthly.awardedHonorary', { count: done, honorary }) : t('memberTitle.monthly.awarded', { count: done }));
    } catch (e) {
        toast.error(t('memberTitle.monthly.partial', { done, reason: errorOf(e).message }), 8000);
    } finally {
        busy.value = false;
        await load();
    }
}

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
        await api.revoke(row.key, row.id, { reason });
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

useSeoMeta({ title: () => `${t('memberTitle.tab.monthly')} · ${t('memberTitle.title')}` });
</script>
