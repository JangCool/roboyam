package com.community.emoticon.entity;

/** 그림 형식 — GIF(움직임) · PNG(정지). 파일 이름·Content-Type 이 아니라 바이트로 가른다 */
public enum EmoticonImageFormat {
    GIF("image/gif", "gif"),
    PNG("image/png", "png");

    private final String contentType;
    private final String extension;

    EmoticonImageFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }
}
