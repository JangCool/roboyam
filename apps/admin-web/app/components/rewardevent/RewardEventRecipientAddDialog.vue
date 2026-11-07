<template>
    <!--
        당첨자 추가(직접 입력·검색) — 두 가지로 모은 회원을 한 목록에 쌓았다가 등수를 골라 한 번에 넣는다.
        ① 붙여 넣기: 회원 번호·닉네임·이메일을 줄마다(쉼표·탭도 됨, 한 번에 500줄 — 넘으면 [찾기]를 끈다) → [찾기] → 찾은 회원은 목록에 더하고, 못 찾은 줄·이미 있는 회원·넣을 수 없는 회원(정지·탈퇴·운영자 회원 계정)은 따로 보인다.
        ② 검색: 닉네임·이메일·회원 번호 일부 → 결과에서 [더하기].
        서버는 부르지 않는다 — 찾기·검색·넣기는 `resolve`·`search`·`submit` 으로 올리고 결과는 props 로 받는다(`components.md`).
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="t('rewardEvent.add.title')" :style="{ width: 'min(44rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <div class="flex flex-col gap-4">
            <LabeledField :label="t('rewardEvent.add.tier')">
                <template #default="{ id }">
                    <Select v-model="tierId" :input-id="id" :options="tierOptions" option-label="label" option-value="value" option-disabled="disabled" :disabled="busy" fluid />
                </template>
            </LabeledField>

            <Tabs v-model:value="tab">
                <TabList>
                    <Tab value="paste">{{ t('rewardEvent.add.paste') }}</Tab>
                    <Tab value="search">{{ t('rewardEvent.add.search') }}</Tab>
                </TabList>
                <TabPanels class="px-0!">
                    <TabPanel value="paste">
                        <div class="flex flex-col gap-2">
                            <label :for="pasteId" class="text-sm">{{ t('rewardEvent.add.pasteLabel', { max: UtilRewardEvent.PASTE_MAX }) }}</label>
                            <Textarea :id="pasteId" v-model="pasted" rows="5" :placeholder="t('rewardEvent.add.pastePlaceholder')" :disabled="busy || resolving" fluid />
                            <div class="flex flex-wrap items-center gap-2">
                                <Button type="button" :label="t('rewardEvent.add.resolve')" icon="pi pi-search" size="small" severity="secondary" variant="outlined" :loading="resolving" :disabled="!pastedLines.length || pasteTooMany" @click="emit('resolve', [...pastedLines])" />
                                <span class="text-xs" :class="pasteTooMany ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ t('rewardEvent.add.lineCount', { n: pastedLines.length }) }}</span>
                            </div>
                            <p v-if="pasteTooMany" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ t('rewardEvent.add.pasteTooMany', { max: UtilRewardEvent.PASTE_MAX }) }}</p>
                            <div v-if="resolveResult" class="flex flex-col gap-1.5 text-xs" aria-live="polite">
                                <p class="m-0 text-green-700 dark:text-green-400">{{ t('rewardEvent.add.found', { n: resolveResult.found.length }) }}</p>
                                <p v-if="resolveResult.notFound.length" class="m-0 text-red-600 dark:text-red-400">
                                    {{ t('rewardEvent.add.notFound', { n: resolveResult.notFound.length }) }}
                                    <span class="break-all text-muted-color">{{ resolveResult.notFound.join(', ') }}</span>
                                </p>
                                <p v-if="resolveResult.alreadyIn.length" class="m-0 text-muted-color">{{ t('rewardEvent.add.alreadyIn', { n: resolveResult.alreadyIn.length, names: resolveResult.alreadyIn.map((m) => memberName(m)).join(', ') }) }}</p>
                                <p v-if="resolveResult.excluded.length" class="m-0 text-amber-700 dark:text-amber-400">
                                    {{ t('rewardEvent.add.excluded', { n: resolveResult.excluded.length }) }}
                                    <span class="text-muted-color">{{ resolveResult.excluded.map((x) => `${memberName(x.member)}(${t(`rewardEvent.excludeReason.${x.reason}`)})`).join(', ') }}</span>
                                </p>
                            </div>
                        </div>
                    </TabPanel>
                    <TabPanel value="search">
                        <div class="flex flex-col gap-2">
                            <form class="flex gap-2" role="search" @submit.prevent="emit('search', query.trim())">
                                <label :for="searchId" class="sr-only">{{ t('rewardEvent.add.search') }}</label>
                                <InputText :id="searchId" v-model="query" type="search" maxlength="100" autocomplete="off" :placeholder="t('rewardEvent.add.searchPlaceholder')" class="min-w-0 flex-1" :disabled="busy" />
                                <Button type="submit" :label="t('common.search')" size="small" :loading="searching" :disabled="!query.trim()" />
                            </form>
                            <ul class="m-0 flex max-h-56 list-none flex-col overflow-y-auto rounded-lg border border-(--line) p-0" :aria-busy="searching">
                                <li v-if="!searchResults.length" class="px-3 py-4 text-center text-sm text-muted-color">{{ searched ? t('rewardEvent.add.searchEmpty') : t('rewardEvent.add.searchHint') }}</li>
                                <li v-for="m in searchResults" :key="m.id" class="flex items-center gap-2 border-b border-(--line) px-3 py-2 last:border-b-0">
                                    <span class="flex min-w-0 flex-1 flex-col">
                                        <span class="truncate text-sm">{{ memberName(m) }}</span>
                                        <span class="truncate text-[11px] text-muted-color"><code>{{ m.id }}</code><template v-if="m.email"> · {{ m.email }}</template></span>
                                    </span>
                                    <Button type="button" :label="picked.has(m.id) ? t('rewardEvent.add.added') : t('rewardEvent.add.addOne')" size="small" :severity="picked.has(m.id) ? 'secondary' : undefined" variant="outlined" :disabled="picked.has(m.id) || busy" @click="addMember(m)" />
                                </li>
                            </ul>
                        </div>
                    </TabPanel>
                </TabPanels>
            </Tabs>

            <!-- 넣을 목록 -->
            <div class="flex flex-col gap-1.5">
                <span class="text-sm font-medium">{{ t('rewardEvent.add.selected', { n: selected.length }) }}</span>
                <p v-if="!selected.length" class="m-0 text-xs text-muted-color">{{ t('rewardEvent.add.selectedEmpty') }}</p>
                <div v-else class="flex max-h-32 flex-wrap gap-1.5 overflow-y-auto">
                    <Chip v-for="m in selected" :key="m.id" :label="`${memberName(m)} · ${m.id}`" removable @remove="removeMember(m.id)" />
                </div>
                <p v-if="tooMany" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ t('rewardEvent.add.tooMany', { max: UtilRewardEvent.BATCH_MAX }) }}</p>
                <p v-if="room !== null && selected.length > room" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ t('rewardEvent.add.overRoom', { n: room }) }}</p>
            </div>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </div>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="button" :label="t('rewardEvent.add.submit', { n: selected.length })" icon="pi pi-user-plus" :loading="busy" :disabled="!tierId || !selected.length || tooMany || (room !== null && selected.length > room)" @click="submit" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { RewardDrawTierChoice } from '~/components/rewardevent/RewardEventDrawDialog.types';
import type { RewardMemberRef, RewardRecipientAddRequest, RewardRecipientResolveResult } from '~/composables/rewardevent/api/useRewardEventAdminApi.types';
import UtilRewardEvent from '~/utils/util-reward-event';
import UtilRewardEventMessages from '~/utils/util-reward-event-messages';

interface Props {
    visible: boolean;
    tiers: RewardDrawTierChoice[];
    resolveResult?: RewardRecipientResolveResult | null;
    resolving?: boolean;
    searchResults?: RewardMemberRef[];
    searching?: boolean;
    /** 검색을 한 번이라도 했다(빈 결과 문구) */
    searched?: boolean;
    busy?: boolean;
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { resolveResult: null, resolving: false, searchResults: () => [], searching: false, searched: false, busy: false, error: '' });
const emit = defineEmits<{
    'update:visible': [visible: boolean];
    resolve: [lines: string[]];
    search: [q: string];
    submit: [body: RewardRecipientAddRequest];
}>();

const { t } = useI18n();
const memberName = (m: RewardMemberRef): string => UtilRewardEventMessages.memberName(t, m);
const pasteId = useId();
const searchId = useId();
const tab = ref<string | number>('paste');
const tierId = ref('');
const pasted = ref('');
const query = ref('');
const selected = ref<RewardMemberRef[]>([]);
const picked = computed(() => new Set(selected.value.map((m) => m.id)));
const pastedLines = computed(() => UtilRewardEvent.lines(pasted.value));
/** 서버는 한 번에 500줄·500명까지 받는다(`BATCH_MAX`) — 넘으면 미리 막는다 */
const pasteTooMany = computed(() => pastedLines.value.length > UtilRewardEvent.PASTE_MAX);
const tooMany = computed(() => selected.value.length > UtilRewardEvent.BATCH_MAX);

const tierOptions = computed(() => props.tiers.map((tier) => ({ value: tier.id, disabled: tier.current >= tier.quota, label: t('rewardEvent.draw.tierOption', { name: tier.name, current: tier.current, quota: tier.quota }) })));
const room = computed(() => {
    const tier = props.tiers.find((x) => x.id === tierId.value);
    return tier ? Math.max(0, tier.quota - tier.current) : null;
});

// 열 때마다 비운다
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        tab.value = 'paste';
        tierId.value = props.tiers.find((x) => x.current < x.quota)?.id ?? '';
        pasted.value = '';
        query.value = '';
        selected.value = [];
    },
);

// 붙여 넣기 결과가 오면 찾은 회원을 목록에 더한다(부수 효과 — 받은 결과를 쌓는 목록에 옮긴다)
watch(
    () => props.resolveResult,
    (result) => {
        if (!result) return;
        result.found.forEach(addMember);
    },
);

function addMember(m: RewardMemberRef): void {
    if (picked.value.has(m.id)) return;
    selected.value = [...selected.value, m];
}

function removeMember(id: string): void {
    selected.value = selected.value.filter((m) => m.id !== id);
}

function submit(): void {
    if (props.busy || !tierId.value || !selected.value.length || tooMany.value) return;
    emit('submit', { tierId: tierId.value, memberIds: selected.value.map((m) => m.id) });
}
</script>
