package com.community.notification.repository.master;

import com.community.notification.entity.NotificationEventChannelEntity;
import com.community.notification.entity.NotificationEventTeamEntity;
import com.community.notification.entity.QNotificationEventChannelEntity;
import com.community.notification.entity.QNotificationEventEntity;
import com.community.notification.entity.QNotificationEventTeamEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 이벤트별 전달 도구(Slack · Mattermost)와 추가 수신 팀 — V41 의 {@code notification_event_channels}·{@code notification_event_teams}. <b>부르는 서비스가 트랜잭션을 연다.</b>
 * 관리 화면의 읽기·통째로 바꾸기·수 세기는 연결 엔티티({@link NotificationEventChannelEntity}·{@link NotificationEventTeamEntity})와 QueryDSL 이다 —
 * 쓰기 전에 이벤트 행을 먼저 내보낸다(부르는 쪽이 flush, 외래 키).
 *
 * <p>Native SQL(JDBC)로 남긴 것:
 * <ul>
 *   <li>{@link #builtinEnabled}·{@link #enabledChannelIdsOf}·{@link #enabledTeamMemberIdsOf} — 발행기({@code NotificationPublisher})가 업무 트랜잭션의 <b>JDBC 세이브포인트 안에서</b>
 *       부른다. 거기서 JPA 질의가 실패하면 업무 트랜잭션 전체가 롤백 전용이 된다(2026-11-07 실측 — {@code NotificationPublishReads} 와 같은 까닭).</li>
 *   <li>{@link #restoreDefaultRecipientsWithoutTeams} — 되돌린 이벤트 코드가 그 UPDATE 의 결과({@code RETURNING})여야 한다.</li>
 * </ul>
 */
@Repository
public class NotificationDeliveryRepository extends IrobitRepositoryQDSL {

    private static final QNotificationEventChannelEntity EC = QNotificationEventChannelEntity.notificationEventChannelEntity;
    private static final QNotificationEventTeamEntity ET = QNotificationEventTeamEntity.notificationEventTeamEntity;
    private static final QNotificationEventEntity EVENT = QNotificationEventEntity.notificationEventEntity;

    private final NotificationEventChannelRepository channels;
    private final NotificationEventTeamRepository teams;
    private final JdbcTemplate jdbc;

    public NotificationDeliveryRepository(JPAQueryFactory query, NotificationEventChannelRepository channels, NotificationEventTeamRepository teams,
                                          DataSource dataSource) {
        super(query);
        this.channels = channels;
        this.teams = teams;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 이벤트마다 고른 Slack·Mattermost 도구 id (도구 만든 순서) */
    public Map<String, List<Long>> channelIdsByEvent() {
        Map<String, List<Long>> out = new LinkedHashMap<>();
        for (Tuple t : query.select(EC.eventCode, EC.channelId).from(EC).orderBy(EC.eventCode.asc(), EC.channelId.asc()).fetch()) {
            out.computeIfAbsent(t.get(EC.eventCode), key -> new ArrayList<>()).add(t.get(EC.channelId));
        }
        return out;
    }

    /** 이벤트마다 고른 추가 수신 팀 id */
    public Map<String, List<Long>> teamIdsByEvent() {
        Map<String, List<Long>> out = new LinkedHashMap<>();
        for (Tuple t : query.select(ET.eventCode, ET.teamId).from(ET).orderBy(ET.eventCode.asc(), ET.teamId.asc()).fetch()) {
            out.computeIfAbsent(t.get(ET.eventCode), key -> new ArrayList<>()).add(t.get(ET.teamId));
        }
        return out;
    }

    public List<Long> channelIdsOf(String eventCode) {
        return query.select(EC.channelId).from(EC).where(EC.eventCode.eq(eventCode)).orderBy(EC.channelId.asc()).fetch();
    }

    public List<Long> teamIdsOf(String eventCode) {
        return query.select(ET.teamId).from(ET).where(ET.eventCode.eq(eventCode)).orderBy(ET.teamId.asc()).fetch();
    }

    /** 이벤트의 도구 선택을 통째로 바꾼다(지우기는 바로, 넣기는 다음 질의나 커밋 때) */
    public void replaceChannels(String eventCode, Collection<Long> channelIds) {
        query.delete(EC).where(EC.eventCode.eq(eventCode)).execute();
        for (Long id : new LinkedHashSet<>(channelIds)) {
            channels.persist(NotificationEventChannelEntity.of(eventCode, id));
        }
    }

    /** 이벤트의 추가 수신 팀 선택을 통째로 바꾼다(지우기는 바로, 넣기는 다음 질의나 커밋 때) */
    public void replaceTeams(String eventCode, Collection<Long> teamIds) {
        query.delete(ET).where(ET.eventCode.eq(eventCode)).execute();
        for (Long id : new LinkedHashSet<>(teamIds)) {
            teams.persist(NotificationEventTeamEntity.of(eventCode, id));
        }
    }

    /** 도구마다 그 도구를 고른 이벤트 수 */
    public Map<Long, Integer> eventCountByChannel() {
        Map<Long, Integer> out = new LinkedHashMap<>();
        for (Tuple t : query.select(EC.channelId, EC.count()).from(EC).groupBy(EC.channelId).fetch()) {
            out.put(t.get(EC.channelId), Math.toIntExact(t.get(EC.count())));
        }
        return out;
    }

    /** 팀마다 그 팀을 고른 이벤트 수 */
    public Map<Long, Integer> eventCountByTeam() {
        Map<Long, Integer> out = new LinkedHashMap<>();
        for (Tuple t : query.select(ET.teamId, ET.count()).from(ET).groupBy(ET.teamId).fetch()) {
            out.put(t.get(ET.teamId), Math.toIntExact(t.get(ET.count())));
        }
        return out;
    }

    /** 이메일 전달을 켜 둔 이벤트 수 */
    public int emailEventCount() {
        return eventCount(EVENT.emailEnabled.isTrue());
    }

    /** 브라우저 알림 전달을 켜 둔 이벤트 수 */
    public int browserEventCount() {
        return eventCount(EVENT.browserEnabled.isTrue());
    }

    /** 이 이벤트에 고른 도구 중 지금 <b>켜져 있는</b> 것 — 발행할 때 Slack·Mattermost 로 한 번 게시할 곳(세이브포인트 안 — JDBC) */
    public List<Long> enabledChannelIdsOf(String eventCode) {
        return jdbc.queryForList("select c.id from notification_event_channels ec join notification_channels c on c.id = ec.channel_id "
                + "where ec.event_code = ? and c.enabled order by c.id", Long.class, eventCode);
    }

    /** 이 이벤트에 고른 팀 중 <b>사용 중인</b> 팀의 팀원 운영자 id (중복 없이). 운영자의 활성 여부·보는 권한은 부르는 쪽이 거른다(세이브포인트 안 — JDBC) */
    public Set<Long> enabledTeamMemberIdsOf(String eventCode) {
        return new LinkedHashSet<>(jdbc.queryForList("select distinct tm.operator_id from notification_event_teams et "
                + "join teams t on t.id = et.team_id and t.enabled join team_members tm on tm.team_id = t.id where et.event_code = ? order by tm.operator_id",
                Long.class, eventCode));
    }

    /**
     * 「팀에만 보내기」({@code default_recipients=false})인데 고른 팀이 하나도 남지 않은 이벤트를 기본 수신으로 되돌린다 — 팀을 지워 이벤트 선택에서 빠졌을 때(연쇄 삭제) 부른다.
     * 그대로 두면 저장된 값이 「팀 없이 팀에만」이 되어 그 이벤트의 다른 설정을 저장할 수 없다(400 {@code fields.teamIds}).
     *
     * @return 되돌린 이벤트 코드
     */
    public List<String> restoreDefaultRecipientsWithoutTeams() {
        return jdbc.queryForList("update notification_events e set default_recipients = true where not e.default_recipients "
                + "and not exists (select 1 from notification_event_teams et where et.event_code = e.code) returning e.code", String.class);
    }

    /** 기본 제공 도구(이메일 · 브라우저)가 켜져 있는가. 상태 행이 없으면 켜진 것이다(발행기가 세이브포인트 안에서도 부른다 — JDBC) */
    public boolean builtinEnabled(String channelKey) {
        List<Boolean> rows = jdbc.queryForList("select enabled from notification_builtin_channels where channel_key = ?", Boolean.class, channelKey);
        return rows.isEmpty() || Boolean.TRUE.equals(rows.get(0));
    }

    private int eventCount(BooleanExpression where) {
        Long n = query.select(EVENT.count()).from(EVENT).where(where).fetchOne();
        return n == null ? 0 : Math.toIntExact(n);
    }
}
