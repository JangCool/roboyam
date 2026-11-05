/**
 * 사업자 서류 「접수 기록」(인쇄본·팩스로 받은 계약서 사본·사업자등록증 사본) — 서버 `DocIntakeAdminView`·`DocIntakeAdminRequest` 와 주고받는 모양(docs/features/biz-doc-intake-contract.md).
 * 계약서 사본(신청 건) + 사업자등록증 사본(사업자 신원) 둘 다. 팩스는 받은 형태 종이·파일 중 고른다 —
 * 종이면 아래 `DocIntake` 줄(보관·폐기 상태), 파일(전자 팩스)이면 파일 사본으로 올리고 파일 줄에 방식만 `FAX` 로 표시한다.
 */

/** 어느 서류인가 — 계약서 사본(신청 건 `biz_applications`) · 사업자등록증 사본(사업자 신원 `business_profiles`) */
export type DocIntakeDoc = 'CONTRACT' | 'CERTIFICATE';

/** 종이 접수 방식 — 우편 · 직접 전달 · 팩스(종이로 출력된 것) */
export type DocIntakeChannel = 'MAIL' | 'VISIT' | 'FAX';
export const DOC_INTAKE_CHANNELS: readonly DocIntakeChannel[] = ['MAIL', 'VISIT', 'FAX'];

/** 인쇄본 처리 — 계속 보관 · 스캔 후 폐기 예정 · 폐기함(되돌릴 수 없다) */
export type DocIntakeHandling = 'KEEP' | 'DISPOSE_PENDING' | 'DISPOSED';
/** 고를 수 있는 처리(폐기는 [폐기 처리] 로만) */
export type DocIntakeOpenHandling = Exclude<DocIntakeHandling, 'DISPOSED'>;
export const DOC_INTAKE_OPEN_HANDLINGS: readonly DocIntakeOpenHandling[] = ['KEEP', 'DISPOSE_PENDING'];

/** 파일 사본이 들어온 길 — 올림(기본) · 전자 팩스. 파일 줄에 표시만 더한다 */
export type DocFileChannel = 'UPLOAD' | 'FAX';

/** 접수 기록의 주인 — 계약서는 신청 건 id, 등록증은 사업자 id */
export interface DocIntakeTarget {
    doc: DocIntakeDoc;
    ownerId: string;
}

/** 종이 접수 한 줄. 「잘못 기록」(취소)은 지우지 않고 `voidedAt` 을 찍은 채 남는다 — 개수에 세지 않는다 */
export interface DocIntake {
    id: string;
    doc: DocIntakeDoc;
    channel: DocIntakeChannel;
    /** 받은 날(`YYYY-MM-DD`, KST 오늘 이전) */
    receivedOn: string;
    /** 접수한 운영자 이름(서버가 로그인 운영자로 채운다) — 지워진 운영자면 null */
    receivedBy: string | null;
    /** 기록한 시각(ISO) */
    recordedAt: string;
    /** 쪽수(1~200) — 모르면 null */
    pages: number | null;
    /** 메모(≤200) */
    memo: string | null;
    handling: DocIntakeHandling;
    /** 보관 위치(≤100) — 계속 보관일 때만 */
    storageLocation: string | null;
    /** 폐기일(`YYYY-MM-DD`) · 폐기한 운영자 · 사유(≤200) · 스캔본 파일 id(계약서는 필수 — 대체되지 않은 파일 사본 중) */
    disposedOn: string | null;
    disposedBy: string | null;
    disposeReason: string | null;
    scanFileId: string | null;
    /** 잘못 기록(취소) 시각 · 운영자 · 사유(5~200) */
    voidedAt: string | null;
    voidedBy: string | null;
    voidReason: string | null;
}

/** 등록증 전자 팩스 파일 — 그 사업자의 등록증 파일(`business_documents`, 파기되지 않은 것) 중 들어온 길이 `FAX` 인 줄 */
export interface DocIntakeFaxFile {
    id: string;
    fileName: string;
    mimeType: string;
    size: number;
    receivedAt: string;
    /** 올린 운영자 — 회원이 올렸거나 지워진 운영자면 null */
    receivedBy: string | null;
}

/** 한 대상의 접수 기록 묶음 */
export interface DocIntakeBundle {
    target: DocIntakeTarget;
    intakes: DocIntake[];
    /**
     * 파일 사본 id → 들어온 길 — 계약서는 그 건의 계약서 파일, 등록증은 그 사업자의 등록증 파일(파기되지 않은 것, 올린 순).
     * 계약서 「전자 팩스」 표시는 파일 줄 `channel` 로 하고, 이 값은 등록증 폐기의 스캔본 후보(파일 id)에 쓴다.
     */
    fileChannels: Record<string, DocFileChannel>;
    /** 등록증 전자 팩스 파일 — 계약서는 늘 빈 목록 */
    faxFiles: DocIntakeFaxFile[];
    /** 신청자가 등록증을 「우편·팩스로 보냄」으로 골랐나(등록증만 — 사업자 상세 `certificateDelivery === 'MAIL_FAX'` 와 같은 값) */
    mailDeclared: boolean;
}

export interface DocIntakeCreateRequest {
    channel: DocIntakeChannel;
    receivedOn: string;
    pages: number | null;
    memo: string | null;
    handling: DocIntakeOpenHandling;
    /** 계속 보관일 때만(아니면 null) */
    storageLocation: string | null;
}

export interface DocIntakeHandlingRequest {
    handling: DocIntakeOpenHandling;
    storageLocation: string | null;
}

export interface DocIntakeDisposeRequest {
    disposedOn: string;
    reason: string;
    /** 스캔본 파일 id — 계약서는 필수, 등록증은 선택 */
    scanFileId: string | null;
}
