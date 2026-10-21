package com.community.mail.dto;

/** 연결 시험이 실패한 원인(화면이 문구를 고른다) */
public enum MailCheckReason {
    CONNECT_FAILED, AUTH_FAILED, TLS_FAILED, TIMEOUT
}
