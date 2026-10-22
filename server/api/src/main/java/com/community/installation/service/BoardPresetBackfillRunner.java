package com.community.installation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 게시판 기능 전에 설치된 곳 — 공개 서버가 뜰 때 설치 때 고른 게시판 구성을 한 번 적용한다({@link BoardPresetApplier#applyOnceIfMissing}: 게시판이 하나도 없을 때만 만들고,
 * 어느 쪽이든 적용 표시를 채워 다시 하지 않는다). 실패해도 서버는 뜬다(다음 기동이 다시 시도한다) — 원인은 WARN 으로 남긴다.
 */
@Component
public class BoardPresetBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BoardPresetBackfillRunner.class);

    private final BoardPresetApplier applier;

    public BoardPresetBackfillRunner(BoardPresetApplier applier) {
        this.applier = applier;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            applier.applyOnceIfMissing();
        } catch (RuntimeException e) {
            log.warn("설치 때 고른 게시판 구성을 적용하지 못했다(다음 기동에서 다시 시도한다) 원인={}", e.toString());
        }
    }
}
