<template>
    <!--
        탈퇴 회원의 연락용 이메일(분리 보관) — 회원 상세 「기본 정보」 구획 아래(탈퇴 회원만). commerce.md 17-2(결제 이력이 있으면 이메일 하나만 암호화해 따로 보관) · 10-9(운영자 열람 방식).
        - 보관함: 「연락용 이메일 · 분리 보관」 + 가린 값(「•••」) · 탈퇴 시각 · 파기 예정일 · [열람](`member.withdrawn.contact` 없으면 단추 대신 「열람 권한이 없어요」).
          열람(부모가 사유 대화상자 → 서버) 뒤에는 이메일 + [복사] + 다시 가릴 때까지 남은 시간 + [지금 가리기]. 서버가 정한 순간(기본 10분 뒤)이 지나면 상태가 지운다.
          아래 열람 기록(누가·언제·사유, 최근 것부터).
        - 보관 안 함: 「분리 보관한 연락처가 없어요(결제 이력이 없는 탈퇴)」.
        이메일 값은 부모 상태의 메모리에만 있다 — 토스트·로그에 싣지 않는다. 서버 호출은 하지 않는다(이벤트로 올린다).
    -->
    <section :aria-labelledby="titleId" class="flex flex-col gap-3 rounded-lg border border-(--line) px-3 py-3">
        <h3 :id="titleId" class="m-0 flex flex-wrap items-center gap-1.5 text-[13px] font-semibold">
            <i class="pi pi-inbox text-muted-color" aria-hidden="true" />
            {{ t('withdrawnContact.title') }}
            <MemberDetailTag :value="t('withdrawnContact.separated')" icon="pi pi-shield" severity="secondary" />
        </h3>

        <ErrorState v-if="view?.error" :title="t('withdrawnContact.loadFailed')" :description="view.error.message" retry :retrying="view.loading" @retry="emit('retry')" />

        <div v-else-if="!view?.contact" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 3" :key="n" height="1rem" />
        </div>

        <p v-else-if="!view.contact.stored" class="m-0 text-[13px] text-muted-color" data-testid="withdrawn-contact-none">{{ t('withdrawnContact.none') }}</p>

        <template v-else>
            <dl class="m-0 flex flex-col">
                <MemberDetailItem :label="t('withdrawnContact.email')">
                    <div v-if="view.revealed" class="flex flex-col gap-1.5" data-testid="withdrawn-contact-revealed">
                        <div class="flex flex-wrap items-center gap-2">
                            <code class="rounded bg-surface-100 px-1.5 py-0.5 text-[12px] [overflow-wrap:anywhere] dark:bg-surface-800">{{ view.revealed.email }}</code>
                            <Button type="button" :label="t('withdrawnContact.copy')" icon="pi pi-copy" size="small" severity="secondary" variant="text" @click="copy" />
                            <Button type="button" :label="t('withdrawnContact.maskNow')" icon="pi pi-eye-slash" size="small" severity="secondary" variant="text" @click="emit('mask')" />
                        </div>
                        <span class="text-xs text-muted-color tabular-nums" aria-live="off">{{ t('withdrawnContact.masksIn', { time: time.duration(view.remaining) }) }}</span>
                    </div>
                    <div v-else class="flex flex-wrap items-center gap-2">
                        <span class="tracking-widest text-muted-color" :aria-label="t('withdrawnContact.hidden')">•••</span>
                        <Button v-if="canReveal" type="button" :label="t('withdrawnContact.reveal')" icon="pi pi-eye" size="small" severity="secondary" variant="outlined" data-testid="withdrawn-contact-reveal" @click="emit('reveal')" />
                        <span v-else class="inline-flex items-center gap-1 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('withdrawnContact.noPermission') }}</span>
                    </div>
                </MemberDetailItem>
                <MemberDetailItem :label="t('withdrawnContact.withdrawnAt')" :empty="!view.contact.withdrawnAt">
                    <span class="tabular-nums">{{ time.full(view.contact.withdrawnAt) }}</span>
                </MemberDetailItem>
                <MemberDetailItem :label="t('withdrawnContact.purgeAfter')" :empty="!view.contact.purgeAfter">
                    <span class="tabular-nums">{{ time.serviceDate(view.contact.purgeAfter) }}</span>
                </MemberDetailItem>
            </dl>

            <div>
                <h4 class="m-0 mb-1.5 text-xs font-semibold text-muted-color">{{ t('withdrawnContact.revealsTitle') }}</h4>
                <p v-if="!view.contact.reveals.length" class="m-0 text-xs text-muted-color">{{ t('withdrawnContact.revealsEmpty') }}</p>
                <ul v-else class="m-0 flex list-none flex-col gap-1.5 p-0">
                    <li v-for="(r, index) in view.contact.reveals" :key="`${r.revealedAt}-${index}`" class="flex flex-col gap-0.5 rounded-md bg-surface-50 px-2.5 py-1.5 text-xs dark:bg-surface-900">
                        <span>
                            <b class="font-medium">{{ r.operatorName ?? t('member.detail.empty') }}</b>
                            <span class="ml-1.5 text-muted-color tabular-nums">{{ time.full(r.revealedAt) }}</span>
                        </span>
                        <span class="break-words text-muted-color">{{ r.reason }}</span>
                    </li>
                </ul>
            </div>
        </template>
    </section>
</template>

<script setup lang="ts">
import type { WithdrawnContactView } from '~/components/member/MemberDetailContent.types';

interface Props {
    view: WithdrawnContactView | null;
}

const props = withDefaults(defineProps<Props>(), {});
const emit = defineEmits<{ reveal: []; mask: []; retry: [] }>();

const { t } = useI18n();
const time = useDisplayTime();
const toast = useAppToast();
const titleId = useId();
const { can } = useAdminPermission();

/** 열람 권한(정의: util-permission-catalog.ts — SENSITIVE) */
const canReveal = computed(() => can('member.withdrawn.contact'));

/** 이메일 복사 — 토스트에는 값을 싣지 않는다 */
async function copy(): Promise<void> {
    const email = props.view?.revealed?.email;
    if (!email) return;
    try {
        await navigator.clipboard.writeText(email);
        toast.success(t('withdrawnContact.copied'));
    } catch {
        toast.error(t('withdrawnContact.copyFailed'), 6000);
    }
}
</script>
