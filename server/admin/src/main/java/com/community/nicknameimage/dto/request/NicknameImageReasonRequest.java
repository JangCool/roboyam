package com.community.nicknameimage.dto.request;

/** 반려·해제 사유 — 앞뒤 공백을 자르고 1~200자(서비스가 {@code 400 fields.reason}) */
public record NicknameImageReasonRequest(String reason) {
}
