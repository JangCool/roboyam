/**
 * 메일 서버 설정 API 가 주고받는 모양 — 서버 `com.community.mail.dto.*`(server/admin) 와 같은 이름이다.
 * 계약: `docs/features/mail-settings-contract.md`. ⚠ 비밀번호 값은 어떤 응답에도 없다(`passwordSet` 만).
 */
import type { SettingHistoryItem, SettingHistoryPage } from '~/composables/common/useSettingHistory.types';

/** 보안 방식 — 비워 보내면 서버가 `NONE` 으로 저장한다 */
export type MailSecurity = 'NONE' | 'STARTTLS' | 'SSL';

/** `GET /api/admin/mail-settings` (`MailSettingsView`) — 설치 마법사가 넣은 행(하나뿐)을 그대로 보여 준다 */
export interface MailSettingsView {
    /** false 면 설정이 없다(설치 때 건너뜀) — 값 칸이 모두 null */
    configured: boolean;
    host: string | null;
    port: number | null;
    security: MailSecurity | null;
    /** 비어 있으면(null) 인증 없이 보낸다 */
    username: string | null;
    passwordSet: boolean;
    fromAddress: string | null;
    /**
     * 보내는 이름(받는 사람 메일함에 「보내는 사람」으로 보이는 이름) — 없으면 주소만 보인다.
     * ⚠ 서버(shared V55)가 함께 들어오는 칸이라 아직 응답에 없을 수 있다 → 없으면 null 로 다룬다.
     */
    senderName?: string | null;
    /** 제목 머리말(모든 메일 제목 앞에 붙는 글자, 예: `[ROBOYAM]`). `senderName` 과 같은 이유로 없을 수 있다 */
    subjectPrefix?: string | null;
    /** 서버에 암호화 키가 있는가 — 없으면 새 비밀번호 저장이 503 `CRYPTO_KEY_MISSING` 이고 인증 메일도 못 보낸다 */
    cryptoKeyConfigured: boolean;
    /** 마지막 저장 시각(UTC ISO). 설정이 없으면 null */
    updatedAt: string | null;
    /** 마지막으로 저장한 운영자 이름(지워진 운영자는 「삭제된 운영자」) */
    updatedByName: string | null;
}

/** `PUT /api/admin/mail-settings` (`MailSettingsSaveRequest`) */
export interface MailSettingsSaveRequest {
    host: string;
    port: number;
    security: MailSecurity;
    /** 비우면(null) 인증 없이 보낸다 — 저장된 비밀번호도 지운다 */
    username: string | null;
    /** 빼면 저장된 비밀번호를 그대로 둔다(단 사용자 이름·서버 주소·보안 방식을 바꾸면 400 `fields.password`) */
    password?: string;
    fromAddress: string;
    /** 비우면 null 로 저장 */
    senderName: string | null;
    /** 비우면 null 로 저장 */
    subjectPrefix: string | null;
}

/** 시험 발송 실패 원인 코드 */
export type MailTestReason = 'NOT_CONFIGURED' | 'CRYPTO_KEY_MISSING' | 'CONNECT_FAILED' | 'AUTH_FAILED' | 'TLS_FAILED' | 'TIMEOUT' | 'REJECTED' | 'FAILED';

/** `POST /api/admin/mail-settings/test` 응답 (`MailTestResultView`) — 실패도 200 */
export interface MailTestResultView {
    succeeded: boolean;
    reason: MailTestReason | null;
    /** 원인 코드를 요청 언어로 푼 짧은 문구(성공이면 null) */
    detail: string | null;
}

export type MailSettingHistoryItemView = SettingHistoryItem;
export type MailSettingHistoryPage = SettingHistoryPage<MailSettingHistoryItemView>;
