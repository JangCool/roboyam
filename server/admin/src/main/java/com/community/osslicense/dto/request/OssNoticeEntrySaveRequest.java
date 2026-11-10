package com.community.osslicense.dto.request;

/**
 * {@code POST /api/admin/oss-licenses/entries} · {@code PUT …/entries/{id}} — 직접 추가 항목의 모든 칸. 검사는 서비스가 모아서 한다({@code fields.<칸>}).
 *
 * @param category USER · ADMIN · SERVER · FONT_ICON · OTHER
 * @param hidden   비우면 만들 때 보임(false), 고칠 때 지금 값
 */
public record OssNoticeEntrySaveRequest(String name, String version, String license, String copyright, String licenseText, String sourceUrl, String category,
                                        Boolean hidden) {

    public static OssNoticeEntrySaveRequest empty() {
        return new OssNoticeEntrySaveRequest(null, null, null, null, null, null, null, null);
    }

    /** 전문은 길어 로그에 싣지 않는다 */
    @Override
    public String toString() {
        return "OssNoticeEntrySaveRequest[name=" + name + ", category=" + category + ", hidden=" + hidden + "]";
    }
}
