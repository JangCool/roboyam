package com.community.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.storage.* — 올린 이미지 파일을 두는 곳.
 *
 * @param type      저장소 종류. 지금은 {@code local}(서버 디스크)만 있다
 * @param localPath 로컬 디스크 저장소의 폴더. 공개 사이트(api)와 관리자 앱(admin)이 <b>같은 폴더</b>를 보아야 한다(admin 이 쓰고 둘 다 {@code /media/**} 로 읽는다).
 *                  환경 변수 {@code ROBOYAM_STORAGE_PATH}. 기본값은 설치형·도커 배포의 볼륨 경로({@code /var/lib/roboyam/media})이고 local 프로파일은 {@code ./data/media}
 */
@ConfigurationProperties(prefix = "roboyam.storage")
public record StorageProperties(@DefaultValue("local") String type, @DefaultValue("/var/lib/roboyam/media") String localPath) {
}
