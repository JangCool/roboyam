package com.community.media.service;

/**
 * 이미지 형식 — 파일 이름·확장자·Content-Type 을 믿지 않고 <b>첫 바이트</b>(서명)로 판별한다. SVG(첫 글자가 {@code <})·그 밖의 형식은 {@code null}.
 */
public enum ImageFormat {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    GIF("image/gif", "gif"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String extension;

    ImageFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    /** 저장소 키의 확장자 */
    public String extension() {
        return extension;
    }

    /** 서명으로 형식을 알아낸다. 모르는 형식이면 null */
    public static ImageFormat sniff(byte[] d) {
        if (d == null) {
            return null;
        }
        if (d.length >= 8 && (d[0] & 0xff) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G' && d[4] == 0x0d && d[5] == 0x0a && d[6] == 0x1a && d[7] == 0x0a) {
            return PNG;
        }
        if (d.length >= 3 && (d[0] & 0xff) == 0xff && (d[1] & 0xff) == 0xd8 && (d[2] & 0xff) == 0xff) {
            return JPEG;
        }
        if (d.length >= 6 && d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8' && (d[4] == '7' || d[4] == '9') && d[5] == 'a') {
            return GIF;
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return WEBP;
        }
        return null;
    }
}
