// MOCK: 사업자 서류 접수 기록(인쇄본·팩스) API 의 메모리 목업 — `useDocIntakeApi.ts` 가 부른다. 서버가 붙으면 이 파일을 지운다.
// 저장은 이 모듈 안 메모리(대상 `doc:ownerId` 마다 한 묶음)라 새로고침하면 비어 있는 처음 상태로 돌아간다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름`(쉼표로 여러 개) ─
//   docintake-seed      처음 여는 대상마다 종이 접수 한 줄(우편 · 계속 보관 · 메모 「목업 시드」)을 넣는다
//   docintake-mailfax   처음 여는 사업자를 「등록증을 우편·팩스로 보냄」으로 둔다(서랍의 「목업」 스위치로도 켜고 끈다)
//   docintake-fail      쓰기 요청이 모두 실패(500)
//
// ── 목업 규칙(가정 — 서버가 다시 판정) ─
//   · 칸 오류는 서버와 같은 `400 VALIDATION` + `fields.*`, 이미 바뀐 줄(잘못 기록·폐기함)은 409 `DOC_INTAKE_STATE_CHANGED`.
//   · 잘못 기록은 폐기하지 않은 줄만(폐기 기록은 근거라 남긴다). 계약서 폐기는 스캔본 필수(대체되지 않은 파일인지는 서버가 본다).
//   · 문구는 서버 몫이라 번역 키로 옮기지 않는다(`i18n.md` 5절).

import { ApiError } from '~/composables/common/useApiClient';
import type {
    DocFileChannel,
    DocIntake,
    DocIntakeBundle,
    DocIntakeCreateRequest,
    DocIntakeDisposeRequest,
    DocIntakeFaxFile,
    DocIntakeHandlingRequest,
    DocIntakeTarget,
} from '~/composables/docintake/api/useDocIntakeApi.types';
import { DOC_INTAKE_CHANNELS, DOC_INTAKE_OPEN_HANDLINGS } from '~/composables/docintake/api/useDocIntakeApi.types';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilDocIntake from '~/utils/util-doc-intake';

interface Store {
    intakes: DocIntake[];
    fileChannels: Record<string, DocFileChannel>;
    faxFiles: DocIntakeFaxFile[];
    mailDeclared: boolean;
}

const stores = new Map<string, Store>();
let seq = 0;

function flags(): Set<string> {
    const out = new Set<string>();
    if (typeof window === 'undefined') return out;
    for (const value of new URLSearchParams(window.location.search).getAll('mock')) {
        for (const name of value.split(',')) if (name.trim()) out.add(name.trim());
    }
    return out;
}

function delay(): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, 200 + Math.random() * 250));
}

function nextId(prefix: string): string {
    seq += 1;
    return `${prefix}-mock-${Date.now().toString(36)}-${seq}`;
}

const keyOf = (target: DocIntakeTarget) => `${target.doc}:${target.ownerId}`;

function store(target: DocIntakeTarget): Store {
    const key = keyOf(target);
    const found = stores.get(key);
    if (found) return found;
    const f = flags();
    const created: Store = { intakes: [], fileChannels: {}, faxFiles: [], mailDeclared: target.doc === 'CERTIFICATE' && f.has('docintake-mailfax') };
    if (f.has('docintake-seed')) {
        const today = UtilBizApplication.kstToday();
        created.intakes.push(blank(target, { channel: 'MAIL', receivedOn: UtilBizApplication.addDays(today, -2), pages: target.doc === 'CONTRACT' ? 4 : 1, memo: '목업 시드', handling: 'KEEP', storageLocation: '사무실 문서함 A-1' }, '목업 운영자'));
    }
    stores.set(key, created);
    return created;
}

function blank(target: DocIntakeTarget, body: DocIntakeCreateRequest, who: string): DocIntake {
    return {
        id: nextId('intake'),
        doc: target.doc,
        channel: body.channel,
        receivedOn: body.receivedOn,
        receivedBy: who,
        recordedAt: new Date().toISOString(),
        pages: body.pages,
        memo: body.memo?.trim() || null,
        handling: body.handling,
        storageLocation: body.handling === 'KEEP' ? body.storageLocation?.trim() || null : null,
        disposedOn: null,
        disposedBy: null,
        disposeReason: null,
        scanFileId: null,
        voidedAt: null,
        voidedBy: null,
        voidReason: null,
    };
}

function view(target: DocIntakeTarget): DocIntakeBundle {
    const s = store(target);
    return JSON.parse(JSON.stringify({ target, ...s, intakes: [...s.intakes].sort((a, b) => b.recordedAt.localeCompare(a.recordedAt)) })) as DocIntakeBundle;
}

const invalid = (fields: Record<string, string>) => new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 값을 확인해 주세요.', fields });
const changed = () => new ApiError({ status: 409, code: 'DOC_INTAKE_STATE_CHANGED', message: '이미 처리가 바뀐 접수 기록이에요. 다시 불러올게요.' });
const notFound = () => new ApiError({ status: 404, code: 'DOC_INTAKE_NOT_FOUND', message: '접수 기록을 찾을 수 없어요.' });

async function write(): Promise<void> {
    await delay();
    if (flags().has('docintake-fail')) throw new ApiError({ status: 500, code: 'INTERNAL', message: '서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.' });
}

function find(target: DocIntakeTarget, id: string): DocIntake {
    const found = store(target).intakes.find((i) => i.id === id);
    if (!found) throw notFound();
    return found;
}

export async function mockDocIntakeList(target: DocIntakeTarget): Promise<DocIntakeBundle> {
    await delay();
    return view(target);
}

export async function mockDocIntakeCreate(target: DocIntakeTarget, body: DocIntakeCreateRequest, who: string): Promise<DocIntakeBundle> {
    await write();
    const today = UtilBizApplication.kstToday();
    const fields: Record<string, string> = {};
    if (!DOC_INTAKE_CHANNELS.includes(body.channel)) fields.channel = '접수 방식을 골라 주세요.';
    if (!body.receivedOn) fields.receivedOn = '접수일을 골라 주세요.';
    else if (body.receivedOn > today) fields.receivedOn = '접수일은 오늘 이전이어야 해요.';
    if (body.pages !== null && (body.pages < UtilDocIntake.PAGES.min || body.pages > UtilDocIntake.PAGES.max)) fields.pages = '쪽수는 1~200 사이로 적어 주세요.';
    if (!DOC_INTAKE_OPEN_HANDLINGS.includes(body.handling)) fields.handling = '인쇄본 처리를 골라 주세요.';
    if ((body.storageLocation ?? '').trim().length > UtilDocIntake.STORAGE_MAX) fields.storageLocation = '보관 위치는 100자까지 적을 수 있어요.';
    if ((body.memo ?? '').trim().length > UtilDocIntake.MEMO_MAX) fields.memo = '메모는 200자까지 적을 수 있어요.';
    if (Object.keys(fields).length) throw invalid(fields);
    store(target).intakes.push(blank(target, body, who));
    return view(target);
}

export async function mockDocIntakeChangeHandling(target: DocIntakeTarget, id: string, body: DocIntakeHandlingRequest): Promise<DocIntakeBundle> {
    await write();
    const row = find(target, id);
    if (!UtilDocIntake.open(row)) throw changed();
    if (!DOC_INTAKE_OPEN_HANDLINGS.includes(body.handling)) throw invalid({ handling: '인쇄본 처리를 골라 주세요.' });
    if ((body.storageLocation ?? '').trim().length > UtilDocIntake.STORAGE_MAX) throw invalid({ storageLocation: '보관 위치는 100자까지 적을 수 있어요.' });
    row.handling = body.handling;
    row.storageLocation = body.handling === 'KEEP' ? body.storageLocation?.trim() || null : null;
    return view(target);
}

export async function mockDocIntakeDispose(target: DocIntakeTarget, id: string, body: DocIntakeDisposeRequest, who: string): Promise<DocIntakeBundle> {
    await write();
    const row = find(target, id);
    if (!UtilDocIntake.open(row)) throw changed();
    const today = UtilBizApplication.kstToday();
    const fields: Record<string, string> = {};
    if (!body.disposedOn) fields.disposedOn = '폐기일을 골라 주세요.';
    else if (body.disposedOn > today) fields.disposedOn = '폐기일은 오늘 이전이어야 해요.';
    else if (body.disposedOn < row.receivedOn) fields.disposedOn = '폐기일은 접수일 이후여야 해요.';
    if (!body.reason.trim()) fields.reason = '폐기 사유를 적어 주세요.';
    else if (body.reason.trim().length > UtilDocIntake.DISPOSE_REASON_MAX) fields.reason = '폐기 사유는 200자까지 적을 수 있어요.';
    if (target.doc === 'CONTRACT' && !body.scanFileId) fields.scanFileId = '스캔본(이 건의 파일 사본)을 골라 주세요.';
    if (Object.keys(fields).length) throw invalid(fields);
    row.handling = 'DISPOSED';
    row.storageLocation = null;
    row.disposedOn = body.disposedOn;
    row.disposedBy = who;
    row.disposeReason = body.reason.trim();
    row.scanFileId = body.scanFileId;
    return view(target);
}

export async function mockDocIntakeVoid(target: DocIntakeTarget, id: string, reason: string, who: string): Promise<DocIntakeBundle> {
    await write();
    const row = find(target, id);
    if (!UtilDocIntake.open(row)) throw changed();
    const problem = UtilBizApplication.reasonProblem(reason);
    if (problem) throw invalid({ reason: '사유를 5~200자로 적어 주세요.' });
    row.voidedAt = new Date().toISOString();
    row.voidedBy = who;
    row.voidReason = reason.trim();
    return view(target);
}

export async function mockDocIntakeMarkFile(target: DocIntakeTarget, fileId: string, channel: DocFileChannel): Promise<DocIntakeBundle> {
    await write();
    store(target).fileChannels[fileId] = channel;
    return view(target);
}

export async function mockDocIntakeFaxFile(target: DocIntakeTarget, file: File, who: string): Promise<DocIntakeBundle> {
    await write();
    const problem = UtilDocIntake.fileProblem(target.doc, file);
    if (problem) throw invalid({ file: problem === 'SIZE' ? '10MB 이하 파일만 올릴 수 있어요.' : 'PDF·JPG·PNG 파일만 올릴 수 있어요.' });
    store(target).faxFiles.push({ id: nextId('fax'), fileName: file.name, mimeType: file.type, size: file.size, receivedAt: new Date().toISOString(), receivedBy: who });
    return view(target);
}

export async function mockDocIntakeMailDeclared(target: DocIntakeTarget, on: boolean): Promise<DocIntakeBundle> {
    await delay();
    store(target).mailDeclared = target.doc === 'CERTIFICATE' && on;
    return view(target);
}
