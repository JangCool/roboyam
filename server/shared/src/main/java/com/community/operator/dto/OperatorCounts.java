package com.community.operator.dto;

/** 운영자 목록의 탭마다 몇 명인지 */
public record OperatorCounts(long active, long pending, long blocked) {
}
