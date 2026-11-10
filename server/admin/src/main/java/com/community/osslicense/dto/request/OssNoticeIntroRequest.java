package com.community.osslicense.dto.request;

/** {@code PUT /api/admin/oss-licenses/intro} — 공개 화면 제목 아래 안내 문구(비우면 화면 기본 문구) */
public record OssNoticeIntroRequest(String intro) {
}
