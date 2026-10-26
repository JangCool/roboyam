<template>
    <!--
        포트원 설정(`/settings/portone`, 커머스 0-4) — 포트원 V2 의 상점 ID · API 비밀키 · 본인인증 채널 키를 넣고 지운다. 값은 **절대 보이지 않고** 설정 여부·마지막 변경만 보인다.
        구획: ① 지금 쓰는 본인인증 구현(읽기 전용 — 서버 배포 설정 `roboyam.identity.provider` 가 정하고 화면에서 바꿀 수 없다) ② 「본인인증」 카드(`PortoneSecretCard`, 카드 단위 저장).
        2a 단계에 「결제」 카드가 같은 부품으로 ② 아래에 더해진다.
        - 서버에 암호화 키가 없으면(`cryptoKeyConfigured=false`) 맨 위 경고 — 저장할 수 없다(지우기는 된다). 소셜 로그인 설정과 같다.
        - 포트원인데 세 칸이 다 차지 않았으면(`identityReady=false`) 「본인인증을 쓸 수 없어요」 경고. 가짜면 「local·시험 전용」 안내.
        - 권한: 화면 `site.read`(메뉴가 거른다) · 저장·지우기 `site.secret` — 없으면 읽기 전용 안내와 상태만.
        - 저장하지 않은 변경이 있는데 다른 화면으로 가면 묻는다. 지우기는 확인을 묻는다.
        서버 API: `usePortoneSettingsApi`(서버 `PortOneSettingsController`).
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('portone.title')" :subtitle="t('portone.subtitle')" />

        <Message v-if="!canSecret" severity="info" :closable="false" size="small">{{ t('portone.readOnly') }}</Message>

        <div v-if="loading && !view" class="flex flex-col gap-4" role="status" :aria-label="t('common.loading')">
            <AppGadget v-for="n in 2" :key="n" :title="t('common.loading')">
                <div class="flex flex-col gap-3">
                    <Skeleton height="0.75rem" width="60%" />
                    <Skeleton height="2.25rem" width="min(36rem, 100%)" />
                </div>
            </AppGadget>
        </div>

        <AppGadget v-else-if="!view" :title="t('portone.title')">
            <ErrorState :title="t('portone.loadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load()" />
        </AppGadget>

        <template v-else>
            <Message v-if="!view.cryptoKeyConfigured" severity="warn" :closable="false" size="small">
                <b>{{ t('portone.cryptoMissingTitle') }}</b>
                <span class="mt-0.5 block">{{ t('portone.cryptoMissingText') }}</span>
            </Message>

            <!-- ① 지금 쓰는 본인인증 구현 — 읽기 전용 -->
            <AppGadget :title="t('portone.providerTitle')" anchor="portone-provider">
                <div class="flex flex-col gap-3">
                    <div class="flex flex-wrap items-center gap-2">
                        <span class="text-sm">{{ t('portone.providerNow') }}</span>
                        <Tag
                            class="portone-provider-tag"
                            :value="t(`portone.provider.${view.identityProvider}`)"
                            :icon="view.identityProvider === 'PORTONE' ? 'pi pi-mobile' : 'pi pi-wrench'"
                            :severity="view.identityProvider === 'PORTONE' ? 'info' : 'warn'"
                            data-testid="portone-provider"
                        />
                    </div>
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('portone.providerHelp') }}</p>
                    <Message v-if="view.identityProvider === 'FAKE'" severity="warn" :closable="false" size="small">{{ t('portone.providerFake') }}</Message>
                    <Message v-else-if="!view.identityReady" severity="error" :closable="false" size="small" data-testid="portone-not-ready">
                        <b>{{ t('portone.notReadyTitle') }}</b>
                        <span class="mt-0.5 block">{{ t('portone.notReadyText') }}</span>
                    </Message>
                </div>
            </AppGadget>

            <!-- ② 본인인증 카드 -->
            <PortoneSecretCard
                :title="t('portone.identityCard')"
                anchor="portone-identity"
                :description="t('portone.identityCardHelp')"
                :fields="identityFields"
                :editable="canSecret"
                :crypto-key-configured="view.cryptoKeyConfigured"
                :saving="working === 'save'"
                :removing-key="removingKey"
                :server-errors="serverErrors"
                :form-error="formError"
                @save="save"
                @remove="remove"
                @changes="(count) => (dirtyCount = count)"
            />

            <p class="m-0 max-w-[72ch] px-1 text-xs leading-relaxed text-muted-color">{{ t('portone.footnote') }}</p>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { PortoneIdentityFieldKey, PortoneIdentitySaveRequest, PortoneSettingsView } from '~/composables/portone/api/usePortoneSettingsApi.types';
import { PORTONE_IDENTITY_FIELDS } from '~/composables/portone/api/usePortoneSettingsApi.types';

const { t } = useI18n();
useSeoMeta({ title: () => t('portone.title') });

const api = usePortoneSettingsApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();

/** 저장·지우기 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canSecret = computed(() => can('site.secret'));

const view = ref<PortoneSettingsView | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
/** 지금 하는 저장·지우기 — 하나만 둔다 */
const working = ref<'save' | PortoneIdentityFieldKey | null>(null);
const serverErrors = ref<Record<string, string> | undefined>(undefined);
const formError = ref('');
const dirtyCount = ref(0);

const removingKey = computed(() => (working.value && working.value !== 'save' ? working.value : null));
/** 카드의 칸 — 서버 차례와 상관없이 정한 차례로 */
const identityFields = computed(() => {
    const fields = view.value?.fields ?? [];
    return PORTONE_IDENTITY_FIELDS.map((key) => fields.find((f) => f.key === key) ?? { key, configured: false, updatedAt: null, updatedBy: null });
});

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        view.value = await api.get();
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

/** 저장 — 값은 이 함수의 인자로만 지나가고 어디에도 남기지 않는다 */
async function save(body: Record<string, string>): Promise<void> {
    if (working.value || !view.value) return;
    working.value = 'save';
    serverErrors.value = undefined;
    formError.value = '';
    try {
        view.value = await api.saveIdentity(body as PortoneIdentitySaveRequest);
        toast.success(t('portone.saved'));
    } catch (e) {
        const error = toApiError(e);
        const fields: Record<string, string> = {};
        for (const key of PORTONE_IDENTITY_FIELDS) {
            const text = error.field(key);
            if (text) fields[key] = text;
        }
        if (Object.keys(fields).length) {
            serverErrors.value = fields;
            return;
        }
        if (error.code === 'CRYPTO_KEY_MISSING' && view.value) view.value = { ...view.value, cryptoKeyConfigured: false };
        formError.value = error.message;
    } finally {
        working.value = null;
    }
}

/** 칸 지우기 — 응답이 없으므로(204) 지운 뒤 새로 받는다 */
async function remove(key: string): Promise<void> {
    if (working.value) return;
    const field = key as PortoneIdentityFieldKey;
    const name = t(`portone.field.${field}`);
    const ok = await confirm({ title: t('portone.removeTitle', { name }), message: t('portone.removeMessage', { name }), confirmLabel: t('portone.remove'), tone: 'danger' });
    if (!ok || working.value) return;
    working.value = field;
    formError.value = '';
    try {
        await api.removeIdentityField(field);
        toast.success(t('portone.removed', { name }));
        try {
            view.value = await api.get();
        } catch (e) {
            loadError.value = toApiError(e);
        }
    } catch (e) {
        const error = toApiError(e);
        formError.value = error.message;
        // 이미 비어 있는 칸(다른 운영자가 먼저 지움 등) — 화면을 서버 상태로 맞춘다
        if (error.code === 'PORTONE_FIELD_NOT_FOUND') {
            try {
                view.value = await api.get();
            } catch (reloadError) {
                loadError.value = toApiError(reloadError);
            }
        }
    } finally {
        working.value = null;
    }
}

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => {
    if (dirtyCount.value === 0) return true;
    return confirm({ title: t('portone.leaveTitle'), message: t('portone.leaveMessage'), confirmLabel: t('portone.leaveConfirm'), tone: 'danger' });
});

onMounted(() => {
    void load();
});
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.portone-provider-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}
</style>
