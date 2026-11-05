<template>
    <div class="content-card flex flex-col gap-3">
        <!-- 머리(`header` — 카드 테마에서 제목 줄 위 여백이 다른 설정 화면의 h1 과 같다). 좁은 화면은 오른쪽 끝에 「‹ 설정」 링크가 겹쳐 서므로(`pages/settings.vue`) 그만큼 비운다 -->
        <header class="flex flex-wrap items-center justify-between gap-2 max-md:pr-16">
            <h1 class="text-lg font-bold">{{ t('settings.addresses') }}</h1>
            <div v-if="status === 'ready'" class="flex items-center gap-2">
                <span class="text-xs tabular-nums text-muted-color" :aria-label="t('addressBook.countLabel', { count: items.length, max })" data-testid="addresses-count">
                    {{ t('addressBook.count', { count: items.length, max }) }}
                </span>
                <Button type="button" :label="t('addressBook.add')" icon="pi pi-plus" size="small" :disabled="full || busyId !== null" data-testid="addresses-add" @click="openAdd" />
            </div>
        </header>

        <div v-if="status === 'loading'" class="space-y-2" role="status" :aria-label="t('addressBook.loading')" data-testid="addresses-loading">
            <div v-for="n in 2" :key="n" class="h-24 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="status === 'error'" :title="t('addressBook.loadFailed')" :description="errorMessage" retry data-testid="addresses-error" @retry="load()" />

        <template v-else>
            <p class="m-0 rounded-lg bg-surface-100 px-3 py-2.5 text-xs leading-relaxed text-muted-color dark:bg-surface-800/60" role="note">{{ t('addressBook.notice') }}</p>
            <p v-if="full" class="m-0 text-xs text-muted-color" role="status" data-testid="addresses-full">{{ t('addressBook.full', { max }) }}</p>

            <EmptyState v-if="items.length === 0" :title="t('addressBook.emptyTitle')" :description="t('addressBook.emptyText')" icon="pi pi-map-marker" data-testid="addresses-empty">
                <Button type="button" :label="t('addressBook.add')" icon="pi pi-plus" data-testid="addresses-empty-add" @click="openAdd" />
            </EmptyState>

            <ul v-else class="m-0 list-none panel-card p-0" data-testid="addresses-list">
                <li v-for="row in items" :key="row.id" class="flex flex-col gap-2 border-b border-surface px-4 py-3 last:border-b-0 sm:flex-row sm:items-start" data-testid="addresses-row">
                    <AddressBookSummary :address="row" class="flex-1" />
                    <div class="flex shrink-0 flex-wrap items-center gap-1.5">
                        <Button
                            v-if="!row.isDefault"
                            type="button"
                            :label="t('addressBook.makeDefault')"
                            size="small"
                            severity="secondary"
                            variant="text"
                            :loading="busyId === row.id && busyKind === 'default'"
                            :disabled="busyId !== null"
                            :aria-label="t('addressBook.makeDefaultLabel', { name: UtilAddressBook.title(row) })"
                            data-testid="addresses-make-default"
                            @click="makeDefault(row)"
                        />
                        <Button
                            type="button"
                            :label="t('common.edit')"
                            size="small"
                            severity="secondary"
                            variant="outlined"
                            :disabled="busyId !== null"
                            :aria-label="t('addressBook.editLabel', { name: UtilAddressBook.title(row) })"
                            data-testid="addresses-edit"
                            @click="openEdit(row)"
                        />
                        <Button
                            type="button"
                            :label="t('common.delete')"
                            size="small"
                            severity="danger"
                            variant="outlined"
                            :loading="busyId === row.id && busyKind === 'remove'"
                            :disabled="busyId !== null"
                            :aria-label="t('addressBook.removeLabel', { name: UtilAddressBook.title(row) })"
                            data-testid="addresses-remove"
                            @click="remove(row)"
                        />
                    </div>
                </li>
            </ul>
        </template>

        <!-- 추가·수정 시트 — 바꾼 내용이 있는데 닫으면 떠나기 확인 -->
        <AppSheet :visible="sheetOpen" :title="editing ? t('addressBook.editTitle') : t('addressBook.addTitle')" :description="t('addressBook.sheetHelp')" @update:visible="onSheetVisible">
            <form :key="formKey" class="flex flex-col gap-3 px-5 pt-3" novalidate data-testid="addresses-form" @submit.prevent="save">
                <LabeledField :label="t('addressBook.label')" :hint="t('addressBook.labelHint')" :error="fieldErrors.label">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText
                            :id="id"
                            v-model="label"
                            :maxlength="UtilAddressBook.LABEL_MAX"
                            autocomplete="off"
                            :placeholder="t('addressBook.labelPlaceholder')"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                            :disabled="saving"
                            fluid
                            data-testid="addresses-label"
                        />
                    </template>
                </LabeledField>

                <GroupBuyAddressFields v-model="form" :errors="fieldErrors" :disabled="saving" :manual-first="editing !== null" />

                <!-- 기본 배송지 — 첫 배송지면 켜진 채 잠김, 이미 기본인 것을 고칠 때는 숨김 -->
                <div v-if="!editing?.isDefault" class="flex flex-col gap-0.5">
                    <label class="flex cursor-pointer items-start gap-2 text-sm">
                        <Checkbox v-model="makeDefaultChecked" :input-id="`${uid}-default`" binary class="mt-0.5" :disabled="firstAddress || saving" data-testid="addresses-default-check" />
                        <span class="min-w-0">
                            <span class="font-medium">{{ t('addressBook.makeDefaultCheck') }}</span>
                            <span v-if="firstAddress" class="block text-xs leading-relaxed text-muted-color">{{ t('addressBook.firstIsDefault') }}</span>
                        </span>
                    </label>
                </div>

                <Message v-if="formError" severity="error" size="small" variant="simple" :closable="false" role="alert" data-testid="addresses-form-error">{{ formError }}</Message>

                <div class="mt-1 flex gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="outlined" class="flex-1" :disabled="saving" @click="onSheetVisible(false)" />
                    <Button type="submit" :label="t('common.save')" class="flex-1" :loading="saving" data-testid="addresses-save" />
                </div>
            </form>
        </AppSheet>
    </div>
</template>

<script setup lang="ts">
import type { ShippingAddress } from '~/composables/address/api/useAddressBookApi.types';
import type { GroupBuyAddressInput } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilAddressBook from '~/utils/util-address-book';
import UtilBoardMessages from '~/utils/util-board-messages';

/**
 * 배송지 관리 `/settings/addresses` — 로그인 필요. 공구 주문서에서 고를 배송지를 최대 10개 두고 하나를 기본으로 정한다(추가·수정·삭제·기본 지정).
 *  - 회원 본인 화면이라 값을 가리지 않는다(저장은 서버가 암호화, 운영자는 이 목록을 볼 수 없다). 값은 이 화면 메모리에만(forms.md 2-1).
 *  - 차례는 서버가 정한다(기본이 맨 위, 나머지는 최근 만든/고친 순). 고치거나 지우거나 기본을 바꾸면 목록을 조용히 다시 받는다(기본 배송지를 지우면 서버가 새 기본을 고른다).
 *  - 첫 배송지는 자동으로 기본(체크 상자가 켜진 채 잠김). 지우면 바로 삭제(확인 대화상자).
 *  - 칸 오류는 화면 미리 검사(받는 분·연락처·우편번호·주소 — 주문서와 같은 문구) + 서버 `400 VALIDATION fields`(`label`·`recipient`·`phone`·`postcode`·`address1`·`address2`·`memo`).
 * 서버 연결됨(`useAddressBookApi`, 계약 `docs/features/address-book-contract.md`). 데이터 키가 없으면 503 `DATA_KEY_MISSING` — 서버 문구 그대로 ErrorState·토스트·폼 오류에 보인다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('settings.addresses'), description: () => t('addressBook.seo', { site: siteLabel.value }), robots: 'noindex' });

const api = useAddressBookApi();
const { confirm } = useConfirmDialog();
const toast = useAppToast();
const uid = useId();

/* ── 목록 ── */

const status = ref<'loading' | 'ready' | 'error'>('loading');
const errorMessage = ref('');
const items = ref<ShippingAddress[]>([]);
const max = ref(UtilAddressBook.MAX);
const full = computed(() => items.value.length >= max.value);

let request = 0;
async function load(): Promise<void> {
    const mine = ++request;
    status.value = 'loading';
    try {
        const res = await api.list();
        if (mine !== request) return;
        items.value = res.items;
        max.value = res.max;
        status.value = 'ready';
    } catch (e) {
        if (mine !== request) return;
        errorMessage.value = UtilBoardMessages.of(t, e);
        status.value = 'error';
    }
}

/** 바꾼 뒤 서버 차례·기본 표시를 다시 받는다 — 못 받으면 화면이 아는 만큼(`fallback`)만 맞춘다 */
async function refreshQuietly(fallback: () => void): Promise<void> {
    const mine = ++request;
    try {
        const res = await api.list();
        if (mine !== request) return;
        items.value = res.items;
        max.value = res.max;
    } catch {
        if (mine === request) fallback();
    }
}

void load();
onBeforeUnmount(() => request++);

/* ── 기본으로 · 삭제 ── */

const busyId = ref<string | null>(null);
const busyKind = ref<'default' | 'remove' | null>(null);

async function makeDefault(row: ShippingAddress): Promise<void> {
    if (busyId.value !== null) return;
    busyId.value = row.id;
    busyKind.value = 'default';
    try {
        await api.makeDefault(row.id);
    } catch (e) {
        toast.error(t('addressBook.defaultFailed', { reason: UtilBoardMessages.of(t, e) }), 8000);
        if (e instanceof ApiError && e.code === 'ADDRESS_NOT_FOUND') void load();
        return;
    } finally {
        busyId.value = null;
        busyKind.value = null;
    }
    toast.success(t('addressBook.defaultDone'));
    await refreshQuietly(() => {
        const picked = items.value.find((a) => a.id === row.id);
        const rest = items.value.filter((a) => a.id !== row.id).map((a) => ({ ...a, isDefault: false }));
        items.value = picked ? [{ ...picked, isDefault: true }, ...rest] : rest;
    });
}

async function remove(row: ShippingAddress): Promise<void> {
    if (busyId.value !== null) return;
    const name = UtilAddressBook.title(row);
    const ok = await confirm({
        title: t('addressBook.removeTitle'),
        message: row.isDefault && items.value.length > 1 ? t('addressBook.removeMessageDefault', { name }) : t('addressBook.removeMessage', { name }),
        confirmLabel: t('common.delete'),
        tone: 'danger',
    });
    if (!ok) return;
    busyId.value = row.id;
    busyKind.value = 'remove';
    try {
        await api.remove(row.id);
    } catch (e) {
        toast.error(t('addressBook.removeFailed', { reason: UtilBoardMessages.of(t, e) }), 8000);
        if (e instanceof ApiError && e.code === 'ADDRESS_NOT_FOUND') void load();
        return;
    } finally {
        busyId.value = null;
        busyKind.value = null;
    }
    toast.success(t('addressBook.removed'));
    // 기본을 지웠으면 서버가 새 기본을 고른다 — 다시 받지 못하면 빼기만 한다
    await refreshQuietly(() => (items.value = items.value.filter((a) => a.id !== row.id)));
}

/* ── 추가·수정 시트 ── */

const sheetOpen = ref(false);
const editing = ref<ShippingAddress | null>(null);
const formKey = ref(0);
const label = ref('');
const form = ref<GroupBuyAddressInput>(UtilAddressBook.emptyInput());
const makeDefaultChecked = ref(false);
const saving = ref(false);
const fieldErrors = ref<Record<string, string>>({});
const formError = ref('');
/** 연 때의 값 — 바꾼 내용이 있는지 본다 */
let initial = { label: '', form: UtilAddressBook.emptyInput(), makeDefault: false };

/** 새로 더하는 것이 첫 배송지 — 기본 체크가 켜진 채 잠긴다(서버도 첫 배송지를 기본으로) */
const firstAddress = computed(() => editing.value === null && items.value.length === 0);
const dirty = computed(() => label.value !== initial.label || !UtilAddressBook.sameInput(form.value, initial.form) || makeDefaultChecked.value !== initial.makeDefault);

function openSheet(target: ShippingAddress | null): void {
    editing.value = target;
    label.value = target?.label ?? '';
    form.value = target ? UtilAddressBook.toInput(target) : UtilAddressBook.emptyInput();
    makeDefaultChecked.value = target ? target.isDefault : items.value.length === 0;
    initial = { label: label.value, form: { ...form.value }, makeDefault: makeDefaultChecked.value };
    fieldErrors.value = {};
    formError.value = '';
    formKey.value++;
    sheetOpen.value = true;
}

function openAdd(): void {
    if (full.value) return;
    openSheet(null);
}

function openEdit(row: ShippingAddress): void {
    openSheet(row);
}

/** 닫는다 — 칸 값은 다음에 열 때 새로 채운다(닫히는 동안 칸이 비어 보이지 않게). 값은 이 화면 메모리에만 있다 */
function closeSheet(): void {
    sheetOpen.value = false;
}

let asking = false;
async function confirmDiscard(): Promise<boolean> {
    return await confirm({ title: t('settings.leaveTitle'), message: t('settings.leaveMessage'), confirmLabel: t('settings.leaveConfirm'), tone: 'danger' });
}

/** 시트가 닫히려 한다(바깥 누르기 · Esc · 취소) — 저장 중이면 막고, 바꾼 내용이 있으면 묻는다 */
async function onSheetVisible(visible: boolean): Promise<void> {
    if (visible || !sheetOpen.value || saving.value || asking) return;
    if (dirty.value) {
        asking = true;
        try {
            if (!(await confirmDiscard())) return;
        } finally {
            asking = false;
        }
    }
    closeSheet();
}

/** 화면 미리 검사(편의 — 판정은 서버). 문구는 주문서와 같은 키 */
function precheck(): Record<string, string> {
    const out: Record<string, string> = {};
    const messages: Record<string, string> = {
        recipient: t('groupBuy.errRecipient'),
        phone: t('groupBuy.errPhone'),
        postcode: t('groupBuy.errPostcode'),
        address1: t('groupBuy.errAddress1'),
    };
    for (const field of UtilAddressBook.missing(form.value)) out[field] = messages[field] ?? '';
    if (label.value.trim().length > UtilAddressBook.LABEL_MAX) out.label = t('addressBook.labelTooLong');
    return out;
}

async function save(): Promise<void> {
    if (saving.value) return;
    formError.value = '';
    const local = precheck();
    fieldErrors.value = local;
    if (Object.keys(local).length > 0) {
        formError.value = t('groupBuy.errCheck');
        return;
    }
    const target = editing.value;
    const body = UtilAddressBook.toRequest({ ...toRaw(form.value) }, label.value, firstAddress.value || makeDefaultChecked.value);
    saving.value = true;
    let savedRow: ShippingAddress;
    try {
        savedRow = target ? await api.update(target.id, body) : await api.create(body);
    } catch (e) {
        if (e instanceof ApiError && e.status === 400 && e.fields && Object.keys(e.fields).length > 0) {
            fieldErrors.value = e.fields;
            const known = new Set(['label', 'recipient', 'phone', 'postcode', 'address1', 'address2', 'memo']);
            // 칸에 붙지 않는 오류만 폼 위로(forms.md 2-2)
            formError.value = Object.entries(e.fields).find(([k]) => !known.has(k))?.[1] ?? t('groupBuy.errCheck');
        } else {
            formError.value = UtilBoardMessages.of(t, e);
            if (e instanceof ApiError && (e.code === 'ADDRESS_BOOK_FULL' || e.code === 'ADDRESS_NOT_FOUND')) void refreshQuietly(() => undefined);
        }
        return;
    } finally {
        saving.value = false;
    }
    toast.success(target ? t('addressBook.saved') : t('addressBook.created'));
    closeSheet();
    await refreshQuietly(() => {
        const others = items.value.filter((a) => a.id !== savedRow.id).map((a) => (savedRow.isDefault ? { ...a, isDefault: false } : a));
        items.value = savedRow.isDefault ? [savedRow, ...others] : [...others.filter((a) => a.isDefault), savedRow, ...others.filter((a) => !a.isDefault)];
    });
}

onBeforeRouteLeave(async () => {
    if (!sheetOpen.value || !dirty.value || saving.value) return true;
    return await confirmDiscard();
});
</script>
