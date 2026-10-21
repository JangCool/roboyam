package com.community.operator.dto.response;

import com.community.operator.dto.OperatorCounts;
import java.util.List;

/** 운영자 목록 — 탭의 운영자와 탭마다 몇 명인지({@code counts}). 키워드와 상관없이 전체 인원이다 */
public record OperatorPage(List<OperatorView> items, long total, OperatorCounts counts) {
}
