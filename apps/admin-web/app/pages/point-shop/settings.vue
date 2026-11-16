<template>
    <!--
        포인트 상점 › 포인트 상점 설정(`/point-shop/settings`) — 지금은 셀프 환불 기한(1~30일, 기본 7) 한 칸(계약 B절 `point_shop_settings.refund_days`, commerce.md 7-3).
        실제 서버(`usePointShopAdminApi` — `GET/PUT /api/admin/point-shop/settings`)를 부른다.
        권한: 조회 `pointshop.read`(메뉴), 저장 `pointshop.manage`(없으면 저장 줄 대신 읽기 전용 안내, 칸은 `readonly`). 권한 코드는 `util-permission-catalog.ts`.
        저장은 한 번에(PUT) — 떠 있는 저장 줄(`StickySaveBar`, `forms.md` 2-4). 칸은 `InputText` + `inputmode=numeric`(`InputNumber` 는 범위 밖 값을 말없이 고친다).
        범위 검사는 `UtilPointShop`(서버와 같은 1~30) — 틀리면 저장 단추가 꺼진다. 서버가 거절한 칸(400 `fields.refundDays`)은 칸 아래 서버 문구, 칸에 붙지 않는 것은 저장 줄 안.
        **가정(정책 미확정)**: 기한을 바꾸면 그 뒤 구매뿐 아니라 아직 기한 안의 구매에도 적용된다 — 구매 시점 값이 아니라 지금 값으로 판정한다(서버 판정을 따른다). 화면에 안내로 적었다.
        변경 이력 서랍(`components.md` 2-5)은 이력 API 가 계약에 없어 두지 않았다(감사 로그에는 남는다 — 일반 운영).
        「포인트 게임」 구획(2026-11-16): 로또 번호 뽑기의 「당첨 번호 확인」 링크 — 복권 사업자 이름(1~30자)·주소(`https://` 절대 주소, 300자). 사업자가 바뀔 수 있어 운영자가 바꾼다.
        포인트 게임은 적립 포인트를 쓰는 곳이라 포인트 상점 설정 옆에 두었다(새 메뉴를 만들면 서버 `AdminScreenRegistry` 도 함께 바꿔야 한다). 실제 서버(`usePointGameAdminApi` — `GET/PUT /api/admin/point-games/settings`, 권한은 포인트 상점과 같은 `pointshop.read`·`pointshop.manage`).
        저장 줄은 하나다 — 바뀐 구획만 보내고(환불 기한·포인트 게임은 서로 다른 API), 한쪽이 실패하면 그 까닭만 저장 줄에 남는다.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('pointShop.settingsTitle')" :subtitle="t('pointShop.settingsSubtitle')" />

        <AppGadget v-if="loading && !saved" :title="t('pointShop.settings.refundSection')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 2" :key="n" height="3.5rem" />
            </div>
        </AppGadget>

        <AppGadget v-else-if="!saved" :title="t('pointShop.settings.refundSection')">
            <ErrorState :code="loadError?.status === 403 ? '403' : '!'" :title="loadError?.status === 403 ? t('pointShop.forbidden') : t('pointShop.settings.loadFailed')" :description="loadError?.message" :retry="loadError?.status !== 403" :retrying="loading" @retry="load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <AppGadget :title="t('pointShop.settings.refundSection')" anchor="point-shop-refund" :changes="refundDirty ? 1 : 0" :invalid="errorText ? 1 : 0">
                <div class="flex flex-col gap-4">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('pointShop.settings.refundLead') }}</p>

                    <LabeledField :label="t('pointShop.field.refundDays')" :hint="t('pointShop.range', { min: UtilPointShop.REFUND_DAYS.min, max: UtilPointShop.REFUND_DAYS.max })" :error="errorText" :help="t('pointShop.settings.refundHelp')">
                        <template #default="{ id, describedBy, invalid }">
                            <div class="flex flex-wrap items-center gap-2">
                                <InputText
                                    :id="id"
                                    :model-value="refundDays"
                                    name="refundDays"
                                    inputmode="numeric"
                                    autocomplete="off"
                                    :readonly="!canManage || saving"
                                    :invalid="invalid"
                                    :aria-describedby="describedBy"
                                    :aria-required="true"
                                    class="w-20 text-right tabular-nums"
                                    @update:model-value="onInput"
                                />
                                <span class="text-sm text-muted-color">{{ t('pointShop.unitDays') }}</span>
                                <template v-if="UtilPointShop.parseInt(refundDays) !== saved.defaultRefundDays">
                                    <span class="text-xs text-muted-color">{{ t('pointShop.settings.defaultValue', { value: saved.defaultRefundDays }) }}</span>
                                    <Button v-if="canManage" type="button" :label="t('pointShop.settings.useDefault')" icon="pi pi-replay" size="small" severity="secondary" variant="text" class="px-1! py-0!" :disabled="saving" @click="onInput(String(saved.defaultRefundDays))" />
                                </template>
                            </div>
                        </template>
                    </LabeledField>

                    <Message severity="info" :closable="false" size="small">{{ t('pointShop.settings.assumption') }}</Message>

                    <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-xs leading-relaxed text-muted-color">
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointShop.settings.noteRule') }}</span></li>
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointShop.settings.noteNotice') }}</span></li>
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointShop.settings.noteException') }}</span></li>
                    </ul>

                    <p class="m-0 text-xs text-muted-color">
                        {{ saved.updatedAt ? t('pointShop.settings.lastSaved', { at: time.full(saved.updatedAt), by: saved.updatedBy ?? '—' }) : t('pointShop.settings.neverSaved') }}
                    </p>
                </div>
            </AppGadget>

            <!-- 포인트 게임 — 로또 번호 뽑기의 「당첨 번호 확인」 링크(복권 사업자가 바뀔 수 있어 운영자가 바꾼다). 저장은 `usePointGameAdminApi` -->
            <AppGadget :title="t('pointGame.section')" anchor="point-game" :changes="gameChanges" :invalid="gameInvalid">
                <div v-if="gameLoading && !gameSaved" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                    <Skeleton v-for="n in 2" :key="n" height="3.5rem" />
                </div>
                <ErrorState v-else-if="!gameSaved" :title="t('pointGame.loadFailed')" :description="gameLoadError?.message" retry :retrying="gameLoading" @retry="loadGame" />
                <div v-else class="flex flex-col gap-4" data-testid="point-game-settings">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('pointGame.lead') }}</p>

                    <div class="grid gap-x-4 gap-y-3 sm:grid-cols-[minmax(0,14rem)_minmax(0,1fr)]">
                        <LabeledField :label="t('pointGame.providerName')" :hint="t('pointGame.providerNameHint', { max: UtilPointGame.PROVIDER_NAME_MAX })" :error="gameNameError" :help="t('pointGame.providerNameHelp')">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText
                                    :id="id"
                                    v-model="gameName"
                                    name="lotteryProviderName"
                                    autocomplete="off"
                                    :maxlength="UtilPointGame.PROVIDER_NAME_MAX + 10"
                                    :readonly="!canManage || saving"
                                    :invalid="invalid"
                                    :aria-describedby="describedBy"
                                    :aria-required="true"
                                    class="w-full"
                                    @update:model-value="gameServerErrors.lotteryProviderName = ''"
                                />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('pointGame.checkUrl')" :hint="t('pointGame.checkUrlHint')" :error="gameUrlError" :help="t('pointGame.checkUrlHelp')">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText
                                    :id="id"
                                    v-model="gameUrl"
                                    name="lotteryCheckUrl"
                                    type="url"
                                    inputmode="url"
                                    autocomplete="off"
                                    spellcheck="false"
                                    :readonly="!canManage || saving"
                                    :invalid="invalid"
                                    :aria-describedby="describedBy"
                                    :aria-required="true"
                                    class="w-full"
                                    @update:model-value="gameServerErrors.lotteryCheckUrl = ''"
                                />
                            </template>
                        </LabeledField>
                    </div>

                    <!-- 회원 화면에 보이는 링크 — 맞는 주소일 때만 새 탭으로 열어 볼 수 있다 -->
                    <div class="flex flex-wrap items-center gap-2 rounded-lg bg-surface-100 px-3 py-2 text-xs dark:bg-surface-800/60">
                        <span class="text-muted-color">{{ t('pointGame.preview') }}</span>
                        <span class="font-medium">{{ t('pointGame.previewLabel', { provider: gameName.trim() || '—' }) }}</span>
                        <a v-if="!gameUrlProblem" :href="gameUrl.trim()" target="_blank" rel="noopener noreferrer" class="ml-auto inline-flex items-center gap-1 font-medium text-primary hover:underline">
                            {{ t('pointGame.openLink') }}<i class="pi pi-external-link text-[0.6875rem]" aria-hidden="true" />
                        </a>
                    </div>
                    <div v-if="canManage && (gameName.trim() !== gameSaved.defaultProviderName || gameUrl.trim() !== gameSaved.defaultCheckUrl)" class="flex flex-wrap items-center gap-2">
                        <span class="text-xs text-muted-color">{{ t('pointGame.defaultValue', { provider: gameSaved.defaultProviderName, url: gameSaved.defaultCheckUrl }) }}</span>
                        <Button type="button" :label="t('pointGame.useDefault')" icon="pi pi-replay" size="small" severity="secondary" variant="text" class="px-1! py-0!" :disabled="saving" @click="useGameDefault" />
                    </div>

                    <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-xs leading-relaxed text-muted-color">
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointGame.noteNoWinning') }}</span></li>
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointGame.noteNoOfficial') }}</span></li>
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointGame.noteFixedRules') }}</span></li>
                    </ul>

                    <p class="m-0 text-xs text-muted-color">
                        {{ gameSaved.updatedAt ? t('pointGame.lastSaved', { at: time.full(gameSaved.updatedAt), by: gameSaved.updatedBy ?? '—' }) : t('pointGame.neverSaved') }}
                    </p>
                </div>
            </AppGadget>

            <StickySaveBar v-if="canManage" submit revertable :dirty="dirty" :saving="saving" :disabled="!canSave" :count="changeCount" :error-count="errorCount" :error="formError" @revert="revert" />
            <p v-else class="text-xs text-muted-color">{{ t('pointShop.settings.readOnly') }}</p>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { PointGameSettingsView } from '~/composables/pointgame/api/usePointGameAdminApi.types';
import type { PointShopSettingsView } from '~/composables/pointshop/api/usePointShopAdminApi.types';
import UtilPointGame from '~/utils/util-point-game';
import UtilPointGameMessages from '~/utils/util-point-game-messages';
import UtilPointShop from '~/utils/util-point-shop';
import UtilPointShopMessages from '~/utils/util-point-shop-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('pointShop.settingsTitle') });

const api = usePointShopAdminApi();
const gameApi = usePointGameAdminApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();

/** 저장 권한(`util-permission-catalog.ts`) — 숨기는 것은 편의이고 막는 것은 서버다. 포인트 게임 칸도 같은 권한으로 둔다(가정 — 서버 `Permission` 미정) */
const canManage = computed(() => can('pointshop.manage'));

const saved = ref<PointShopSettingsView | null>(null);
const refundDays = ref('');
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');
const serverError = ref('');

const problem = computed(() => UtilPointShop.refundDaysProblem(refundDays.value));
const errorText = computed(() => (problem.value ? UtilPointShopMessages.refundDaysProblem(t, problem.value) : serverError.value));
/** 숫자로 읽어 비교한다(「07」과 「7」은 같다), 읽을 수 없는 글자는 바뀐 것으로 본다 */
const refundDirty = computed(() => saved.value !== null && UtilPointShop.parseInt(refundDays.value) !== saved.value.refundDays);

/* ── 포인트 게임(당첨 확인 링크) ── */
const gameSaved = ref<PointGameSettingsView | null>(null);
const gameName = ref('');
const gameUrl = ref('');
const gameLoading = ref(false);
const gameLoadError = ref<ApiError | null>(null);
/** 서버가 거절한 칸 문구 — 그 칸을 고치면 지운다 */
const gameServerErrors = ref<{ lotteryProviderName: string; lotteryCheckUrl: string }>({ lotteryProviderName: '', lotteryCheckUrl: '' });

const gameNameProblem = computed(() => UtilPointGame.providerNameProblem(gameName.value));
const gameUrlProblem = computed(() => UtilPointGame.checkUrlProblem(gameUrl.value));
const gameNameError = computed(() => (gameNameProblem.value ? UtilPointGameMessages.providerName(t, gameNameProblem.value) : gameServerErrors.value.lotteryProviderName));
const gameUrlError = computed(() => (gameUrlProblem.value ? UtilPointGameMessages.checkUrl(t, gameUrlProblem.value) : gameServerErrors.value.lotteryCheckUrl));
/** 바뀐 칸 수 — 앞뒤 공백은 저장할 때 빠지므로 빼고 비교한다 */
const gameChanges = computed(() => (gameSaved.value ? Number(gameName.value.trim() !== gameSaved.value.lotteryProviderName) + Number(gameUrl.value.trim() !== gameSaved.value.lotteryCheckUrl) : 0));
const gameInvalid = computed(() => (gameSaved.value ? Number(Boolean(gameNameError.value)) + Number(Boolean(gameUrlError.value)) : 0));

/* ── 화면 전체(한 번에 저장) ── */
const dirty = computed(() => refundDirty.value || gameChanges.value > 0);
const changeCount = computed(() => (refundDirty.value ? 1 : 0) + gameChanges.value);
const errorCount = computed(() => (errorText.value ? 1 : 0) + gameInvalid.value);
/** 화면이 미리 찾은 문제가 있으면 저장 단추를 끈다(서버가 거절한 칸은 막지 않는다 — 고치지 않고 다시 보내 볼 수 있다) */
const canSave = computed(() => canManage.value && dirty.value && !problem.value && !(gameChanges.value > 0 && (gameNameProblem.value || gameUrlProblem.value)));

function onInput(value: string | undefined): void {
    refundDays.value = value ?? '';
    serverError.value = '';
}

onMounted(() => {
    void load();
    void loadGame();
});

async function loadGame(): Promise<void> {
    gameLoading.value = true;
    gameLoadError.value = null;
    try {
        applyGameSaved(await gameApi.settings());
    } catch (e) {
        gameLoadError.value = toApiError(e);
    } finally {
        gameLoading.value = false;
    }
}

function applyGameSaved(view: PointGameSettingsView): void {
    gameSaved.value = view;
    gameName.value = view.lotteryProviderName;
    gameUrl.value = view.lotteryCheckUrl;
    gameServerErrors.value = { lotteryProviderName: '', lotteryCheckUrl: '' };
}

function useGameDefault(): void {
    if (!gameSaved.value) return;
    gameName.value = gameSaved.value.defaultProviderName;
    gameUrl.value = gameSaved.value.defaultCheckUrl;
    gameServerErrors.value = { lotteryProviderName: '', lotteryCheckUrl: '' };
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        applySaved(await api.settings());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

function applySaved(view: PointShopSettingsView): void {
    saved.value = view;
    refundDays.value = String(view.refundDays);
    serverError.value = '';
    formError.value = '';
}

function revert(): void {
    if (saved.value) applySaved(saved.value);
    if (gameSaved.value) applyGameSaved(gameSaved.value);
    formError.value = '';
}

/** 환불 기한 저장 — 실패하면 문구를 돌려준다(칸에 붙는 서버 문구는 칸 아래로) */
async function saveRefund(): Promise<string | null> {
    const value = UtilPointShop.parseInt(refundDays.value);
    if (value === null) return null;
    try {
        applySaved(await api.saveSettings({ refundDays: value }));
        return null;
    } catch (e) {
        const failure = toApiError(e);
        const message = failure.code === 'VALIDATION' ? failure.field('refundDays') : undefined;
        if (message) {
            serverError.value = message;
            return t('pointShop.saveFieldsRejected');
        }
        return `${t('pointShop.saveFailed')} — ${failure.message}`;
    }
}

/** 포인트 게임 링크 저장 — 실패하면 문구를 돌려준다 */
async function saveGame(): Promise<string | null> {
    try {
        applyGameSaved(await gameApi.saveSettings({ lotteryProviderName: gameName.value.trim(), lotteryCheckUrl: gameUrl.value.trim() }));
        return null;
    } catch (e) {
        const failure = toApiError(e);
        const name = failure.code === 'VALIDATION' ? failure.field('lotteryProviderName') : undefined;
        const url = failure.code === 'VALIDATION' ? failure.field('lotteryCheckUrl') : undefined;
        if (name || url) {
            gameServerErrors.value = { lotteryProviderName: name ?? '', lotteryCheckUrl: url ?? '' };
            return t('pointShop.saveFieldsRejected');
        }
        return `${t('pointGame.saveFailed')} — ${failure.message}`;
    }
}

/**
 * 한 번에 저장 — 바뀐 구획만 보낸다. 환불 기한(`/api/admin/point-shop/settings`)과 포인트 게임(`/api/admin/point-games/settings`)은 따로 저장되므로 한쪽만 실패할 수 있다
 * (성공한 쪽은 저장된 값으로 바뀌어 「바뀐 칸」에서 빠지고, 실패한 쪽만 남아 저장 줄에 까닭이 선다).
 */
async function save(): Promise<void> {
    if (saving.value || !canSave.value) return;
    saving.value = true;
    formError.value = '';
    serverError.value = '';
    try {
        const failures: string[] = [];
        if (refundDirty.value) {
            const failure = await saveRefund();
            if (failure) failures.push(failure);
        }
        if (gameChanges.value > 0) {
            const failure = await saveGame();
            if (failure) failures.push(failure);
        }
        if (failures.length) formError.value = [...new Set(failures)].join(' ');
        else toast.success(t('pointShop.settings.saved'));
    } finally {
        saving.value = false;
    }
}

onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('pointShop.leaveTitle'), message: t('pointShop.leaveMessage'), confirmLabel: t('pointShop.leaveConfirm'), cancelLabel: t('pointShop.leaveCancel'), tone: 'danger' });
});
</script>
