package com.community.media.service;

import com.community.media.exception.StorageUnavailableException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 로컬 디스크 저장소. 폴더 하나에 키 경로 그대로 파일을 둔다. 서버가 {@code /media/**} 로 이 폴더를 내보낸다({@code MediaResourceConfig} — api·admin 둘 다).
 *
 * <p>쓰는 도중의 반쪽 파일이 보이지 않게 임시 파일에 쓴 뒤 옮긴다. 키가 폴더 밖을 가리키면(경로 탈출) 거절한다.
 */
public class LocalDiskImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskImageStorage.class);

    /** 공개 주소의 머리 — {@code MediaResourceConfig} 가 같은 경로를 서빙한다 */
    public static final String URL_PREFIX = "/media/";

    private final Path root;

    public LocalDiskImageStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    @Override
    public void put(String key, byte[] data) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(temp, data);
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            log.error("로컬 디스크 저장소에 쓰지 못했다 key={}", key, e);
            throw new StorageUnavailableException();
        }
    }

    @Override
    public void delete(String key) {
        Path target = resolve(key);
        try {
            Files.deleteIfExists(target);
            // 버전 폴더가 비면 함께 지운다(비어 있지 않으면 실패하고 넘어간다)
            Path folder = target.getParent();
            if (folder != null && !folder.equals(root) && folder.startsWith(root)) {
                try {
                    Files.deleteIfExists(folder);
                } catch (IOException ignored) {
                    // 다른 파일이 남아 있다 — 그대로 둔다
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("로컬 디스크 저장소에서 지우지 못했다: " + key, e);
        }
    }

    @Override
    public String publicUrl(String key) {
        return URL_PREFIX + key;
    }

    /** 키를 폴더 안의 경로로 바꾼다. 폴더 밖이면 거절 */
    Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("저장소 키가 올바르지 않다: " + key);
        }
        return path;
    }
}
