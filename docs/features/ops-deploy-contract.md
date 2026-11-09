# 운영 배포 구성 계약 (AWS + Cloudflare, 직접 설치)

1인 운영·AWS 초보가 감당할 수 있는 범위에서 결제 가용성(앱 서버 2대)과 pg_cron DB 배치 설계를 지키며 비용을 줄인다.
2026-11-09 사용자 결정으로 확정했다. 설치·배포 절차는 `installer-native/README.md` 가 정본이다.

## 구성
| 서버 | 인스턴스 (서울, Rocky Linux 9 ARM) | 올라가는 것 |
|---|---|---|
| WAS1 | `t4g.small` 2GB | api + admin (systemd, Caddy) |
| WAS2 | `t4g.small` 2GB | api (systemd, Caddy) |
| DB | `t4g.medium` 4GB (시험 기간 2GB, 오픈 직전 올림) | PostgreSQL 18 + pg_cron |

- **Docker 없이 전부 직접 설치**(dnf + systemd). Nuxt 두 앱은 SPA 정적 파일이라 jar 안에 들어가고 서버에는 JVM 만 올라간다.
- **RDS·ALB 는 쓰지 않는다.** RDS 는 pg_cron 을 마음대로 쓰지 못하고, ALB 는 월 약 $29 라서.
- WAS1 은 api·admin JVM 두 개가 2GB 를 나눈다 — `-Xmx` 를 따로 준다(예: api 768m, admin 384m) + 스왑.

## Cloudflare (무료 플랜)
- 도메인 등록은 메가존 그대로, **네임서버만 Cloudflare 로** 바꾼다(무료).
- DNS·CDN·방문자 HTTPS(Universal SSL, 서브도메인 한 단계까지)·**로드밸런싱($5, 서버 2대)**·이미지 저장소 **R2**.
- 앱 서버 앞단은 Caddy + Cloudflare **Origin CA 인증서**(무료, 최대 15년, 파일로 넣음). SSL 모드 Full (strict), 프록시(주황 구름) 켠 도메인만.
- 로드밸런싱 풀 = WAS1·WAS2, 상태 확인 `/actuator/health/readiness`. 장애 전환이 1분 안팎 걸리는 것은 감수한다. `admin` 도메인은 WAS1 로 바로.
- `/actuator/*` 는 Cloudflare 규칙으로 막는다. 서버는 `CF-Connecting-IP` 를 실제 접속 IP 로 믿어야 한다(방문 통계·제재·로그).

## 접속 경로 (bastion 없음)
- 세 대 모두 **탄력적 IP**(고정 공인 IP)를 붙이고 보안 그룹으로 막는다.

| 서버 | 22 (SSH) | 그 밖 |
|---|---|---|
| WAS | DB 서버 SG + 내 IP | 443 = Cloudflare IP 대역만 |
| DB | 내 IP 만 | 5432 = WAS SG 만 |

- DB 에 공인 IP 를 두는 이유: 내 PC 에서 바로 SSH, dnf 설치·보안 업데이트, R2 백업 업로드. 사설 서브넷으로 두면 NAT 게이트웨이가 월 $40+ 든다. EC2 퍼블릭 DNS 는 공인 IP 의 별명이라 대안이 아니다.
- 22 를 닫고 싶어지면 EC2 Instance Connect Endpoint(무료)나 SSM 으로 바꾼다.

## 비용 (월, 부가세 10%·환율 1,450원, 서울 온디맨드)
계정은 **2025-07-15 이후 만든 신규 계정** 기준 — 12개월 무료(공인 IPv4 750시간 등) 없음, 크레딧 최대 $200·6개월.

| 항목 | 운영 | 시험 (DB 2GB) |
|---|---|---|
| EC2 | $60.8 (`t4g.small`×2 + `t4g.medium`) | $45.6 (`t4g.small`×3) |
| 디스크 gp3 (앱 20GB×2, DB 60GB) | $9.1 | $9.1 |
| 공인 IPv4 3개 (개당 $0.005/시간) | $11.0 | $11.0 |
| DB 스냅샷 | 약 $3 | 약 $3 |
| Cloudflare 로드밸런싱 (AWS 크레딧 대상 아님) | $5 | $5 |
| R2·DNS·CDN·인증서·나가는 트래픽(월 100GB 무료 안) | 0 | 0 |
| **합계** | **약 $89 ≈ 14.2만원** | **약 $74 ≈ 11.8만원** |

- 시험 기간 AWS 몫(약 $69)은 크레딧에서 빠진다 → 약 3개월. 안 쓸 때 정지하면 더 간다.
- 무료 플랜은 `t4g.medium` 을 띄울 수 없다(대상: t3/t4g micro·small, c7i-flex.large, m7i-flex.large). DB 4GB 가 필요해지면 유료 플랜(크레딧 그대로)으로 바꾼다. **오픈 전에는 반드시 유료 플랜 + Budgets 알림.**
- 크레딧을 다 쓰고 서버 크기가 정해지면 Savings Plan(선결제 없음, 1년)으로 월 2~3만원을 줄일 수 있다.
- 단가는 알고 있는 값으로 계산했다 — 가입 후 콘솔·계산기로 다시 확인한다.

## 시험 환경 (2026-11-09)
- 무료 플랜 계정은 **한 리전(시드니 `ap-southeast-2`)에 묶여** 서울을 못 쓴다(AWS 관리 조직의 SCP 거부, 「플랜 업그레이드 후 고급 기능 활성화」). 그래서 시험은 무료 플랜 그대로 시드니에서 한다 — 3대 모두 `t4g.small`, 서브넷 `ap-southeast-2a`, Rocky `Rocky-9-EC2-Base-9.8-20260525.0.aarch64`.
- 역할: api+admin = 탄력적 IP `13.211.96.249`(사설 `172.31.9.142`, `admin.roboyam.kr`), api = `16.178.114.162`(`172.31.12.255`, `roboyam.kr`·`www`), DB = `52.62.22.182`(`172.31.14.143`).
- Cloudflare: 도메인 `roboyam.kr`(호스팅케이알 등록, 네임서버만 Cloudflare), SSL 전체(엄격), Origin 인증서, R2 버킷 `roboyam-media` + 사용자 도메인 `media.roboyam.kr`, `sg-was` 443 ← Cloudflare IPv4 대역.
- 오픈 때는 유료 플랜으로 바꾸고 **서울에 새로 만들어** `installer-native` 로 다시 설치한다(시드니 서버는 옮길 수 없다).

## 미확정 · 남은 일
- 이미지 저장소는 develop `173fe386` 에서 `local | r2 | s3` 로 바뀌었다(prod 기본 `r2`, `docs/features/media-storage-contract.md`). 실제 R2 연결 시험은 아직이다.
- 실서버 설치(dry-run 포함)·shellcheck 를 아직 하지 않았다.
- aarch64: Caddy(COPR `@caddy/caddy`)·TimescaleDB(PGDG `timescaledb_18`) 모두 aarch64 를 지원한다(사용자 확인 2026-11-09). TimescaleDB 는 기본 설치하지 않고, 켜면(`--timescale`) 설치 검증이 판(`timescaledb.license` — timescale 이면 압축·연속 집계 정책 사용 가능)을 알려 준다.
- `docker/prod/*` compose 는 여전히 ALB·RDS 전제다 — 이 구성에서는 쓰지 않는다(정리 여부 미정).

## 세션 정리 결정
- 운영 구성의 정본은 이 계약, 설치·배포 절차의 정본은 `installer-native/README.md` 다. `docker/prod/` 의 ALB·RDS 설명과 다르면 이 계약을 따른다.
