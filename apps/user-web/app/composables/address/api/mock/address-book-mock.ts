// MOCK: 배송지 목록 API(`GET·POST /api/me/addresses`, `PUT·DELETE /api/me/addresses/{id}`, `PUT /api/me/addresses/{id}/default`)의 메모리 목업 —
// `useAddressBookApi.ts` 가 부른다. 서버가 붙으면 이 파일을 지운다. 새로고침하면 처음 상태(「집」 기본 · 「회사」)로 돌아간다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` ─
//   addresses-empty   처음부터 빈 목록            addresses-full       처음부터 10개(가득)
//   addresses-fail    읽기 실패(500)              addresses-save-fail  저장(더하기·고치기·지우기·기본 정하기) 실패(500)
//
// ── 목업 규칙(가정 — 서버 단계에서 확정) ─
//   · 최대 10개(넘으면 409 `ADDRESS_BOOK_FULL`). 첫 배송지는 `makeDefault` 와 상관없이 기본.
//   · 기본을 지우면 남은 것 중 가장 최근(`updatedAt`) 것이 기본. 차례는 기본이 맨 위, 나머지는 최근 만든/고친 순.
//   · 칸 검사: 받는 분 1~30자 · 전화 숫자 9~11자리(하이픈 허용) · 우편번호 숫자 5자리 · 주소 필수 100자 · 상세 100자 · 메모 50자 · 별칭 20자.
//     칸 오류는 서버와 같은 `400 VALIDATION` + `fields`(문구는 서버 몫이라 번역 키로 옮기지 않는다 — i18n.md 5절).

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { ShippingAddress, ShippingAddressList, ShippingAddressRequest } from '~/composables/address/api/useAddressBookApi.types';

const MAX = 10;
let rows: ShippingAddress[] | null = null;
let seq = 0;

function nextId(): string {
    seq += 1;
    return `addr-${Date.now().toString(36)}-${seq}`;
}

function ago(minutes: number): string {
    return new Date(Date.now() - minutes * 60_000).toISOString();
}

function seed(): ShippingAddress[] {
    if (rows) return rows;
    const flags = mockFlags();
    if (flags.has('addresses-empty')) {
        rows = [];
    } else if (flags.has('addresses-full')) {
        rows = Array.from({ length: MAX }, (_, i) => ({
            id: nextId(),
            label: i === 0 ? '집' : i === 1 ? '회사' : null,
            recipient: i < 2 ? '김로보' : `받는분${i + 1}`,
            phone: `010-1234-${String(1000 + i).padStart(4, '0')}`,
            postcode: String(6000 + i * 37).padStart(5, '0'),
            address1: `서울 강남구 테헤란로 ${100 + i}`,
            address2: `${i + 1}층`,
            memo: i % 3 === 0 ? '문 앞에 놓아 주세요' : '',
            isDefault: i === 0,
            updatedAt: ago(i * 60 * 24),
        }));
    } else {
        rows = [
            { id: nextId(), label: '집', recipient: '김로보', phone: '010-1234-5678', postcode: '06236', address1: '서울 강남구 테헤란로 152', address2: '101동 1201호', memo: '문 앞에 놓아 주세요', isDefault: true, updatedAt: ago(60 * 24 * 30) },
            { id: nextId(), label: '회사', recipient: '김로보', phone: '02-555-0123', postcode: '04524', address1: '서울 중구 세종대로 110', address2: '7층 개발팀', memo: '', isDefault: false, updatedAt: ago(60 * 24 * 3) },
        ];
    }
    return rows;
}

/** 서버 차례 — 기본이 맨 위, 나머지는 최근 만든/고친 순 */
function sorted(list: ShippingAddress[]): ShippingAddress[] {
    return [...list].sort((a, b) => (a.isDefault !== b.isDefault ? (a.isDefault ? -1 : 1) : b.updatedAt.localeCompare(a.updatedAt)));
}

function failIfSaveSwitch(): void {
    if (mockFlags().has('addresses-save-fail')) throw MOCK_ERRORS.internal();
}

function notFound(): ApiError {
    return new ApiError({ status: 404, code: 'ADDRESS_NOT_FOUND', message: '배송지를 찾을 수 없어요. 이미 지웠을 수 있어요.' });
}

/** 서버 검사 흉내 — 칸 이름은 요청 본문과 같다 */
function validate(body: ShippingAddressRequest): Record<string, string> {
    const fields: Record<string, string> = {};
    const label = (body.label ?? '').trim();
    const recipient = body.recipient.trim();
    const digits = body.phone.replace(/-/g, '');
    if (label.length > 20) fields.label = '별칭은 20자까지 쓸 수 있어요.';
    if (recipient.length < 1) fields.recipient = '받는 분을 넣어 주세요.';
    else if (recipient.length > 30) fields.recipient = '받는 분은 30자까지 쓸 수 있어요.';
    if (!body.phone.trim()) fields.phone = '연락처를 넣어 주세요.';
    else if (!/^[0-9-]+$/.test(body.phone.trim()) || digits.length < 9 || digits.length > 11) fields.phone = '연락처는 숫자 9~11자리로 넣어 주세요.';
    if (!/^\d{5}$/.test(body.postcode.trim())) fields.postcode = '우편번호는 숫자 5자리예요.';
    if (!body.address1.trim()) fields.address1 = '주소를 넣어 주세요.';
    else if (body.address1.trim().length > 100) fields.address1 = '주소는 100자까지 쓸 수 있어요.';
    if (body.address2.trim().length > 100) fields.address2 = '상세 주소는 100자까지 쓸 수 있어요.';
    if (body.memo.trim().length > 50) fields.memo = '배송 메모는 50자까지 쓸 수 있어요.';
    return fields;
}

function fromBody(body: ShippingAddressRequest): Omit<ShippingAddress, 'id' | 'isDefault' | 'updatedAt'> {
    const label = (body.label ?? '').trim();
    return {
        label: label ? label : null,
        recipient: body.recipient.trim(),
        phone: body.phone.trim(),
        postcode: body.postcode.trim(),
        address1: body.address1.trim(),
        address2: body.address2.trim(),
        memo: body.memo.trim(),
    };
}

export async function mockAddressBookList(): Promise<ShippingAddressList> {
    await mockDelay();
    if (mockFlags().has('addresses-fail')) throw MOCK_ERRORS.internal();
    return mockClone({ items: sorted(seed()), max: MAX });
}

export async function mockAddressBookCreate(body: ShippingAddressRequest): Promise<ShippingAddress> {
    await mockDelay();
    failIfSaveSwitch();
    const list = seed();
    if (list.length >= MAX) throw new ApiError({ status: 409, code: 'ADDRESS_BOOK_FULL', message: `배송지는 ${MAX}개까지 둘 수 있어요. 쓰지 않는 배송지를 지운 뒤 더해 주세요.` });
    const fields = validate(body);
    if (Object.keys(fields).length > 0) throw MOCK_ERRORS.validation(fields);
    const makeDefault = list.length === 0 || body.makeDefault;
    if (makeDefault) for (const r of list) r.isDefault = false;
    const row: ShippingAddress = { id: nextId(), ...fromBody(body), isDefault: makeDefault, updatedAt: new Date().toISOString() };
    list.push(row);
    return mockClone(row);
}

export async function mockAddressBookUpdate(id: string, body: ShippingAddressRequest): Promise<ShippingAddress> {
    await mockDelay();
    failIfSaveSwitch();
    const list = seed();
    const row = list.find((r) => r.id === id);
    if (!row) throw notFound();
    const fields = validate(body);
    if (Object.keys(fields).length > 0) throw MOCK_ERRORS.validation(fields);
    // 기본 배송지를 고치면서 기본을 끌 수는 없다(기본을 바꾸려면 다른 배송지를 기본으로)
    if (body.makeDefault && !row.isDefault) for (const r of list) r.isDefault = false;
    Object.assign(row, fromBody(body), { isDefault: row.isDefault || body.makeDefault, updatedAt: new Date().toISOString() });
    return mockClone(row);
}

export async function mockAddressBookRemove(id: string): Promise<void> {
    await mockDelay();
    failIfSaveSwitch();
    const list = seed();
    const index = list.findIndex((r) => r.id === id);
    if (index < 0) throw notFound();
    const [gone] = list.splice(index, 1);
    if (gone?.isDefault && list.length > 0) {
        const latest = [...list].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))[0];
        if (latest) latest.isDefault = true;
    }
}

export async function mockAddressBookMakeDefault(id: string): Promise<void> {
    await mockDelay();
    failIfSaveSwitch();
    const list = seed();
    const row = list.find((r) => r.id === id);
    if (!row) throw notFound();
    for (const r of list) r.isDefault = r.id === id;
}
