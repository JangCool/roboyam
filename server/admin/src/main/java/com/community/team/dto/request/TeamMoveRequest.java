package com.community.team.dto.request;

/** 한 칸 옮기기 — {@code direction} 은 {@code UP} 또는 {@code DOWN} */
public record TeamMoveRequest(String direction) {
}
