import type { DocIntake, DocIntakeBundle, DocIntakeChannel, DocIntakeDoc, DocIntakeHandling, DocIntakeOpenHandling } from '~/composables/docintake/api/useDocIntakeApi.types';
import type { DocScanFile } from '~/composables/docintake/useDocIntake.types';
import UtilBizApplication from '~/utils/util-biz-application';

type Severity = 'secondary' | 'info' | 'success' | 'warn' | 'danger' | 'contrast';

/** [접수 기록] 대화상자에서 입력 중인 종이 접수 값 */
export interface DocIntakeDraft {
    channel: DocIntakeChannel;
    receivedOn: string;
    pages: number | null;
    handling: DocIntakeOpenHandling;
    storageLocation: string;
    memo: string;
}

/** [폐기 처리] 대화상자에서 입력 중인 값 */
export interface DocIntakeDisposeDraft {
    disposedOn: string;
    reason: string;
    scanFileId: string | null;
}

export type DocIntakeProblem = 'REQUIRED' | 'FUTURE' | 'RANGE' | 'TOO_LONG' | 'BEFORE_RECEIVED';

const DAY_MS = 86_400_000;

/**
 * 사업자 서류 접수 기록(인쇄본·팩스) 화면 규칙 — 입력만 보고 답하는 순수 함수. **목업 단계 가정**이며 서버(예정 `DocIntakePolicy`)가 다시 판정한다.
 * 파일 규칙: 계약서는 `UtilBizApplication.contractProblem`(서버 `biz_contract_files` 와 같은 PDF·JPG·PNG 10MB), 등록증은 등록증 규칙(user-web `UtilBusiness.CERTIFICATE_ACCEPT` · 서버 `business_documents` — 값은 같다).
 */
export default class UtilDocIntake {
    /** 쪽수 범위 */
    static readonly PAGES = { min: 1, max: 200 } as const;
    /** 메모·폐기 사유 길이 */
    static readonly MEMO_MAX = 200;
    static readonly DISPOSE_REASON_MAX = 200;
    /** 보관 위치 길이 */
    static readonly STORAGE_MAX = 100;
    /** 반려·탈퇴 뒤 등록증 파기 기한(business-contract 「보존」) */
    static readonly CERTIFICATE_DISPOSE_DAYS = 90;
    /** 사업자등록증 — PDF·JPG·PNG, 10MB(신청 화면과 같은 규칙) */
    static readonly CERTIFICATE_ACCEPT = 'application/pdf,image/jpeg,image/png';
    static readonly CERTIFICATE_MAX_BYTES = 10 * 1024 * 1024;
    /** 등록증 폐기 때 스캔본으로 고를 수 있는 「신청자가 올린 등록증 파일」의 자리 id(목업 — 서버는 등록증 파일 id) */
    static readonly CERTIFICATE_FILE_ID = 'certificate';

    static acceptOf(doc: DocIntakeDoc): string {
        return doc === 'CONTRACT' ? UtilBizApplication.CONTRACT_ACCEPT : UtilDocIntake.CERTIFICATE_ACCEPT;
    }

    /** 전자 팩스 파일 문제 — 형식·크기 */
    static fileProblem(doc: DocIntakeDoc, file: File): 'TYPE' | 'SIZE' | null {
        if (doc === 'CONTRACT') return UtilBizApplication.contractProblem(file);
        if (!UtilDocIntake.CERTIFICATE_ACCEPT.split(',').includes(file.type)) return 'TYPE';
        if (file.size > UtilDocIntake.CERTIFICATE_MAX_BYTES) return 'SIZE';
        return null;
    }

    static emptyDraft(today: string): DocIntakeDraft {
        return { channel: 'MAIL', receivedOn: today, pages: null, handling: 'KEEP', storageLocation: '', memo: '' };
    }

    /** 종이 접수 칸 문제(접수일 필수·오늘 이전 · 쪽수 1~200 · 보관 위치 100자 · 메모 200자) */
    static draftProblems(draft: DocIntakeDraft, today: string): Partial<Record<'receivedOn' | 'pages' | 'storageLocation' | 'memo', DocIntakeProblem>> {
        const out: Partial<Record<'receivedOn' | 'pages' | 'storageLocation' | 'memo', DocIntakeProblem>> = {};
        if (!draft.receivedOn) out.receivedOn = 'REQUIRED';
        else if (draft.receivedOn > today) out.receivedOn = 'FUTURE';
        if (draft.pages !== null && (draft.pages < UtilDocIntake.PAGES.min || draft.pages > UtilDocIntake.PAGES.max || !Number.isInteger(draft.pages))) out.pages = 'RANGE';
        if (draft.handling === 'KEEP' && draft.storageLocation.trim().length > UtilDocIntake.STORAGE_MAX) out.storageLocation = 'TOO_LONG';
        if (draft.memo.trim().length > UtilDocIntake.MEMO_MAX) out.memo = 'TOO_LONG';
        return out;
    }

    /** 폐기 칸 문제(폐기일 필수·오늘 이전·접수일 이후 · 사유 필수 200자 · 스캔본 — 계약서는 필수) */
    static disposeProblems(draft: DocIntakeDisposeDraft, receivedOn: string, today: string, scanRequired: boolean): Partial<Record<'disposedOn' | 'reason' | 'scanFileId', DocIntakeProblem>> {
        const out: Partial<Record<'disposedOn' | 'reason' | 'scanFileId', DocIntakeProblem>> = {};
        if (!draft.disposedOn) out.disposedOn = 'REQUIRED';
        else if (draft.disposedOn > today) out.disposedOn = 'FUTURE';
        else if (draft.disposedOn < receivedOn) out.disposedOn = 'BEFORE_RECEIVED';
        const reason = draft.reason.trim();
        if (!reason) out.reason = 'REQUIRED';
        else if (reason.length > UtilDocIntake.DISPOSE_REASON_MAX) out.reason = 'TOO_LONG';
        if (scanRequired && !draft.scanFileId) out.scanFileId = 'REQUIRED';
        return out;
    }

    /** 근거로 세는 종이 접수(잘못 기록 제외 — 폐기한 것도 받은 근거라 센다) */
    static activeIntakes(intakes: readonly DocIntake[]): DocIntake[] {
        return intakes.filter((i) => !i.voidedAt);
    }

    /** 계약서 파일 사본 → 스캔본 후보(대체된 파일은 고를 수 없다) */
    static contractScanFiles(files: readonly { id: string; fileName: string; replacedAt: string | null }[]): DocScanFile[] {
        return files.map((f) => ({ id: f.id, label: f.fileName, active: !f.replacedAt }));
    }

    /** 전자 팩스로 받은 파일 id */
    static faxFileIds(bundle: DocIntakeBundle | null | undefined): string[] {
        return bundle ? Object.entries(bundle.fileChannels).filter(([, c]) => c === 'FAX').map(([id]) => id) : [];
    }

    /** 처리를 바꾸거나 폐기할 수 있는 줄 — 잘못 기록·폐기함이 아님 */
    static open(intake: DocIntake): boolean {
        return !intake.voidedAt && intake.handling !== 'DISPOSED';
    }

    static handlingSeverity(handling: DocIntakeHandling): Severity {
        if (handling === 'KEEP') return 'info';
        if (handling === 'DISPOSE_PENDING') return 'warn';
        return 'secondary';
    }

    static channelIcon(channel: DocIntakeChannel): string {
        if (channel === 'MAIL') return 'pi pi-envelope';
        if (channel === 'VISIT') return 'pi pi-user';
        return 'pi pi-print';
    }

    /**
     * 등록증을 「우편·팩스로 보냄」이라고 했는데 아직 아무것도 받지 않았나 — 그러면 심사 승인을 막는다.
     * 받은 것 = 전자 팩스 파일 · 잘못 기록이 아닌 종이 접수 · (목업) 우편·팩스를 고르지 않은 신청자의 실제 등록증 파일.
     * ⚠ 목업 스위치를 켠 동안은 실제 등록증 파일을 받은 것으로 치지 않는다(우편·팩스를 고른 신청자에게는 올린 파일이 없다).
     */
    static certificateAwaiting(bundle: DocIntakeBundle | null | undefined): boolean {
        if (!bundle?.mailDeclared) return false;
        return bundle.faxFiles.length === 0 && UtilDocIntake.activeIntakes(bundle.intakes).length === 0;
    }

    /**
     * 등록증 인쇄본 폐기 기한(ISO) — 반려·탈퇴 상태면 그 처리 기록(가장 최근 `REJECTED`·`WITHDRAWN`) 시각 + 90일. 기록이 없거나 다른 상태면 null.
     * (가정 — 서버 파기 배치의 기준 시각이 정해지면 그 값을 상세에 받는다)
     */
    static certificateDisposeDue(status: string, history: readonly { action: string; at: string }[]): string | null {
        if (status !== 'REJECTED' && status !== 'WITHDRAWN') return null;
        const times = history
            .filter((h) => h.action === status)
            .map((h) => h.at)
            .sort();
        const at = times[times.length - 1];
        return at ? new Date(Date.parse(at) + UtilDocIntake.CERTIFICATE_DISPOSE_DAYS * DAY_MS).toISOString() : null;
    }

    /** 폐기 기한이 지났는데 아직 보관·폐기 예정인 줄인가 */
    static disposeOverdue(intake: DocIntake, dueAt: string | null, now = Date.now()): boolean {
        return Boolean(dueAt) && UtilDocIntake.open(intake) && Date.parse(dueAt!) < now;
    }
}
