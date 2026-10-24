package com.community.block.dto.response;

import java.time.Instant;

/** 내가 남긴 메모 한 줄 — user-web {@code MemoView}. {@code id} 는 메모를 남긴 상대 회원의 ID */
public record MemoView(String id, String nickname, String grade, String gradeLabel, String role, String memo, Instant updatedAt) {
}
