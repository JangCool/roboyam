package com.community.board.entity;

import com.community.board.dto.BoardNotificationValues;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 게시판 기본 설정 「알림」(행 하나) — V42__boards.sql 의 {@code board_notification_settings}. 알림 발송은 후속이라 이번에는 저장만 한다 */
@Entity
@Table(name = "board_notification_settings")
public class BoardNotificationSettingEntity {

    public static final int ROW_ID = 1;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Id
    private Integer id;

    /** 회원 알림 기본값 JSON 배열 글자 */
    @JdbcTypeCode(SqlTypes.JSON)
    private String memberDefaults;

    private int groupWindowMinutes;
    private boolean emailQuietHoursEnabled;
    private String emailQuietStart;
    private String emailQuietEnd;
    private int memberNotificationRetentionDays;
    private Instant updatedAt;
    private Long updatedBy;

    protected BoardNotificationSettingEntity() {
    }

    public BoardNotificationValues values() {
        List<BoardNotificationValues.MemberNotifyRow> rows = memberDefaults == null || memberDefaults.isBlank() ? List.of()
                : MAPPER.readValue(memberDefaults, new TypeReference<List<BoardNotificationValues.MemberNotifyRow>>() {
                });
        return new BoardNotificationValues(rows, groupWindowMinutes, emailQuietHoursEnabled, emailQuietStart, emailQuietEnd, memberNotificationRetentionDays);
    }

    public void change(BoardNotificationValues v, long operatorId, Instant now) {
        this.memberDefaults = MAPPER.writeValueAsString(v.memberDefaults());
        this.groupWindowMinutes = v.groupWindowMinutes();
        this.emailQuietHoursEnabled = v.emailQuietHoursEnabled();
        this.emailQuietStart = v.emailQuietStart();
        this.emailQuietEnd = v.emailQuietEnd();
        this.memberNotificationRetentionDays = v.memberNotificationRetentionDays();
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
