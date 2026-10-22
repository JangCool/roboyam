package com.community.point.dto.response;

/** 오늘 그 일로 받은 횟수와 하루 상한(0 이면 꺼짐) */
public record PointCountView(int count, int cap) {
}
