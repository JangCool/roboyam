package com.community.notification.controller;

import com.community.notification.dto.request.NotificationEventMuteRequest;
import com.community.notification.dto.request.NotificationEventUpdateRequest;
import com.community.notification.dto.response.MyNotificationEventView;
import com.community.notification.dto.response.NotificationEventView;
import com.community.notification.dto.response.NotificationPageView;
import com.community.notification.dto.response.NotificationSummaryView;
import com.community.notification.service.MyNotificationEventService;
import com.community.notification.service.NotificationEventAdminService;
import com.community.notification.service.NotificationQueryService;
import com.community.permission.annotation.AdminAuthenticated;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 알림 API (docs/features/notification-contract.md). 두 갈래다.
 *
 * <ul>
 *   <li><b>운영자 본인</b>({@link AdminAuthenticated} — 권한이 없어도 부를 수 있다, 경로에 운영자 id 가 없고 대상은 늘 요청한 사람): 종 알림 서랍(목록·요약·읽음)과 내 알림 이벤트별 끄기. 개인 정보라 {@code Cache-Control: no-store}.</li>
 *   <li><b>이벤트 관리</b>({@code notification.read} 조회 / {@code notification.manage} 변경): 이벤트마다 사용 여부·이메일 전달.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin")
public class NotificationController {

    private final NotificationQueryService notifications;
    private final MyNotificationEventService myEvents;
    private final NotificationEventAdminService eventAdmin;

    public NotificationController(NotificationQueryService notifications, MyNotificationEventService myEvents, NotificationEventAdminService eventAdmin) {
        this.notifications = notifications;
        this.myEvents = myEvents;
        this.eventAdmin = eventAdmin;
    }

    /** 내 알림 목록 한 쪽(최근순) — {@code cursor} 는 앞 쪽의 {@code nextCursor}, {@code size} 는 기본 30(최대 100) */
    @AdminAuthenticated
    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<NotificationPageView>> list(@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer size,
                                                                   AdminActor actor) {
        return noStore(notifications.list(actor, cursor, size));
    }

    /** 폴링용 요약 — 안 읽은 수와 가장 최근 알림의 id */
    @AdminAuthenticated
    @GetMapping("/notifications/summary")
    public ResponseEntity<ApiResponse<NotificationSummaryView>> summary(AdminActor actor) {
        return noStore(notifications.summary(actor));
    }

    /** 알림 하나를 읽음 처리한다 — 내 알림만(아니면 404) */
    @AdminAuthenticated
    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> read(@PathVariable String id, AdminActor actor) {
        notifications.markRead(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    /** 내가 볼 수 있는 안 읽은 알림을 모두 읽음 처리한다 */
    @AdminAuthenticated
    @PostMapping("/notifications/read-all")
    public ResponseEntity<Void> readAll(AdminActor actor) {
        notifications.markAllRead(actor);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    /** 내가 볼 수 있는 알림 이벤트와 내가 껐는지 */
    @AdminAuthenticated
    @GetMapping("/profile/notification/events")
    public ResponseEntity<ApiResponse<List<MyNotificationEventView>>> myEvents(AdminActor actor) {
        return noStore(myEvents.list(actor));
    }

    /** 내 알림 이벤트를 끄거나 켠다 — 필수 이벤트를 끄면 409 */
    @AdminAuthenticated
    @PutMapping("/profile/notification/events/{code}")
    public ResponseEntity<ApiResponse<MyNotificationEventView>> setMuted(@PathVariable String code, @RequestBody NotificationEventMuteRequest body, AdminActor actor) {
        return noStore(myEvents.setMuted(actor, code, body));
    }

    /** 이벤트 관리 목록 — 모든 이벤트(준비 중 포함), 영역·코드순 */
    @RequirePermission(Permission.NOTIFICATION_READ)
    @GetMapping("/notification-events")
    public ApiResponse<List<NotificationEventView>> events() {
        return ApiResponse.success(eventAdmin.list());
    }

    /** 이벤트의 사용 여부·이메일 전달을 바꾼다 — 저장된 이벤트 한 줄을 돌려준다 */
    @RequirePermission(Permission.NOTIFICATION_MANAGE)
    @PutMapping("/notification-events/{code}")
    public ApiResponse<NotificationEventView> updateEvent(@PathVariable String code, @RequestBody NotificationEventUpdateRequest body, AdminActor actor) {
        return ApiResponse.success(eventAdmin.update(actor, code, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
