<template>
    <!--
        이모티콘 고르기 창의 내용 — 데스크톱 팝오버·모바일 하단 시트(`EmoticonPicker`)가 같이 넣는다.
        탭: 「이모지」(유니코드 글자 — 최근 사용 + 묶음, 누구나) + 받은 팩마다 한 탭(대표 그림, 64px 격자 — 누르면 토큰).
        운영자 회원 계정(`useMyEmoticons().allPacks`)이면 가진 팩 뒤에 나머지 판매 중 팩을 더하고 작은 「운영자」 표시를 붙인다(판매 중단 팩은 가진 것만).
        받은 팩이 없으면 상점 링크, 로그인하지 않았으면 이모지 탭만 + 로그인 안내 한 줄.
    -->
    <div class="flex flex-col" data-testid="emoticon-picker-panel">
        <!-- 탭 줄 — 넘치면 옆으로 민다 -->
        <div role="tablist" :aria-label="t('emoticon.pickerTabs')" class="no-scrollbar flex shrink-0 gap-1 overflow-x-auto border-b border-surface px-2 py-1.5">
            <button
                :id="`${uid}-tab-emoji`"
                type="button"
                role="tab"
                :aria-selected="tab === EMOJI"
                :aria-controls="`${uid}-panel`"
                class="grid size-10 shrink-0 place-items-center rounded-lg text-xl focus-visible:outline-2 focus-visible:outline-primary"
                :class="tab === EMOJI ? 'bg-surface-200 dark:bg-surface-700' : 'hover:bg-surface-100 dark:hover:bg-surface-800'"
                :title="t('emoticon.emojiTab')"
                data-testid="picker-tab-emoji"
                @click="tab = EMOJI"
            >
                <span aria-hidden="true">😀</span><span class="sr-only">{{ t('emoticon.emojiTab') }}</span>
            </button>
            <button
                v-for="pack in packList"
                :id="`${uid}-tab-${pack.id}`"
                :key="pack.id"
                type="button"
                role="tab"
                :aria-selected="tab === pack.id"
                :aria-controls="`${uid}-panel`"
                class="relative grid size-10 shrink-0 place-items-center overflow-hidden rounded-lg p-0.5 focus-visible:outline-2 focus-visible:outline-primary"
                :class="tab === pack.id ? 'bg-surface-200 dark:bg-surface-700' : 'hover:bg-surface-100 dark:hover:bg-surface-800'"
                :title="pack.operator ? t('emoticon.pickerOperatorPack', { name: pack.name }) : pack.name"
                :data-testid="`picker-tab-${pack.id}`"
                @click="tab = pack.id"
            >
                <img :src="pack.coverUrl" :alt="pack.operator ? t('emoticon.pickerOperatorPack', { name: pack.name }) : pack.name" class="size-full object-contain" loading="lazy" />
                <!-- 운영자 회원 계정이라 쓸 수 있는 팩(가지지 않은 판매 중 팩) — 오른쪽 아래 작은 방패 -->
                <span
                    v-if="pack.operator"
                    class="absolute bottom-0 right-0 grid size-3.5 place-items-center rounded-full bg-primary text-primary-contrast"
                    aria-hidden="true"
                    data-testid="picker-tab-operator"
                >
                    <i class="pi pi-shield text-[0.5rem]" />
                </span>
            </button>
            <NuxtLink
                v-if="loggedIn"
                :to="UtilEmoticon.PATH"
                class="ml-auto grid size-10 shrink-0 place-items-center rounded-lg text-muted-color hover:bg-surface-100 hover:text-color focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                :title="t('emoticon.shopTitle')"
                :aria-label="t('emoticon.shopTitle')"
                @click="$emit('navigate')"
            >
                <i class="pi pi-plus text-sm" aria-hidden="true" />
            </NuxtLink>
        </div>

        <div :id="`${uid}-panel`" role="tabpanel" :aria-labelledby="`${uid}-tab-${tab === EMOJI ? 'emoji' : tab}`" class="h-72 overflow-y-auto px-2 py-2">
            <!-- 이모지 -->
            <template v-if="tab === EMOJI">
                <section v-if="recentList.length" class="mb-2" :aria-labelledby="`${uid}-recent`">
                    <h4 :id="`${uid}-recent`" class="m-0 px-1 pb-1 text-[0.6875rem] font-semibold text-muted-color">{{ t('emoticon.emojiRecent') }}</h4>
                    <div class="grid grid-cols-8 gap-0.5">
                        <button v-for="e in recentList" :key="`r-${e}`" type="button" :class="EMOJI_CELL" :aria-label="e" @click="pickEmoji(e)">{{ e }}</button>
                    </div>
                </section>
                <section v-for="group in groups" :key="group.key" class="mb-2" :aria-labelledby="`${uid}-${group.key}`">
                    <h4 :id="`${uid}-${group.key}`" class="m-0 px-1 pb-1 text-[0.6875rem] font-semibold text-muted-color">{{ t(group.labelKey) }}</h4>
                    <div class="grid grid-cols-8 gap-0.5">
                        <button v-for="e in group.items" :key="`${group.key}-${e}`" type="button" :class="EMOJI_CELL" :aria-label="e" @click="pickEmoji(e)">{{ e }}</button>
                    </div>
                </section>
                <p v-if="!loggedIn" class="m-0 px-1 pt-1 text-xs text-muted-color" data-testid="picker-login-hint">{{ t('emoticon.pickerLoginHint') }}</p>
                <p v-else-if="packList.length === 0 && loaded" class="m-0 px-1 pt-1 text-xs text-muted-color" data-testid="picker-empty">
                    {{ t('emoticon.pickerEmpty') }}
                    <NuxtLink :to="UtilEmoticon.PATH" class="font-semibold text-primary underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-primary" @click="$emit('navigate')">{{ t('emoticon.pickerEmptyLink') }}</NuxtLink>
                </p>
            </template>

            <!-- 받은 팩 — 64px 격자 -->
            <template v-else-if="currentPack">
                <p class="m-0 flex min-w-0 items-center gap-1.5 px-1 pb-1.5">
                    <span class="min-w-0 truncate text-xs font-semibold">{{ currentPack.name }}</span>
                    <PointShopTag v-if="currentPack.operator" tone="primary" data-testid="picker-operator-tag">{{ t('emoticon.operatorTag') }}</PointShopTag>
                </p>
                <div class="grid grid-cols-[repeat(auto-fill,minmax(4rem,1fr))] gap-1">
                    <button
                        v-for="e in currentPack.emoticons"
                        :key="e.id"
                        type="button"
                        class="grid aspect-square place-items-center rounded-lg p-0.5 hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                        :title="e.label"
                        :aria-label="e.label"
                        data-testid="picker-emoticon"
                        @click="pickEmoticon(e.id)"
                    >
                        <img :src="e.url" alt="" class="size-16 object-contain" loading="lazy" decoding="async" />
                    </button>
                </div>
            </template>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { EmoticonItem } from '~/composables/emoticon/api/useEmoticonApi.types';
import UtilEmoji from '~/utils/util-emoji';
import UtilEmoticon from '~/utils/util-emoticon';

/** 탭 하나 — 가진 팩, 또는 운영자 회원 계정이 쓰는 판매 중 팩(`operator`) */
interface PickerPack {
    id: string;
    name: string;
    coverUrl: string;
    emoticons: EmoticonItem[];
    /** 가지지 않았지만 운영자 회원 계정이라 쓰는 판매 중 팩 — 「운영자」 표시 */
    operator: boolean;
}

const emit = defineEmits<{
    /** 유니코드 이모지 한 글자(그대로 넣는다) */
    emoji: [value: string];
    /** 이모티콘 ID(토큰으로 넣는다) */
    emoticon: [id: string];
    /** 상점 링크로 나간다 — 창을 닫는다 */
    navigate: [];
}>();

const { t } = useI18n();
const uid = useId();
const { user } = useAuth();
const mine = useMyEmoticons();
const onSale = useOnSaleEmoticonPacks();
const catalog = useEmoticonCatalog();
const recent = useRecentEmoji();

const EMOJI = '__emoji__';
const EMOJI_CELL = 'grid aspect-square place-items-center rounded-lg text-[1.375rem] leading-none hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800';

const loggedIn = computed(() => Boolean(user.value));
const loaded = computed(() => mine.packs.value !== undefined);

/** 운영자 회원 계정의 나머지 판매 중 팩 — 그림은 카탈로그에서 팩 ID 로 묶는다(카탈로그에 그림이 없는 팩은 뺀다) */
const operatorPacks = computed<PickerPack[]>(() => {
    if (!mine.allPacks.value) return [];
    const cards = onSale.packs.value;
    const items = catalog.items.value;
    if (!cards || !items) return [];
    const owned = new Set((mine.packs.value ?? []).map((p) => p.id));
    const byPack = new Map<string, EmoticonItem[]>();
    for (const item of Object.values(items)) {
        if (owned.has(item.packId)) continue;
        const list = byPack.get(item.packId) ?? [];
        list.push({ id: item.id, label: item.label, url: item.url, animated: item.animated });
        byPack.set(item.packId, list);
    }
    return cards
        .filter((card) => !owned.has(card.id) && (byPack.get(card.id)?.length ?? 0) > 0)
        .map((card) => ({ id: card.id, name: card.name, coverUrl: card.coverUrl, emoticons: byPack.get(card.id) ?? [], operator: true }));
});

/** 가진 팩 먼저(받은·산·내가 만든), 그다음 운영자 회원 계정의 나머지 판매 중 팩 */
const packList = computed<PickerPack[]>(() => [
    ...(mine.packs.value ?? []).map((p) => ({ id: p.id, name: p.name, coverUrl: p.coverUrl, emoticons: p.emoticons, operator: false })),
    ...operatorPacks.value,
]);
const groups = UtilEmoji.groups();
const recentList = computed(() => recent.recent.value ?? []);

/** 처음 열 때 — 받은 팩이 있으면 첫 팩, 없으면 이모지 */
const tab = ref<string>(EMOJI);
const currentPack = computed(() => packList.value.find((p) => p.id === tab.value) ?? null);

onMounted(() => {
    recent.ensureLoaded();
    void mine.ensureLoaded().then(() => {
        if (tab.value === EMOJI && packList.value[0]) tab.value = packList.value[0].id;
        // 운영자 회원 계정 — 판매 중 팩 목록과 그림(카탈로그)을 받아 가진 팩 뒤에 더한다
        if (mine.allPacks.value) {
            void Promise.all([onSale.ensureLoaded(), catalog.ensureLoaded()]).then(() => {
                if (tab.value === EMOJI && packList.value[0]) tab.value = packList.value[0].id;
            });
        }
    });
});

/** 팩의 로보티콘을 넣는다 — 넣기만 해서는 「사용함」이 되지 않는다(글·댓글을 저장해야 서버가 찍는다) */
function pickEmoticon(id: string): void {
    emit('emoticon', id);
}

function pickEmoji(value: string): void {
    recent.push(value);
    emit('emoji', value);
}
</script>
