package com.community.notification.controller;

import com.community.notification.dto.request.NotificationChannelEnabledRequest;
import com.community.notification.dto.request.NotificationChannelSaveRequest;
import com.community.notification.dto.response.NotificationChannelTestResultView;
import com.community.notification.dto.response.NotificationChannelView;
import com.community.notification.service.NotificationChannelService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 도구 API (docs/features/notification-contract.md 「알림 도구·조직 구성」). 조회 {@code notification.read}, 만들기·고치기·켜기/끄기·지우기·연결 시험 {@code notification.manage}.
 * 비밀(봇 토큰·웹훅 주소)은 요청으로만 들어오고 응답에는 「입력됨」({@code secretKeys})만 나간다.
 */
@RestController
@RequestMapping("/api/admin/notification-channels")
public class NotificationChannelController {

    private final NotificationChannelService service;

    public NotificationChannelController(NotificationChannelService service) {
        this.service = service;
    }

    @RequirePermission(Permission.NOTIFICATION_READ)
    @GetMapping
    public ApiResponse<List<NotificationChannelView>> list() {
        return ApiResponse.success(service.list());
    }

    @RequirePermission(Permission.NOTIFICATION_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<NotificationChannelView>> create(@RequestBody NotificationChannelSaveRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(actor, body)));
    }

    @RequirePermission(Permission.NOTIFICATION_MANAGE)
    @PutMapping("/{channelId}")
    public ApiResponse<NotificationChannelView> update(@PathVariable String channelId, @RequestBody NotificationChannelSaveRequest body, AdminActor actor) {
        return ApiResponse.success(service.update(actor, channelId, body));
    }

    @RequirePermission(Permission.NOTIFICATION_MANAGE)
    @PutMapping("/{channelId}/enabled")
    public ApiResponse<NotificationChannelView> setEnabled(@PathVariable String channelId, @RequestBody NotificationChannelEnabledRequest body, AdminActor actor) {
        return ApiResponse.success(service.setEnabled(actor, channelId, body));
    }

    @RequirePermission(Permission.NOTIFICATION_MANAGE)
    @DeleteMapping("/{channelId}")
    public ResponseEntity<Void> delete(@PathVariable String channelId, AdminActor actor) {
        service.delete(actor, channelId);
        return ResponseEntity.noContent().build();
    }

    /** 연결을 시험한다 — 실패한 시험도 200 {@code ok:false}. 서랍·브라우저는 400 */
    @RequirePermission(Permission.NOTIFICATION_MANAGE)
    @PostMapping("/{channelId}/test")
    public ApiResponse<NotificationChannelTestResultView> test(@PathVariable String channelId, AdminActor actor) {
        return ApiResponse.success(service.test(actor, channelId));
    }
}
