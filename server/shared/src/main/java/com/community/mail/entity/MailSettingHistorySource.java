package com.community.mail.entity;

/** 메일 서버 설정 이력의 출처 — mail_setting_history.source 의 CHECK 와 같은 값이다 */
public enum MailSettingHistorySource {
    /** 설치 마법사의 첫 저장 */
    INSTALL,
    /** 관리자 화면(설정 › 메일 서버)의 저장 */
    ADMIN
}
