package com.community.site.service;

import java.util.Arrays;
import java.util.List;

/**
 * 사이트 이미지의 저장소 키(경로) 규칙 — 쓰는 곳(관리자 앱의 올리기)과 읽는 곳(공개 사이트의 주소 만들기)이 같은 규칙을 쓰도록 shared 에 둔다.
 * 키마다 <b>버전 토큰</b>(올릴 때마다 새로 만드는 무작위 16자 16진수)이 폴더로 들어 있어 내용이 바뀌면 주소도 바뀐다 — 한 번 쓴 파일은 고치지 않는다.
 *
 * <ul>
 *   <li>로고: {@code site-logo/{버전}/logo.{png|jpg|webp}} (GIF 는 PNG 로 저장한다)</li>
 *   <li>사이트 아이콘: {@code site-icon/{버전}/favicon-32.png} · {@code favicon-48.png} · {@code apple-touch-180.png} · {@code icon-192.png} · {@code icon-512.png}</li>
 *   <li>링크 미리보기 이미지: {@code site-og/{버전}/og.png} (1200×630)</li>
 * </ul>
 */
public final class SiteMediaKeys {

    /** 기본 사이트 아이콘(jar 에 든 SVG, {@code MediaResourceConfig} 가 서빙) — 올려 둔 아이콘이 없을 때 이 그림을 보인다 */
    public static final String DEFAULT_ICON_URL = "/media/default/site-icon.svg";

    /** 기본 링크 미리보기 이미지 */
    public static final String DEFAULT_OG_URL = "/media/default/site-og.svg";

    /** 링크 미리보기 이미지의 가로·세로(px) — 서버가 늘 이 크기로 만든다 */
    public static final int OG_WIDTH = 1200;
    public static final int OG_HEIGHT = 630;

    private static final String LOGO_PREFIX = "site-logo/";
    private static final String ICON_PREFIX = "site-icon/";
    private static final String OG_PREFIX = "site-og/";

    /** 사이트 아이콘으로 만드는 파일 한 종류 — 저장소 파일 이름과 한 변(px). {@code flatten} 이면 투명한 곳을 흰색으로 채운다(iOS 홈 화면 아이콘은 투명을 검게 채운다) */
    public enum IconFile {
        FAVICON_32("favicon-32.png", 32, false),
        FAVICON_48("favicon-48.png", 48, false),
        APPLE_TOUCH_180("apple-touch-180.png", 180, true),
        ICON_192("icon-192.png", 192, false),
        ICON_512("icon-512.png", 512, false);

        private final String fileName;
        private final int side;
        private final boolean flatten;

        IconFile(String fileName, int side, boolean flatten) {
            this.fileName = fileName;
            this.side = side;
            this.flatten = flatten;
        }

        public String fileName() {
            return fileName;
        }

        public int side() {
            return side;
        }

        public boolean flatten() {
            return flatten;
        }
    }

    private SiteMediaKeys() {
    }

    /** 로고 그림 파일의 키. {@code extension} 은 {@code png} · {@code jpg} · {@code webp} */
    public static String logo(String version, String extension) {
        return LOGO_PREFIX + version + "/logo." + extension;
    }

    public static String icon(String version, IconFile file) {
        return ICON_PREFIX + version + "/" + file.fileName;
    }

    /** 한 버전 폴더에 만들어지는 아이콘 키들 */
    public static List<String> iconKeys(String version) {
        return Arrays.stream(IconFile.values()).map(file -> icon(version, file)).toList();
    }

    public static String og(String version) {
        return OG_PREFIX + version + "/og.png";
    }
}
