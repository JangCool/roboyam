package com.community.osslicense.dto.response;

import java.util.List;

/**
 * {@code GET /api/site/oss-licenses} 의 {@code data} — 안내 문구(없으면 null = 화면 기본 문구, {@code {사이트}} 는 화면이 바꾼다)와 숨기지 않은 직접 추가 항목(이름순).
 * 자동 수집 목록은 화면이 정적 파일 {@code /oss-licenses.json} 으로 따로 읽어 합친다.
 */
public record OssNoticePublicResponse(String intro, List<OssNoticePublicEntryView> entries) {
}
