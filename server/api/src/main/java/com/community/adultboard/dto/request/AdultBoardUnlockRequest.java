package com.community.adultboard.dto.request;

/** {@code POST /api/me/adult-board/unlock} — {@code { pin }} */
public record AdultBoardUnlockRequest(String pin) {

    @Override
    public String toString() {
        return "AdultBoardUnlockRequest[pin=(감춤)]";
    }
}
