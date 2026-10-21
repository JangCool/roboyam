package com.community.installation.entity;

/** 설치 상태. installation.status 의 CHECK 와 같은 값이다. */
public enum InstallationStatus {
    /** 설치 전 — 설치 마법사만 열려 있다 */
    PENDING,
    /** 설치 끝 */
    COMPLETED
}
