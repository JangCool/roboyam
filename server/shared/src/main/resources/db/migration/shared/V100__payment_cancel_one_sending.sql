-- 커머스 2a 보강 — 포트원 취소 호출을 DB 트랜잭션 밖으로(docs/features/commerce-payment-contract.md 「2a 보강 — 취소 호출을 잠금 밖으로」).
-- 번호: develop(267d53b) 의 shared 마지막 V99 다음.
--
-- 무엇이 바뀌나
--   · 한 결제 주문에 「보내는 중」(SENDING) 취소는 언제나 하나 — 부분 유일 색인.
--     지금까지는 보내는 동안 결제 주문 행을 잠가 같은 결제의 취소가 줄을 섰다. 이제 잠금 없이 포트원을 부르므로(응답을 기다리는 동안 확정·웹훅이 막히지 않게)
--     줄 세우기를 이 색인이 맡는다. 같은 결제의 두 취소가 같은 잔액(currentCancellableAmount)으로 동시에 나가 한쪽이 포트원에서 거절되는 일을 막는다.
--   · 집기 쿼리(PaymentCancelStore)는 보내는 중인 취소가 있는 결제를 건너뛰고, 서버끼리 부딪쳐도 이 색인이 둘째를 거절한다.

CREATE UNIQUE INDEX ux_payment_cancels_one_sending ON payment_cancels (payment_order_id) WHERE status = 'SENDING';
