package com.community.media.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.media.service.ImageStorage;
import com.community.media.service.LocalDiskImageStorage;
import com.community.media.service.S3ImageStorage;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * 저장소 고르기({@link StorageConfig}) — 종류 local | s3, s3 설정이 틀리면 칸 이름을 모아 기동을 멈추고(값·비밀 키는 싣지 않는다), 빈 글자는 「값 없음」으로 정리한다.
 * 버킷에 닿지 못하는 것은 기동을 막지 않는다(닿지 않는 주소로 확인).
 */
class StorageConfigTest {

    private static final String SECRET = "very-secret-value";

    @TempDir
    Path temp;

    private static StorageProperties.S3 s3(String endpoint, String region, String bucket, String accessKey, String secretKey, Boolean pathStyle,
                                           String publicBaseUrl) {
        return new StorageProperties.S3(endpoint, region, bucket, accessKey, secretKey, pathStyle, publicBaseUrl);
    }

    /** 기본값: local·dev = local(공통 파일), prod = s3(prod 파일) — 둘 다 ROBOYAM_STORAGE_TYPE 이 먼저다 */
    @Test
    void defaultTypeIsLocalExceptProd() {
        assertThat(yaml("roboyam-shared.yml").getProperty("roboyam.storage.type")).isEqualTo("${ROBOYAM_STORAGE_TYPE:local}");
        assertThat(yaml("roboyam-shared-prod.yml").getProperty("roboyam.storage.type")).isEqualTo("${ROBOYAM_STORAGE_TYPE:s3}");
        assertThat(yaml("roboyam-shared-local.yml").getProperty("roboyam.storage.type")).isNull();
    }

    private static Properties yaml(String path) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(path));
        return factory.getObject();
    }

    @Test
    void localTypeMakesLocalDiskStorageAndFolder() {
        ImageStorage storage = new StorageConfig().imageStorage(new StorageProperties(" LOCAL ", temp.resolve("media").toString(), null), new MockEnvironment());

        assertThat(storage).isInstanceOf(LocalDiskImageStorage.class);
        assertThat(temp.resolve("media")).isDirectory();
        assertThat(storage.publicUrl("post-image/202611/a.png")).isEqualTo("/media/post-image/202611/a.png");
    }

    @Test
    void unknownOrBlankTypeStopsStartup() {
        assertThatThrownBy(() -> new StorageConfig().imageStorage(new StorageProperties("ftp", temp.toString(), null), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ROBOYAM_STORAGE_TYPE");
        assertThatThrownBy(() -> new StorageConfig().imageStorage(new StorageProperties("", temp.toString(), null), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("local 또는 s3");
    }

    @Test
    void missingBucketAndRegionAreReportedTogether() {
        assertThatThrownBy(() -> StorageConfig.settings(s3("", " ", null, null, null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ROBOYAM_STORAGE_S3_BUCKET")
                .hasMessageContaining("ROBOYAM_STORAGE_S3_REGION");
        assertThatThrownBy(() -> StorageConfig.settings(null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("ROBOYAM_STORAGE_S3_BUCKET");
    }

    @Test
    void onlyOneOfTheKeyPairIsRejectedWithoutLeakingTheSecret() {
        assertThatThrownBy(() -> StorageConfig.settings(s3(null, "auto", "media", null, SECRET, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ROBOYAM_STORAGE_S3_ACCESS_KEY")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(SECRET));
        assertThatThrownBy(() -> StorageConfig.settings(s3(null, "auto", "media", "AKID", " ", null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ROBOYAM_STORAGE_S3_SECRET_KEY");
    }

    @Test
    void endpointAndPublicUrlMustBeHttpUrls() {
        assertThatThrownBy(() -> StorageConfig.settings(s3("r2.example.com", "auto", "media", null, null, null, "media.example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ROBOYAM_STORAGE_S3_ENDPOINT")
                .hasMessageContaining("ROBOYAM_STORAGE_S3_PUBLIC_BASE_URL");
    }

    @Test
    void settingsAreNormalized() {
        StorageConfig.S3Settings settings = StorageConfig.settings(s3(" ", " auto ", " media ", "", "", null, " https://media.example.com// "));

        assertThat(settings.endpoint()).isNull();
        assertThat(settings.region()).isEqualTo("auto");
        assertThat(settings.bucket()).isEqualTo("media");
        assertThat(settings.accessKey()).isNull();
        assertThat(settings.secretKey()).isNull();
        assertThat(settings.pathStyle()).isFalse();
        assertThat(settings.publicBaseUrl()).isEqualTo("https://media.example.com");
        assertThat(StorageConfig.settings(s3("http://localhost:9000", "us-east-1", "m", "a", "b", true, null)).pathStyle()).isTrue();
    }

    @Test
    void secretsAreNotPrinted() {
        assertThat(s3(null, "auto", "media", "AKID-VALUE", SECRET, null, null).toString()).doesNotContain(SECRET).doesNotContain("AKID-VALUE");
        assertThat(StorageConfig.settings(s3(null, "auto", "media", "AKID-VALUE", SECRET, null, null)).toString()).doesNotContain(SECRET);
    }

    @Test
    void s3TypeStartsEvenWhenTheBucketIsUnreachable() {
        StorageProperties properties = new StorageProperties("s3", temp.toString(),
                s3("http://127.0.0.1:9", "us-east-1", "media", "minio", "minio-secret", true, "https://media.example.com/"));
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        ImageStorage storage = new StorageConfig().imageStorage(properties, prod);

        assertThat(storage).isInstanceOf(S3ImageStorage.class);
        assertThat(((S3ImageStorage) storage).linksDirectly()).isTrue();
        assertThat(storage.publicUrl("site-icon/abc/icon-512.png")).isEqualTo("https://media.example.com/site-icon/abc/icon-512.png");
        ((S3ImageStorage) storage).close();
    }
}
