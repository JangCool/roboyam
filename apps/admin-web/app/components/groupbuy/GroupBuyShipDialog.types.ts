/** 발송 처리 표 한 줄에 보일 주문(`GroupBuyShipDialog` 의 `orders`) */
export interface GroupBuyShipTarget {
    orderId: string;
    orderNo: string;
    member: string;
    items: string;
    carrierCode: string | null;
    trackingNo: string | null;
}
