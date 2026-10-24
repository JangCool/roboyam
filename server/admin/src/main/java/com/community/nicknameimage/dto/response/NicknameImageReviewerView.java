package com.community.nicknameimage.dto.response;

/** 승인·반려한 운영자(지워진 운영자면 이름이 null) */
public record NicknameImageReviewerView(String id, String name) {
}
