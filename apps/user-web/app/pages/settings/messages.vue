<template>
    <div class="flex flex-col gap-4">
        <h1 class="text-lg font-bold">{{ t('settings.messages') }}</h1>

        <div v-if="status === 'loading'" class="space-y-3" role="status" :aria-label="t('dm.settingsLoading')" data-testid="dm-settings-loading">
            <div class="h-28 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="h-12 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
            <div class="h-12 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="status === 'error'" :title="t('dm.settingsLoadFailed')" :description="errorMessage" retry data-testid="dm-settings-error" @retry="load" />

        <template v-else>
            <!-- 받기 / 받지 않기 — 고르면 바로 저장한다 -->
            <section :aria-labelledby="`${uid}-title`">
                <h2 :id="`${uid}-title`" class="sr-only">{{ t('dm.settingsGroup') }}</h2>
                <div role="radiogroup" :aria-labelledby="`${uid}-title`" class="panel-card" data-testid="dm-settings-options">
                    <label
                        v-for="option in options"
                        :key="option.id"
                        :for="idOf(option.id, 'input')"
                        class="flex min-h-14 cursor-pointer items-start gap-3 border-b border-surface px-4 py-3 last:border-b-0 hover:bg-surface-100 dark:hover:bg-surface-800"
                        :class="acceptDm === option.value ? 'bg-primary-50 dark:bg-primary-950/40' : ''"
                        :data-testid="`dm-settings-${option.id}`"
                    >
                        <RadioButton
                            :model-value="acceptDm"
                            :value="option.value"
                            :input-id="idOf(option.id, 'input')"
                            name="acceptDm"
                            class="mt-0.5 shrink-0"
                            :aria-labelledby="idOf(option.id, 'title')"
                            :pt="{ input: { 'aria-describedby': idOf(option.id, 'hint') } }"
                            @update:model-value="choose(option.value)"
                        />
                        <span class="min-w-0 flex-1">
                            <span :id="idOf(option.id, 'title')" class="block text-sm font-medium">{{ option.title }}</span>
                            <small :id="idOf(option.id, 'hint')" class="block text-xs text-muted-color">{{ option.hint }}</small>
                        </span>
                    </label>
                </div>
            </section>

            <p v-if="!acceptDm" class="m-0 rounded-lg bg-amber-100 px-3 py-2.5 text-xs leading-relaxed text-amber-900 dark:bg-amber-900/40 dark:text-amber-200" role="note" data-testid="dm-settings-refuse-note">
                {{ t('dm.settingsRefuseNote') }}
            </p>

            <!-- 법적 고지 두 줄 — 쪽지함·대화방에도 같은 뜻으로 적는다 -->
            <p class="m-0 rounded-lg bg-primary-50 px-3 py-2.5 text-xs leading-relaxed text-primary-800 dark:bg-primary-950/40 dark:text-primary-200" role="note" data-testid="dm-settings-notice-report">
                {{ t('dm.settingsNoticeReport') }}
            </p>
            <p class="m-0 rounded-lg bg-surface-100 px-3 py-2.5 text-xs leading-relaxed text-muted-color dark:bg-surface-800/60" role="note" data-testid="dm-settings-notice-retention">
                {{ t('dm.settingsNoticeRetention') }}
            </p>

            <p class="m-0 px-1 text-xs text-muted-color" data-testid="dm-settings-help">{{ t('dm.settingsBlockHelp') }}</p>

            <Button as="router-link" :to="UtilDm.INBOX_PATH" :label="t('dm.openInbox')" icon="pi pi-envelope" severity="secondary" variant="outlined" fluid data-testid="dm-settings-inbox" />
        </template>
    </div>
</template>

<script setup lang="ts">
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilDm from '~/utils/util-dm';

/**
 * 쪽지 수신 설정 `/settings/messages` — 로그인 필요(community `pages/settings/messages.vue`, `docs/api-dm.md`).
 * 「쪽지 받기 / 받지 않기」 둘 중 하나를 고르면 **바로 저장한다** — 먼저 반영하고(낙관적) 실패하면 되돌리고 서버 문구를 토스트(8초). 성공은 토스트(3초).
 * 저장 요청은 한 줄로 세워 차례로 보내고, 차례가 되면 그때의 화면 값을 보낸다. 받지 않아도 이미 받은 쪽지·기존 대화는 그대로다.
 * API 는 `useDmApi`(`GET·PUT /api/me/dm/settings`).
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('settings.messages'), description: () => t('dm.settingsSeo', { site: siteLabel.value }), robots: 'noindex' });

const api = useDmApi();
const toast = useAppToast();
const uid = useId();

type OptionId = 'accept' | 'refuse';
const options = computed(() => [
    { id: 'accept' as OptionId, value: true, title: t('dm.settingsAccept'), hint: t('dm.settingsAcceptHint') },
    { id: 'refuse' as OptionId, value: false, title: t('dm.settingsRefuse'), hint: t('dm.settingsRefuseHint') },
]);

function idOf(id: OptionId, part: 'input' | 'title' | 'hint'): string {
    return `${uid}-${id}-${part}`;
}

const status = ref<'loading' | 'ready' | 'error'>('loading');
const errorMessage = ref('');
const acceptDm = ref(true);
let confirmed = true;
let saveChain: Promise<void> = Promise.resolve();

let request = 0;
async function load() {
    const mine = ++request;
    status.value = 'loading';
    errorMessage.value = '';
    try {
        const res = await api.settings();
        if (mine !== request) return;
        confirmed = res.acceptDm;
        acceptDm.value = res.acceptDm;
        status.value = 'ready';
    } catch (e) {
        if (mine !== request) return;
        errorMessage.value = UtilBoardMessages.of(t, e);
        status.value = 'error';
    }
}

void load();
onBeforeUnmount(() => request++);

async function save(): Promise<void> {
    const target = acceptDm.value;
    if (target === confirmed) return;
    try {
        const saved = await api.updateSettings(target);
        confirmed = saved.acceptDm;
        if (acceptDm.value === target) acceptDm.value = saved.acceptDm;
        toast.success(t('dm.settingsSaved'));
    } catch (e) {
        if (acceptDm.value === target) acceptDm.value = confirmed;
        toast.error(t('dm.settingsSaveFailed', { reason: UtilBoardMessages.of(t, e) }), 8000);
    }
}

function choose(value: boolean) {
    if (value === acceptDm.value) return;
    acceptDm.value = value;
    saveChain = saveChain.then(save);
}
</script>
