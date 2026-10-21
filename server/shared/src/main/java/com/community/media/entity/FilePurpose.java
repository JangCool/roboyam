package com.community.media.entity;

/** 파일의 용도 — 용도마다 허용하는 형식·크기와 저장소 키 머리가 다르다 */
public enum FilePurpose {

    /** 사이트 로고 그림(PNG·JPG·GIF·WEBP) — 저장소 키 {@code site-logo/{버전}/…} */
    SITE_LOGO
}
