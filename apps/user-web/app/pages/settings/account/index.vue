<template>
    <div class="mx-auto max-w-2xl">
        <h1 class="mb-4 text-lg font-bold">{{ t('account.title') }}</h1>

        <!-- 소셜 연결이 끝나지 않고 돌아왔다 — 다른 회원에 연결된 소셜 계정 · 취소·실패·시간 초과 -->
        <Message v-if="linkNotice" severity="error" :closable="false" size="small" class="mb-4" data-testid="account-link-notice">
            <b>{{ linkNotice.title }}</b> {{ linkNotice.text }}
        </Message>

        <div v-if="status === 'loading'" class="flex flex-col gap-4" role="status" :aria-label="t('account.loading')" data-testid="account-loading">
            <div v-for="n in 2" :key="n">
                <div class="mb-2 h-3 w-20 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                <div class="h-28 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            </div>
        </div>

        <ErrorState v-else-if="status === 'error'" :title="t('account.loadFailed')" :description="loadError" retry data-testid="account-error" @retry="load()" />

        <template v-else-if="view">
            <!-- 로그인 수단 — 비밀번호가 있는가 -->
            <section class="mb-6" :aria-labelledby="`${uid}-methods`">
                <h2 :id="`${uid}-methods`" class="mb-2 px-1 text-xs font-semibold text-muted-color">{{ t('account.methodsTitle') }}</h2>
                <div class="rounded-xl border border-surface bg-surface-0 dark:bg-surface-900">
                    <div class="flex min-h-14 items-center gap-3 px-4 py-2.5" data-testid="account-password-row">
                        <span class="grid size-8 shrink-0 place-items-center rounded-lg bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true">
                            <i class="pi pi-envelope" />
                        </span>
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ t('account.passwordRow') }}</span>
                            <small class="block text-xs leading-snug text-muted-color">{{ view.hasPassword ? t('account.passwordOn') : t('account.passwordOff') }}</small>
                        </span>
                    </div>
                </div>
            </section>

            <!-- 소셜 계정 연결 — 서버가 카카오·네이버·구글 3개를 모두 준다 -->
            <section :aria-labelledby="`${uid}-social`">
                <h2 :id="`${uid}-social`" class="mb-2 px-1 text-xs font-semibold text-muted-color">{{ t('account.socialTitle') }}</h2>
                <div class="rounded-xl border border-surface bg-surface-0 dark:bg-surface-900">
                    <div
                        v-for="p in view.providers"
                        :key="p.id"
                        class="flex min-h-14 items-center gap-3 border-b border-surface px-4 py-2.5 last:border-b-0"
                        :data-provider="p.id"
                        data-testid="account-social-row"
                    >
                        <span class="grid size-8 shrink-0 place-items-center rounded-lg text-sm font-bold" :class="UtilProvider.markClass(p.id)" aria-hidden="true">
                            {{ UtilProvider.mark(p.id) }}
                        </span>
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ nameOf(p) }}</span>
                            <small :id="`${uid}-${p.id}-hint`" class="block break-words text-xs leading-snug text-muted-color" data-testid="account-social-state">{{ stateText(p) }}</small>
                        </span>

                        <Button
                            v-if="p.linked"
                            type="button"
                            :label="t('account.unlink')"
                            size="small"
                            severity="secondary"
                            variant="outlined"
                            class="shrink-0"
                            :loading="busyId === p.id"
                            :disabled="!p.canUnlink || busyId !== null"
                            :aria-label="t('account.unlinkLabel', { provider: nameOf(p) })"
                            :aria-describedby="`${uid}-${p.id}-hint`"
                            data-testid="account-social-unlink"
                            @click="onUnlink(p)"
                        />
                        <Button
                            v-else-if="p.linkable"
                            type="button"
                            :label="t('account.link')"
                            size="small"
                            class="shrink-0"
                            :loading="busyId === p.id"
                            :disabled="busyId !== null"
                            :aria-label="t('account.linkLabel', { provider: nameOf(p) })"
                            data-testid="account-social-link"
                            @click="onLink(p)"
                        />
                    </div>
                </div>
                <!-- 마지막 로그인 수단이라 해제할 수 없는 줄이 있고, 다른 소셜 계정을 더 연결할 수 있을 때 -->
                <p v-if="showLastMethodHelp" class="mt-2 px-1 text-xs leading-relaxed text-muted-color" data-testid="account-social-help">{{ t('account.lastMethodHelp') }}</p>
            </section>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { MeSocialProvider, MeSocialResponse } from '~/composables/auth/api/useAuthApi.types';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilProvider from '~/utils/util-provider';

/**
 * 계정·로그인 연결 `/settings/account` — 로그인 필요(`auth` 미들웨어, 서버도 401). 사용자 메뉴(헤더 프로필 메뉴)의 「계정·로그인 연결」로 온다.
 * 로그인 수단(비밀번호 유무)과 소셜 계정(카카오·네이버·구글) 연결·해제. 원본(community) `pages/settings/account/index.vue` 의 소셜 연동 부분만 옮겼다.
 *
 * - **연결**은 `POST /api/me/social/{id}/link` 로 시작하고 응답의 `redirectUrl` 로 **페이지 이동**한다. 끝나면 서버가 `?linked=` · `?link=taken|failed&provider=&reason=` 를 붙여 이 화면으로 되돌려 보낸다 —
 *   읽은 뒤 주소에서 지운다(새로고침해도 같은 안내가 다시 뜨지 않게). 연결 완료는 토스트, 실패는 위의 안내.
 * - **해제**는 확인 대화상자를 거친다(`DELETE`). `409 SOCIAL_LAST_LOGIN_METHOD` 는 「다른 로그인 수단이 없어 해제할 수 없어요」, 그 밖의 실패는 서버 문구를 토스트(8초)로 보이고 서버 상태를 다시 받는다.
 * - 운영자가 꺼 둔(키 없는) 공급자는 줄은 보이고 단추가 없다(`linkable=false`). 마지막 로그인 수단의 해제 단추는 비활성 + 줄 설명에 이유.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('account.title'), description: () => t('account.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

const route = useRoute();
const router = useRouter();
const api = useMeSocialApi();
const auth = useAuth();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const uid = useId();

const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const view = ref<MeSocialResponse | null>(null);
/** 연결 시작·해제 요청을 보내는 중인 공급자 id — 그 줄만 로딩이고 나머지 단추는 잠긴다 */
const busyId = ref<string | null>(null);
/** 연결하지 못하고 돌아왔을 때의 안내 */
const linkNotice = ref<{ title: string; text: string } | null>(null);

/** 공급자 이름 — id 로 지금 언어의 이름을 고르고, 모르는 공급자만 서버 이름을 쓴다 */
function nameOf(p: { id: string; name: string }): string {
    return UtilProvider.name(t, p.id, p.name);
}

function stateText(p: MeSocialProvider): string {
    if (p.linked) return p.canUnlink ? t('account.linked') : `${t('account.linked')} · ${t('account.lastMethod')}`;
    return p.linkable ? t('account.notLinked') : t('account.unavailable');
}

const showLastMethodHelp = computed(() => {
    const providers = view.value?.providers ?? [];
    return providers.some((p) => p.linked && !p.canUnlink) && providers.some((p) => !p.linked && p.linkable);
});

function messageOf(e: unknown): string {
    return e instanceof ApiError ? e.message : UtilAuthMessages.fallback(t, 0, 'NETWORK');
}

let loadSeq = 0;
/** 연결 상태를 받는다. `initial` 이 false 면(해제·실패 뒤 새로 받기) 로딩으로 되돌리지 않고, 못 받아도 있던 내용을 그대로 둔다 */
async function load(initial = true) {
    const mine = ++loadSeq;
    if (initial) {
        status.value = 'loading';
        loadError.value = '';
    }
    try {
        const value = await api.list();
        if (mine !== loadSeq) return;
        view.value = value;
        status.value = 'ready';
    } catch (e) {
        if (mine !== loadSeq || (!initial && view.value)) return;
        loadError.value = messageOf(e);
        status.value = 'error';
    }
}

/** 로그인 정보(`authMethods`)를 새로 받는다 — 실패해도 연결·해제는 이미 됐다 */
async function refreshAuth() {
    try {
        await auth.refresh();
    } catch {
        /* 다음 화면 이동 때 다시 받는다 */
    }
}

// 소셜 연결에서 돌아온 주소 — 완료는 토스트, 실패는 안내. 읽은 뒤 주소의 결과 표시는 지운다
function readLinkResult() {
    const q = route.query;
    const provider = UtilProvider.name(t, typeof q.provider === 'string' ? q.provider : typeof q.linked === 'string' ? q.linked : null);
    let handled = false;
    if (typeof q.linked === 'string') {
        toast.success(t('account.linkedToast', { provider }));
        void refreshAuth();
        handled = true;
    } else if (q.link === 'taken') {
        linkNotice.value = { title: t('account.linkTakenTitle'), text: t('account.linkTaken', { provider }) };
        handled = true;
    } else if (q.link === 'failed') {
        linkNotice.value = { title: t('account.linkFailedTitle', { provider }), text: UtilAuthMessages.socialReason(t, q.reason) };
        handled = true;
    }
    if (handled || 'link' in q || 'provider' in q || 'reason' in q) void router.replace({ path: route.path, hash: route.hash });
}

readLinkResult();
void load();
onBeforeUnmount(() => loadSeq++);

/** 연결 시작 — 응답의 주소로 페이지 이동한다(이동하는 동안 단추는 로딩인 채로 둔다) */
async function onLink(p: MeSocialProvider) {
    if (busyId.value) return;
    busyId.value = p.id;
    linkNotice.value = null;
    try {
        if (api.follow(await api.startLink(p.id))) return;
        toast.error(t('account.linkStartFailed'), 8000);
    } catch (e) {
        toast.error(messageOf(e), 8000);
        await load(false); // 이미 연결돼 있다(409)·쓸 수 없는 공급자(404)처럼 화면이 아는 것과 서버가 어긋났을 수 있다
    }
    busyId.value = null;
}

/** 연결 해제 — 확인 대화상자 → 요청 → 서버 상태를 다시 받는다 */
async function onUnlink(p: MeSocialProvider) {
    if (busyId.value) return;
    const name = nameOf(p);
    const ok = await confirm({ title: t('account.unlinkTitle', { provider: name }), message: t('account.unlinkMessage'), confirmLabel: t('account.unlink'), tone: 'danger' });
    if (!ok) return;

    busyId.value = p.id;
    linkNotice.value = null;
    let done = false;
    try {
        await api.unlink(p.id);
        done = true;
        toast.success(t('account.unlinked', { provider: name }));
    } catch (e) {
        // 마지막 로그인 수단 — 화면 문구로(서버 상태와 어긋났던 것이라 다시 받으면 단추가 잠긴다)
        toast.error(e instanceof ApiError && e.code === 'SOCIAL_LAST_LOGIN_METHOD' ? t('account.lastMethod') : messageOf(e), 8000);
    }
    await load(false);
    busyId.value = null;
    if (done) await refreshAuth();
}

// 소셜 화면에서 "뒤로" 로 돌아오면(bfcache) 연결 단추가 로딩인 채로 남지 않게 푼다
function onPageShow(e: PageTransitionEvent) {
    if (e.persisted) busyId.value = null;
}
onMounted(() => window.addEventListener('pageshow', onPageShow));
onBeforeUnmount(() => window.removeEventListener('pageshow', onPageShow));
</script>
