package com.community.jjal.entity;

import com.community.report.entity.ReportReasons;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 짤 파일 — V261 {@code jjal_files}. 한 줄 = 원본 바이트 SHA-256 하나(유일) — 같은 해시를 다시 올리거나 담으면 이 줄을 가리킨다(my-jjal-contract 「저장 합치기」).
 * 저장소 키·공개 ID 는 무작위다(해시를 키·주소에 쓰지 않는다). 처음 올린 회원({@code uploadedBy})은 어디에도 내보내지 않는다.
 *
 * <p><b>내려감</b>: {@link JjalFileStatus#BLOCKED} + 까닭({@code blockedReason} — 운영자 {@code STAFF} · 신고 자동 숨김 {@code REPORTED}·{@code ILLEGAL}·{@code RIGHTS}).
 * 권리침해 임시조치는 {@code blockedUntil} 이 지나면 다시 보인다(글·댓글 자동 숨김과 같다). 내려간 짤은 모든 보관함·글·댓글에서 「내려간 짤」이고 같은 해시 다시 올리기를 거절한다.
 *
 * <p><b>저장 위치</b>(사용자 결정 2026-11-20): 보이는 짤은 공개 접두사({@code jjal/…}), 내려간 짤은 서빙하지 않는 접두사({@code private/jjal/…})의 <b>새 무작위 키</b>로 옮긴다.
 * 다시 보이면 또 새 무작위 키로 공개 접두사에 둔다(옛 주소는 살아나지 않는다). 옮기기는 커밋 뒤 {@code JjalStorageSync} 가 하고 이 행은 늘 실제로 있는 위치를 가리킨다
 * — 그래서 「지금 공개 주소로 내보낼 수 있는가」는 {@link #servableAt}(내려가지 않았고 공개 위치에 있다)로 본다.
 * 운영자가 불법촬영물로 내린 짤({@link #purgeAsIllegal})은 파일을 지우고({@code storageKey} null) 해시 행만 남긴다 — 되돌림 없음.
 * {@code toString} 을 두지 않는다.
 */
@Entity
@Table(name = "jjal_files")
public class JjalFileEntity {

    @Id
    private Long id;
    private String publicId;
    private String sha256;
    private String storageKey;
    private String contentType;
    private int sizeBytes;
    private int width;
    private int height;
    private boolean animated;
    private Long uploadedBy;
    @Enumerated(EnumType.STRING)
    private JjalFileStatus status;
    private String blockedReason;
    private Instant blockedUntil;
    private Instant blockedAt;
    /** 불법촬영물로 확정해 파일을 지운 시각(V261) — 되돌릴 수 없다 */
    private Instant purgedAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected JjalFileEntity() {
    }

    public static JjalFileEntity create(long id, String publicId, String sha256, String storageKey, String contentType, int sizeBytes, int width, int height,
                                        boolean animated, long uploadedBy, Instant now) {
        JjalFileEntity f = new JjalFileEntity();
        f.id = id;
        f.publicId = publicId;
        f.sha256 = sha256;
        f.storageKey = storageKey;
        f.contentType = contentType;
        f.sizeBytes = sizeBytes;
        f.width = width;
        f.height = height;
        f.animated = animated;
        f.uploadedBy = uploadedBy;
        f.status = JjalFileStatus.ACTIVE;
        f.createdAt = now;
        f.updatedAt = now;
        return f;
    }

    /** 지금 내려가 있는가(권리침해 임시조치는 기한 안일 때만) */
    public boolean isBlockedAt(Instant now) {
        return status == JjalFileStatus.BLOCKED && (blockedUntil == null || blockedUntil.isAfter(now));
    }

    /** 비공개(서빙하지 않는) 저장 위치의 키 머리 */
    public static final String PRIVATE_PREFIX = com.community.media.service.ImageStorage.PRIVATE_PREFIX + "jjal/";
    /** 공개 저장 위치의 키 머리 */
    public static final String PUBLIC_PREFIX = "jjal/";

    /** 비공개 위치의 키인가 */
    public static boolean privateKey(String key) {
        return key != null && key.startsWith(PRIVATE_PREFIX);
    }

    /** 지금 공개 주소로 내보낼 수 있는가 — 내려가지 않았고 파일이 공개 위치에 있다(옮기는 중이면 내려간 짤처럼 보인다) */
    public boolean servableAt(Instant now) {
        return !isBlockedAt(now) && storageKey != null && !privateKey(storageKey);
    }

    /** 불법촬영물로 확정해 파일을 지웠는가(또는 지울 차례인가) */
    public boolean isPurged() {
        return purgedAt != null;
    }

    /** 권리침해 임시조치 기한이 지나 다시 보일 차례인가(상태는 아직 BLOCKED) */
    public boolean blockExpiredAt(Instant now) {
        return status == JjalFileStatus.BLOCKED && blockedUntil != null && !blockedUntil.isAfter(now);
    }

    /** 기한이 지난 임시조치를 끝낸다(BLOCKED → ACTIVE). 끝냈으면 true */
    public boolean expireBlock(Instant now) {
        if (!blockExpiredAt(now)) {
            return false;
        }
        clear(now);
        return true;
    }

    /** 저장 위치를 옮겼다(공개 ↔ 비공개, 새 무작위 키) */
    public void relocate(String newKey, Instant now) {
        this.storageKey = newKey;
        this.updatedAt = now;
    }

    /** 지운 파일의 키를 비운다(불법촬영물 확정 — 저장소에서 지운 뒤) */
    public void clearStorageKey(Instant now) {
        if (purgedAt == null) {
            throw new IllegalStateException("불법촬영물로 확정한 짤만 파일을 지운다");
        }
        this.storageKey = null;
        this.updatedAt = now;
    }

    /**
     * 운영자가 불법촬영물로 확정해 내린다 — 까닭 ILLEGAL·기한 없음·지울 시각. 파일은 커밋 뒤 저장소에서 지우고 해시 행만 남는다(같은 해시 다시 올리기 거절). 이미 지웠으면 false
     */
    public boolean purgeAsIllegal(Instant now) {
        if (purgedAt != null) {
            return false;
        }
        set(ReportReasons.HIDDEN_ILLEGAL, null, now);
        this.purgedAt = now;
        return true;
    }

    /** 지금 내려간 까닭(내려가 있지 않으면 null) */
    public String blockedReasonAt(Instant now) {
        return isBlockedAt(now) ? blockedReason : null;
    }

    /**
     * 신고 자동 숨김(REPORTED · ILLEGAL · RIGHTS) — 이미 내려가 있으면 그대로 두고 false.
     *
     * @param until 권리침해 임시조치의 끝(그 밖 null)
     */
    public boolean blockAutomatically(String reason, Instant until, Instant now) {
        if (isBlockedAt(now)) {
            return false;
        }
        set(reason, until, now);
        return true;
    }

    /** 운영자 짤 내림(STAFF) — 보이던 짤이면 내리고, 자동 숨김 중이면 운영자 내림으로 확정한다(임시조치 기한을 지운다). 이미 운영자가 내렸으면 false */
    public boolean blockByStaff(Instant now) {
        if (purgedAt != null || (isBlockedAt(now) && ReportReasons.HIDDEN_STAFF.equals(blockedReason))) {
            return false;
        }
        set(ReportReasons.HIDDEN_STAFF, null, now);
        return true;
    }

    /** 자동 숨김이면 다시 보이게(신고 무시) — 운영자 내림·보이는 짤은 그대로. 풀었으면 true */
    public boolean unblockAutomatic(Instant now) {
        if (purgedAt != null || !isBlockedAt(now) || !ReportReasons.isAutoHidden(blockedReason)) {
            return false;
        }
        clear(now);
        return true;
    }

    /** 되돌림 — 내려가 있으면(까닭 무관, 불법촬영물로 지운 짤은 빼고) 다시 보이게. 풀었으면 true */
    public boolean unblock(Instant now) {
        if (purgedAt != null || !isBlockedAt(now)) {
            return false;
        }
        clear(now);
        return true;
    }

    private void set(String reason, Instant until, Instant now) {
        this.status = JjalFileStatus.BLOCKED;
        this.blockedReason = reason;
        this.blockedUntil = until;
        this.blockedAt = now;
        this.updatedAt = now;
    }

    private void clear(Instant now) {
        this.status = JjalFileStatus.ACTIVE;
        this.blockedReason = null;
        this.blockedUntil = null;
        this.blockedAt = null;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getSha256() {
        return sha256;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isAnimated() {
        return animated;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public JjalFileStatus getStatus() {
        return status;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public Instant getBlockedUntil() {
        return blockedUntil;
    }

    public Instant getBlockedAt() {
        return blockedAt;
    }

    public Instant getPurgedAt() {
        return purgedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
