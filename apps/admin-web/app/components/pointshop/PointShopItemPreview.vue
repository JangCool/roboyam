<template>
    <!--
        품목 편집 서랍의 미리보기(계약 「1-2 화면 목업」 — 미리보기 필수, commerce.md 7-3 · 20-1 L6). 입력 중인 값으로 바로 다시 그린다. 서버를 부르지 않는다.
        · 닉네임 색: 샘플 글쓴이 줄(이니셜 · 닉네임 · 등급 뱃지 · 시각)을 라이트(`#ffffff`)·다크(`#202125`) 두 바탕에 나란히 — 공개 화면과 같은 대비 보정(`UtilColor.pair`, user-web 사본과 같은 식)으로 칠한다.
          보정되면 「밝은/어두운 화면에서는 조금 바꿔 보여요」 표시(저장은 막지 않는다 — 저장값은 원색 그대로).
        · 프로필 테두리: 두 바탕에 공개 프로필 머리의 큰 동그라미 + 글 보기 머리의 작은 작성자 줄(계약 — 테두리를 보일 자리).
        · 프로필 아바타: 두 바탕에 공개 프로필 머리의 큰 동그라미 + 글 보기 머리의 작은 작성자 줄(테두리 없이 — 장착한 테두리는 공개 화면에서 이 둘레에 둘러진다).
        · 닉네임 변경권: 미리보기를 줄 수 없어 회원 화면 상품 상세에 늘 적을 내용 설명 다섯 줄(무엇 · 언제 · 어디서 · 기간 · 환불)과 입력한 설명을 보인다.
        상자마다 `color-scheme` 을 걸어 안쪽의 `light-dark()`(등급 뱃지·이니셜 면)가 그 바탕에 맞게 풀린다.
    -->
    <section class="flex min-w-0 flex-col gap-3" :aria-labelledby="titleId">
        <h3 :id="titleId" class="text-sm font-semibold">{{ t('pointShop.preview.title') }}</h3>

        <p v-if="!kind" class="m-0 rounded-lg border border-dashed border-(--line) px-3 py-6 text-center text-xs text-muted-color">{{ t('pointShop.preview.pickKind') }}</p>

        <!-- 닉네임 색 -->
        <template v-else-if="kind === 'NICKNAME_COLOR'">
            <p v-if="!pair" class="m-0 rounded-lg border border-dashed border-(--line) px-3 py-6 text-center text-xs text-muted-color">{{ t('pointShop.preview.pickColor') }}</p>
            <template v-else>
                <div class="grid grid-cols-2 gap-2">
                    <div v-for="box in boxes" :key="box.scheme" class="flex min-w-0 flex-col gap-2 rounded-lg border border-(--line) p-3" :style="{ background: box.bg, colorScheme: box.scheme }">
                        <span class="text-[11px]" :style="{ color: box.muted }">{{ box.label }}</span>
                        <div class="flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-1">
                            <PointShopBorderPreview :initial="initial" size="sm" :gap-color="box.bg" />
                            <span class="min-w-0 truncate text-[13px] font-semibold" :style="{ color: box.scheme === 'light' ? pair.light : pair.dark }">{{ nickname }}</span>
                            <GradeBadge :label="t('pointShop.preview.sampleGrade')" color="violet" icon="pi pi-star" size="small" />
                        </div>
                        <span class="text-[11px]" :style="{ color: box.muted }">{{ t('pointShop.preview.sampleMeta') }}</span>
                        <code class="text-[10px] tabular-nums" :style="{ color: box.muted }">{{ t('pointShop.preview.shownColor', { color: box.scheme === 'light' ? pair.light : pair.dark }) }}</code>
                    </div>
                </div>
                <p v-if="correctedNote" class="m-0 flex items-start gap-1.5 text-xs text-amber-700 dark:text-amber-400" role="status">
                    <i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" />
                    <span>{{ correctedNote }}</span>
                </p>
                <p class="m-0 text-[11px] leading-relaxed text-muted-color">{{ t('pointShop.preview.colorRule') }}</p>
            </template>
        </template>

        <!-- 프로필 테두리 -->
        <template v-else-if="kind === 'PROFILE_BORDER'">
            <p v-if="!gradient" class="m-0 rounded-lg border border-dashed border-(--line) px-3 py-6 text-center text-xs text-muted-color">{{ t('pointShop.preview.pickBorder') }}</p>
            <div v-else class="grid grid-cols-2 gap-2">
                <div v-for="box in boxes" :key="box.scheme" class="flex min-w-0 flex-col items-center gap-2 rounded-lg border border-(--line) p-3" :style="{ background: box.bg, colorScheme: box.scheme }">
                    <span class="self-start text-[11px]" :style="{ color: box.muted }">{{ box.label }}</span>
                    <PointShopBorderPreview :border-style="borderStyle" :initial="initial" size="lg" :gap-color="box.bg" :label="t('pointShop.preview.borderLabel', { name: borderName })" />
                    <span class="text-[13px] font-semibold" :style="{ color: box.text }">{{ nickname }}</span>
                    <div class="flex w-full min-w-0 items-center gap-1.5 border-t pt-2" :style="{ borderColor: box.line }">
                        <PointShopBorderPreview :border-style="borderStyle" :initial="initial" size="sm" :gap-color="box.bg" />
                        <span class="min-w-0 truncate text-[12px]" :style="{ color: box.text }">{{ nickname }}</span>
                    </div>
                </div>
            </div>
            <p class="m-0 text-[11px] leading-relaxed text-muted-color">{{ t('pointShop.preview.borderPlaces') }}</p>
        </template>

        <!-- 프로필 아바타 — 큰 동그라미(테두리 없이), 두 바탕 -->
        <template v-else-if="kind === 'PROFILE_AVATAR'">
            <p v-if="!avatarId" class="m-0 rounded-lg border border-dashed border-(--line) px-3 py-6 text-center text-xs text-muted-color">—</p>
            <div v-else class="grid grid-cols-2 gap-2">
                <div v-for="box in boxes" :key="box.scheme" class="flex min-w-0 flex-col items-center gap-2 rounded-lg border border-(--line) p-3" :style="{ background: box.bg, colorScheme: box.scheme }">
                    <span class="self-start text-[11px]" :style="{ color: box.muted }">{{ box.label }}</span>
                    <PointShopAvatarThumb :avatar-id="avatarId" size="lg" :alt="name" />
                    <span class="max-w-full truncate text-[13px] font-semibold" :style="{ color: box.text }">{{ name || nickname }}</span>
                    <div class="flex w-full min-w-0 items-center gap-1.5 border-t pt-2" :style="{ borderColor: box.line }">
                        <PointShopAvatarThumb :avatar-id="avatarId" size="sm" />
                        <span class="min-w-0 truncate text-[12px]" :style="{ color: box.text }">{{ nickname }}</span>
                    </div>
                </div>
            </div>
            <p class="m-0 text-[11px] leading-relaxed text-muted-color">{{ t('pointShop.preview.avatarPlaces') }}</p>
        </template>

        <!-- 닉네임 변경권 — 내용 설명 -->
        <div v-else class="flex flex-col gap-2 rounded-lg border border-(--line) p-3">
            <p class="m-0 text-[11px] text-muted-color">{{ t('pointShop.preview.ticketLead') }}</p>
            <p class="m-0 text-sm font-semibold">{{ name || t('pointShop.preview.untitled') }}</p>
            <p v-if="pricePoint !== null" class="m-0 text-sm tabular-nums">{{ t('pointShop.pricePoint', { price: pricePoint.toLocaleString(locale) }) }}</p>
            <p v-if="description" class="m-0 whitespace-pre-line text-xs leading-relaxed">{{ description }}</p>
            <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-xs leading-relaxed">
                <li v-for="line in ticketLines" :key="line.key" class="flex items-start gap-2">
                    <i :class="line.icon" class="mt-0.5 text-xs text-muted-color" aria-hidden="true" />
                    <span><span class="font-medium">{{ t(`pointShop.ticketInfo.${line.key}Label`) }}</span> · {{ line.text }}</span>
                </li>
            </ul>
        </div>
    </section>
</template>

<script setup lang="ts">
import type { PointShopItemKind } from '~/composables/pointshop/api/usePointShopAdminApi.types';
import UtilColor from '~/utils/util-color';
import UtilPointShop from '~/utils/util-point-shop';
import UtilPointShopMessages from '~/utils/util-point-shop-messages';

interface Props {
    /** 아직 안 골랐으면 null */
    kind?: PointShopItemKind | null;
    /** `#rrggbb` 글자(닉네임 색) — 모양이 틀리면 미리보기를 그리지 않는다 */
    color?: string;
    borderStyle?: string | null;
    /** 아바타 카탈로그 ID(아바타만) */
    avatarId?: string;
    name?: string;
    description?: string;
    /** 읽을 수 있는 가격. 없으면 null */
    pricePoint?: number | null;
    /** 셀프 환불 기한 일수 — 못 받았으면 null(기한 없이 말한다) */
    refundDays?: number | null;
}

const props = withDefaults(defineProps<Props>(), { kind: null, color: '', borderStyle: null, avatarId: '', name: '', description: '', pricePoint: null, refundDays: null });

const { t, locale } = useI18n();
const titleId = useId();

const nickname = computed(() => t('pointShop.preview.sampleNickname'));
const initial = computed(() => Array.from(nickname.value)[0] ?? '');

/** 두 바탕 — 공개 화면 카드 바탕과 같은 값(`UtilColor.BG_*`). 글자·흐린 글자·선 색은 그 바탕에서 읽히는 값 */
const boxes = computed(() => [
    { scheme: 'light' as const, label: t('pointShop.preview.light'), bg: UtilColor.BG_LIGHT, text: '#1f2328', muted: '#5b6270', line: '#e5e7eb' },
    { scheme: 'dark' as const, label: t('pointShop.preview.dark'), bg: UtilColor.BG_DARK, text: '#e8eaed', muted: '#9aa0a6', line: '#3c4043' },
]);

const pair = computed(() => UtilColor.pair(props.color.trim()));
const correctedNote = computed(() => (pair.value ? UtilPointShopMessages.correctedNote(t, pair.value.correctedLight, pair.value.correctedDark) : ''));

const gradient = computed(() => UtilPointShop.borderGradient(props.borderStyle));
const borderName = computed(() => (props.borderStyle ? t(`pointShop.border.${props.borderStyle}`) : ''));

/** 변경권 내용 설명 다섯 줄(계약 제안 — 미리보기 없는 품목) */
const ticketLines = computed(() => [
    { key: 'what', icon: 'pi pi-pencil', text: t('pointShop.ticketInfo.what') },
    { key: 'when', icon: 'pi pi-clock', text: t('pointShop.ticketInfo.when') },
    { key: 'where', icon: 'pi pi-user-edit', text: t('pointShop.ticketInfo.where') },
    { key: 'period', icon: 'pi pi-calendar', text: t('pointShop.ticketInfo.period') },
    { key: 'refund', icon: 'pi pi-replay', text: props.refundDays === null ? t('pointShop.ticketInfo.refundNoDays') : t('pointShop.ticketInfo.refund', { days: props.refundDays }) },
]);
</script>
