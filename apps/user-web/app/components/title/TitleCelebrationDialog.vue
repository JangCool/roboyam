<template>
    <!--
        칭호 축하 창(member-title-contract 「화면」·「구현 결정」 4) — 로그인 뒤 `GET /api/me/titles` 의 `unseen[]` 이 있으면 한 번 뜬다(`useTitleCelebration`, 셸 `layouts/default.vue` 가 처음 필요할 때 코드를 받는다).
        맨 앞 칭호를 크게(4배 그림 + 2배 칩 + 보유 비율), 더 받은 것은 아래 작은 칩 줄. 「대표 칭호로 쓰기」(맨 앞 칭호) · 「명예의 전당 보기」. 어떻게 닫든 서버에 봤다고 알린다(`POST /api/me/titles/seen`).
    -->
    <Dialog
        :visible="open"
        modal
        :draggable="false"
        :closable="!busy"
        :header="t('memberTitle.celebrateTitle')"
        :style="{ width: 'min(24rem, calc(100vw - 2rem))' }"
        data-testid="title-celebration"
        @update:visible="(v: boolean) => !v && close()"
    >
        <div v-if="first" class="flex flex-col items-center gap-3 text-center">
            <TitleIcon :icon="first.icon" :tier="first.tier" :label="firstLabel" :scale="4" />
            <TitleChip :title="first" :scale="2" />
            <p class="m-0 text-sm">{{ t('memberTitle.celebrateText', { label: firstLabel }) }}</p>
            <p class="m-0 text-xs text-muted-color" data-testid="title-celebration-ratio">{{ t('memberTitle.celebrateRatio', { ratio: UtilMemberTitleMessages.ratio(t, first.ratio) }) }}</p>
            <div v-if="rest.length" class="w-full border-t border-surface pt-3">
                <p class="m-0 text-xs text-muted-color">{{ t('memberTitle.celebrateMore', { count: rest.length }) }}</p>
                <div class="mt-2 flex flex-wrap justify-center gap-1.5">
                    <TitleChip v-for="title in rest" :key="title.key" :title="title" />
                </div>
            </div>
            <Message v-if="error" severity="error" variant="simple" size="small">{{ error }}</Message>
        </div>
        <template #footer>
            <div class="flex w-full flex-col gap-2 sm:flex-row-reverse">
                <Button type="button" :label="t('memberTitle.celebrateUse')" icon="pi pi-check" :loading="busy" class="sm:flex-1" data-testid="title-celebration-use" @click="useFirst" />
                <Button type="button" :label="t('memberTitle.celebrateHall')" icon="pi pi-trophy" severity="secondary" variant="outlined" :disabled="busy" class="sm:flex-1" data-testid="title-celebration-hall" @click="goHall" />
            </div>
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilMemberTitle from '~/utils/util-member-title';
import UtilMemberTitleMessages from '~/utils/util-member-title-messages';

const { t } = useI18n();
const router = useRouter();
const toast = useAppToast();
const celebration = useTitleCelebration();
const { unseen } = celebration;
// 로그인한 회원의 `unseen` 을 묻는다(같은 회원은 한 번) — 셸이 첫 화면 뒤에 이 창을 그린다
celebration.watchLogin();

const open = computed(() => (unseen.value?.length ?? 0) > 0);
const first = computed(() => unseen.value?.[0] ?? null);
const rest = computed(() => unseen.value?.slice(1) ?? []);
const { localizedName } = useLocalizedName();
const firstLabel = computed(() => (first.value ? localizedName(first.value.label, first.value.labelEn ?? null) : ''));
const busy = ref(false);
const error = ref('');

function close(): void {
    if (busy.value) return;
    error.value = '';
    void celebration.dismiss();
}

async function useFirst(): Promise<void> {
    const title = first.value;
    if (!title || busy.value) return;
    busy.value = true;
    error.value = '';
    try {
        await celebration.makeRepresentative(title.key);
        toast.success(t('memberTitle.repSaved'));
        busy.value = false;
        close();
    } catch (e) {
        error.value = UtilBoardMessages.of(t, e);
        busy.value = false;
    }
}

function goHall(): void {
    close();
    void router.push(UtilMemberTitle.HALL_PATH);
}
</script>
