package com.community.nicknameimage.dto.response;

import java.util.List;

/** 올리기 한도(화면이 미리 검사할 값 — 판정은 서버) — user-web {@code NicknameImageLimits} */
public record NicknameImageLimitsView(int maxBytes, int width, int height, String ratio, int maxGifFrames, List<String> formats, int rejectedReapplyHours) {
}
