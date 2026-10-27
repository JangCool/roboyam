<template>
    <!--
        포인트 상점 품목 편집 서랍(오른쪽) — 품목 관리 표의 [품목 추가] 또는 줄을 누르면 연다. 넓은 화면은 왼쪽 입력 · 오른쪽 미리보기, 좁은 화면은 미리보기가 입력 아래로 간다.
        **종류를 먼저 고른다** — 새 품목은 종류(변경권 · 닉네임 색 · 프로필 테두리)를 고르기 전에는 다른 칸이 보이지 않고, 고르면 그 종류의 칸만 보인다(색은 닉네임 색만 · 테두리는 프로필 테두리만 · 기간은 기간제만).
        종류·코드는 만든 뒤 바꿀 수 없다 — 고칠 때는 읽기 전용으로 보이고 요청에는 그대로 실린다(`forms.md` 2-1, 서버가 바뀌었으면 거절한다).
        이미 있는 품목의 색·테두리를 고치면 「산 사람의 장식도 바로 바뀌어요 · 가격·이름은 산 때 그대로예요」 경고(이미 팔린 품목은 판 수와 함께 더 강하게). 저장을 막지는 않는다.
        검사는 `UtilPointShop.problems`(서버와 같은 규칙 — 판정은 서버) — 빈 칸은 [저장]을 한 번 누른 뒤에 탓한다. 서버가 거절한 칸(`400 fields.<칸>`)은 그 칸 아래에 서버 문구 그대로, 칸에 붙지 않는 것은 아래 한 줄.
        아바타 품목(아바타 탭 격자에서 연다 — 결정 14)은 카탈로그 시드라 종류 고르기 목록에 없고(새로 못 만든다), 이름·영문명·가격·순서·사용 여부만 보인다. 카테고리·아바타 ID·영구는 읽기 전용, 미리보기는 아바타 그림(큰 동그라미, 테두리 없이).
        권한(`pointshop.manage`)이 없으면 칸이 모두 읽기 전용이고 저장 단추 대신 안내 한 줄. 저장하지 않은 변경이 있는 채 닫으면 확인을 묻는다.
        서버를 부르지 않는다(`components.md`) — 값은 `save` 로 올리고, 저장·토스트·닫기는 부모 몫이다.
    -->
    <Drawer :visible="open" position="right" :aria-labelledby="titleId" :style="{ width: '64rem', maxWidth: '100vw' }" :pt="{ content: { class: 'pb-0!' } }" @update:visible="onVisible">
        <template #header>
            <div class="flex min-w-0 flex-1 flex-col gap-1">
                <div class="flex min-w-0 flex-wrap items-center gap-2">
                    <h2 :id="titleId" class="min-w-0 truncate text-base font-semibold">{{ item ? item.name : t('pointShop.item.addTitle') }}</h2>
                    <code v-if="item" class="text-xs text-muted-color">{{ item.code }}</code>
                </div>
                <p v-if="item" class="m-0 text-[11px] text-muted-color">
                    {{ t('pointShop.item.updated', { at: time.full(item.updatedAt), by: item.updatedBy ?? t('pointShop.system') }) }}
                    · {{ t('pointShop.item.soldCount', { count: item.soldCount.toLocaleString(locale) }) }}
                </p>
            </div>
        </template>

        <form :id="formId" class="grid min-w-0 gap-6 pb-4 lg:grid-cols-[minmax(0,1fr)_auto_22rem]" novalidate @submit.prevent="submit">
            <div class="flex min-w-0 flex-col gap-6">
                <!-- ① 종류 -->
                <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-kind`">
                    <h3 :id="`${titleId}-kind`" class="text-sm font-semibold">{{ t('pointShop.item.sec.kind') }}</h3>
                    <template v-if="creating">
                        <div role="radiogroup" :aria-labelledby="`${titleId}-kind`" :aria-invalid="Boolean(errorOf('kind'))" class="grid gap-2 sm:grid-cols-3">
                            <label
                                v-for="kind in UtilPointShop.CREATABLE_KINDS"
                                :key="kind"
                                class="flex cursor-pointer items-start gap-2 rounded-lg border px-3 py-2.5"
                                :class="[form.kind === kind ? 'border-primary bg-primary-50 dark:bg-primary-950' : 'border-(--line)', readonly ? 'cursor-not-allowed opacity-60' : '']"
                            >
                                <RadioButton :model-value="form.kind" :value="kind" :name="`${formId}-kind`" :disabled="readonly" class="mt-0.5" @update:model-value="pickKind(kind)" />
                                <span class="flex min-w-0 flex-col gap-0.5">
                                    <span class="flex items-center gap-1.5 text-sm font-medium"><i :class="kindIcon(kind)" class="text-xs" aria-hidden="true" />{{ t(`pointShop.kind.${kind}`) }}</span>
                                    <span class="text-[11px] leading-snug text-muted-color">{{ t(`pointShop.kindDesc.${kind}`) }}</span>
                                </span>
                            </label>
                        </div>
                        <p v-if="errorOf('kind')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errorOf('kind') }}</p>
                        <p v-else class="m-0 text-xs text-muted-color">{{ t('pointShop.item.kindHelp') }}</p>
                    </template>
                    <template v-else-if="form.kind">
                        <p class="m-0 flex items-center gap-2 text-sm"><i :class="kindIcon(form.kind)" class="text-xs" aria-hidden="true" />{{ t(`pointShop.kind.${form.kind}`) }}</p>
                        <p v-if="errorOf('kind')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errorOf('kind') }}</p>
                        <p v-else class="m-0 text-xs text-muted-color">{{ isAvatar ? t('pointShop.item.avatarFixed') : t('pointShop.item.kindFixed') }}</p>
                    </template>
                </section>

                <template v-if="form.kind">
                    <!-- ② 기본 정보 -->
                    <section class="flex flex-col gap-3" :aria-labelledby="`${titleId}-basic`">
                        <h3 :id="`${titleId}-basic`" class="text-sm font-semibold">{{ t('pointShop.item.sec.basic') }}</h3>

                        <LabeledField :label="t('pointShop.field.code')" :hint="t('pointShop.item.codeHint')" :help="creating ? t('pointShop.item.codeHelp') : t('pointShop.item.codeFixed')" :error="errorOf('code')">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText :id="id" :model-value="form.code" name="code" autocomplete="off" spellcheck="false" maxlength="40" :readonly="readonly || !creating" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" class="w-full max-w-sm font-mono" @update:model-value="(v: string | undefined) => set('code', (v ?? '').toLowerCase())" />
                            </template>
                        </LabeledField>

                        <div class="grid gap-3 sm:grid-cols-2">
                            <LabeledField :label="t('pointShop.field.name')" :hint="t('pointShop.item.nameHint', { max: UtilPointShop.NAME_MAX })" :error="errorOf('name')">
                                <template #default="{ id, describedBy, invalid }">
                                    <InputText :id="id" :model-value="form.name" name="name" autocomplete="off" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" fluid @update:model-value="(v: string | undefined) => set('name', v ?? '')" />
                                </template>
                            </LabeledField>
                            <LabeledField :label="t('pointShop.field.nameEn')" :hint="t('pointShop.item.optional')" :error="errorOf('nameEn')">
                                <template #default="{ id, describedBy, invalid }">
                                    <InputText :id="id" :model-value="form.nameEn" name="nameEn" autocomplete="off" lang="en" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="(v: string | undefined) => set('nameEn', v ?? '')" />
                                </template>
                            </LabeledField>
                        </div>

                        <!-- 아바타는 설명 칸이 없다(이름·영문명·가격·순서·사용 여부만) -->
                        <LabeledField v-if="!isAvatar" :label="t('pointShop.field.description')" :hint="t('pointShop.item.descriptionHint', { max: UtilPointShop.DESCRIPTION_MAX })" :error="errorOf('description')">
                            <template #default="{ id, describedBy, invalid }">
                                <Textarea :id="id" :model-value="form.description" name="description" rows="2" auto-resize :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="(v: string | undefined) => set('description', v ?? '')" />
                            </template>
                            <template #aside>
                                <small class="shrink-0 text-xs tabular-nums text-muted-color">{{ t('pointShop.counter', { length: UtilPointShop.length(form.description.trim()), max: UtilPointShop.DESCRIPTION_MAX }) }}</small>
                            </template>
                        </LabeledField>
                        <LabeledField v-if="!isAvatar" :label="t('pointShop.field.descriptionEn')" :hint="t('pointShop.item.optional')" :error="errorOf('descriptionEn')">
                            <template #default="{ id, describedBy, invalid }">
                                <Textarea :id="id" :model-value="form.descriptionEn" name="descriptionEn" rows="2" auto-resize lang="en" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="(v: string | undefined) => set('descriptionEn', v ?? '')" />
                            </template>
                        </LabeledField>
                    </section>

                    <!-- ③' 아바타 — 카탈로그 값(읽기 전용) -->
                    <section v-if="isAvatar" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-avatar`">
                        <h3 :id="`${titleId}-avatar`" class="text-sm font-semibold">{{ t('pointShop.item.sec.avatar') }}</h3>
                        <dl class="m-0 grid grid-cols-[7rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
                            <dt class="text-muted-color">{{ t('pointShop.field.category') }}</dt>
                            <dd class="m-0">{{ item?.category ? UtilPointShopMessages.avatarCategory(t, item.category) : '—' }}</dd>
                            <dt class="text-muted-color">{{ t('pointShop.field.avatarId') }}</dt>
                            <dd class="m-0"><code class="text-xs">{{ form.avatarId }}</code></dd>
                            <dt class="text-muted-color">{{ t('pointShop.field.usageType') }}</dt>
                            <dd class="m-0">{{ t('pointShop.usage.PERMANENT') }}</dd>
                        </dl>
                        <p v-if="errorOf('avatarId')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errorOf('avatarId') }}</p>
                    </section>

                    <!-- ③ 모양·사용 방식(종류별 칸) -->
                    <section v-else class="flex flex-col gap-3" :aria-labelledby="`${titleId}-shape`">
                        <h3 :id="`${titleId}-shape`" class="text-sm font-semibold">{{ t('pointShop.item.sec.shape') }}</h3>

                        <!-- 사용 방식 — 변경권은 소모형 하나뿐이라 글자로만 -->
                        <fieldset class="m-0 min-w-0 border-0 p-0">
                            <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('pointShop.field.usageType') }}</legend>
                            <p v-if="usages.length === 1" class="m-0 text-sm">
                                {{ t(`pointShop.usage.${usages[0]}`) }}
                                <small class="block text-xs text-muted-color">{{ t(`pointShop.usageDesc.${usages[0]}`) }}</small>
                            </p>
                            <div v-else class="flex flex-wrap gap-2">
                                <label
                                    v-for="usage in usages"
                                    :key="usage"
                                    class="flex cursor-pointer items-start gap-2 rounded-lg border px-3 py-2 text-sm"
                                    :class="[form.usageType === usage ? 'border-primary bg-primary-50 dark:bg-primary-950' : 'border-(--line)', readonly ? 'cursor-not-allowed opacity-60' : '']"
                                >
                                    <RadioButton :model-value="form.usageType" :value="usage" :name="`${formId}-usage`" :disabled="readonly" class="mt-0.5" @update:model-value="set('usageType', usage)" />
                                    <span class="flex flex-col">
                                        <span class="font-medium">{{ t(`pointShop.usage.${usage}`) }}</span>
                                        <span class="text-[11px] text-muted-color">{{ t(`pointShop.usageDesc.${usage}`) }}</span>
                                    </span>
                                </label>
                            </div>
                            <p v-if="errorOf('usageType')" role="alert" class="m-0 mt-1 text-xs text-red-600 dark:text-red-400">{{ errorOf('usageType') }}</p>
                        </fieldset>

                        <LabeledField v-if="UtilPointShop.hasDuration(form.usageType)" :label="t('pointShop.field.durationDays')" :hint="t('pointShop.range', { min: UtilPointShop.DURATION.min, max: UtilPointShop.DURATION.max })" :help="t('pointShop.item.durationHelp')" :error="errorOf('durationDays')">
                            <template #default="{ id, describedBy, invalid }">
                                <div class="flex items-center gap-2">
                                    <InputText :id="id" :model-value="form.durationDays" name="durationDays" inputmode="numeric" autocomplete="off" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" class="w-24 text-right tabular-nums" @update:model-value="(v: string | undefined) => set('durationDays', v ?? '')" />
                                    <span class="text-sm text-muted-color">{{ t('pointShop.unitDays') }}</span>
                                </div>
                            </template>
                        </LabeledField>

                        <!-- 닉네임 색 — 고르개와 글자 칸이 같은 값을 쓴다 -->
                        <LabeledField v-if="UtilPointShop.hasColor(form.kind)" :label="t('pointShop.field.color')" :hint="t('pointShop.item.colorHint')" :help="t('pointShop.item.colorHelp')" :error="errorOf('color')">
                            <template #default="{ id, describedBy, invalid }">
                                <div class="flex items-center gap-2">
                                    <input
                                        type="color"
                                        :value="pickerValue"
                                        :disabled="readonly"
                                        :aria-label="t('pointShop.item.colorPicker')"
                                        class="h-8 w-10 shrink-0 cursor-pointer rounded border border-(--line) bg-transparent p-0.5 disabled:cursor-not-allowed"
                                        @input="(e: Event) => set('color', (e.target as HTMLInputElement).value)"
                                    />
                                    <InputText :id="id" :model-value="form.color" name="color" autocomplete="off" spellcheck="false" maxlength="7" placeholder="#e11d48" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" class="w-32 font-mono" @update:model-value="(v: string | undefined) => set('color', v ?? '')" />
                                </div>
                            </template>
                        </LabeledField>

                        <!-- 프로필 테두리 — 팔레트(코드 고정)에서 하나 -->
                        <fieldset v-if="UtilPointShop.hasBorder(form.kind)" class="m-0 min-w-0 border-0 p-0">
                            <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('pointShop.field.borderStyle') }}</legend>
                            <div role="radiogroup" :aria-label="t('pointShop.field.borderStyle')" class="grid grid-cols-2 gap-2 sm:grid-cols-4">
                                <label
                                    v-for="key in UtilPointShop.BORDER_STYLES"
                                    :key="key"
                                    class="flex cursor-pointer items-center gap-2 rounded-lg border px-2.5 py-2 text-sm"
                                    :class="[form.borderStyle === key ? 'border-primary bg-primary-50 dark:bg-primary-950' : 'border-(--line)', readonly ? 'cursor-not-allowed opacity-60' : '']"
                                >
                                    <RadioButton :model-value="form.borderStyle" :value="key" :name="`${formId}-border`" :disabled="readonly" @update:model-value="set('borderStyle', key)" />
                                    <PointShopBorderPreview :border-style="key" :initial="initial" size="sm" />
                                    <span class="min-w-0 truncate">{{ t(`pointShop.border.${key}`) }}</span>
                                </label>
                            </div>
                            <p v-if="errorOf('borderStyle')" role="alert" class="m-0 mt-1 text-xs text-red-600 dark:text-red-400">{{ errorOf('borderStyle') }}</p>
                        </fieldset>

                        <!-- 표시값을 고치면 산 사람의 장식도 바뀐다 — 저장을 막지 않는다 -->
                        <Message v-if="decorationWarning" :severity="item && item.soldCount > 0 ? 'error' : 'warn'" :closable="false" size="small" role="status">{{ decorationWarning }}</Message>
                    </section>

                    <!-- ④ 판매 -->
                    <section class="flex flex-col gap-3" :aria-labelledby="`${titleId}-sale`">
                        <h3 :id="`${titleId}-sale`" class="text-sm font-semibold">{{ t('pointShop.item.sec.sale') }}</h3>

                        <div class="grid gap-3 sm:grid-cols-2">
                            <LabeledField :label="t('pointShop.field.pricePoint')" :hint="t('pointShop.range', { min: UtilPointShop.PRICE.min.toLocaleString(locale), max: UtilPointShop.PRICE.max.toLocaleString(locale) })" :help="item ? t('pointShop.item.priceHelpEdit') : ''" :error="errorOf('pricePoint')">
                                <template #default="{ id, describedBy, invalid }">
                                    <div class="flex items-center gap-2">
                                        <InputText :id="id" :model-value="form.pricePoint" name="pricePoint" inputmode="numeric" autocomplete="off" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" class="w-32 text-right tabular-nums" @update:model-value="(v: string | undefined) => set('pricePoint', v ?? '')" />
                                        <span class="text-sm text-muted-color">P</span>
                                    </div>
                                </template>
                            </LabeledField>
                            <LabeledField v-if="!isAvatar" :label="t('pointShop.field.stock')" :hint="t('pointShop.item.optional')" :help="stockHelp" :error="errorOf('stock')">
                                <template #default="{ id, describedBy, invalid }">
                                    <InputText :id="id" :model-value="form.stock" name="stock" inputmode="numeric" autocomplete="off" :placeholder="t('pointShop.unlimited')" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" class="w-32 text-right tabular-nums" @update:model-value="(v: string | undefined) => set('stock', v ?? '')" />
                                </template>
                            </LabeledField>
                            <LabeledField v-if="!isAvatar" :label="t('pointShop.field.saleStartAt')" :hint="t('pointShop.item.optional')" :help="t('pointShop.item.saleStartHelp')" :error="errorOf('saleStartAt')">
                                <template #default="{ id, describedBy, invalid }">
                                    <AppDateTimePicker :model-value="form.saleStartAt" :input-id="id" :described-by="describedBy" :invalid="invalid" :readonly="readonly" :show-zone="false" class="w-full" @update:model-value="(v: string) => set('saleStartAt', v)" />
                                </template>
                            </LabeledField>
                            <LabeledField v-if="!isAvatar" :label="t('pointShop.field.saleEndAt')" :hint="t('pointShop.item.optional')" :help="t('pointShop.item.saleEndHelp')" :error="errorOf('saleEndAt')">
                                <template #default="{ id, describedBy, invalid }">
                                    <AppDateTimePicker :model-value="form.saleEndAt" :input-id="id" :described-by="describedBy" :invalid="invalid" :readonly="readonly" :show-zone="false" class="w-full" @update:model-value="(v: string) => set('saleEndAt', v)" />
                                </template>
                            </LabeledField>
                            <LabeledField :label="t('pointShop.field.sortOrder')" :help="t('pointShop.item.sortHelp')" :error="errorOf('sortOrder')">
                                <template #default="{ id, describedBy, invalid }">
                                    <InputText :id="id" :model-value="form.sortOrder" name="sortOrder" inputmode="numeric" autocomplete="off" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" class="w-24 text-right tabular-nums" @update:model-value="(v: string | undefined) => set('sortOrder', v ?? '')" />
                                </template>
                            </LabeledField>
                            <div class="flex flex-col gap-1.5">
                                <span :id="`${formId}-active`" class="text-sm font-medium">{{ t('pointShop.field.active') }}</span>
                                <div class="flex items-center gap-2">
                                    <ToggleSwitch :model-value="form.active" :aria-labelledby="`${formId}-active`" :aria-describedby="`${formId}-active-help`" :disabled="readonly" @update:model-value="(v: boolean) => set('active', v)" />
                                    <span class="text-sm">{{ form.active ? t('pointShop.active') : t('pointShop.inactive') }}</span>
                                </div>
                                <p :id="`${formId}-active-help`" class="m-0 text-xs text-muted-color">{{ t('pointShop.item.activeHelp') }}</p>
                            </div>
                        </div>
                    </section>
                </template>

                <Message v-if="otherError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ otherError }}</Message>
            </div>

            <!-- 입력과 미리보기를 나누는 선 — 넓은 화면은 세로, 좁은 화면(미리보기가 입력 아래)은 가로 -->
            <Divider layout="vertical" class="m-0! hidden lg:flex" />
            <Divider class="m-0! lg:hidden" />

            <!-- 미리보기 — 넓은 화면은 오른쪽에 붙어 있고, 좁은 화면은 입력 아래 -->
            <aside class="min-w-0 lg:sticky lg:top-0 lg:self-start">
                <PointShopItemPreview :kind="form.kind" :color="form.color" :border-style="form.borderStyle" :avatar-id="form.avatarId" :name="form.name.trim()" :description="form.description.trim()" :price-point="UtilPointShop.parseInt(form.pricePoint)" :refund-days="refundDays" />
            </aside>
        </form>

        <template #footer>
            <div class="flex flex-wrap items-center justify-end gap-2 border-t border-(--line) pt-3">
                <p v-if="!canManage" class="m-0 mr-auto text-xs text-muted-color">{{ t('pointShop.item.readOnly') }}</p>
                <Button type="button" :label="canManage ? t('common.cancel') : t('common.close')" severity="secondary" variant="text" :disabled="saving" @click="onVisible(false)" />
                <Button v-if="canManage" type="submit" :form="formId" :label="creating ? t('pointShop.item.create') : t('common.save')" :loading="saving" :disabled="!dirty" />
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { PointShopAdminItem, PointShopItemField, PointShopItemKind, PointShopItemSaveRequest } from '~/composables/pointshop/api/usePointShopAdminApi.types';
import UtilColor from '~/utils/util-color';
import UtilPointShop, { type PointShopItemForm } from '~/utils/util-point-shop';
import UtilPointShopMessages from '~/utils/util-point-shop-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 고칠 품목. null 이면 새 품목 */
    item?: PointShopAdminItem | null;
    /** 저장 권한(`pointshop.manage`) — 없으면 읽기 전용 */
    canManage?: boolean;
    /** 저장 중 — 칸·단추가 잠긴다 */
    saving?: boolean;
    /** 칸에 붙지 않는 저장 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 칸마다 준 오류(`fields.<칸>`) */
    fieldErrors?: Record<string, string>;
    /** 셀프 환불 기한(변경권 미리보기 설명) — 못 받았으면 null */
    refundDays?: number | null;
}

const props = withDefaults(defineProps<Props>(), { item: null, canManage: false, saving: false, error: '', fieldErrors: () => ({}), refundDays: null });
const emit = defineEmits<{ save: [body: PointShopItemSaveRequest] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const titleId = useId();
const formId = useId();

const creating = computed(() => props.item === null);
/** 아바타 품목(카탈로그 시드) — 이름·영문명·가격·순서·사용 여부만 고친다. [품목 추가]의 종류에는 없다 */
const isAvatar = computed(() => UtilPointShop.isAvatar(form.value.kind));
const readonly = computed(() => !props.canManage || props.saving);

const form = ref<PointShopItemForm>(UtilPointShop.emptyForm());
/** 연 순간의 값 — 바꾼 것이 있는지 비교한다 */
const base = ref<PointShopItemForm>(UtilPointShop.emptyForm());
/** [저장]을 한 번 눌렀다 — 그 뒤로는 빈 칸도 오류로 보인다 */
const submitted = ref(false);
/** 서버 문구를 숨긴 칸 — 그 칸을 고쳤다. 부모가 새 오류를 주면 다시 보인다 */
const dismissed = ref<Set<string>>(new Set());

/** 열 때마다 품목 값(새 품목은 빈 값)으로 시작한다 */
watch(
    () => open.value,
    (visible) => {
        if (!visible) return;
        const start = props.item ? UtilPointShop.formOf(props.item) : UtilPointShop.emptyForm();
        form.value = { ...start };
        base.value = { ...start };
        submitted.value = false;
        dismissed.value = new Set();
    },
    { immediate: true },
);
/** 저장한 뒤 부모가 새 품목 값을 주면(같은 서랍에서 계속 볼 때) 그 값을 기준으로 다시 잡는다 */
watch(
    () => props.item,
    (next) => {
        if (!open.value || !next) return;
        form.value = UtilPointShop.formOf(next);
        base.value = UtilPointShop.formOf(next);
    },
);
watch(
    () => props.fieldErrors,
    () => {
        dismissed.value = new Set();
    },
);

const dirty = computed(() => UtilPointShop.isDirty(form.value, base.value));
const problems = computed(() => UtilPointShop.problems(form.value, creating.value));
const usages = computed(() => (form.value.kind ? UtilPointShop.usagesOf(form.value.kind) : []));
const initial = computed(() => Array.from(t('pointShop.preview.sampleNickname'))[0] ?? '');
/** 색 고르개는 `#rrggbb` 만 받는다 — 글자 칸이 아직 틀린 값이면 검정으로 둔다 */
const pickerValue = computed(() => (UtilColor.isHex(form.value.color.trim()) ? form.value.color.trim().toLowerCase() : '#000000'));

const stockHelp = computed(() => (props.item ? t('pointShop.item.stockHelpEdit', { sold: props.item.soldCount.toLocaleString(locale.value) }) : t('pointShop.item.stockHelp')));

const decorationWarning = computed(() => {
    if (!props.item || !UtilPointShop.decorationChanged(form.value, props.item)) return '';
    const what = props.item.kind === 'NICKNAME_COLOR' ? 'color' : 'border';
    if (props.item.soldCount > 0) return t(`pointShop.item.warn.${what}Sold`, { count: props.item.soldCount.toLocaleString(locale.value) });
    return t(`pointShop.item.warn.${what}`);
});

const KNOWN_FIELDS: readonly string[] = ['kind', 'code', 'usageType', 'name', 'nameEn', 'description', 'descriptionEn', 'color', 'borderStyle', 'avatarId', 'pricePoint', 'durationDays', 'stock', 'saleStartAt', 'saleEndAt', 'active', 'sortOrder'];
/** 칸에 붙지 않는 오류 — 부모가 준 문구 + 보이지 않는 칸(이 종류에 없는 칸)의 서버 문구 */
const otherError = computed(() => {
    const hidden = Object.entries(props.fieldErrors)
        .filter(([key]) => !KNOWN_FIELDS.includes(key) || !shown(key))
        .map(([, message]) => message);
    return [props.error, ...hidden].filter(Boolean).join(' ');
});

/** 지금 화면에 보이는 칸인가 — 종류·사용 방식에 따라 칸이 숨는다 */
function shown(field: string): boolean {
    if (field === 'kind') return true;
    if (!form.value.kind) return false;
    if (field === 'color') return UtilPointShop.hasColor(form.value.kind);
    if (field === 'borderStyle') return UtilPointShop.hasBorder(form.value.kind);
    if (field === 'durationDays') return UtilPointShop.hasDuration(form.value.usageType);
    if (field === 'active') return false;
    // 아바타는 이름·영문명·가격·순서·사용 여부(+ 읽기 전용 코드·아바타)만 보인다
    if (UtilPointShop.isAvatar(form.value.kind)) return ['code', 'name', 'nameEn', 'pricePoint', 'sortOrder', 'avatarId'].includes(field);
    if (field === 'avatarId') return false;
    return true;
}

/** 칸 아래 오류 — 화면 검사가 먼저(빈 칸은 [저장]을 누른 뒤), 없으면 서버 문구 */
function errorOf(field: PointShopItemField): string {
    const problem = problems.value[field];
    if (problem && (submitted.value || problem !== 'REQUIRED')) return UtilPointShopMessages.itemProblem(t, field, problem, locale.value);
    return dismissed.value.has(field) ? '' : (props.fieldErrors[field] ?? '');
}

function set<K extends keyof PointShopItemForm>(field: K, value: PointShopItemForm[K]): void {
    if (readonly.value) return;
    form.value = { ...form.value, [field]: value };
    if (field === 'usageType' && !UtilPointShop.hasDuration(value as PointShopItemForm['usageType'])) form.value = { ...form.value, durationDays: '' };
    if (props.fieldErrors[field]) dismissed.value = new Set([...dismissed.value, field]);
}

function pickKind(kind: PointShopItemKind): void {
    if (readonly.value || !creating.value) return;
    form.value = UtilPointShop.withKind(form.value, kind);
    if (props.fieldErrors.kind) dismissed.value = new Set([...dismissed.value, 'kind']);
}

function kindIcon(kind: PointShopItemKind): string {
    if (kind === 'NICKNAME_CHANGE_TICKET') return 'pi pi-ticket';
    if (kind === 'NICKNAME_COLOR') return 'pi pi-palette';
    if (kind === 'PROFILE_AVATAR') return 'pi pi-user';
    return 'pi pi-circle';
}

function submit(): void {
    if (!props.canManage || props.saving) return;
    submitted.value = true;
    const body = UtilPointShop.toRequest(form.value, creating.value);
    if (body) emit('save', body);
}

/** 닫기 — 저장하지 않은 변경이 있으면 묻는다(저장 중에는 닫지 않는다) */
async function onVisible(next: boolean): Promise<void> {
    if (next) {
        open.value = true;
        return;
    }
    if (props.saving) return;
    if (props.canManage && dirty.value) {
        const ok = await confirm({ title: t('pointShop.leaveTitle'), message: t('pointShop.leaveMessage'), confirmLabel: t('pointShop.leaveConfirm'), cancelLabel: t('pointShop.leaveCancel'), tone: 'danger' });
        if (!ok) return;
    }
    open.value = false;
}
</script>
