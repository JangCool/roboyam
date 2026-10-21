package com.community.media.config;

import com.community.media.service.ImageStorage;
import com.community.media.service.LocalDiskImageStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 저장소 빈 — 설정({@link StorageProperties})의 종류에 맞는 구현을 만든다. 로컬 폴더가 없으면 만든다(못 만들어도 기동은 막지 않고, 올리는 순간 503 으로 알린다) */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    public ImageStorage imageStorage(StorageProperties properties) {
        if (!"local".equalsIgnoreCase(properties.type())) {
            throw new IllegalStateException("지원하지 않는 저장소 종류다: " + properties.type() + " (지금은 local 만 있다)");
        }
        Path root = Paths.get(properties.localPath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            log.warn("이미지 저장 폴더를 만들지 못했다(올리기는 503 STORAGE_UNAVAILABLE 로 실패한다) path={}", root, e);
        }
        log.info("이미지 저장소: 로컬 디스크 {}", root);
        return new LocalDiskImageStorage(root);
    }
}
