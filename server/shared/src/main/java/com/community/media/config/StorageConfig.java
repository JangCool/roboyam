package com.community.media.config;

import com.community.media.service.ImageStorage;
import com.community.media.service.LocalDiskImageStorage;
import com.community.media.service.S3ImageStorage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * 저장소 빈 — 설정 {@code irobit.storage.type}({@link StorageProperties})에 맞는 구현을 만든다(docs/features/media-storage-contract.md).
 *
 * <ul>
 *   <li>{@code local}: 로컬 폴더가 없으면 만든다(못 만들어도 기동은 막지 않고, 올리는 순간 503 으로 알린다).</li>
 *   <li>{@code r2}·{@code s3}: 같은 S3 호환 구현({@link S3ImageStorage}), 기본값만 다르다 — r2 는 계정 ID 로 endpoint 를, 지역은 {@code auto} 를 채우고 키(R2 API 토큰)가 필수,
 *       s3 는 endpoint 비우면 AWS 기본·지역 필수·키를 비우면 기본 자격 증명 체인. 직접 준 endpoint·region 이 늘 이긴다.
 *       <b>설정이 틀리면(버킷·지역·계정 ID 없음, 키 한쪽만, 주소 형식) 기동을 멈춘다</b> — 어느 프로필이든 같다. 조용히 로컬 디스크로 물러나면
 *       앱 서버 여러 대가 서로 다른 곳에 쓰는 문제가 아무도 모르게 돌아오기 때문이다. 버킷에 <b>닿지 못하는 것</b>(네트워크·권한)은 기동을 막지 않고 경고만 남긴다 —
 *       로컬 폴더를 못 만들 때와 같이 올리기·읽기가 503 으로 알린다(잠깐의 장애로 앱 전체가 뜨지 못하지 않게).
 *       prod 에서 공개 주소가 비면 경고한다(앱이 이미지를 대신 내보내 앱 서버 송신량이 는다).</li>
 * </ul>
 */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    static final Profiles PROD = Profiles.of("prod");

    private static final Pattern HTTP_URL = Pattern.compile("^https?://[^\\s/?#]+(/[^\\s?#]*)?$");
    private static final Pattern R2_ACCOUNT_ID = Pattern.compile("^[A-Za-z0-9]{1,64}$");

    /** S3 호환 저장소의 종류 — 같은 구현, 기본값만 다르다 */
    enum BucketKind {
        R2, S3
    }

    @Bean
    public ImageStorage imageStorage(StorageProperties properties, Environment environment) {
        String type = properties.type() == null ? "" : properties.type().trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "local" -> local(properties.localPath());
            case "r2" -> bucket(settings(BucketKind.R2, properties.s3(), properties.r2()), environment.acceptsProfiles(PROD));
            case "s3" -> bucket(settings(BucketKind.S3, properties.s3(), properties.r2()), environment.acceptsProfiles(PROD));
            default -> throw new IllegalStateException("irobit.storage.type(환경 변수 IROBIT_STORAGE_TYPE)은 local · r2 · s3 중 하나다 — 지금 값: '" + type + "'");
        };
    }

    private static ImageStorage local(String localPath) {
        Path root = Paths.get(localPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            log.warn("이미지 저장 폴더를 만들지 못했다(올리기는 503 STORAGE_UNAVAILABLE 로 실패한다) path={}", root, e);
        }
        log.info("이미지 저장소: 로컬 디스크 {}", root);
        return new LocalDiskImageStorage(root);
    }

    private static ImageStorage bucket(S3Settings settings, boolean prod) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(settings.region()))
                .credentialsProvider(credentials(settings))
                .forcePathStyle(settings.pathStyle())
                // SDK 2.30 부터 기본이 된 「요청마다 체크섬」을 S3 호환 저장소(R2·MinIO 옛 판)가 다 받지는 않는다 — 필요할 때만 붙인다
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClient(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(30)).build());
        if (settings.endpoint() != null) {
            builder.endpointOverride(URI.create(settings.endpoint()));
        }
        S3ImageStorage storage = new S3ImageStorage(builder.build(), settings.bucket(), settings.publicBaseUrl());
        log.info("이미지 저장소: {} 버킷(S3 호환) bucket={} endpoint={} region={} pathStyle={} 자격증명={} 공개주소={}", settings.kind().name().toLowerCase(Locale.ROOT),
                settings.bucket(),
                settings.endpoint() == null ? "(AWS 기본)" : settings.endpoint(), settings.region(), settings.pathStyle(),
                settings.accessKey() == null ? "기본 체인(IAM 역할 등)" : "접근 키", settings.publicBaseUrl() == null ? "(없음 — 앱이 /media/** 로 내보낸다)" : settings.publicBaseUrl());
        if (prod && settings.publicBaseUrl() == null) {
            log.warn("prod 인데 irobit.storage.s3.public-base-url(IROBIT_STORAGE_S3_PUBLIC_BASE_URL)이 비었다 — 이미지를 앱 서버가 버킷에서 읽어 내보낸다. "
                    + "R2 사용자 도메인 등 공개 주소를 주면 브라우저가 CDN 에서 바로 가져간다");
        }
        storage.probe();
        return storage;
    }

    private static AwsCredentialsProvider credentials(S3Settings settings) {
        if (settings.accessKey() == null) {
            return DefaultCredentialsProvider.builder().build();
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(settings.accessKey(), settings.secretKey()));
    }

    /** 정리한 버킷 설정(빈 글자 → null, r2 기본값 채움, 공개 주소 끝 「/」 제거) */
    record S3Settings(BucketKind kind, String endpoint, String region, String bucket, String accessKey, String secretKey, boolean pathStyle,
                      String publicBaseUrl) {

        @Override
        public String toString() {
            return "S3Settings[kind=" + kind + ", bucket=" + bucket + ", region=" + region + "]";
        }
    }

    /**
     * 설정을 검사·정리한다 — 틀린 것이 있으면 칸 이름(값은 싣지 않는다)을 모두 모아 기동을 멈춘다.
     * r2: endpoint 가 비면 계정 ID 로 만들고(계정 ID 도 없으면 오류), region 이 비면 {@code auto}, 키는 둘 다 필수. s3: region 필수, 키는 둘 다 주거나 둘 다 비운다
     */
    static S3Settings settings(BucketKind kind, StorageProperties.S3 s3, StorageProperties.R2 r2) {
        StorageProperties.S3 raw = s3 == null ? new StorageProperties.S3(null, null, null, null, null, null, null) : s3;
        String endpoint = trimmed(raw.endpoint());
        String region = trimmed(raw.region());
        String bucket = trimmed(raw.bucket());
        String accessKey = trimmed(raw.accessKey());
        String secretKey = trimmed(raw.secretKey());
        String publicBaseUrl = trimmed(raw.publicBaseUrl());
        while (publicBaseUrl != null && publicBaseUrl.endsWith("/")) {
            publicBaseUrl = trimmed(publicBaseUrl.substring(0, publicBaseUrl.length() - 1));
        }
        List<String> problems = new ArrayList<>();
        if (kind == BucketKind.R2) {
            String accountId = r2 == null ? null : trimmed(r2.accountId());
            if (endpoint == null) {
                if (accountId == null) {
                    problems.add("r2.account-id(IROBIT_STORAGE_R2_ACCOUNT_ID)가 비었다 — R2 › 개요의 계정 ID(또는 IROBIT_STORAGE_S3_ENDPOINT 를 직접 준다)");
                } else if (!R2_ACCOUNT_ID.matcher(accountId).matches()) {
                    problems.add("r2.account-id(IROBIT_STORAGE_R2_ACCOUNT_ID)는 영숫자다");
                } else {
                    endpoint = "https://" + accountId + ".r2.cloudflarestorage.com";
                }
            }
            if (region == null) {
                region = "auto";
            }
            if (accessKey == null || secretKey == null) {
                problems.add("r2 는 R2 API 토큰의 access-key(IROBIT_STORAGE_S3_ACCESS_KEY)와 secret-key(IROBIT_STORAGE_S3_SECRET_KEY)가 둘 다 필요하다");
            }
        } else {
            if (region == null) {
                problems.add("region(IROBIT_STORAGE_S3_REGION)이 비었다 — AWS 는 ap-northeast-2 등, MinIO 는 us-east-1");
            }
            if ((accessKey == null) != (secretKey == null)) {
                problems.add("access-key(IROBIT_STORAGE_S3_ACCESS_KEY)와 secret-key(IROBIT_STORAGE_S3_SECRET_KEY)는 둘 다 주거나 둘 다 비운다(비우면 IAM 역할 등 기본 자격 증명)");
            }
        }
        if (bucket == null) {
            problems.add("bucket(IROBIT_STORAGE_S3_BUCKET)이 비었다");
        }
        if (endpoint != null && !HTTP_URL.matcher(endpoint).matches()) {
            problems.add("endpoint(IROBIT_STORAGE_S3_ENDPOINT)는 http(s)://주소 형식이다");
        }
        if (publicBaseUrl != null && !HTTP_URL.matcher(publicBaseUrl).matches()) {
            problems.add("public-base-url(IROBIT_STORAGE_S3_PUBLIC_BASE_URL)은 http(s)://주소 형식이다");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("이미지 저장소 " + kind.name().toLowerCase(Locale.ROOT) + " 설정(irobit.storage.*)이 올바르지 않다: "
                    + String.join(" / ", problems));
        }
        return new S3Settings(kind, endpoint, region, bucket, accessKey, secretKey, Boolean.TRUE.equals(raw.pathStyle()), publicBaseUrl);
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
