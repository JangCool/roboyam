package com.community.installation.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 설치 상태(행이 하나뿐이다, {@code id = 1}) — V4__installation.sql, V6__installation_board_preset.sql.
 *
 * <p>행은 마이그레이션이 만든다. 쓰기는 조건부 UPDATE 로만 한다({@code InstallationRepositoryQDSL} — 설치 토큰 해시 갱신·완료 표시는
 * {@code status = PENDING} 일 때만 먹는다)라 엔티티에는 값을 바꾸는 메서드가 없다. 설치 토큰은 <b>해시</b>만 들고 있다.
 *
 * <p>{@link #toString()} 은 값을 싣지 않는다(기본 {@code Object.toString} 을 그대로 쓴다).
 */
@Entity
@Table(name = "installation")
public class InstallationEntity {

    /** 행 번호 — 행은 하나뿐이라 항상 1 이다 */
    public static final int ROW_ID = 1;

    @Id
    private Integer id;

    @Enumerated(EnumType.STRING)
    private InstallationStatus status;

    /** 설치 토큰의 SHA-256(16진 64자). 설치가 끝나면 비워진다 */
    private String tokenHash;

    private Instant installedAt;

    /** 설치 때 만든 루트 운영자(최고 관리자, operators.id) */
    private Long rootOperatorId;

    /** 설치 때 고른 기본 게시판 구성의 아이디({@code NEUTRAL} · {@code ROBOYAM} …). 게시판을 실제로 만드는 적용은 게시판 기능에서 한다 */
    private String boardPreset;

    /** 고른 게시판 구성으로 기본 게시판을 만든(또는 게시판이 이미 있어 건너뛴) 시각 — V42__boards.sql. null 이면 아직 */
    private Instant boardPresetAppliedAt;

    protected InstallationEntity() {
    }

    public Integer getId() {
        return id;
    }

    public InstallationStatus getStatus() {
        return status;
    }

    public boolean isCompleted() {
        return status == InstallationStatus.COMPLETED;
    }

    public Long getRootOperatorId() {
        return rootOperatorId;
    }

    public String getBoardPreset() {
        return boardPreset;
    }

    public Instant getBoardPresetAppliedAt() {
        return boardPresetAppliedAt;
    }
}
