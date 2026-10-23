package com.community.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.media.* — 이미지 처리(디코딩·크기 맞추기·다시 인코딩)의 보호 한도. 디코딩한 그림은 메모리를 많이 쓰므로(4000×4000 ARGB ≈ 64MB) 동시에 처리하는 수를 제한한다.
 *
 * <p>관리자 앱(사이트 이미지)과 공개 사이트(글 이미지 {@code POST /api/images})가 각자 자기 값으로 쓴다 — 코덱·관문은 shared 에 있다.
 *
 * @param maxConcurrentProcessing 동시에 처리할 수 있는 이미지 수(관리 콘솔은 트래픽이 작아 2 로 충분하다). 모자라면 기다린다
 * @param acquireTimeoutSeconds   자리가 날 때까지 기다리는 시간(초). 넘기면 503 {@code IMAGE_BUSY}
 * @param orphanRetentionHours    올리기만 하고 지정하지 않은 파일을 지우기까지 두는 시간(「올리다 만 파일 정리」 배치)
 */
@ConfigurationProperties(prefix = "roboyam.media")
public record MediaProperties(@DefaultValue("2") int maxConcurrentProcessing, @DefaultValue("10") int acquireTimeoutSeconds,
                              @DefaultValue("24") int orphanRetentionHours) {
}
