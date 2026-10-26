package com.community.point.dto.response;

import java.util.List;

/** 묶음 한 쪽과 같은 조건의 전체 수 */
public record PointLotPageView(List<PointLotView> items, long total) {
}
