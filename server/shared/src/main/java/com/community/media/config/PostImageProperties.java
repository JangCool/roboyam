package com.community.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.post-image.* — 글 본문 이미지 올리기({@code POST /api/images})의 한도. 처리 동시 수·기다리는 시간·안 쓴 이미지 보관 시간은 공통 {@code roboyam.media.*}.
 *
 * @param maxBytes   한 장의 최대 용량(바이트). 기본 10MB — multipart 한도(roboyam-shared.yml {@code max-file-size} 10MB)와 같아 그보다 크면 413 {@code FILE_TOO_LARGE}
 * @param maxPixels  가로×세로 최대 화소 — 디코딩 전에 머리만 읽어 거절한다(작은 파일이 거대한 그림으로 풀리는 압축 폭탄 방지). 기본 2,500만(5000×5000)
 * @param maxSide    저장할 그림의 긴 변(px). 넘으면 줄인다(JPEG·PNG·움직이지 않는 GIF). WebP 는 디코더가 없어 줄이지 않는다
 * @param perMinute  회원 한 명이 1분에 올릴 수 있는 수(서버 메모리에서 센다) — 넘으면 429 {@code IMAGE_RATE_LIMITED}
 * @param dailyLimit 회원 한 명이 최근 24시간에 올릴 수 있는 수(DB 에서 센다, 글에 쓰지 않은 것 포함) — 넘으면 429 {@code IMAGE_DAILY_LIMIT}
 */
@ConfigurationProperties(prefix = "roboyam.post-image")
public record PostImageProperties(@DefaultValue("10485760") long maxBytes, @DefaultValue("25000000") long maxPixels, @DefaultValue("2048") int maxSide,
                                  @DefaultValue("20") int perMinute, @DefaultValue("300") int dailyLimit) {
}
