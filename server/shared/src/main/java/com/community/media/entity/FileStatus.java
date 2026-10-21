package com.community.media.entity;

/** 파일의 상태 — 올리기만 한 파일(PENDING)은 어디에도 쓰이지 않고, 쓰는 중(IN_USE)이 되면 정리 대상에서 빠진다 */
public enum FileStatus {
    PENDING,
    IN_USE
}
