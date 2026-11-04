<template>
    <div>
        <!--
            개인정보처리방침(`/privacy`) — 기본 레이아웃, 비회원도 본다. `?version=N` 이면 그 버전(시행 예정·지난). 모양은 `TermsDocumentView`, 불러오기는 `useTermsDocument`(`docs/features/terms-contract.md`).
            본문(운영자가 넣은 DB 약관) **아래**에 「방문 통계」 칸(2단계 2026-11-04 — 이용자 선택권, `docs/features/site-analytics-contract.md`):
              - 「이 브라우저에서 방문 통계 수집 거부」 스위치 — 로컬 스토리지에 거부 표시(`UtilVisitStore.setOptedOut`) → 방문 신호 레이어가 보내지 않는다. 거부하면 이 브라우저의 방문자 ID 도 지운다.
              - 「이 브라우저의 방문 기록 지우기」 단추 — 확인 창 → `POST /api/visits/forget { visitorId }`(접수) → 이 브라우저의 방문자 ID 를 지운다(다음 방문부터 새 방문자 ID). 방문자 ID 가 없으면(기록 없음) 잠긴다.
              - 사이트의 방문 수집이 꺼져 있으면(사이트 정보 `analytics` — `UtilVisitSignal.isCollecting`) 칸을 숨긴다. 이용자가 거부했어도 사이트가 수집 중이면 칸은 보인다(다시 허용할 수 있게).
            서버 호출은 `useVisitForgetApi`. 오류 문구는 서버 문구 그대로(400 은 `fields.visitorId` 가 있으면 그것), 429 `VISIT_FORGET_RATE_LIMITED` 는 `retryAfterSeconds` 가 있으면 「약 N분 뒤」 안내를 붙인다.
        -->
        <TermsDocumentView
            kind="PRIVACY"
            :status="status"
            :doc="doc"
            :shown="shown"
            :error-message="errorMessage"
            :version-loading="versionLoading"
            :version-missing="versionMissing"
            :version-error="versionError"
            @retry="load"
        />

        <section v-if="visitShown" class="mx-auto mt-8 w-full max-w-3xl rounded-xl border border-surface p-4 sm:p-5" :aria-labelledby="`${uid}-visit-title`" data-testid="privacy-visit-choice">
            <h2 :id="`${uid}-visit-title`" class="m-0 text-base font-semibold">{{ t('visitPrivacy.title') }}</h2>
            <p class="mb-0 mt-1 text-sm leading-relaxed text-muted-color">{{ t('visitPrivacy.intro') }}</p>

            <!-- 수집 거부 -->
            <div class="mt-4 flex items-start gap-3">
                <ToggleSwitch :model-value="optedOut" :input-id="`${uid}-optout`" :aria-describedby="`${uid}-optout-help`" data-testid="privacy-visit-optout" @update:model-value="onOptOut" />
                <div class="flex min-w-0 flex-col gap-1">
                    <label :for="`${uid}-optout`" class="cursor-pointer text-sm font-medium">{{ t('visitPrivacy.optOutLabel') }}</label>
                    <small :id="`${uid}-optout-help`" class="text-xs leading-relaxed text-muted-color">{{ optedOut ? t('visitPrivacy.optOutOn') : t('visitPrivacy.optOutHelp') }}</small>
                </div>
            </div>

            <!-- 방문 기록 지우기 -->
            <div class="mt-4 flex flex-col gap-2 border-t border-surface pt-4">
                <div class="flex flex-wrap items-center gap-x-3 gap-y-2">
                    <Button
                        type="button"
                        :label="t('visitPrivacy.forgetButton')"
                        icon="pi pi-eraser"
                        size="small"
                        severity="secondary"
                        variant="outlined"
                        :loading="forgetting"
                        :disabled="!visitorId || forgetting"
                        data-testid="privacy-visit-forget"
                        @click="forget"
                    />
                    <span v-if="!visitorId && !received" class="text-xs text-muted-color">{{ t('visitPrivacy.forgetNone') }}</span>
                </div>
                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('visitPrivacy.forgetHelp') }}</p>
                <p v-if="received" class="m-0 rounded-lg bg-surface-100 px-3 py-2 text-sm leading-relaxed dark:bg-surface-800/60" role="status" data-testid="privacy-visit-received">
                    {{ t('visitPrivacy.received', { id: received.requestId, at: time.full(received.receivedAt) }) }}
                </p>
                <p v-if="forgetError" class="m-0 text-sm text-red-600 dark:text-red-400" role="alert">{{ forgetError }}</p>
            </div>
        </section>
    </div>
</template>

<script setup lang="ts">
import UtilVisitSignal from '#layers/analytics/utils/util-visit-signal';
import UtilVisitStore from '#layers/analytics/utils/util-visit-store';
import type { VisitForgetResponse } from '~/composables/analytics/api/useVisitForgetApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilTerms from '~/utils/util-terms';

const { t } = useI18n();
useSeoMeta({ title: () => t('common.privacy') });

const route = useRoute();
/** 주소의 버전 — 없거나 맞지 않으면 null(지금 약관) */
const version = computed(() => UtilTerms.parseVersion(route.query.version));
const { doc, status, errorMessage, shown, versionLoading, versionMissing, versionError, load } = useTermsDocument('PRIVACY', version);

onMounted(() => void load());

/* ── 방문 통계(이용자 선택권) ── */

const uid = useId();
const siteInfo = useSiteInfoState();
const forgetApi = useVisitForgetApi();
const toast = useAppToast();
const { confirm, choose } = useConfirmDialog();
const displayTime = useDisplayTime();
const time = computed(() => displayTime.value);

/** 사이트가 방문 통계를 모으는 중일 때만 칸을 보인다 */
const visitShown = computed(() => UtilVisitSignal.isCollecting(siteInfo.value?.analytics));

/** 브라우저 저장소 값 — 화면에 그릴 때 한 번 읽고, 스위치·지우기 뒤에 다시 읽는다 */
const optedOut = ref(false);
const visitorId = ref<string | null>(null);

function readStore(): void {
    optedOut.value = UtilVisitStore.optedOut();
    visitorId.value = UtilVisitStore.peekVisitorId();
}

onMounted(readStore);

/** 거부를 켤 때 방문자 ID 가 있으면 남은 기록도 지울지 묻는다 — 기본 권장은 「기록도 지우고 거부」(사용자 결정 2026-11-04). ID 를 먼저 지우면 그 뒤엔 기록을 찾을 수 없어서 */
async function onOptOut(value: boolean): Promise<void> {
    if (value && visitorId.value) {
        const answer = await choose({
            title: t('visitPrivacy.optOutAskTitle'),
            message: t('visitPrivacy.optOutAskMessage'),
            choices: [
                { key: 'erase', label: t('visitPrivacy.optOutAskErase'), tone: 'primary' },
                { key: 'only', label: t('visitPrivacy.optOutAskOnly'), tone: 'neutral' },
                { key: 'stay', label: t('common.cancel'), tone: 'neutral' },
            ],
        });
        if (answer === null || answer === 'stay') return;
        // 지우기 요청이 실패하면 거부도 하지 않는다 — ID 가 남아 있어야 다시 지울 수 있다
        if (answer === 'erase' && !(await sendForget())) return;
    }
    UtilVisitStore.setOptedOut(value);
    readStore();
    toast.success(value ? t('visitPrivacy.optOutSaved') : t('visitPrivacy.optInSaved'));
}

const forgetting = ref(false);
const forgetError = ref('');
const received = ref<VisitForgetResponse | null>(null);

async function forget(): Promise<void> {
    if (!visitorId.value || forgetting.value) return;
    const ok = await confirm({ title: t('visitPrivacy.confirmTitle'), message: t('visitPrivacy.confirmMessage'), confirmLabel: t('visitPrivacy.confirmLabel'), tone: 'danger' });
    if (ok) await sendForget();
}

/** 지우기 요청을 보내고, 접수되면 이 브라우저의 방문자 ID 를 지운다. 접수됐으면 true */
async function sendForget(): Promise<boolean> {
    const id = visitorId.value;
    if (!id || forgetting.value) return false;
    forgetting.value = true;
    forgetError.value = '';
    try {
        received.value = await forgetApi.forget({ visitorId: id });
        // 접수됐으면 이 브라우저의 방문자 ID 를 지운다 — 다음 방문 신호부터 새 방문자 ID
        UtilVisitStore.forget();
        readStore();
        return true;
    } catch (e) {
        forgetError.value = e instanceof ApiError ? forgetErrorText(e) : t('visitPrivacy.forgetFailed');
        return false;
    } finally {
        forgetting.value = false;
    }
}

/** 지우기 실패 문구 — 서버 문구 그대로. 입력 오류는 칸 문구가 더 구체적이고, 너무 잦은 요청(429)은 기다릴 시간을 붙인다 */
function forgetErrorText(e: ApiError): string {
    if (e.status === 429) {
        const minutes = e.retryAfterSeconds ? Math.max(1, Math.ceil(e.retryAfterSeconds / 60)) : null;
        return minutes === null ? e.message : `${e.message} ${t('visitPrivacy.forgetRetryAfter', { minutes })}`;
    }
    return e.field('visitorId') ?? e.message;
}
</script>
