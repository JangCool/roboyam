<template>
    <!--
        소셜 로그인 설정(`/settings/social-login`) — community `admin/app/pages/social-login.vue` 를 옮긴 것이다. 카카오·네이버·구글의 클라이언트 ID·시크릿을 보고·고치고·지운다.
        구획: 공급자마다 카드 하나(서버 순서 — 카카오 → 네이버 → 구글, `SocialProviderCard`, **카드마다 따로 저장**) + 설정 이력(제목 옆 「변경 이력」 단추 → 오른쪽 서랍, 공급자 거르기). 구획은 `AppGadget anchor` 로 우측 구역 내비에 오른다.
        - 서버에 암호화 키가 없으면(`cryptoKeyConfigured=false`) 맨 위 경고 — 저장할 수 없다(삭제는 된다).
        - 저장·삭제는 **한 번에 하나만**(진행 중이면 다른 카드도 잠근다). 저장은 응답으로 그 카드만 바꾸고, 삭제(204)는 목록을 새로 받는다. 둘 다 이력을 첫 쪽부터 다시 받는다.
        - 입력 오류(`fields.clientId|clientSecret|enabled`)는 그 칸 아래, 503 `CRYPTO_KEY_MISSING`(경고도 켠다)·404·네트워크는 카드 안 한 줄(서버 문구 그대로).
        - 권한: 화면 `site.read`(메뉴가 거른다) · 저장·삭제 `site.secret` — 없으면 입력이 잠기고 단추가 없다. 감사·이력은 서버가 남긴다.
        - 저장하지 않은 변경이 있는데 다른 화면으로 가면 묻는다.
        서버 API: `useSocialProviderSettingsApi`(`docs/features/login-contract.md` 「관리자 소셜 로그인 설정」).
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('social.title')" :subtitle="t('social.subtitle')">
            <template #actions>
                <Button :label="t('settingHistory.open')" icon="pi pi-history" severity="secondary" outlined size="small" @click="openHistory" />
            </template>
        </PageTitle>

        <Message v-if="!canSecret" severity="info" :closable="false" size="small">{{ t('social.readOnly') }}</Message>

        <div v-if="loading && !view" class="flex flex-col gap-4" role="status" :aria-label="t('common.loading')">
            <AppGadget v-for="n in 3" :key="n" :title="t('common.loading')">
                <div class="flex flex-col gap-3">
                    <Skeleton height="0.75rem" width="60%" />
                    <Skeleton height="2.25rem" width="min(36rem, 100%)" />
                    <Skeleton height="2.25rem" width="min(36rem, 100%)" />
                </div>
            </AppGadget>
        </div>

        <AppGadget v-else-if="!view" :title="t('social.title')">
            <ErrorState :title="t('social.loadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load()" />
        </AppGadget>

        <template v-else>
            <Message v-if="!view.cryptoKeyConfigured" severity="warn" :closable="false" size="small">
                <b>{{ t('social.cryptoMissingTitle') }}</b>
                <span class="mt-0.5 block">{{ t('social.cryptoMissingText') }}</span>
            </Message>

            <SocialProviderCard
                v-for="p in view.providers"
                :key="p.provider"
                :setting="p"
                :crypto-key-configured="view.cryptoKeyConfigured"
                :editable="canSecret"
                :saving="working?.provider === p.provider && working.action === 'save'"
                :removing="working?.provider === p.provider && working.action === 'remove'"
                :locked="working !== null && working.provider !== p.provider"
                :server-errors="serverErrors[p.provider]"
                :form-error="formErrors[p.provider] ?? ''"
                @save="(body) => save(p, body)"
                @remove="remove(p)"
                @changes="(count) => (dirtyCounts[p.provider] = count)"
            />

            <p class="m-0 max-w-[72ch] px-1 text-xs leading-relaxed text-muted-color">{{ t('social.footnote') }}</p>
        </template>

        <!-- 설정 이력 — 제목 옆 단추가 오른쪽 서랍으로 연다(최신이 위, 공급자 거르기) -->
        <SettingHistoryDrawer v-model:visible="historyOpen" :hint="t('social.historyHint')">
            <template #filters>
                <label :for="providerFilterId" class="sr-only">{{ t('settingHistory.provider') }}</label>
                <Select v-model="providerFilter" :input-id="providerFilterId" :options="providerOptions" option-label="label" option-value="value" size="small" class="w-36" />
            </template>
            <SettingHistoryTable
                :items="history.rows.value"
                :total="history.total.value"
                :first="history.first.value"
                :page-size="history.size.value"
                :loading="history.loading.value"
                :loaded="history.loaded.value"
                :error="history.error.value"
                show-provider
                :compact="prefs.density === 'compact'"
                @page="history.onPage"
                @retry="history.load"
            />
        </SettingHistoryDrawer>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { SocialProviderCode, SocialProviderSaveRequest, SocialProviderSettingsView, SocialProviderSettingView } from '~/composables/social/api/useSocialProviderSettingsApi.types';
import type { SocialFieldErrors } from '~/utils/util-social-login';
import UtilSocialLoginMessages from '~/utils/util-social-login-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('social.title') });

const api = useSocialProviderSettingsApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다
const { prefs } = useLayoutPrefs();

/** 저장·삭제에 필요한 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canSecret = computed(() => can('site.secret'));

const view = ref<SocialProviderSettingsView | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
/** 지금 하는 저장·삭제 — 하나만 둔다(중복 클릭 방지, 다른 카드도 잠근다) */
const working = ref<{ provider: SocialProviderCode; action: 'save' | 'remove' } | null>(null);
/** 공급자별 서버가 거절한 칸 문구 — 저장할 때마다 새 객체 */
const serverErrors = ref<Partial<Record<SocialProviderCode, SocialFieldErrors>>>({});
/** 공급자별 칸에 붙지 않는 오류(서버 문구 그대로) */
const formErrors = ref<Partial<Record<SocialProviderCode, string>>>({});
/** 공급자별 저장하지 않은 칸 수 — 화면을 떠날 때 묻는다 */
const dirtyCounts = reactive<Partial<Record<SocialProviderCode, number>>>({});

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        view.value = await api.list();
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

/* ── 이력 ── */

const providerFilterId = useId();
const providerFilter = ref<SocialProviderCode | ''>('');
const PROVIDERS: readonly SocialProviderCode[] = ['KAKAO', 'NAVER', 'GOOGLE'];
/** 거르기 선택지 — 이름은 서버가 준 목록(요청 언어)에서, 아직 못 받았으면 i18n 이름 */
const providerOptions = computed(() => [
    { value: '', label: t('settingHistory.allProviders') },
    ...PROVIDERS.map((code) => ({ value: code, label: view.value?.providers.find((p) => p.provider === code)?.label ?? t(`social.provider_${code}`) })),
]);

const history = useSettingHistory((page, size) => api.history({ provider: providerFilter.value || null, page, size }));
/** 이력 서랍 — 처음 열 때 받는다(화면을 열 때마다 이력을 부르지 않는다) */
const historyOpen = ref(false);
function openHistory(): void {
    historyOpen.value = true;
    if (!history.loaded.value && !history.loading.value) void history.load();
}
watch(providerFilter, () => {
    if (history.loaded.value) void history.reloadFirst();
});

/* ── 저장·삭제 ── */

function clearErrors(provider: SocialProviderCode): void {
    serverErrors.value = { ...serverErrors.value, [provider]: undefined };
    formErrors.value = { ...formErrors.value, [provider]: '' };
}

/** 등록·수정 — 시크릿은 이 함수의 인자로만 지나가고 어디에도 남기지 않는다 */
async function save(setting: SocialProviderSettingView, body: SocialProviderSaveRequest): Promise<void> {
    if (working.value || !view.value) return;
    const provider = setting.provider;
    working.value = { provider, action: 'save' };
    clearErrors(provider);
    try {
        const saved = await api.save(provider, body);
        if (view.value) view.value = { ...view.value, providers: view.value.providers.map((p) => (p.provider === saved.provider ? saved : p)) };
        toast.success(UtilSocialLoginMessages.saved(t, saved), 5000);
        if (history.loaded.value) void history.reloadFirst();
    } catch (e) {
        const error = toApiError(e);
        const fields: SocialFieldErrors = { clientId: error.field('clientId'), clientSecret: error.field('clientSecret'), enabled: error.field('enabled') };
        if (fields.clientId || fields.clientSecret || fields.enabled) {
            serverErrors.value = { ...serverErrors.value, [provider]: fields };
            return;
        }
        if (error.code === 'CRYPTO_KEY_MISSING' && view.value) view.value = { ...view.value, cryptoKeyConfigured: false };
        formErrors.value = { ...formErrors.value, [provider]: error.message };
    } finally {
        working.value = null;
    }
}

/** 설정 삭제 — 응답이 없으므로(204) 지운 뒤 목록을 새로 받는다 */
async function remove(setting: SocialProviderSettingView): Promise<void> {
    if (working.value) return;
    const ok = await confirm({ title: t('social.removeTitle', { name: setting.label }), message: t('social.removeMessage', { name: setting.label }), confirmLabel: t('social.remove'), tone: 'danger' });
    if (!ok || working.value) return;
    const provider = setting.provider;
    working.value = { provider, action: 'remove' };
    clearErrors(provider);
    try {
        await api.remove(provider);
        toast.success(t('social.removed', { name: setting.label }));
        if (history.loaded.value) void history.reloadFirst();
        try {
            view.value = await api.list();
        } catch (e) {
            loadError.value = toApiError(e);
        }
    } catch (e) {
        formErrors.value = { ...formErrors.value, [provider]: toApiError(e).message };
    } finally {
        working.value = null;
    }
}

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => {
    if (!Object.values(dirtyCounts).some((count) => (count ?? 0) > 0)) return true;
    return confirm({ title: t('social.leaveTitle'), message: t('social.leaveMessage'), confirmLabel: t('social.leaveConfirm'), tone: 'danger' });
});

onMounted(() => {
    void load();
});
</script>
