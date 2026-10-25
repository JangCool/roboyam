package com.community.popular.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET·PUT /api/admin/popular-settings}.
 *
 * @param settings           지금 값
 * @param defaults           기본값(예전 서버 상수 — 「기본값으로」 단추)
 * @param limits             숫자 칸 범위
 * @param weekStarts         주 시작 요일 선택지 MONDAY · SUNDAY
 * @param current            실시간 순위 미리보기(상위 10개)
 * @param computedAt         실시간 순위를 마지막으로 계산한 시각(없으면 null)
 * @param recomputePending   다시 계산 요청이 대기·실행 중이다
 * @param recomputeRequested 이 응답(PUT)이 다시 계산을 요청했다 — 바뀐 칸이 없거나 GET 이면 false(이미 대기 중인 요청이 있어도 false — 그 요청이 새 값으로 계산한다)
 * @param updatedAt          마지막 저장 시각(저장한 적 없으면 null)
 * @param updatedBy          마지막 저장 운영자(없으면 null)
 */
public record PopularSettingsView(PopularSettingValuesView settings, PopularSettingValuesView defaults, PopularLimitsView limits, List<String> weekStarts,
                                  List<PopularPreviewItemView> current, Instant computedAt, boolean recomputePending, boolean recomputeRequested,
                                  Instant updatedAt, PopularOperatorView updatedBy) {
}
