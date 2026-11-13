package com.community.home.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 가젯 페이지 한 개({@code gadget_pages}, V108) — 홈처럼 가젯 격자로 꾸미는 공개 페이지({@code /p/{key}}, docs/features/gadget-page-contract.md).
 * 페이지 정보는 테마와 상관없이 하나이고, 배치는 테마마다 {@link GadgetPageLayoutEntity}·가젯은 {@link GadgetEntity}({@code page_id}) 다.
 * 기준 폴더({@code folderId})는 공개 메뉴 main 의 폴더 노드 ID 이고 FK 가 없다 — 없어졌으면 「기준 폴더 없음」으로 읽는다.
 * 처음 배치({@code initialLayout})는 만들 때 기준 폴더의 게시판으로 만든 JSON 이다({@code GadgetPageInitial}).
 * 시스템 페이지({@code systemPage}, V209 — 지금 {@value #SYSTEM_TIPS} 하나)는 사이트 화면의 배치를 갖는 행이다: {@value #SYSTEM_TIPS} = 팁·강좌·사용기 매거진({@code /tips}, 키
 * {@value #TIPS_PAGE_KEY}). 지울 수 없고 키를 바꿀 수 없으며 매거진 가젯 4종을 받는다(tips-contract 「/tips 레이아웃 편집」). 운영자가 만드는 페이지는 null.
 */
@Entity
@Table(name = "gadget_pages")
public class GadgetPageEntity {

    /** 시스템 페이지 값 — 팁·강좌·사용기 매거진({@code /tips}) */
    public static final String SYSTEM_TIPS = "TIPS";
    /** 시스템 페이지 TIPS 의 키(V209 가 넣는다 — 바꿀 수 없다) */
    public static final String TIPS_PAGE_KEY = "tips-magazine";
    /** 시스템 페이지 TIPS 의 공개 주소 */
    public static final String TIPS_PATH = "/tips";

    @Id
    private Long id;

    private String pageKey;
    private String name;
    private String nameEn;
    private String description;
    private Long folderId;
    private boolean writeButton;
    /** 시스템 페이지({@value #SYSTEM_TIPS}) — 운영자가 만든 페이지는 null. 앱은 만들지도 바꾸지도 않는다(마이그레이션·설치 구조 파일만) */
    private String systemPage;

    @JdbcTypeCode(SqlTypes.JSON)
    private String initialLayout;

    private Long createdBy;
    private Instant createdAt;
    private Long updatedBy;
    private Instant updatedAt;

    protected GadgetPageEntity() {
    }

    public static GadgetPageEntity create(long id, Fields fields, String initialLayout, long operatorId, Instant now) {
        GadgetPageEntity row = new GadgetPageEntity();
        row.id = id;
        row.initialLayout = initialLayout;
        row.createdBy = operatorId;
        row.createdAt = now;
        row.change(fields, operatorId, now);
        return row;
    }

    /** 페이지 정보를 바꾼다(처음 배치는 그대로) */
    public void change(Fields fields, long operatorId, Instant now) {
        pageKey = fields.key();
        name = fields.name();
        nameEn = fields.nameEn();
        description = fields.description();
        folderId = fields.folderId();
        writeButton = fields.writeButton();
        updatedBy = operatorId;
        updatedAt = now;
    }

    public Fields fields() {
        return new Fields(pageKey, name, nameEn, description, folderId, writeButton);
    }

    public Long getId() {
        return id;
    }

    public String getPageKey() {
        return pageKey;
    }

    public String getName() {
        return name;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getDescription() {
        return description;
    }

    public Long getFolderId() {
        return folderId;
    }

    public boolean isWriteButton() {
        return writeButton;
    }

    /** 시스템 페이지 값({@value #SYSTEM_TIPS}) — 운영자가 만든 페이지는 null */
    public String getSystemPage() {
        return systemPage;
    }

    /** 시스템 페이지(지울 수 없고 키를 바꿀 수 없다)인가 */
    public boolean isSystem() {
        return systemPage != null;
    }

    /** 매거진 가젯 4종을 받는 시스템 페이지 TIPS 인가 */
    public boolean isTips() {
        return SYSTEM_TIPS.equals(systemPage);
    }

    /** 공개 주소 — 시스템 페이지 TIPS 는 {@value #TIPS_PATH}, 그 밖은 {@code /p/{key}} */
    public String publicPath() {
        return publicPath(systemPage, pageKey);
    }

    /** 공개 주소 — 시스템 페이지 TIPS 는 {@value #TIPS_PATH}, 그 밖은 {@code /p/{key}} */
    public static String publicPath(String systemPage, String pageKey) {
        return SYSTEM_TIPS.equals(systemPage) ? TIPS_PATH : "/p/" + pageKey;
    }

    public String getInitialLayout() {
        return initialLayout;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 운영자가 고치는 칸(검사를 마친 값)
     *
     * @param key      영문 주소
     * @param nameEn   영문 이름 — 없으면 null
     * @param folderId 기준 폴더 — 없으면 null
     */
    public record Fields(String key, String name, String nameEn, String description, Long folderId, boolean writeButton) {
    }
}
