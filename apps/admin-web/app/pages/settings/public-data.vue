<template>
    <!--
        공공데이터포털 설정(`/settings/public-data`) — 공공데이터포털 서비스 키 하나를 넣고 지운다(국세청 사업자 상태조회·진위확인이 같이 쓴다). 값은 **절대 보이지 않고**
        설정 여부·어디서 왔는지(화면 / 배포 환경 변수)·마지막 변경만 보인다. 포트원 설정(`/settings/portone`)과 같은 규칙이다.
        구획: ① 사용처(국세청 사업자 조회 — 지금 쓰는 구현, 읽기 전용) ② 서비스 키(입력·저장·지우기) ③ 연결 시험(저장된 키로 실제 국세청 상태조회 한 번).
        - 서버에 암호화 키가 없으면 맨 위 경고 — 저장할 수 없다(지우기는 된다).
        - 권한: 화면 `site.read`(메뉴가 거른다) · 저장·지우기 `site.secret` — 없으면 읽기 전용 안내와 상태만. 연결 시험은 `site.read`.
        - 저장하지 않은 입력이 있는데 다른 화면으로 가면 묻는다. 지우기는 확인을 묻는다.
        서버 API: `usePublicDataSettingsApi`(⚠ 지금은 목업).
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('publicData.title')" :subtitle="t('publicData.subtitle')" />

        <Message v-if="!canSecret" severity="info" :closable="false" size="small">{{ t('publicData.readOnly') }}</Message>

        <div v-if="loading && !view" class="flex flex-col gap-4" role="status" :aria-label="t('common.loading')">
            <AppGadget v-for="n in 3" :key="n" :title="t('common.loading')">
                <div class="flex flex-col gap-3">
                    <Skeleton height="0.75rem" width="60%" />
                    <Skeleton height="2.25rem" width="min(36rem, 100%)" />
                </div>
            </AppGadget>
        </div>

        <AppGadget v-else-if="!view" :title="t('publicData.title')">
            <ErrorState :title="t('publicData.loadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load()" />
        </AppGadget>

        <template v-else>
            <Message v-if="!view.cryptoKeyConfigured" severity="warn" :closable="false" size="small">
                <b>{{ t('publicData.cryptoMissingTitle') }}</b>
                <span class="mt-0.5 block">{{ t('publicData.cryptoMissingText') }}</span>
            </Message>

            <!-- ① 사용처 — 읽기 전용 -->
            <AppGadget :title="t('publicData.usageTitle')" anchor="public-data-usage">
                <div class="flex flex-col gap-3">
                    <div class="flex flex-wrap items-center gap-2">
                        <span class="text-sm">{{ t('publicData.usageNts') }}</span>
                        <Tag
                            class="public-data-tag"
                            :value="t(`publicData.provider.${view.ntsProvider}`)"
                            :icon="view.ntsProvider === 'ODCLOUD' ? 'pi pi-building-columns' : 'pi pi-wrench'"
                            :severity="view.ntsProvider === 'ODCLOUD' ? 'info' : 'warn'"
                            data-testid="public-data-provider"
                        />
                    </div>
                    <Message v-if="view.ntsProvider === 'FAKE'" severity="warn" :closable="false" size="small">{{ t('publicData.providerFake') }}</Message>
                    <Message v-else-if="view.serviceKey.source === 'NONE'" severity="error" :closable="false" size="small" data-testid="public-data-no-key">
                        <b>{{ t('publicData.noKeyTitle') }}</b>
                        <span class="mt-0.5 block">{{ t('publicData.noKeyText') }}</span>
                    </Message>
                </div>
            </AppGadget>

            <!-- ② 서비스 키 — 카드 하나가 form 하나 -->
            <form novalidate @submit.prevent="save">
                <AppGadget :title="t('publicData.keyTitle')" anchor="public-data-key" :changes="dirty ? 1 : 0" :invalid="keyError ? 1 : 0">
                    <template #actions>
                        <Tag class="public-data-tag shrink-0" :value="t(`publicData.source.${view.serviceKey.source}`)" :severity="view.serviceKey.source === 'NONE' ? 'warn' : 'success'" data-testid="public-data-source" />
                    </template>
                    <div class="flex max-w-xl flex-col gap-4">
                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('publicData.keyHelp') }}</p>

                        <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                            <span class="text-sm font-medium">{{ t('publicData.field') }}</span>
                            <span class="inline-flex items-center gap-1 text-xs" :class="view.serviceKey.source !== 'NONE' ? 'text-green-700 dark:text-green-400' : 'text-muted-color'">
                                <i :class="view.serviceKey.source !== 'NONE' ? 'pi pi-check-circle' : 'pi pi-circle'" aria-hidden="true" />
                                {{ view.serviceKey.source !== 'NONE' ? t('publicData.configured') : t('publicData.notConfigured') }}
                            </span>
                            <span v-if="view.serviceKey.updatedAt" class="text-xs text-muted-color tabular-nums">
                                · {{ view.serviceKey.updatedBy ? t('publicData.updatedBy', { at: time.full(view.serviceKey.updatedAt), name: view.serviceKey.updatedBy }) : t('publicData.updatedAt', { at: time.full(view.serviceKey.updatedAt) }) }}
                            </span>
                            <Button
                                v-if="canSecret && view.serviceKey.source === 'SCREEN'"
                                type="button"
                                class="ml-auto"
                                :label="t('publicData.remove')"
                                icon="pi pi-trash"
                                size="small"
                                severity="danger"
                                variant="text"
                                :loading="working === 'remove'"
                                :disabled="working !== null"
                                @click="remove"
                            />
                        </div>
                        <Message v-if="view.serviceKey.source === 'ENV'" severity="info" :closable="false" size="small">{{ t('publicData.envNotice') }}</Message>

                        <NotificationSecretField
                            v-if="canSecret"
                            v-model="keyInput"
                            :label="view.serviceKey.source === 'NONE' ? t('publicData.value') : t('publicData.newValue')"
                            :saved="false"
                            :help="view.serviceKey.source === 'NONE' ? t('publicData.valueHelp') : t('publicData.keepHint')"
                            :error="keyError"
                            :disabled="working !== null || !view.cryptoKeyConfigured"
                        />

                        <Message v-if="formError" severity="error" variant="simple" size="small">{{ formError }}</Message>

                        <div v-if="canSecret" class="flex flex-wrap items-center justify-end gap-2">
                            <span class="mr-auto text-xs text-muted-color">{{ dirty ? t('publicData.changes') : t('publicData.noChanges') }}</span>
                            <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" :disabled="working !== null || !dirty" @click="keyInput = ''" />
                            <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="working === 'save'" :disabled="working !== null || !dirty || !view.cryptoKeyConfigured" />
                        </div>
                    </div>
                </AppGadget>
            </form>

            <!-- ③ 연결 시험 -->
            <AppGadget :title="t('publicData.testTitle')" anchor="public-data-test">
                <div class="flex max-w-xl flex-col gap-3">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('publicData.testHelp') }}</p>
                    <Message v-if="view.lastTest" :severity="view.lastTest.result === 'OK' ? 'success' : 'error'" :closable="false" size="small" data-testid="public-data-test-result">
                        <b>{{ t(`publicData.testResult.${view.lastTest.result}`) }}</b>
                        <span class="mt-0.5 block text-xs tabular-nums">{{ view.lastTest.testedBy ? t('publicData.testedBy', { at: time.full(view.lastTest.testedAt), name: view.lastTest.testedBy }) : t('publicData.testedAt', { at: time.full(view.lastTest.testedAt) }) }}</span>
                    </Message>
                    <div>
                        <Button type="button" :label="t('publicData.testRun')" icon="pi pi-bolt" severity="secondary" variant="outlined" :loading="working === 'test'" :disabled="working !== null || dirty" @click="test" />
                        <span v-if="dirty" class="ml-2 text-xs text-muted-color">{{ t('publicData.testSaveFirst') }}</span>
                    </div>
                </div>
            </AppGadget>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { PublicDataSettingsView } from '~/composables/publicdata/api/usePublicDataSettingsApi.types';

const { t } = useI18n();
useSeoMeta({ title: () => t('publicData.title') });

const api = usePublicDataSettingsApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();

/** 저장·지우기 권한 — 숨기는 것은 편의이고 막는 것은 서버다 */
const canSecret = computed(() => can('site.secret'));

const view = ref<PublicDataSettingsView | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
/** 입력한 새 키 — 이 화면의 메모리에만 있고 저장에 성공하면 비운다 */
const keyInput = ref('');
const keyError = ref('');
const formError = ref('');
const working = ref<'save' | 'remove' | 'test' | null>(null);

const dirty = computed(() => keyInput.value.trim().length > 0);
watch(keyInput, () => (keyError.value = ''));

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

async function save(): Promise<void> {
    if (!canSecret.value || working.value || !view.value || !dirty.value) return;
    const value = keyInput.value.trim();
    if (/\s/.test(value)) {
        keyError.value = t('publicData.noSpaces');
        return;
    }
    working.value = 'save';
    formError.value = '';
    try {
        view.value = await api.save({ serviceKey: value });
        keyInput.value = '';
        toast.success(t('publicData.saved'));
    } catch (e) {
        const error = toApiError(e);
        const field = error.field('serviceKey');
        if (field) keyError.value = field;
        else {
            if (error.code === 'CRYPTO_KEY_MISSING' && view.value) view.value = { ...view.value, cryptoKeyConfigured: false };
            formError.value = error.message;
        }
    } finally {
        working.value = null;
    }
}

async function remove(): Promise<void> {
    if (working.value) return;
    const ok = await confirm({ title: t('publicData.removeTitle'), message: t('publicData.removeMessage'), confirmLabel: t('publicData.remove'), tone: 'danger' });
    if (!ok || working.value) return;
    working.value = 'remove';
    formError.value = '';
    try {
        await api.removeServiceKey();
        toast.success(t('publicData.removed'));
        view.value = await api.get();
    } catch (e) {
        formError.value = toApiError(e).message;
    } finally {
        working.value = null;
    }
}

async function test(): Promise<void> {
    if (working.value || !view.value) return;
    working.value = 'test';
    try {
        const lastTest = await api.test();
        view.value = { ...view.value, lastTest };
    } catch (e) {
        toast.error(toApiError(e).message);
    } finally {
        working.value = null;
    }
}

// 저장하지 않은 입력이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('publicData.leaveTitle'), message: t('publicData.leaveMessage'), confirmLabel: t('publicData.leaveConfirm'), tone: 'danger' });
});

onMounted(() => {
    void load();
});
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.public-data-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
