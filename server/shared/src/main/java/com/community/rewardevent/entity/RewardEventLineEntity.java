package com.community.rewardevent.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 보상 줄 — shared V163 {@code reward_event_lines}(등수마다 1~10줄). 품목·팩 이름·종류·사용 방식·기간은 고를 때의 사본(지급은 지급 순간의 품목 기간을 쓴다). 준비 상태에서만 바뀐다 —
 * 고치면 줄을 지우고 다시 넣는다(대기 중인 줄 결과도 다시 만든다).
 */
@Entity
@Table(name = "reward_event_lines")
public class RewardEventLineEntity {

    @Id
    private Long id;
    private Long eventId;
    private Long tierId;
    private short position;
    @Enumerated(EnumType.STRING)
    private RewardKind kind;
    private Integer points;
    private Long itemId;
    private String itemName;
    private String itemKind;
    private String itemUsage;
    private Integer itemDurationDays;
    private Short quantity;
    private Long packId;
    private String packName;
    private String manualName;
    private String manualMethod;

    protected RewardEventLineEntity() {
    }

    /** 줄 값 — 종류에 맞지 않는 칸은 null */
    public record Values(RewardKind kind, Integer points, Long itemId, String itemName, String itemKind, String itemUsage, Integer itemDurationDays, Integer quantity,
                         Long packId, String packName, String manualName, String manualMethod) {
    }

    public static RewardEventLineEntity create(long id, long eventId, long tierId, int position, Values v) {
        RewardEventLineEntity l = new RewardEventLineEntity();
        l.id = id;
        l.eventId = eventId;
        l.tierId = tierId;
        l.position = (short) position;
        l.kind = v.kind();
        l.points = v.points();
        l.itemId = v.itemId();
        l.itemName = v.itemName();
        l.itemKind = v.itemKind();
        l.itemUsage = v.itemUsage();
        l.itemDurationDays = v.itemDurationDays();
        l.quantity = v.quantity() == null ? null : v.quantity().shortValue();
        l.packId = v.packId();
        l.packName = v.packName();
        l.manualName = v.manualName();
        l.manualMethod = v.manualMethod();
        return l;
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getTierId() {
        return tierId;
    }

    public short getPosition() {
        return position;
    }

    public RewardKind getKind() {
        return kind;
    }

    public Integer getPoints() {
        return points;
    }

    public Long getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public String getItemKind() {
        return itemKind;
    }

    public String getItemUsage() {
        return itemUsage;
    }

    public Integer getItemDurationDays() {
        return itemDurationDays;
    }

    public Short getQuantity() {
        return quantity;
    }

    public Long getPackId() {
        return packId;
    }

    public String getPackName() {
        return packName;
    }

    public String getManualName() {
        return manualName;
    }

    public String getManualMethod() {
        return manualMethod;
    }
}
