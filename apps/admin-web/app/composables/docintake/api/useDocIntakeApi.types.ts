/**
 * 사업자 서류 「접수 기록」(인쇄본·팩스로 받은 계약서 사본·사업자등록증 사본) — 서버와 주고받을 모양(**목업 — 서버 연결 전, API 후보**).
 * 설계 메모 2026-11-05(사용자 결정): 계약서 사본(신청 건) + 사업자등록증 사본(사업자 신원) 둘 다. 팩스는 받은 형태 종이·파일 중 고른다 —
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
    /** 접수한 운영자 이름(서버가 로그인 운영자로 채운다) */
    receivedBy: string;
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

/**
 * 등록증 전자 팩스 파일(목업 전용 줄) — 관리자가 등록증 파일을 올리는 API 가 아직 없어 목업 메모리에 이름·크기만 둔다.
 * 서버가 붙으면 `business_documents` 의 등록증 파일 줄(+ 들어온 길 `FAX`)이 된다.
 */
export interface DocIntakeFaxFile {
    id: string;
    fileName: string;
    mimeType: string;
    size: number;
    receivedAt: string;
    receivedBy: string;
}

/** 한 대상의 접수 기록 묶음 */
export interface DocIntakeBundle {
    target: DocIntakeTarget;
    intakes: DocIntake[];
    /** 파일 사본 id → 들어온 길(없으면 올림) — 계약서 파일 줄의 「전자 팩스」 표시 */
    fileChannels: Record<string, DocFileChannel>;
    /** 등록증 전자 팩스 파일(목업) — 계약서는 늘 빈 목록 */
    faxFiles: DocIntakeFaxFile[];
    /**
     * 신청자가 등록증을 「우편·팩스로 보냄」으로 골랐나(등록증만) — 서버가 붙으면 사업자 상세의 `certificateDelivery` 가 된다.
     * 목업은 서랍의 「목업」 스위치나 `?mock=docintake-mailfax` 로 켠다.
     */
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
