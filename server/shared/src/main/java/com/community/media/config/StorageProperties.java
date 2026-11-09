package com.community.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.storage.* — 올린 이미지 파일을 두는 곳(docs/features/media-storage-contract.md).
 *
 * @param type      저장소 종류 {@code local}(서버 디스크) | {@code s3}(S3 호환 버킷 — Cloudflare R2·AWS S3·MinIO). 환경 변수 {@code ROBOYAM_STORAGE_TYPE}.
 *                  기본은 local·dev 프로필 {@code local}, prod 프로필 {@code s3}(roboyam-shared-prod.yml)
 * @param localPath 로컬 디스크 저장소의 폴더. 공개 사이트(api)와 관리자 앱(admin)이 <b>같은 폴더</b>를 보아야 한다(둘 다 쓰고 둘 다 {@code /media/**} 로 읽는다).
 *                  환경 변수 {@code ROBOYAM_STORAGE_PATH}. 기본값은 설치형·도커 배포의 볼륨 경로({@code /var/lib/roboyam/media})이고 local 프로파일은 {@code ~/.roboyam/media}
 * @param s3        S3 호환 버킷 설정 — {@code type = s3} 일 때만 쓴다
 */
@ConfigurationProperties(prefix = "roboyam.storage")
public record StorageProperties(@DefaultValue("local") String type, @DefaultValue("/var/lib/roboyam/media") String localPath, @DefaultValue S3 s3) {

    /**
     * roboyam.storage.s3.* — S3 호환 API 하나로 R2·AWS S3·MinIO 를 설정만으로 고른다. 빈 글자는 「값 없음」이다.
     *
     * @param endpoint      API 주소(R2 {@code https://<계정 ID>.r2.cloudflarestorage.com}, MinIO {@code http://localhost:9000}). 비우면 AWS S3 기본 주소
     * @param region        지역(R2 {@code auto}, AWS {@code ap-northeast-2}, MinIO {@code us-east-1}) — 필수
     * @param bucket        버킷 이름 — 필수
     * @param accessKey     접근 키 ID. 비밀 키와 둘 다 비우면 AWS 기본 자격 증명 체인(EC2 IAM 역할·환경 변수·~/.aws)을 쓴다. {@code ENC(...)} 가능
     * @param secretKey     비밀 접근 키 — {@code ENC(...)}(ROBOYAM_CRYPTO_KEY 로 복호화) 권장
     * @param pathStyle     {@code https://endpoint/bucket/key} 경로 방식으로 부를지(MinIO 는 true). 비우면 false
     * @param publicBaseUrl 브라우저가 이미지를 바로 가져갈 공개 주소(R2 사용자 도메인 {@code https://media.example.com} 등, 끝 「/」 없이).
     *                      비우면 새 이미지 주소도 {@code /media/…} 이고 앱이 버킷에서 대신 읽어 내보낸다
     */
    public record S3(String endpoint, String region, String bucket, String accessKey, String secretKey, Boolean pathStyle, String publicBaseUrl) {

        /** 비밀 키를 로그·오류에 싣지 않는다 */
        @Override
        public String toString() {
            return "S3[endpoint=" + endpoint + ", region=" + region + ", bucket=" + bucket + ", accessKey=" + (blank(accessKey) ? "" : "***")
                    + ", secretKey=" + (blank(secretKey) ? "" : "***") + ", pathStyle=" + pathStyle + ", publicBaseUrl=" + publicBaseUrl + "]";
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }
}
