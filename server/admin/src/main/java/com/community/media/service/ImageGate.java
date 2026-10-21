package com.community.media.service;

import com.community.media.config.MediaProperties;
import com.community.media.exception.ImageBusyException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 이미지 처리의 <b>동시 처리 수 제한</b>(세마포어). 디코딩한 그림은 메모리를 많이 쓰므로 한꺼번에 몇 장만 처리하고 나머지는 잠깐 기다리게 한다.
 * 정해진 시간 안에 자리가 나지 않으면 {@link ImageBusyException}(503 {@code IMAGE_BUSY}). 요청 스레드가 DB 연결을 쥐고 기다리지 않도록 트랜잭션 밖에서 부른다.
 */
@Component
public class ImageGate {

    private static final Logger log = LoggerFactory.getLogger(ImageGate.class);

    private final Semaphore permits;
    private final long timeoutSeconds;

    public ImageGate(MediaProperties properties) {
        this.permits = new Semaphore(Math.max(1, properties.maxConcurrentProcessing()), true);
        this.timeoutSeconds = Math.max(1, properties.acquireTimeoutSeconds());
    }

    /** 자리를 얻어 일을 하고 돌려준다 */
    public <T> T run(Supplier<T> work) {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageBusyException();
        }
        if (!acquired) {
            log.warn("이미지 처리 자리가 {}초 안에 나지 않았다", timeoutSeconds);
            throw new ImageBusyException();
        }
        try {
            return work.get();
        } finally {
            permits.release();
        }
    }
}
