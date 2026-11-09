package com.community.media.config;

import com.community.media.service.S3ImageStorage;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

/**
 * S3 저장소일 때 {@code /media/**} 를 버킷에서 읽어 내보낸다 — 공개 주소가 없을 때의 새 이미지와, 예전에 본문에 저장된 {@code /media/…} 주소를 위해서다.
 * Spring 의 정적 리소스 처리기(경로 검사·Range·Last-Modified·Cache-Control)를 그대로 쓰고 「파일 찾기」만 버킷으로 바꾼다. 없으면 null → 404.
 */
final class BucketMediaResolver implements ResourceResolver {

    private final S3ImageStorage storage;

    BucketMediaResolver(S3ImageStorage storage) {
        this.storage = storage;
    }

    @Override
    public Resource resolveResource(HttpServletRequest request, String requestPath, List<? extends Resource> locations, ResourceResolverChain chain) {
        return storage.read(requestPath).map(object -> (Resource) new BucketResource(requestPath, object)).orElse(null);
    }

    @Override
    public String resolveUrlPath(String resourcePath, List<? extends Resource> locations, ResourceResolverChain chain) {
        return resourcePath;
    }

    /** 버킷에서 읽어 둔 바이트 — 처리기가 길이·시각·파일 이름(Content-Type 판단)을 묻는다 */
    static final class BucketResource extends AbstractResource {

        private final String key;
        private final S3ImageStorage.StoredObject object;

        BucketResource(String key, S3ImageStorage.StoredObject object) {
            this.key = key;
            this.object = object;
        }

        @Override
        public String getDescription() {
            return "S3 객체 [" + key + "]";
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(object.data());
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public long contentLength() {
            return object.data().length;
        }

        @Override
        public long lastModified() {
            return object.lastModified() == null ? 0L : object.lastModified().toEpochMilli();
        }

        @Override
        public String getFilename() {
            return key.substring(key.lastIndexOf('/') + 1);
        }
    }
}
