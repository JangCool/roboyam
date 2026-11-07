package com.community.avatarupload.dto.response;

import java.util.List;

/**
 * 올리기 한도 — 화면이 미리 거를 값(판정은 서버, {@code AvatarUploadRules}).
 *
 * @param minSize               가장 짧은 변의 최소(px)
 * @param maxSize               서버가 맞추는 저장 크기의 한도(px — 움직이는 GIF 원본·WebP 는 줄이지 않는다)
 * @param ratioTolerancePercent 정사각형 허용 오차(%)
 */
public record AvatarUploadLimitsView(int maxBytes, int minSize, int maxSize, int ratioTolerancePercent, int maxGifFrames, List<String> formats) {
}
