package com.community.osslicense.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/admin/oss-licenses}(와 안내 문구 저장 응답) — 안내 문구 + 직접 추가 항목(이름순). 자동 수집 목록은 화면이 정적 파일 {@code /oss-licenses.json} 으로 따로 읽는다.
 *
 * @param intro          안내 문구(없으면 null — 화면 기본 문구)
 * @param introUpdatedAt 안내 문구를 마지막으로 바꾼 때(마이그레이션이 만든 줄이면 그 시각)
 */
public record OssNoticeAdminView(String intro, Instant introUpdatedAt, List<OssNoticeEntryView> entries) {
}
