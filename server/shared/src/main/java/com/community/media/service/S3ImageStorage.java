package com.community.media.service;

import com.community.media.exception.StorageUnavailableException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3 호환 버킷 저장소 — S3 API 하나로 Cloudflare R2·AWS S3·MinIO 를 쓴다(어느 것인지는 설정의 endpoint·region·path-style, {@code StorageConfig}).
 * 키는 로컬 디스크와 같은 문자열을 객체 키로 그대로 쓴다 — 로컬 폴더를 그대로 버킷에 올리면 장부({@code files.storage_key} 등)를 고치지 않고 옮겨진다.
 *
 * <ul>
 *   <li>쓰기: 키의 확장자로 Content-Type 을, 1년 immutable Cache-Control 을 객체에 붙인다(키에 버전 토큰이 있어 내용이 바뀌면 키가 바뀐다 — {@link ImageStorage}).
 *       공개 도메인(R2 사용자 도메인 = Cloudflare CDN)이 이 머리글로 캐시한다.</li>
 *   <li>주소: 공개 주소({@code publicBaseUrl})가 있으면 {@code <공개 주소>/<키>}, 없으면 로컬과 같은 {@code /media/<키>} 이고 앱이 {@link #read} 로 대신 내보낸다.
 *       어느 쪽이든 예전에 본문에 저장된 {@code /media/…} 주소는 앱이 버킷에서 읽어 준다({@code MediaResourceConfig}).</li>
 * </ul>
 */
public class S3ImageStorage implements ImageStorage, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(S3ImageStorage.class);

    /** 객체에 붙이는 캐시 머리글 — {@code /media/**} 서빙의 캐시와 같다 */
    public static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    /** 우리가 쓰는 형식은 직접 정한다(메일·서블릿 MIME 표에 webp 가 없을 수 있다). 나머지는 Spring 의 MIME 표, 그래도 모르면 octet-stream */
    private static final Map<String, String> CONTENT_TYPES = Map.of("png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg", "gif", "image/gif",
            "webp", "image/webp", "svg", "image/svg+xml");

    /** 버킷에서 읽은 객체 한 개(앱이 {@code /media/**} 로 대신 내보낼 때 — Content-Type 은 내보내는 처리기가 파일 이름으로 정한다) */
    public record StoredObject(byte[] data, Instant lastModified) {
    }

    private final S3Client client;
    private final String bucket;
    private final String publicBaseUrl;

    /**
     * @param publicBaseUrl 끝 「/」 없는 공개 주소, 없으면 null({@code /media/…} 를 앱이 내보낸다)
     */
    public S3ImageStorage(S3Client client, String bucket, String publicBaseUrl) {
        this.client = client;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
    }

    public String bucket() {
        return bucket;
    }

    /** 공개 주소로 바로 링크하는지(아니면 앱이 {@code /media/**} 로 대신 내보낸다) */
    public boolean linksDirectly() {
        return publicBaseUrl != null;
    }

    @Override
    public void put(String key, byte[] data) {
        String checked = checkKey(key);
        try {
            client.putObject(b -> b.bucket(bucket).key(checked).contentType(contentType(checked)).cacheControl(CACHE_CONTROL), RequestBody.fromBytes(data));
        } catch (SdkException e) {
            log.error("S3 저장소에 쓰지 못했다 bucket={} key={}", bucket, checked, e);
            throw new StorageUnavailableException();
        }
    }

    @Override
    public void delete(String key) {
        String checked = checkKey(key);
        try {
            client.deleteObject(b -> b.bucket(bucket).key(checked)); // 없는 키를 지워도 성공이다(S3 의 DeleteObject 는 멱등)
        } catch (SdkException e) {
            throw new IllegalStateException("S3 저장소에서 지우지 못했다: " + checked, e);
        }
    }

    @Override
    public String publicUrl(String key) {
        return publicBaseUrl == null ? LocalDiskImageStorage.URL_PREFIX + key : publicBaseUrl + "/" + key;
    }

    /**
     * 객체를 통째로 읽는다 — 없으면 빈 값. 올린 이미지는 처리 관문이 크기를 제한해 메모리에 담을 만하다.
     * 버킷에 닿지 못하면 {@code StorageUnavailableException}(503)
     */
    public Optional<StoredObject> read(String key) {
        String checked;
        try {
            checked = checkKey(key);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        try {
            ResponseBytes<GetObjectResponse> bytes = client.getObjectAsBytes(b -> b.bucket(bucket).key(checked));
            return Optional.of(new StoredObject(bytes.asByteArray(), bytes.response().lastModified()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            log.warn("S3 저장소에서 읽지 못했다 bucket={} key={} status={}", bucket, checked, e.statusCode(), e);
            throw new StorageUnavailableException();
        } catch (SdkException e) {
            log.warn("S3 저장소에서 읽지 못했다 bucket={} key={}", bucket, checked, e);
            throw new StorageUnavailableException();
        }
    }

    /** 버킷에 닿는지 본다(기동 때 한 번 — 실패해도 기동은 막지 않는다). 성공하면 true */
    public boolean probe() {
        try {
            client.headBucket(b -> b.bucket(bucket));
            return true;
        } catch (SdkException e) {
            log.warn("S3 저장소 버킷에 닿지 못했다(올리기·읽기는 503 STORAGE_UNAVAILABLE 로 실패한다) bucket={} 원인={}", bucket, e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        client.close();
    }

    /** 키의 확장자로 정한 Content-Type */
    static String contentType(String key) {
        int dot = key.lastIndexOf('.');
        String extension = dot < 0 ? "" : key.substring(dot + 1).toLowerCase(Locale.ROOT);
        String known = CONTENT_TYPES.get(extension);
        if (known != null) {
            return known;
        }
        return MediaTypeFactory.getMediaType(key).map(MediaType::toString).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    /** 키는 서버가 만든 상대 경로다 — 비었거나 「/」로 시작하거나 「..」·「\」·빈 칸 마디가 있으면 거절(로컬 디스크의 경로 탈출 검사와 같은 뜻) */
    static String checkKey(String key) {
        if (key == null || key.isBlank() || key.startsWith("/") || key.endsWith("/") || key.contains("\\") || key.contains("//")) {
            throw new IllegalArgumentException("저장소 키가 올바르지 않다: " + key);
        }
        for (String part : key.split("/")) {
            if (part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("저장소 키가 올바르지 않다: " + key);
            }
        }
        return key;
    }
}
