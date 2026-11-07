<template>
    <div class="content-card flex flex-col gap-4">
        <h1 class="text-lg font-bold">{{ t('settings.avatarUpload') }}</h1>

        <div v-if="status === 'loading'" class="space-y-3" role="status" :aria-label="t('avatarUpload.loading')" data-testid="avatar-upload-loading">
            <div class="h-16 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="h-28 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="h-40 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="status === 'error'" :title="t('avatarUpload.loadFailed')" :description="loadError" retry data-testid="avatar-upload-error" @retry="load()" />

        <template v-else-if="state">
            <p class="m-0 text-sm text-muted-color" data-testid="avatar-upload-intro">{{ t('avatarUpload.intro') }}</p>

            <!-- 이메일 인증 전·정지 중 — 신청·취소·지우기를 막는다(변경권 사기·보기는 된다). 정지는 서버가 403 으로 알려 준 뒤에만 안다 -->
            <div v-if="blocked" role="status" class="rounded-lg bg-amber-50 px-3 py-2.5 text-sm leading-relaxed text-amber-900 dark:bg-amber-950/40 dark:text-amber-200" data-testid="avatar-upload-blocked">
                <p class="m-0 font-semibold">{{ blocked === 'EMAIL_NOT_VERIFIED' ? t('avatarUpload.emailTitle') : t('avatarUpload.restrictedTitle') }}</p>
                <NuxtLink v-if="blocked === 'EMAIL_NOT_VERIFIED'" to="/verify-email" class="mt-1 inline-block font-semibold underline">{{ t('nicknameImage.verifyLink') }}</NuxtLink>
            </div>

            <!-- 변경권 — 쓸 수 있는 장 수 · 대기 신청에 예약된 장 · 상점 링크(값과 함께) -->
            <section class="flex flex-wrap items-center gap-3 panel-card p-4" :aria-labelledby="`${uid}-ticket`" data-testid="avatar-upload-ticket">
                <PointShopItemThumb kind="AVATAR_UPLOAD_TICKET" compact />
                <div class="min-w-0 flex-1">
                    <h2 :id="`${uid}-ticket`" class="m-0 text-sm font-semibold">{{ t('avatarUpload.ticketTitle', { count: state.ticketCount }) }}</h2>
                    <p class="m-0 mt-0.5 text-xs leading-relaxed text-muted-color">
                        {{ state.reserved ? t('avatarUpload.ticketReservedNote') : t('avatarUpload.ticketRule') }}
                    </p>
                </div>
                <Button
                    as="router-link"
                    :to="ticketPath"
                    :label="state.ticketPrice !== null ? t('avatarUpload.buyTicket', { points: UtilPoint.points(state.ticketPrice) }) : t('avatarUpload.buyTicketPlain')"
                    icon="pi pi-shopping-cart"
                    size="small"
                    :severity="state.ticketCount > 0 ? 'secondary' : undefined"
                    :variant="state.ticketCount > 0 ? 'outlined' : undefined"
                    class="max-sm:w-full"
                    data-testid="avatar-upload-buy-ticket"
                />
            </section>

            <!-- 내가 올린 아바타(승인된 것 한 장) — 쓰는 중이면 「사용 중」, 아니면 다시 프로필로 쓰기(변경권 없이) · 지우기(변경권은 돌려받지 않는다) -->
            <section v-if="state.current" class="panel-card p-4" :aria-labelledby="`${uid}-current`" data-testid="avatar-upload-current">
                <div class="flex flex-wrap items-center gap-4">
                    <ProfileBorderAvatar :initial="initial" :src="state.current.url" :still-src="state.current.stillUrl" size="lg" surface="card" :alt="t('avatarUpload.altCurrent')" />
                    <div class="min-w-0 flex-1">
                        <h2 :id="`${uid}-current`" class="m-0 flex flex-wrap items-center gap-1.5 text-sm font-semibold">
                            {{ t('avatarUpload.currentTitle') }}
                            <PointShopTag v-if="state.inUse" tone="primary">{{ t('avatar.inUse') }}</PointShopTag>
                            <PointShopTag v-if="state.current.animated">{{ t('avatar.motionBadge') }}</PointShopTag>
                        </h2>
                        <p class="m-0 mt-0.5 text-xs leading-relaxed text-muted-color">{{ state.inUse ? t('avatarUpload.currentInUse') : t('avatarUpload.currentKept') }}</p>
                        <!-- 실제 자리에서 어떻게 보이나 — 글 보기 머리(움직인다) · 글 목록(정지 그림) -->
                        <div class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-color">
                            <span class="inline-flex items-center gap-1.5">
                                {{ t('avatarUpload.whereView') }}
                                <ProfileBorderAvatar :initial="initial" :src="state.current.url" :still-src="state.current.stillUrl" size="xs" surface="card" />
                            </span>
                            <span class="inline-flex items-center gap-1.5">
                                {{ t('avatarUpload.whereList') }}
                                <ProfileBorderAvatar :initial="initial" :src="state.current.stillUrl ?? state.current.url" size="2xs" surface="card" />
                            </span>
                        </div>
                    </div>
                </div>
                <div class="mt-3 flex gap-2">
                    <Button
                        v-if="!state.inUse"
                        type="button"
                        :label="t('avatar.useInProfileShort')"
                        icon="pi pi-user"
                        class="flex-1"
                        :loading="busy === 'use'"
                        :disabled="busy !== null"
                        data-testid="avatar-upload-use"
                        @click="useCurrent"
                    />
                    <Button
                        type="button"
                        :label="t('avatarUpload.remove')"
                        severity="danger"
                        variant="outlined"
                        class="flex-1"
                        :loading="busy === 'remove'"
                        :disabled="busy !== null || blocked !== null"
                        data-testid="avatar-upload-remove"
                        @click="removeCurrent"
                    />
                </div>
            </section>

            <!-- 승인 대기 — 변경권 한 장이 이 신청에 예약돼 있다 · 취소하면 돌려받는다 -->
            <section v-if="state.request?.status === 'PENDING'" class="panel-card p-4" :aria-labelledby="`${uid}-pending`" data-testid="avatar-upload-pending">
                <div class="flex items-center justify-between gap-2">
                    <h2 :id="`${uid}-pending`" class="m-0 text-sm font-semibold"><PointShopTag tone="warn">{{ t('nicknameImage.pendingTag') }}</PointShopTag></h2>
                    <small class="text-xs text-muted-color">{{ t('nicknameImage.requestedOn', { date: time.date(state.request.createdAt) }) }}</small>
                </div>
                <div class="mt-3 flex items-center gap-3">
                    <ProfileBorderAvatar :initial="initial" :src="state.request.url" :still-src="state.request.stillUrl" size="md" surface="card" :alt="t('avatarUpload.altRequested')" />
                    <div class="min-w-0 flex-1 text-sm">
                        <p class="m-0">{{ t('avatarUpload.pendingText') }}</p>
                        <p v-if="state.reserved" class="m-0 mt-0.5 text-xs text-muted-color">{{ t('avatarUpload.pendingTicket') }}</p>
                    </div>
                </div>
                <Button
                    type="button"
                    :label="t('nicknameImage.cancelRequest')"
                    severity="secondary"
                    variant="outlined"
                    fluid
                    class="mt-3"
                    :loading="busy === 'cancel'"
                    :disabled="busy !== null || blocked !== null"
                    data-testid="avatar-upload-cancel"
                    @click="cancelRequest"
                />
            </section>

            <!-- 반려 — 사유 · 변경권은 돌아왔다 -->
            <section v-if="state.request?.status === 'REJECTED'" class="rounded-lg bg-red-50 px-3 py-2.5 text-sm leading-relaxed text-red-900 dark:bg-red-950/40 dark:text-red-200" role="status" data-testid="avatar-upload-rejected">
                <h2 class="m-0 text-sm font-semibold">{{ t('avatarUpload.rejectedTitle') }}</h2>
                <p class="m-0 mt-0.5 whitespace-pre-line break-words">{{ state.request.rejectReason?.trim() || t('nicknameImage.rejectReasonEmpty') }}</p>
                <p class="m-0 mt-1 text-xs">{{ t('avatarUpload.rejectedTicket') }}</p>
            </section>

            <!-- 신청 — 파일 고르기 · 원 안 미리보기(큰 것 + 글 목록 크기) · 동의 · 변경권 1장으로 신청. 대기 중이면 새 신청이 그것을 대체한다(예약된 장을 그대로 쓴다) -->
            <section class="panel-card p-4" :aria-labelledby="`${uid}-form`" data-testid="avatar-upload-form">
                <h2 :id="`${uid}-form`" class="m-0 text-sm font-semibold">{{ state.current || state.request?.status === 'PENDING' ? t('avatarUpload.formTitleChange') : t('avatarUpload.formTitle') }}</h2>
                <ul class="m-0 mt-2 list-none space-y-0.5 p-0 text-xs leading-relaxed text-muted-color" data-testid="avatar-upload-rules">
                    <li>{{ t('avatarUpload.ruleFormat') }}</li>
                    <li>{{ t('avatarUpload.ruleSize', sizeArgs) }}</li>
                    <li>{{ t('avatarUpload.ruleMotion') }}</li>
                    <li>{{ t('avatarUpload.ruleReview') }}</li>
                </ul>

                <input ref="fileInput" type="file" class="sr-only" :accept="UtilAvatarUpload.acceptOf(state.limits)" tabindex="-1" aria-hidden="true" data-testid="avatar-upload-file" @change="onFilePicked" />

                <button
                    v-if="!picked && !fileProblem"
                    type="button"
                    class="mt-3 grid size-28 place-items-center rounded-full border border-dashed border-surface-400 text-sm text-muted-color hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50 dark:hover:bg-surface-800"
                    :disabled="busy !== null"
                    data-testid="avatar-upload-pick"
                    @click="fileInput?.click()"
                >
                    <span class="flex flex-col items-center gap-1"><i class="pi pi-upload" aria-hidden="true" />{{ t('nicknameImage.pick') }}</span>
                </button>

                <div v-else-if="fileProblem" class="mt-3" data-testid="avatar-upload-problem">
                    <p role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fileProblem }}</p>
                    <Button type="button" :label="t('nicknameImage.pickAgain')" severity="secondary" variant="outlined" size="small" class="mt-2" :disabled="busy !== null" @click="fileInput?.click()" />
                </div>

                <div v-else-if="picked" class="mt-3" data-testid="avatar-upload-picked">
                    <div class="flex flex-wrap items-end gap-4">
                        <ProfileBorderAvatar :initial="initial" :src="picked.url" :still-src="picked.stillUrl" size="xl" surface="card" :alt="t('avatarUpload.altPicked')" />
                        <div class="flex flex-col gap-1.5 text-xs text-muted-color">
                            <span class="inline-flex items-center gap-1.5">{{ t('avatarUpload.whereView') }} <ProfileBorderAvatar :initial="initial" :src="picked.url" :still-src="picked.stillUrl" size="xs" surface="card" /></span>
                            <span class="inline-flex items-center gap-1.5">{{ t('avatarUpload.whereList') }} <ProfileBorderAvatar :initial="initial" :src="picked.stillUrl ?? picked.url" size="2xs" surface="card" /></span>
                        </div>
                    </div>
                    <p class="m-0 mt-2 text-xs text-muted-color">{{ picked.file.name }} · {{ UtilNicknameImage.formatBytes(picked.file.size) }}<template v-if="picked.animated"> · {{ t('avatar.motionBadge') }}</template></p>
                    <!-- 움직이는 GIF + 움직이는 닉네임 이미지 적용 중 — 승인되면 나중에 고른 쪽(아바타)이 남고 닉네임 이미지는 쉰다 -->
                    <p v-if="picked.animated && nicknameMotion" class="m-0 mt-2 rounded-lg bg-amber-50 px-3 py-2 text-xs leading-relaxed text-amber-900 dark:bg-amber-950/40 dark:text-amber-200" data-testid="avatar-upload-motion-note">
                        {{ t('avatarUpload.motionNicknameNote') }}
                    </p>
                    <Button type="button" :label="t('nicknameImage.pickAgain')" severity="secondary" variant="outlined" size="small" class="mt-2" :disabled="busy !== null" @click="fileInput?.click()" />
                </div>

                <div class="mt-4 flex items-start gap-2.5">
                    <Checkbox v-model="consent" binary :input-id="`${uid}-consent`" :disabled="busy !== null" data-testid="avatar-upload-consent" />
                    <label :for="`${uid}-consent`" class="cursor-pointer text-sm leading-snug">{{ t('avatarUpload.consent') }}</label>
                </div>

                <p v-if="state.ticketCount === 0 && !state.reserved" class="m-0 mt-3 text-xs text-muted-color" data-testid="avatar-upload-no-ticket">
                    {{ t('avatarUpload.noTicket') }} <NuxtLink :to="ticketPath" class="font-semibold text-primary hover:underline">{{ t('avatarUpload.goBuy') }}</NuxtLink>
                </p>

                <div v-if="busy === 'submit'" class="mt-3 w-full max-w-60" role="status" data-testid="avatar-upload-progress">
                    <div class="h-1.5 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700">
                        <div class="h-full rounded-full bg-primary transition-[width]" :style="{ width: `${Math.round(progress * 100)}%` }" />
                    </div>
                    <p class="m-0 mt-1 text-xs text-muted-color">{{ progress < 1 ? t('nicknameImage.uploading', { n: Math.round(progress * 100) }) : t('nicknameImage.processing') }}</p>
                </div>

                <p v-if="serverError" role="alert" class="m-0 mt-3 text-sm text-red-600 dark:text-red-400" data-testid="avatar-upload-server-error">{{ serverError }}</p>

                <Button
                    type="button"
                    :label="state.reserved ? t('avatarUpload.submitReplace') : t('avatarUpload.submit')"
                    fluid
                    class="mt-3"
                    :loading="busy === 'submit'"
                    :disabled="!canSubmit"
                    data-testid="avatar-upload-submit"
                    @click="submit"
                />
            </section>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { MyAvatarUpload } from '~/composables/avatar/api/useAvatarUploadApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilAvatar from '~/utils/util-avatar';
import UtilAvatarUpload from '~/utils/util-avatar-upload';
import type { AvatarUploadProblem } from '~/utils/util-avatar-upload';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilNicknameImage from '~/utils/util-nickname-image';
import UtilPoint from '~/utils/util-point';
import UtilPointShop from '~/utils/util-point-shop';

/**
 * 아바타 올리기 `/settings/avatar-upload` — 로그인 필요. **서버 연결됨**(`useAvatarUploadApi` — 계약 `docs/features/commerce-motion-avatar-contract.md` 「API 계약」).
 * 직접 만든 그림(정지 PNG·JPG·WebP · 정지·움직이는 GIF)을 **아바타 변경권 한 장**으로 신청하고 운영자 심사 뒤 프로필 아바타로 적용한다(사용자 확정 2026-11-06).
 *  - 변경권(community 방식): 신청하면 한 장이 그 신청에 예약 → 승인되면 쓴 것 · 반려·본인 취소·새 신청으로 대체되면 같은 장이 돌아온다 · 적용된 뒤 지우면 돌려주지 않는다.
 *  - 신청 조건은 이메일 인증·정지 아님 + 변경권뿐(닉네임 이미지의 관리자 신청 조건은 걸지 않는다 — 사용자 확정).
 *  - 승인되면 「내가 올린 아바타」 한 장이 되고 바로 프로필 아바타로 쓴다(나중에 고른 쪽이 남는다). 다른 아바타로 바꿨다가 다시 쓸 때는 변경권이 들지 않는다(`PUT /api/me/profile/avatar { avatarId: 'upload-mine' }`).
 *  - 움직이는 GIF 는 움직이는 아바타다 — 움직이는 닉네임 이미지와 하나만(`useAvatarChoice`). 글 목록에서는 정지 그림(서버가 만든 첫 장면). 화면의 canvas 정지 그림은 고른 그림 미리보기에만 쓴다.
 *  - 파일은 서버 한도(`limits`)로 미리 거르고(판정은 서버), 올린 뒤·취소·지우기·다시 쓰기 뒤에는 서버 상태를 다시 받는다(낙관적으로 바꾸지 않는다).
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('settings.avatarUpload'), description: () => t('avatarUpload.seo', { site: siteLabel.value }), robots: 'noindex' });

const uid = useId();
const time = useDisplayTime();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { user, emailVerified } = useAuth();
const api = useAvatarUploadApi();
const avatarApi = useAvatarApi();
const avatarChoice = useAvatarChoice();
const profileApi = useProfileApi();
const decorations = useMyDecoration();

const initial = computed(() => UtilPointShop.initial(user.value?.nickname));

/* ── 서버 상태 ── */
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const state = ref<MyAvatarUpload | null>(null);
let request = 0;
let alive = true;

async function load(silent = false): Promise<void> {
    const mine = ++request;
    if (!silent) status.value = 'loading';
    try {
        const value = await api.status();
        if (mine !== request) return;
        state.value = value;
        status.value = 'ready';
    } catch (e) {
        if (mine !== request) return;
        loadError.value = UtilBoardMessages.of(t, e);
        status.value = 'error';
    }
}
void load();

/** 사러 갈 곳 — 변경권 품목 상세(품목이 없으면 상점 「변경권」 탭) */
const ticketPath = computed(() => (state.value?.ticketItemId ? UtilPointShop.itemPath(state.value.ticketItemId) : UtilPointShop.listPath('NICKNAME_CHANGE_TICKET')));
/** 규격 문구의 값 — 서버 한도 */
const sizeArgs = computed(() => ({ kb: Math.round(UtilAvatarUpload.maxBytesOf(state.value?.limits) / 1024), min: UtilAvatarUpload.minSizeOf(state.value?.limits) }));

/** 신청을 막는 까닭 — 이메일 인증 전(로그인 정보) · 서버가 알려 준 정지 */
const serverBlocked = ref<'EMAIL_NOT_VERIFIED' | 'ACCOUNT_RESTRICTED' | null>(null);
const blocked = computed(() => (user.value && !emailVerified.value ? 'EMAIL_NOT_VERIFIED' : serverBlocked.value));

/** 움직이는 닉네임 이미지가 적용 중(움직이는 GIF 를 고르면 안내) */
const nicknameMotion = ref(false);
void avatarChoice.nicknameMotionApplied().then((on) => {
    if (alive) nicknameMotion.value = on;
});

const busy = ref<'submit' | 'cancel' | 'use' | 'remove' | null>(null);
const consent = ref(false);
const fileInput = ref<HTMLInputElement | null>(null);
const fileProblem = ref('');
const serverError = ref('');
const progress = ref(0);
let uploadController: AbortController | null = null;
/** 고른 그림 — 올릴 파일 + 미리보기(data URL) + 움직이는 GIF 의 첫 장면 PNG(미리보기용 — 저장할 정지 그림은 서버가 만든다) */
const picked = ref<{ file: File; url: string; stillUrl: string | null; animated: boolean } | null>(null);

const canSubmit = computed(
    () =>
        Boolean(picked.value) &&
        !fileProblem.value &&
        consent.value &&
        blocked.value === null &&
        busy.value === null &&
        Boolean(state.value) &&
        ((state.value?.ticketCount ?? 0) > 0 || Boolean(state.value?.reserved)),
);

function problemText(code: AvatarUploadProblem): string {
    return t(`avatarUpload.problem.${code}`, sizeArgs.value);
}

function readDataUrl(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = () => reject(reader.error);
        reader.readAsDataURL(file);
    });
}

function loadImage(url: string): Promise<HTMLImageElement | null> {
    return new Promise((resolve) => {
        const image = new Image();
        image.onload = () => resolve(image);
        image.onerror = () => resolve(null);
        image.src = url;
    });
}

/** 첫 장면을 서버 저장 크기 이하 PNG 로 — 고른 움직이는 GIF 의 미리보기(글 목록 크기·움직임 줄이기). 저장되는 정지 그림은 서버가 만든다 */
function stillOf(image: HTMLImageElement): string {
    const side = Math.min(state.value?.limits.maxSize || 256, image.naturalWidth);
    const canvas = document.createElement('canvas');
    canvas.width = side;
    canvas.height = side;
    canvas.getContext('2d')?.drawImage(image, 0, 0, side, side);
    return canvas.toDataURL('image/png');
}

let pickSeq = 0;
async function onFilePicked(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    const mine = ++pickSeq;
    picked.value = null;
    fileProblem.value = '';
    serverError.value = '';
    const limits = state.value?.limits ?? null;
    const early = UtilAvatarUpload.precheck(file, limits);
    if (early) {
        fileProblem.value = problemText(early);
        return;
    }
    const bytes = new Uint8Array(await file.arrayBuffer());
    if (mine !== pickSeq || !alive) return;
    if (file.type === 'image/webp' && UtilAvatarUpload.isAnimatedWebp(bytes)) {
        fileProblem.value = problemText('ANIMATED_WEBP');
        return;
    }
    const url = await readDataUrl(file);
    const image = await loadImage(url);
    if (mine !== pickSeq || !alive) return;
    if (!image) {
        fileProblem.value = problemText('UNREADABLE');
        return;
    }
    const sizeProblem = UtilAvatarUpload.checkSize(image.naturalWidth, image.naturalHeight, limits);
    if (sizeProblem) {
        fileProblem.value = problemText(sizeProblem);
        return;
    }
    const animated = file.type === 'image/gif' && UtilAvatarUpload.isAnimatedGif(bytes);
    picked.value = { file, url, stillUrl: animated ? stillOf(image) : null, animated };
}

/** 서버 거절·실패의 한 줄 — 서버 문구를 그대로 쓰고(가공하지 않는다), 화면이 아는 것과 어긋났으면 상태를 다시 받는다 */
async function failText(e: unknown): Promise<string> {
    if (!(e instanceof ApiError)) return UtilAuthMessages.fallback(t, 0, 'NETWORK');
    if (e.code === 'EMAIL_NOT_VERIFIED' || e.code === 'ACCOUNT_RESTRICTED') serverBlocked.value = e.code;
    let text = e.message || t('nicknameImage.failOther');
    if (e.code === 'AVATAR_UPLOAD_TICKET_REQUIRED') text = t('avatarUpload.noTicket');
    else if (e.code === 'AVATAR_UPLOAD_REQUEST_NOT_FOUND' || e.code === 'AVATAR_UPLOAD_NOT_FOUND') text = t('avatarUpload.failStale');
    else if (e.code === 'VALIDATION') text = e.field('file') || text;
    else if (e.status === 429 && e.retryAfterSeconds) text = t('nicknameImage.failRetryIn', { head: text, time: time.value.duration(e.retryAfterSeconds) });
    else if (e.status === 0) text = UtilAuthMessages.fallback(t, 0, 'NETWORK');
    // 변경권 수·대기 신청·내가 올린 아바타가 서버와 어긋났다
    if (e.code === 'AVATAR_UPLOAD_TICKET_REQUIRED' || e.status === 404) await load(true);
    return text;
}

/** 신청 — 변경권 한 장이 예약된다(대기 신청이 있으면 그것을 대체하고 예약된 장을 그대로 쓴다) */
async function submit() {
    const chosen = picked.value;
    if (!canSubmit.value || !chosen) return;
    const replacing = state.value?.request?.status === 'PENDING';
    if (replacing && !(await confirm({ title: t('avatarUpload.replaceConfirmTitle'), message: t('avatarUpload.replaceConfirmText'), confirmLabel: t('avatarUpload.submitReplace') }))) return;
    if (busy.value !== null || !alive) return;
    busy.value = 'submit';
    progress.value = 0;
    serverError.value = '';
    uploadController = new AbortController();
    try {
        // 201 = 신청 직후의 서버 상태(다시 읽은 것과 같은 모양)
        const next = await api.apply(chosen.file, { signal: uploadController.signal, onProgress: (fraction) => (progress.value = fraction) });
        if (!alive) return;
        state.value = next;
    } catch (e) {
        if (!alive) return;
        busy.value = null;
        serverError.value = await failText(e);
        return;
    }
    busy.value = null;
    picked.value = null;
    consent.value = false;
    toast.success(t('avatarUpload.toastSubmitted'));
}

/** 대기 신청 취소 — 변경권을 돌려받는다 */
async function cancelRequest() {
    if (busy.value !== null) return;
    if (!(await confirm({ title: t('avatarUpload.cancelConfirmTitle'), message: t('avatarUpload.cancelConfirmText'), confirmLabel: t('nicknameImage.cancelRequest') }))) return;
    busy.value = 'cancel';
    try {
        await api.cancelRequest();
        if (!alive) return;
        toast.success(t('avatarUpload.toastCancelled'));
        await load(true);
    } catch (e) {
        if (alive) toast.error(await failText(e), 8000);
    } finally {
        busy.value = null;
    }
}

/** 다시 프로필로 쓰기 — 변경권은 들지 않는다. 움직이는 것이면 하나만 규칙을 묻는다 */
async function useCurrent() {
    const current = state.value?.current;
    if (!current || busy.value !== null) return;
    if (!(await avatarChoice.confirmChoose({ animated: current.animated, refundable: false }, decorations.myAvatarStillUrl() !== null))) return;
    busy.value = 'use';
    try {
        const profile = await avatarApi.select({ avatarId: UtilAvatar.UPLOAD_ID });
        decorations.applyAvatar(profile.avatarUrl, profile.avatarStillUrl ?? null);
        if (!alive) return;
        toast.success(t('avatar.saved'));
        await load(true);
    } catch (e) {
        if (alive) toast.error(await failText(e), 8000);
    } finally {
        busy.value = null;
    }
}

/** 지우기 — 변경권은 돌려주지 않는다. 쓰는 중이었으면 서버가 마지막으로 쓴 정지 아바타로 돌린다 */
async function removeCurrent() {
    if (busy.value !== null) return;
    const ok = await confirm({ title: t('avatarUpload.removeConfirmTitle'), message: t('avatarUpload.removeConfirmText'), confirmLabel: t('avatarUpload.remove'), tone: 'danger' });
    if (!ok) return;
    busy.value = 'remove';
    try {
        await api.remove();
        if (!alive) return;
        toast.success(t('avatarUpload.toastRemoved'));
        // 내 아바타가 바뀌었을 수 있다 — 작성자 표시 겹치기를 서버 값으로 맞춘다(못 읽으면 새로고침 때 맞는다)
        try {
            const profile = await profileApi.me();
            decorations.applyAvatar(profile.avatarUrl, profile.avatarStillUrl ?? null);
        } catch {
            // 지우기는 끝났다 — 겹치기만 못 맞췄다
        }
        await load(true);
    } catch (e) {
        if (alive) toast.error(await failText(e), 8000);
    } finally {
        busy.value = null;
    }
}

onBeforeUnmount(() => {
    alive = false;
    request++;
    uploadController?.abort();
});
</script>
