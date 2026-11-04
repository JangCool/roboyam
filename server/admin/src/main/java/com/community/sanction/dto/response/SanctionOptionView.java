package com.community.sanction.dto.response;

/** 단계 선택지 — {@code state} NEXT · SKIP · LOWER · BLOCKED, {@code requiresBan} 영구 정지·건너뛰기({@code sanction.ban}) */
public record SanctionOptionView(String kind, int rank, String state, boolean requiresBan) {
}
