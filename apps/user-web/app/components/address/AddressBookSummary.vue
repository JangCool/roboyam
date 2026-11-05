<template>
    <!--
        저장된 배송지 한 장의 내용 — 제목(별칭, 없으면 받는 분) + 「기본」 딱지 · 받는 분 · 전화 · `(우편번호) 주소 상세주소` · 메모(있으면).
        설정 › 배송지 관리의 줄, 공구 주문서의 배송지 카드·「배송지 변경」 시트의 고르기 줄이 같이 쓴다(겉 상자·단추는 부르는 쪽 몫).
        회원 본인 화면이라 값을 가리지 않는다(정책). 값은 화면 메모리에만 — 저장소·주소줄에 남기지 않는다(forms.md 2-1).
    -->
    <div class="min-w-0">
        <p class="m-0 flex min-w-0 items-center gap-1.5 text-sm font-semibold">
            <span class="truncate" data-testid="address-book-title">{{ UtilAddressBook.title(address) }}</span>
            <Tag v-if="address.isDefault" :value="t('addressBook.defaultTag')" severity="info" class="shrink-0 text-[0.6875rem]" data-testid="address-book-default-tag" />
        </p>
        <p class="m-0 mt-0.5 text-sm break-words [overflow-wrap:anywhere]">
            <template v-if="address.label">{{ address.recipient }} · </template>{{ address.phone }}
        </p>
        <p class="m-0 text-sm break-words text-muted-color [overflow-wrap:anywhere]" data-testid="address-book-line">{{ UtilAddressBook.line(address) }}</p>
        <p v-if="showMemo && address.memo" class="m-0 mt-0.5 text-xs break-words text-muted-color [overflow-wrap:anywhere]">
            <i class="pi pi-comment mr-1 text-[0.6875rem]" aria-hidden="true" /><span class="sr-only">{{ t('groupBuy.addrMemo') }}: </span>{{ address.memo }}
        </p>
    </div>
</template>

<script setup lang="ts">
import type { ShippingAddress } from '~/composables/address/api/useAddressBookApi.types';
import UtilAddressBook from '~/utils/util-address-book';

interface Props {
    address: ShippingAddress;
    /** 저장된 배송 메모도 보인다(주문서 카드는 메모를 아래 칸에서 따로 고치므로 끈다) */
    showMemo?: boolean;
}

withDefaults(defineProps<Props>(), { showMemo: true });
const { t } = useI18n();
</script>
