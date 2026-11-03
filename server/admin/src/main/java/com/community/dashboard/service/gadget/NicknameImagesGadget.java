package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardNicknameImagesView;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import com.community.media.service.ImageStorage;
import org.springframework.stereotype.Component;

/** 닉네임 이미지 심사 대기 — 대기 건수(상태 인덱스 COUNT) + 오래된 차례 6장. 주소는 심사 화면과 같은 {@link ImageStorage#publicUrl} */
@Component
public class NicknameImagesGadget implements DashboardGadgetProvider {

    static final int THUMBNAILS = 6;

    private final DashboardGadgetStore store;
    private final ImageStorage storage;

    public NicknameImagesGadget(DashboardGadgetStore store, ImageStorage storage) {
        this.store = store;
        this.storage = storage;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.NICKNAME_IMAGES;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        return new DashboardNicknameImagesView(store.pendingNicknameImages(), store.oldestNicknameImages(THUMBNAILS).stream()
                .map(r -> new DashboardNicknameImagesView.Recent(Long.toString(r.id()), r.nickname(), r.imageKey() == null ? null : storage.publicUrl(r.imageKey())))
                .toList());
    }
}
