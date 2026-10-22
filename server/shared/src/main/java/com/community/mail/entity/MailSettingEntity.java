package com.community.mail.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 메일 서버 설정(행이 하나뿐이다, {@code id = 1}) — V4__installation.sql. 인증 메일·재설정 메일을 보내는 SMTP 서버다.
 *
 * <p>비밀번호는 <b>암호문만</b> 들고 있다({@code v1:} 로 시작). 평문은 어디에도 저장하지 않는다. 행은 설치 때 처음 생긴다(건너뛰면 없다).
 * {@link #toString()} 은 값을 싣지 않는다(기본 {@code Object.toString} 을 그대로 쓴다).
 */
@Entity
@Table(name = "mail_settings")
public class MailSettingEntity {

    /** 행 번호 — 행은 하나뿐이라 항상 1 이다 */
    public static final int ROW_ID = 1;

    @Id
    private Integer id;

    private String host;

    private int port;

    /** 비어 있으면 인증 없이 보낸다 */
    private String username;

    /** 암호문. 사용자 이름이 있을 때만 있다 */
    private String passwordEnc;

    private String fromAddress;

    @Enumerated(EnumType.STRING)
    private MailSecurity security;

    /** 보내는 사람 표시 이름(V55). 없으면 From 은 주소만 */
    private String senderName;

    /** 모든 메일 제목 앞에 붙는 머리말(V55). 없으면 제목 그대로 */
    private String subjectPrefix;

    /** 마지막으로 저장한 운영자(회원 ID). 설치 때는 루트 계정 */
    private Long updatedBy;

    private Instant updatedAt;

    protected MailSettingEntity() {
    }

    /**
     * 설치 때 처음 만든다.
     *
     * @param passwordEnc 이미 암호화한 비밀번호({@code v1:…}) 또는 null. 평문을 넘기지 않는다
     */
    public static MailSettingEntity create(String host, int port, String username, String passwordEnc, String fromAddress,
                                           MailSecurity security, long updatedBy, Instant now) {
        MailSettingEntity setting = new MailSettingEntity();
        setting.id = ROW_ID;
        setting.host = host;
        setting.port = port;
        setting.username = username;
        setting.passwordEnc = passwordEnc;
        setting.fromAddress = fromAddress;
        setting.security = security;
        setting.updatedBy = updatedBy;
        setting.updatedAt = now;
        return setting;
    }

    /**
     * 관리자 화면에서 행이 없을 때(설치 때 건너뜀) 처음 만든다 — 보내는 이름·제목 머리말까지 넣는다.
     *
     * @param passwordEnc 이미 암호화한 비밀번호({@code v1:…}) 또는 null. 평문을 넘기지 않는다
     */
    public static MailSettingEntity create(String host, int port, String username, String passwordEnc, String fromAddress, MailSecurity security,
                                           String senderName, String subjectPrefix, long updatedBy, Instant now) {
        MailSettingEntity setting = create(host, port, username, passwordEnc, fromAddress, security, updatedBy, now);
        setting.senderName = senderName;
        setting.subjectPrefix = subjectPrefix;
        return setting;
    }

    /**
     * 관리자 화면(설정 › 메일 서버)에서 고친다 — 행 잠금을 잡고 부른다. 사용자 이름이 없으면 비밀번호도 지운다.
     *
     * @param passwordEnc   이미 암호화한 비밀번호({@code v1:…}) 또는 null. 평문을 넘기지 않는다
     * @param senderName    보내는 이름(없으면 null)
     * @param subjectPrefix 제목 머리말(없으면 null)
     */
    public void change(String host, int port, String username, String passwordEnc, String fromAddress, MailSecurity security,
                       String senderName, String subjectPrefix, long updatedBy, Instant now) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.passwordEnc = username == null ? null : passwordEnc;
        this.fromAddress = fromAddress;
        this.security = security;
        this.senderName = senderName;
        this.subjectPrefix = subjectPrefix;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    public Integer getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    /** 암호문({@code v1:…}). 평문이 아니다 — 풀어 쓰는 곳은 메일 발송뿐이다 */
    public String getPasswordEnc() {
        return passwordEnc;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public MailSecurity getSecurity() {
        return security;
    }

    /** 보내는 사람 표시 이름(없으면 null) */
    public String getSenderName() {
        return senderName;
    }

    /** 제목 머리말(없으면 null) */
    public String getSubjectPrefix() {
        return subjectPrefix;
    }

    /** 마지막으로 저장한 운영자 ID */
    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
