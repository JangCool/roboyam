package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicInsert;

/**
 * 고시 항목 값·판매자 추가 항목 — shared V101.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_notice_values")
public class GroupBuyNoticeValueEntity {

    @Id
    private Long id;
    private Long noticeId;
    private Boolean custom;
    private String itemKey;
    private String customName;
    private Integer sortOrder;
    private String value;
    private Boolean unavailable;
    private String unavailableReason;

    protected GroupBuyNoticeValueEntity() {
    }

    /** 고시 항목 값(카탈로그 항목 — 차례는 DB 기본값 0) */
    public static GroupBuyNoticeValueEntity item(long id, long noticeId, String itemKey, String value, boolean unavailable, String unavailableReason) {
        GroupBuyNoticeValueEntity v = new GroupBuyNoticeValueEntity();
        v.id = id;
        v.noticeId = noticeId;
        v.custom = false;
        v.itemKey = itemKey;
        v.value = value;
        v.unavailable = unavailable;
        v.unavailableReason = unavailableReason;
        return v;
    }

    /** 판매자 추가 항목(차례대로) */
    public static GroupBuyNoticeValueEntity custom(long id, long noticeId, String name, String value, int sortOrder) {
        GroupBuyNoticeValueEntity v = new GroupBuyNoticeValueEntity();
        v.id = id;
        v.noticeId = noticeId;
        v.custom = true;
        v.customName = name;
        v.value = value;
        v.sortOrder = sortOrder;
        return v;
    }
}
