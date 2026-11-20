package com.community.jjal.service;

import com.community.jjal.entity.JjalFileEntity;
import com.community.jjal.repository.master.JjalFileRepository;
import com.community.media.service.ImageStorage;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 짤 파일의 저장 위치를 DB 상태에 맞춘다(사용자 결정 2026-11-20 — 내린 짤은 공개 주소에서 뺀다). DB 상태(내려감·되돌림·불법촬영물 확정)는 업무 트랜잭션이 바꾸고, 저장소 작업은
 * <b>그 트랜잭션이 커밋된 뒤</b>({@link #afterCommit}) 여기서 한다. 놓친 것(서버가 멈춤·저장소 오류)과 시각이 지나 바뀌는 것(권리침해 임시조치 끝)은 배치 {@code jjal-storage-sync} 가 다시 맞춘다.
 *
 * <ul>
 *   <li><b>내려간 짤</b>(공개 위치에 있음) → 비공개 접두사 {@link JjalFileEntity#PRIVATE_PREFIX} 의 새 무작위 키로 옮긴다 — {@code /media/**} 가 이 접두사를 내보내지 않는다.</li>
 *   <li><b>다시 보이는 짤</b>(비공개 위치에 있음 — 되돌림·신고 무시·임시조치 끝) → 공개 접두사의 <b>새</b> 무작위 키로(옛 주소는 계속 죽어 있다). 임시조치가 끝난 행은 이때 ACTIVE 로.</li>
 *   <li><b>불법촬영물로 확정한 짤</b> → 저장소에서 지우고 키를 비운다(해시 행은 남는다).</li>
 * </ul>
 * 옮기는 차례(글 이미지 저장과 같은 원칙 — 실패해도 DB 가 실제 있는 파일을 가리킨다): 원래 파일 읽기 → 새 키에 쓰기 → 짧은 트랜잭션에서 행을 잠가 상태가 그대로면 키 교체
 * → 커밋 뒤 옛 파일 삭제(그사이 상태가 바뀌었으면 새 파일을 지운다). 지우기는 저장소 삭제 → 키 비우기 차례(삭제는 멱등이라 다시 해도 된다). 실패는 경고 로그만 남기고 배치가 다시 한다.
 *
 * <p>CDN(Cloudflare) 캐시: 공개 주소가 앞단에 캐시돼 있으면 옛 주소가 캐시 기간 동안 남을 수 있다 — 캐시 지우기는 {@link #onPublicKeyRetired} 한 곳에 붙이면 된다(지금은 로그만).
 */
@Component
public class JjalStorageSync {

    private static final Logger log = LoggerFactory.getLogger(JjalStorageSync.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneOffset.UTC);

    private final JjalFileRepository files;
    private final ImageStorage storage;
    private final TransactionTemplate tx;

    public JjalStorageSync(JjalFileRepository files, ImageStorage storage, PlatformTransactionManager txManager) {
        this.files = files;
        this.storage = storage;
        this.tx = new TransactionTemplate(txManager);
        // 커밋 뒤 콜백 안에서도 따로 트랜잭션을 연다(끝난 트랜잭션에 참여하지 않게)
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 새 저장소 키 — 공개면 {@code jjal/{UTC 년월}/{무작위 32자}.{확장자}}, 비공개면 {@code private/jjal/…} */
    public static String newKey(boolean privateLocation, String extension) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return (privateLocation ? JjalFileEntity.PRIVATE_PREFIX : JjalFileEntity.PUBLIC_PREFIX) + MONTH.format(Instant.now()) + "/" + HexFormat.of().formatHex(bytes)
                + "." + extension;
    }

    /** 지금 트랜잭션이 커밋된 뒤 이 파일의 저장 위치를 맞춘다(트랜잭션 밖에서 부르면 바로). 실패해도 부르는 쪽 업무는 그대로다(배치가 다시 맞춘다) */
    public void afterCommit(long fileId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncQuietly(fileId);
                }
            });
        } else {
            syncQuietly(fileId);
        }
    }

    /** 맞추기 — 실패는 경고 로그. 바꿨으면 true */
    public boolean syncQuietly(long fileId) {
        try {
            return sync(fileId);
        } catch (RuntimeException e) {
            log.warn("짤 저장 위치를 맞추지 못했다(배치가 다시 한다) fileId={}", fileId, e);
            return false;
        }
    }

    /** 한 파일의 저장 위치를 DB 상태에 맞춘다. 바꾼 것이 있으면 true */
    public boolean sync(long fileId) {
        JjalFileEntity f = tx.execute(status -> files.findById(fileId).orElse(null));
        if (f == null) {
            return false;
        }
        Instant now = Instant.now();
        String key = f.getStorageKey();
        if (f.isPurged()) {
            if (key == null) {
                return false;
            }
            storage.delete(key); // 멱등 — 키 비우기가 실패하면 다음 번에 다시 지우고 비운다
            tx.executeWithoutResult(status -> files.findWithLockById(fileId).filter(l -> key.equals(l.getStorageKey())).ifPresent(l -> l.clearStorageKey(Instant.now())));
            onPublicKeyRetired(key);
            log.info("불법촬영물로 확정한 짤 파일을 지웠다 fileId={}", fileId);
            return true;
        }
        if (key == null) {
            return false;
        }
        boolean wantPrivate = f.isBlockedAt(now);
        if (wantPrivate == JjalFileEntity.privateKey(key)) {
            if (f.blockExpiredAt(now)) {
                tx.executeWithoutResult(status -> files.findWithLockById(fileId).ifPresent(l -> l.expireBlock(Instant.now()))); // 이미 공개 위치 — 상태만 정리
                return true;
            }
            return false;
        }
        byte[] bytes = storage.readBytes(key).orElse(null);
        if (bytes == null) {
            log.warn("옮길 짤 파일이 저장소에 없다 fileId={} private={}", fileId, JjalFileEntity.privateKey(key));
            return false;
        }
        String target = newKey(wantPrivate, extensionOf(key));
        storage.put(target, bytes);
        Boolean moved;
        try {
            moved = tx.execute(status -> {
                Instant at = Instant.now();
                JjalFileEntity locked = files.findWithLockById(fileId).orElse(null);
                if (locked == null || locked.isPurged() || !key.equals(locked.getStorageKey()) || locked.isBlockedAt(at) != wantPrivate) {
                    return false; // 그사이 상태·위치가 바뀌었다 — 새 파일을 버린다(다음 맞추기가 다시 본다)
                }
                locked.relocate(target, at);
                if (!wantPrivate) {
                    locked.expireBlock(at); // 임시조치가 끝나 다시 보이는 짤은 ACTIVE 로
                }
                return true;
            });
        } catch (RuntimeException e) {
            deleteQuietly(target);
            throw e;
        }
        if (Boolean.TRUE.equals(moved)) {
            deleteQuietly(key);
            if (wantPrivate) {
                onPublicKeyRetired(key);
            }
            log.info("짤 저장 위치를 옮겼다 fileId={} toPrivate={}", fileId, wantPrivate);
            return true;
        }
        deleteQuietly(target);
        return false;
    }

    /**
     * 공개 주소가 죽었다(내려서 옮김 · 불법촬영물로 지움). 앞단 CDN 캐시 지우기를 붙일 자리다 — 지금은 범위 밖이라 아무것도 하지 않는다(주소는 저장소 키 → {@code ImageStorage#publicUrl}).
     */
    protected void onPublicKeyRetired(String key) {
        log.debug("짤 공개 주소가 죽었다(CDN 캐시 지우기 자리) key={}", key);
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("짤 저장소 파일을 지우지 못했다 key={}", key, e);
        }
    }

    private static String extensionOf(String key) {
        int dot = key.lastIndexOf('.');
        int slash = key.lastIndexOf('/');
        return dot > slash ? key.substring(dot + 1) : "bin";
    }
}
