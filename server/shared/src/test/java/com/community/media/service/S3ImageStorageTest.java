package com.community.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.media.exception.StorageUnavailableException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3 호환 저장소({@link S3ImageStorage}) — 로컬 디스크와 같은 키로 쓰고(Content-Type·1년 immutable 캐시), 지우기는 멱등, 주소는 공개 주소 또는 {@code /media/…},
 * 읽기는 없으면 빈 값·닿지 못하면 503, 경로 탈출 키는 버킷을 부르지 않고 거절한다. 버킷은 메모리 가짜({@link FakeS3Client}).
 */
class S3ImageStorageTest {

    private final FakeS3Client client = new FakeS3Client();

    @Test
    void putWritesTheSameKeyWithContentTypeAndImmutableCache() {
        new S3ImageStorage(client, "media", null).put("post-image/202611/abc.webp", new byte[] {1, 2, 3});

        PutObjectRequest request = client.puts.getFirst();
        assertThat(request.bucket()).isEqualTo("media");
        assertThat(request.key()).isEqualTo("post-image/202611/abc.webp");
        assertThat(request.contentType()).isEqualTo("image/webp");
        assertThat(request.cacheControl()).isEqualTo("public, max-age=31536000, immutable");
        assertThat(client.objects.get("post-image/202611/abc.webp")).containsExactly(1, 2, 3);
    }

    @Test
    void putFailureIsStorageUnavailable() {
        client.failWith = SdkClientException.create("down");

        assertThatThrownBy(() -> new S3ImageStorage(client, "media", null).put("a/b.png", new byte[] {1})).isInstanceOf(StorageUnavailableException.class);
    }

    @Test
    void deleteIsIdempotentAndFailureThrows() {
        S3ImageStorage storage = new S3ImageStorage(client, "media", null);
        storage.put("site-icon/abc/icon-512.png", new byte[] {1});

        storage.delete("site-icon/abc/icon-512.png");
        storage.delete("site-icon/abc/icon-512.png"); // 이미 없다 — 오류가 아니다

        assertThat(client.objects).isEmpty();
        assertThat(client.deletes).hasSize(2).allSatisfy(d -> assertThat(d.bucket()).isEqualTo("media"));

        client.failWith = SdkClientException.create("down");
        assertThatThrownBy(() -> storage.delete("site-icon/abc/icon-512.png")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void publicUrlUsesThePublicBaseOrTheAppMediaPath() {
        assertThat(new S3ImageStorage(client, "media", "https://media.example.com").publicUrl("group/202611/x.jpg"))
                .isEqualTo("https://media.example.com/group/202611/x.jpg");
        assertThat(new S3ImageStorage(client, "media", null).publicUrl("group/202611/x.jpg")).isEqualTo("/media/group/202611/x.jpg");
    }

    @Test
    void readReturnsBytesOrEmptyWhenMissing() {
        S3ImageStorage storage = new S3ImageStorage(client, "media", null);
        client.objects.put("post-image/202611/abc.png", new byte[] {9, 8});

        S3ImageStorage.StoredObject object = storage.read("post-image/202611/abc.png").orElseThrow();
        assertThat(object.data()).containsExactly(9, 8);
        assertThat(object.lastModified()).isEqualTo(client.lastModified);
        assertThat(storage.read("post-image/202611/none.png")).isEmpty();
    }

    @Test
    void readFailureIsStorageUnavailable() {
        S3ImageStorage storage = new S3ImageStorage(client, "media", null);
        client.failWith = S3Exception.builder().statusCode(500).message("boom").build();
        assertThatThrownBy(() -> storage.read("a/b.png")).isInstanceOf(StorageUnavailableException.class);

        client.failWith = S3Exception.builder().statusCode(404).message("gone").build();
        assertThat(storage.read("a/b.png")).isEmpty();

        client.failWith = SdkClientException.create("down");
        assertThatThrownBy(() -> storage.read("a/b.png")).isInstanceOf(StorageUnavailableException.class);
        assertThat(storage.probe()).isFalse();
    }

    @Test
    void escapingKeysAreRejected() {
        S3ImageStorage storage = new S3ImageStorage(client, "media", null);

        for (String bad : new String[] {"", "/abs.png", "a/../b.png", "../b.png", "a\\b.png", "a//b.png", "a/./b.png", "a/"}) {
            assertThatThrownBy(() -> storage.put(bad, new byte[] {1})).as(bad).isInstanceOf(IllegalArgumentException.class);
            assertThat(storage.read(bad)).as(bad).isEmpty();
        }
        assertThat(client.puts).isEmpty();
        assertThat(client.gets).isEmpty();
    }

    @Test
    void contentTypeFollowsTheExtension() {
        assertThat(S3ImageStorage.contentType("a/b.PNG")).isEqualTo("image/png");
        assertThat(S3ImageStorage.contentType("a/b.jpg")).isEqualTo("image/jpeg");
        assertThat(S3ImageStorage.contentType("a/b.gif")).isEqualTo("image/gif");
        assertThat(S3ImageStorage.contentType("a/b.svg")).isEqualTo("image/svg+xml");
        assertThat(S3ImageStorage.contentType("a/b")).isEqualTo("application/octet-stream");
    }
}
