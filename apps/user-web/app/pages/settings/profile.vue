<template>
    <div class="flex flex-col gap-4">
        <h1 class="text-lg font-bold">{{ t('profile.title') }}</h1>

        <div v-if="status === 'loading'" class="flex flex-col gap-4" role="status" :aria-label="t('profile.loading')" data-testid="profile-loading">
            <div class="h-40 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="status === 'error'" :title="t('profile.loadFailed')" :description="loadError" retry data-testid="profile-error" @retry="load" />
        <!-- 프로필 아바타(고르면 바로 저장) 구획 + 닉네임·소개 폼(한 화면 하나로 저장 — PUT 한 번, 저장 줄은 폼 맨 끝 `StickySaveBar`) -->
        <!-- 한 화면 하나로 저장(PUT 한 번) — 저장 줄은 폼 맨 끝(`StickySaveBar`) -->
        <template v-else-if="saved">
            <!-- 정지 중 — 프로필은 읽기 전용(서버 `403 ACCOUNT_RESTRICTED`). 풀리면 할 수 있는 일이라 폼을 감추지 않고 막은 채로 이유를 보인다 -->
            <div v-if="restricted" role="status" class="rounded-lg bg-amber-50 px-3 py-2.5 text-sm leading-relaxed text-amber-900 dark:bg-amber-950/40 dark:text-amber-200" data-testid="profile-restricted">
                <p class="m-0 font-semibold">{{ t('profile.restrictedTitle') }}</p>
                <p class="m-0 mt-0.5">{{ t('profile.restrictedText') }}</p>
                <NuxtLink :to="UtilSanction.PATH" class="mt-1 inline-block font-semibold underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="profile-restricted-link">
                    {{ t('profile.restrictedLink') }}
                </NuxtLink>
            </div>

            <!--
                프로필 아바타 — 이 구획은 **고르면 바로 저장**된다(`PUT /api/me/profile/avatar`). 아래 닉네임·소개 폼(맨 아래 떠 있는 저장 줄 하나로 저장)과 저장 단위가 달라
                폼 밖의 따로 된 구획으로 두고 「바로 저장돼요」를 늘 적는다(forms.md 2-4 — 다른 저장 단위의 단추·저장 줄을 섞지 않는다).
            -->
            <section class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" :aria-labelledby="`${uid}-avatar`" data-testid="profile-avatar">
                <div class="flex flex-wrap items-center gap-3">
                    <ProfileBorderAvatar :initial="UtilPointShop.initial(saved.nickname)" :src="avatarShown" size="lg" surface="card" :border-style="myDecoration?.profileBorder ?? null" />
                    <div class="min-w-0 flex-1">
                        <h2 :id="`${uid}-avatar`" class="m-0 text-sm font-semibold">{{ t('avatar.sectionTitle') }}</h2>
                        <p class="m-0 mt-0.5 text-xs leading-relaxed text-muted-color">{{ t('avatar.sectionHint') }}</p>
                    </div>
                    <Button
                        type="button"
                        :label="pickerOpen ? t('common.close') : t('avatar.change')"
                        :icon="pickerOpen ? 'pi pi-angle-up' : 'pi pi-images'"
                        size="small"
                        severity="secondary"
                        variant="outlined"
                        :disabled="restricted"
                        :aria-expanded="pickerOpen"
                        :aria-controls="`${uid}-picker`"
                        data-testid="profile-avatar-change"
                        @click="togglePicker"
                    />
                </div>

                <div v-if="pickerOpen" :id="`${uid}-picker`" class="mt-4 flex flex-col gap-3 border-t border-surface pt-4" data-testid="profile-avatar-picker">
                    <div v-if="catalogStatus === 'loading'" class="grid grid-cols-4 gap-2 sm:grid-cols-6" role="status" :aria-label="t('avatar.loading')">
                        <div v-for="n in 12" :key="n" class="h-[4.75rem] animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
                    </div>
                    <ErrorState v-else-if="catalogStatus === 'error'" :title="t('avatar.loadFailed')" :description="catalogError" retry class="py-6!" @retry="loadCatalog()" />
                    <template v-else-if="catalog">
                        <AvatarCategoryChips v-model="pickerCategory" :categories="pickerCategories" :label="t('avatar.categoriesLabel')" testid="profile-avatar-categories" />
                        <AvatarGrid :cells="pickerCells" :label="t(UtilAvatar.categoryKey(pickerCategory))" :disabled="Boolean(savingAvatarId) || restricted" :busy-id="savingAvatarId" testid="profile-avatar-grid" @choose="chooseAvatar" />
                    </template>
                    <NuxtLink
                        v-if="!businessAccount"
                        :to="UtilPointShop.listPath('PROFILE_AVATAR')"
                        class="inline-flex w-fit items-center gap-1 rounded text-sm font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                        data-testid="profile-avatar-shop"
                    >
                        <i class="pi pi-shopping-bag text-xs" aria-hidden="true" />{{ t('avatar.moreInShop') }}
                    </NuxtLink>
                </div>
            </section>
            <form :key="formKey" class="flex flex-col gap-4" novalidate data-testid="profile-form" @submit.prevent="save">
            <section class="flex flex-col gap-5 rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" :aria-label="t('profile.title')">
                <LabeledField
                    :label="t('signup.nicknameLabel')"
                    :hint="t('signup.nicknameHint', { min: UtilValidation.NICKNAME_MIN, max: UtilValidation.NICKNAME_MAX })"
                    :help="nicknameHelp"
                    :error="errors.nickname"
                >
                    <template #aside>
                        <small class="text-xs text-muted-color" aria-hidden="true">{{ form.nickname.length }}/{{ UtilValidation.NICKNAME_MAX }}</small>
                    </template>
                    <template #default="{ id, describedBy, invalid }">
                        <!-- 바꿀 수 없는 동안(이미 바꿈·정지 중)은 readonly — 값은 저장 요청에 그대로 실린다(`forms.md` 2-1) -->
                        <InputText
                            :id="id"
                            ref="nicknameInput"
                            v-model="form.nickname"
                            type="text"
                            name="nickname"
                            autocomplete="nickname"
                            autocapitalize="off"
                            spellcheck="false"
                            :maxlength="UtilValidation.NICKNAME_MAX"
                            :readonly="nicknameLocked || restricted"
                            :disabled="saving"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                            fluid
                            data-testid="profile-nickname"
                        />
                    </template>
                </LabeledField>
                <!-- 무료 변경을 썼고 변경권이 없다 — 포인트 상점의 변경권 거르기로(가격은 상점 품목 값) -->
                <NuxtLink
                    v-if="nicknameStage === 'NEED_TICKET' && !restricted"
                    :to="UtilPointShop.listPath('NICKNAME_CHANGE_TICKET')"
                    class="-mt-3 inline-flex w-fit items-center gap-1 rounded text-sm font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                    data-testid="profile-buy-ticket"
                >
                    <i class="pi pi-ticket text-xs" aria-hidden="true" />{{ t('profile.nicknameBuyTicket', { price: UtilPoint.points(saved.nicknameChange.ticketPrice ?? 0) }) }}
                </NuxtLink>

                <LabeledField :label="t('profile.bioLabel')" :hint="t('profile.bioHint', { max: PROFILE_BIO_MAX })" :help="t('profile.bioHelp')" :error="errors.bio">
                    <template #aside>
                        <small class="text-xs text-muted-color" aria-hidden="true">{{ form.bio.length }}/{{ PROFILE_BIO_MAX }}</small>
                    </template>
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea
                            :id="id"
                            v-model="form.bio"
                            name="bio"
                            rows="3"
                            auto-resize
                            :maxlength="PROFILE_BIO_MAX"
                            :readonly="restricted"
                            :disabled="saving"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                            fluid
                            data-testid="profile-bio"
                        />
                    </template>
                </LabeledField>
            </section>

            <StickySaveBar v-if="!restricted" :dirty="dirty" :saving="saving" :count="changedCount" :error-count="errorCount" :error="formError" :disabled="errorCount > 0" revertable submit @revert="revert" />
            </form>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { AvatarGridCell } from '~/components/avatar/AvatarGrid.types';
import type { AvatarCatalog } from '~/composables/avatar/api/useAvatarApi.types';
import type { MyProfile } from '~/composables/profile/api/useProfileApi.types';
import UtilAvatar from '~/utils/util-avatar';
import UtilBusiness from '~/utils/util-business';
import { PROFILE_BIO_MAX } from '~/composables/profile/api/useProfileApi.types';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilNicknameChange from '~/utils/util-nickname-change';
import UtilPoint from '~/utils/util-point';
import UtilPointShop from '~/utils/util-point-shop';
import UtilSanction from '~/utils/util-sanction';
import UtilValidation from '~/utils/util-validation';

/**
 * 프로필 `/settings/profile` — 로그인 필요. 닉네임·한 줄 소개를 고친다(`GET/PUT /api/me/profile` — 서버 연결됨).
 * 닉네임 규칙은 가입 화면과 같은 화면 검사(`UtilValidation.nickname` — 서버 `UserPolicy`)로 미리 알리고, 중복·예약어·변경 횟수는 서버가 정한다:
 * `409 NICKNAME_TAKEN` · `400 VALIDATION fields.nickname|bio` 는 칸 아래, `409 NICKNAME_TICKET_REQUIRED`(최상위 `ticketCount`·`ticketPrice`)는 닉네임 칸 아래 + 상태를 맞춘다, 그 밖은 저장 줄 안 한 줄.
 * 닉네임 변경(`member-mypage-contract.md` 결정 · 「후속 작업: 닉네임 변경권」): 가입 뒤 1번 무료(날짜 대기 없음), 그다음부터는 포인트 상점에서 산 변경권 1장씩.
 * 상태는 `nicknameChange` 를 셋으로 가른다(`UtilNicknameChange.stageOf`): 무료(1번 남음 안내) · 변경권 있음(칸 열림 + 「변경권 N장 · 바꾸면 1장 써요」, `useTicket: true` 로 저장) ·
 * 변경권 없음(칸 잠금 + 「포인트 상점에서 변경권 사기(가격)」 링크 — 목록의 변경권 거르기로).
 * 닉네임을 바꾸는 저장은 확인을 한 번 묻는다(무료: 이번이 한 번뿐 · 변경권: 1장을 쓴다). 소개만 고치는 저장은 횟수·변경권을 쓰지 않아 묻지 않는다.
 * 프로필 아바타 구획(결정 14): 지금 아바타(장착 테두리 둘레) + [바꾸기] → 고르기(무료 + 산 것, 카테고리 칩 + 격자), 누르면 바로 저장(`PUT /api/me/profile/avatar` — 폼과 따로).
 * 환불 기한 안의 유료 아바타는 「고르면 환불할 수 없어요」를 한 번 묻는다. 「더 많은 아바타는 포인트 상점에서」 링크는 사업자 전용 계정에게 숨긴다(무료는 고른다). 정지 중이면 고르기를 막는다.
 * 정지·영구 정지 중(로그인 정보 `restriction`, 또는 저장 때 서버 `403 ACCOUNT_RESTRICTED`)이면 폼은 읽기 전용이고 저장 줄을 그리지 않으며 제재 안내(`/sanction`) 링크를 보인다.
 * 저장하면 로그인 정보(`me`)를 다시 받아 셸의 닉네임을 맞춘다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('profile.title'), description: () => t('profile.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

const api = useProfileApi();
const auth = useAuth();
const toast = useAppToast();
const { confirm } = useConfirmDialog();

const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
/** 서버에 저장된 값 */
const saved = ref<MyProfile | null>(null);
const form = reactive({ nickname: '', bio: '' });
/** 저장한 뒤 폼을 다시 만든다(`forms.md` 2-1) */
const formKey = ref(0);
const saving = ref(false);
const formError = ref('');
/** 서버가 칸에 붙여 준 오류(입력을 바꾸면 그 칸 것만 지운다) */
const serverErrors = reactive<{ nickname: string; bio: string }>({ nickname: '', bio: '' });
const nicknameInput = ref<{ $el: HTMLInputElement } | null>(null);
/** 저장 때 서버가 정지 중이라고 알려 왔다(로그인 정보가 아직 정상이어도) */
const restrictedByServer = ref(false);
/** 정지·영구 정지 중 — 프로필 편집은 읽기 전용 */
const restricted = computed(() => restrictedByServer.value || UtilSanction.isRestricted(auth.restriction.value));

function messageOf(e: unknown): string {
    return e instanceof ApiError ? e.message : UtilAuthMessages.fallback(t, 0, 'NETWORK');
}

function fill(value: MyProfile) {
    saved.value = value;
    form.nickname = value.nickname;
    form.bio = value.bio;
    serverErrors.nickname = '';
    serverErrors.bio = '';
    formError.value = '';
    formKey.value++;
}

async function load() {
    status.value = 'loading';
    loadError.value = '';
    try {
        fill(await api.me());
        status.value = 'ready';
    } catch (e) {
        loadError.value = messageOf(e);
        status.value = 'error';
    }
}
void load();

watch(
    () => form.nickname,
    () => (serverErrors.nickname = ''),
);
watch(
    () => form.bio,
    () => (serverErrors.bio = ''),
);

/** 닉네임 변경의 지금 상태 — 무료 · 변경권 있음 · 변경권 필요 */
const nicknameStage = computed(() => UtilNicknameChange.stageOf(saved.value?.nicknameChange));
/** 닉네임을 바꿀 수 없는가(무료를 썼고 변경권이 없다) */
const nicknameLocked = computed(() => nicknameStage.value === 'NEED_TICKET');

const nicknameHelp = computed(() => {
    if (!saved.value) return '';
    if (nicknameStage.value === 'TICKET') return t('profile.nicknameTicketHelp', { count: saved.value.nicknameChange.ticketCount });
    return nicknameLocked.value ? t('profile.nicknameUsedUp') : t('profile.nicknameOnce');
});

/** 화면 검사 — 서버와 같은 닉네임 규칙(중복·예약어는 서버만 안다) */
const errors = computed(() => {
    const problem = nicknameChanged.value ? UtilValidation.nickname(form.nickname) : null;
    return {
        nickname: serverErrors.nickname || (problem ? UtilAuthMessages.nicknameProblem(t, problem, form.nickname.length) : ''),
        bio: serverErrors.bio || (form.bio.trim().length > PROFILE_BIO_MAX ? t('profile.bioTooLong', { max: PROFILE_BIO_MAX }) : ''),
    };
});
const errorCount = computed(() => Object.values(errors.value).filter(Boolean).length);

const nicknameChanged = computed(() => Boolean(saved.value) && form.nickname !== saved.value?.nickname);
const bioChanged = computed(() => Boolean(saved.value) && form.bio.trim() !== (saved.value?.bio ?? ''));
const changedCount = computed(() => Number(nicknameChanged.value) + Number(bioChanged.value));
const dirty = computed(() => changedCount.value > 0);

function revert() {
    if (saved.value) fill(saved.value);
}

/** 닉네임을 바꾸는 저장의 확인 — 가입 뒤 1번뿐인 무료 변경, 또는 변경권 1장을 쓰는 일이라 한 번 묻는다 */
async function confirmNicknameChange(): Promise<boolean> {
    const current = saved.value;
    if (!current || !nicknameChanged.value) return true;
    if (nicknameStage.value === 'TICKET') {
        const count = current.nicknameChange.ticketCount;
        const message = t('profile.nicknameTicketConfirmText', { from: current.nickname, to: form.nickname, count, after: Math.max(0, count - 1) });
        return await confirm({ title: t('profile.nicknameTicketConfirmTitle'), message, confirmLabel: t('profile.nicknameTicketConfirmButton') });
    }
    const message = t('profile.nicknameConfirmText', { from: current.nickname, to: form.nickname });
    return await confirm({ title: t('profile.nicknameConfirmTitle'), message, confirmLabel: t('profile.nicknameConfirmButton') });
}

/** 서버가 알려 준 변경권 상태로 맞춘다 — 무료는 다 썼다(`409 NICKNAME_TICKET_REQUIRED` 최상위 `ticketCount`·`ticketPrice`, 없으면 받아 둔 값) */
function markFreeUsed(e: ApiError) {
    if (!saved.value) return;
    const change = saved.value.nicknameChange;
    const count = Number(e.extraText('ticketCount'));
    const price = Number(e.extraText('ticketPrice'));
    saved.value = {
        ...saved.value,
        nicknameChange: {
            ...change,
            allowed: false,
            changeCount: Math.max(change.changeCount, change.freeLimit),
            ticketCount: Number.isFinite(count) && e.extraText('ticketCount') !== undefined ? count : change.ticketCount,
            ticketPrice: Number.isFinite(price) && e.extraText('ticketPrice') !== undefined ? price : change.ticketPrice,
        },
    };
}

async function save() {
    if (saving.value || !dirty.value || restricted.value) return;
    formError.value = '';
    if (errorCount.value > 0) {
        if (errors.value.nickname) nicknameInput.value?.$el.focus();
        return;
    }
    if (!(await confirmNicknameChange())) return;
    saving.value = true;
    try {
        // 무료를 썼고 변경권이 있으면 닉네임을 바꿀 때 1장을 쓴다(소개만이면 보내지 않는다 — 서버도 쓰지 않는다)
        const useTicket = nicknameStage.value === 'TICKET' && nicknameChanged.value;
        const value = await api.save({ nickname: form.nickname, bio: form.bio.trim(), ...(useTicket ? { useTicket: true } : {}) });
        const nicknameDone = nicknameChanged.value;
        fill(value);
        toast.success(t('common.saved'));
        // 셸(헤더·햄버거)의 닉네임 — 로그인 정보를 다시 받는다. 실패해도 저장은 끝났다
        if (nicknameDone) await auth.refresh().catch(() => undefined);
    } catch (e) {
        if (e instanceof ApiError && e.code === 'ACCOUNT_RESTRICTED') {
            // 정지 중 — 고친 값을 되돌리고 읽기 전용 + 제재 안내로 바꾼다(다음부터는 요청 없이 같은 안내)
            restrictedByServer.value = true;
            if (saved.value) fill(saved.value);
        } else if (e instanceof ApiError && (e.code === 'NICKNAME_TAKEN' || e.code === 'NICKNAME_TICKET_REQUIRED')) {
            // 화면이 본 상태가 낡았다(다른 탭에서 먼저 바꿈·변경권을 씀 등) — 서버가 알려 준 대로 상태를 맞춘다(변경권이 없으면 칸 잠금 + 상점 링크). 문구는 칸 아래(맞춘 뒤에 붙인다 — `fill` 이 지우지 않게)
            if (e.code === 'NICKNAME_TICKET_REQUIRED') markFreeUsed(e);
            serverErrors.nickname = e.message;
            nicknameInput.value?.$el.focus();
        } else if (e instanceof ApiError && e.code === 'VALIDATION' && (e.field('nickname') || e.field('bio'))) {
            serverErrors.nickname = e.field('nickname') ?? '';
            serverErrors.bio = e.field('bio') ?? '';
        } else {
            formError.value = messageOf(e);
        }
    } finally {
        saving.value = false;
    }
}

/* ── 프로필 아바타(고르면 바로 저장 — 폼과 따로) ── */
const uid = useId();
const avatarApi = useAvatarApi();
const decorations = useMyDecoration();
// 지금 아바타 둘레의 장착 테두리 — 서버에서 한 번 받는다
void useMyDecorationSync().ensure();
const businessAccount = computed(() => UtilBusiness.isBusinessAccount(auth.user.value?.role));
/** 지금 아바타 — 서버 `avatarUrl`(후보)에 방금 고른 것을 겹친다 */
const avatarShown = computed(() => decorations.avatarOf(auth.user.value?.id ?? null, saved.value?.avatarUrl) ?? UtilAvatar.FALLBACK_URL);
/** 장착한 테두리 — 지금 아바타 둘레 */
const myDecoration = computed(() => decorations.mine());

const pickerOpen = ref(false);
const catalog = ref<AvatarCatalog | null>(null);
const catalogStatus = ref<'idle' | 'loading' | 'ready' | 'error'>('idle');
const catalogError = ref('');
const pickerCategory = ref('');
const savingAvatarId = ref<string | null>(null);

/** 고를 수 있는 것 — 무료 + 내가 산 것 */
const pickerItems = computed(() => (catalog.value ? UtilAvatar.choosable(catalog.value.items) : []));
const pickerCategories = computed(() => (catalog.value ? UtilAvatar.categoriesOf(catalog.value.categories, pickerItems.value) : []));
const pickerCells = computed<AvatarGridCell[]>(() =>
    pickerItems.value
        .filter((a) => a.category === pickerCategory.value)
        .map((a) => {
            const caption = a.selected ? t('avatar.inUse') : a.refundable ? t('avatar.refundableCaption') : '';
            return { id: a.id, name: a.name, url: a.url, active: a.selected, caption, dim: false, ariaLabel: caption ? `${a.name} · ${caption}` : a.name };
        }),
);

/** 카탈로그를 받는다(처음 열 때 · 고른 뒤 조용히) — 지금 쓰는 아바타의 카테고리를 먼저 보인다 */
async function loadCatalog(quiet = false) {
    if (!quiet) {
        catalogStatus.value = 'loading';
        catalogError.value = '';
    }
    try {
        catalog.value = await avatarApi.catalog();
        catalogStatus.value = 'ready';
        if (!pickerCategories.value.some((c) => c.id === pickerCategory.value)) {
            const selected = catalog.value.items.find((a) => a.selected);
            pickerCategory.value = selected && pickerCategories.value.some((c) => c.id === selected.category) ? selected.category : (pickerCategories.value[0]?.id ?? '');
        }
    } catch (e) {
        if (quiet) return;
        catalogError.value = messageOf(e);
        catalogStatus.value = 'error';
    }
}

function togglePicker() {
    pickerOpen.value = !pickerOpen.value;
    if (pickerOpen.value && catalogStatus.value !== 'ready') void loadCatalog();
}

/** 고르면 바로 저장한다 — 환불 기한 안의 유료 아바타는 고르면 환불할 수 없어 한 번 묻는다 */
async function chooseAvatar(id: string) {
    const avatar = catalog.value?.items.find((a) => a.id === id);
    if (!avatar || avatar.selected || savingAvatarId.value || restricted.value) return;
    if (avatar.refundable && !(await confirm({ title: t('avatar.useConfirmTitle'), message: t('avatar.useConfirmText'), confirmLabel: t('avatar.useNow') }))) return;
    savingAvatarId.value = id;
    try {
        const profile = await avatarApi.select({ avatarId: id });
        // 닉네임·소개 폼은 건드리지 않는다(저장하지 않은 입력이 있을 수 있다) — 아바타 칸만 맞춘다
        if (saved.value) saved.value = { ...saved.value, avatarUrl: profile.avatarUrl };
        decorations.applyAvatar(profile.avatarUrl);
        toast.success(t('avatar.saved'));
        await loadCatalog(true);
    } catch (e) {
        if (e instanceof ApiError && e.code === 'ACCOUNT_RESTRICTED') restrictedByServer.value = true;
        toast.error(messageOf(e), 8000);
        if (e instanceof ApiError && (e.status === 409 || e.status === 404)) await loadCatalog(true);
    } finally {
        savingAvatarId.value = null;
    }
}

// 저장하지 않은 변경이 있으면 떠나기 전에 묻는다
onBeforeRouteLeave(async () => {
    if (!dirty.value || saving.value || restricted.value) return true;
    return await confirm({ title: t('settings.leaveTitle'), message: t('settings.leaveMessage'), confirmLabel: t('settings.leaveConfirm'), tone: 'danger' });
});
</script>
