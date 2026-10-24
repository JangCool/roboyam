package com.community.nicknameimage.dto.response;

/** 등급 고르개의 한 칸 — 서열 순으로 온다({@code grade.read} 없이도 보인다) */
public record NicknameImageGradeOptionView(String key, String label, int level) {
}
