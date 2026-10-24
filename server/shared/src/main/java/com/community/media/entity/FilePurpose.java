package com.community.media.entity;

/** 파일의 용도 — 용도마다 허용하는 형식·크기와 저장소 키 머리가 다르다 */
public enum FilePurpose {

    /** 사이트 로고 그림(PNG·JPG·GIF·WEBP) — 저장소 키 {@code site-logo/{버전}/…}. 운영자가 올린다 */
    SITE_LOGO,

    /** 글 본문 이미지(JPEG·PNG·WebP·GIF) — 저장소 키 {@code post-image/{년월}/{버전}.{확장자}}. 회원이 공개 사이트 에디터에서 올린다({@code POST /api/images}) */
    POST_IMAGE,

    /** 하이라이트 대표 이미지(PNG·JPG·GIF·WEBP — GIF 는 첫 장면 PNG) — 저장소 키 {@code highlight/{버전}/image.{확장자}}. 운영자가 올린다(권한 highlight.manage, V75) */
    HIGHLIGHT_IMAGE
}
