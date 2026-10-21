package com.community.site.service;

import com.community.media.service.ImageStorage;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 사이트 이미지(로고·아이콘·링크 미리보기 이미지)를 저장소에 쓰고 지우는 보조 — 버전 토큰 발급과 "여러 장을 쓰다 실패하면 쓴 것을 지운다", "DB 가 바뀐 뒤 예전 파일을 지운다(못 지워도 던지지 않는다)".
 *
 * <p>저장 순서(community 와 같다): 검사·처리 → <b>저장소 쓰기</b> → DB 교체 트랜잭션 → <b>커밋 뒤 예전 파일 삭제</b>. DB 에 반영하지 못하면 방금 쓴 새 파일을 지운다(주인 없는 파일이 남지 않게).
 * 버전은 올릴 때마다 새로 만드는 무작위 값이라 같은 그림을 다시 올려도 주소가 새로워진다(예전 파일 지우기가 새 파일을 지우지 않는다).
 */
@Component
public class SiteImageFiles {

    private static final Logger log = LoggerFactory.getLogger(SiteImageFiles.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ImageStorage storage;

    public SiteImageFiles(ImageStorage storage) {
        this.storage = storage;
    }

    /** 새 버전 토큰(16자 16진수) */
    public String newVersion() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** 파일들을 모두 쓴다. 하나라도 실패하면 쓴 것을 지우고 던진다 */
    public void writeAll(Map<String, byte[]> files) {
        try {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                storage.put(file.getKey(), file.getValue());
            }
        } catch (RuntimeException e) {
            deleteQuietly(files.keySet());
            throw e;
        }
    }

    /** 파일들을 지운다 — 못 지워도 던지지 않는다(DB 는 이미 바뀌었다). 키가 null 이면 건너뛴다 */
    public void deleteQuietly(Collection<String> keys) {
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            try {
                storage.delete(key);
            } catch (RuntimeException e) {
                log.warn("사이트 이미지 파일을 지우지 못했다(DB 는 이미 바뀌었다) key={}", key, e);
            }
        }
    }
}
