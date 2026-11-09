package com.community.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.storage.* — 올린 이미지 파일을 두는 곳(docs/features/media-storage-contract.md).
 *
 * @param type      저장소 종류 — 환경 변수 {@code ROBOYAM_STORAGE_TYPE}. 기본은 local·dev 프로필 {@code local}, prod 프로필 {@code r2}(roboyam-shared-prod.yml)
 *                  <ul>
 *                    <li>{@code local}: 서버 디스크({@code localPath})</li>
 *                    <li>{@code r2}: Cloudflare R2 — 계정 ID({@code r2.accountId})만 주면 endpoint·region({@code auto})을 채운다. 키는 R2 API 토큰(필수)</li>
 *                    <li>{@code s3}: AWS S3(endpoint 비움) 또는 MinIO 등 다른 S3 호환(endpoint·path-style). 키를 비우면 기본 자격 증명 체인(EC2 IAM 역할)</li>
 *                  </ul>
 *                  r2 와 s3 는 같은 구현({@code S3ImageStorage})이고 기본값만 다르다
 * @param localPath 로컬 디스크 저장소의 폴더. 공개 사이트(api)와 관리자 앱(admin)이 <b>같은 폴더</b>를 보아야 한다(둘 다 쓰고 둘 다 {@code /media/**} 로 읽는다).
 *                  환경 변수 {@code ROBOYAM_STORAGE_PATH}. 기본값은 설치형·도커 배포의 볼륨 경로({@code /var/lib/roboyam/media})이고 local 프로파일은 {@code ~/.roboyam/media}
 * @param s3        버킷 공통 설정(버킷·키·공개 주소 등) — {@code r2}·{@code s3} 가 같이 쓴다
 * @param r2        R2 전용 — 계정 ID
 */
@ConfigurationProperties(prefix = "roboyam.storage")
public record StorageProperties(@DefaultValue("local") String type, @DefaultValue("/var/lib/roboyam/media") String localPath, @DefaultValue S3 s3,
                                @DefaultValue R2 r2) {

    /**
     * roboyam.storage.r2.* — Cloudflare R2.
     *
     * @param accountId 계정 ID(R2 › 개요). endpoint 를 직접 주지 않았으면 {@code https://<계정 ID>.r2.cloudflarestorage.com} 이 된다
     */
    public record R2(String accountId) {
    }

    /**
     * roboyam.storage.s3.* — S3 호환 API 의 버킷 설정({@code r2}·{@code s3} 공통). 빈 글자는 「값 없음」이다.
     *
     * @param endpoint      API 주소. r2 는 비우면 계정 ID 로 만들고, s3 는 비우면 AWS S3 기본 주소(MinIO {@code http://localhost:9000})
     * @param region        지역 — r2 는 비우면 {@code auto}, s3 는 필수(AWS {@code ap-northeast-2}, MinIO {@code us-east-1})
     * @param bucket        버킷 이름 — 필수
     * @param accessKey     접근 키 ID. r2 는 필수(R2 API 토큰), s3 는 비밀 키와 둘 다 비우면 AWS 기본 자격 증명 체인(EC2 IAM 역할·환경 변수·~/.aws). {@code ENC(...)} 가능
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
