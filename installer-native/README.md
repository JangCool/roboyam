# installer-native — 운영 서버 원스톱 설치 (Docker 없이)

AWS 서울 EC2 3대(Rocky Linux 9, aarch64)에 로보얌을 **yum/dnf + systemd** 로 올린다.
**DB 서버에서 한 번 실행하면** DB 를 먼저 설치하고, 앱 서버 두 대에 SSH 로 묶음(jar·스크립트·인증서)을 보내 풀고 설치·기동까지 한다.
Docker 설치형(`installer/`)·compose(`docker/`)와 별개다.

> ⚠ 실서버에서 아직 시험하지 않았다(`bash -n` 문법 검사만). 처음은 `--dry-run` 으로 점검부터 한다.

## 구성

```
방문자 ─HTTPS→ Cloudflare (DNS·CDN·로드밸런싱·방문자 인증서, SSL Full (strict))
                 ├─ roboyam.com ──→ 로드밸런싱 풀 [WAS1, WAS2]   상태 확인 /actuator/health/readiness
                 └─ admin.roboyam.com ──→ WAS1
WAS1 (t4g.small, 공인 IP)  Caddy:443 → api :8080 (NODE_ID 1) + admin :8081 (NODE_ID 3)
WAS2 (t4g.small, 공인 IP)  Caddy:443 → api :8080 (NODE_ID 2)
DB   (t4g.medium, 공인 IP) PostgreSQL 18 + pg_cron :5432 — 원스톱 스크립트를 여기서 돌린다
                           └─ SSH(사설 IP) → WAS1, WAS2
```

## 접속 경로 — bastion 없음 (사용자 결정 2026-11-09)

- **세 대 모두 탄력적 IP(고정 공인 IP)** 를 붙인다. WAS 는 Cloudflare 가 들어오는 주소라 꼭 필요하고, DB 는 내 PC 에서 바로 SSH 로 들어가고 `dnf`(설치·보안 업데이트)·R2 백업 업로드로 밖에 나가려고 붙인다.
- 공인 IP 가 있어도 아래 보안 그룹이 내 IP·앱 서버 말고는 다 막는다. bastion 서버·NAT 게이트웨이(월 $40 이상)는 쓰지 않는다.
- 원스톱 스크립트는 DB 서버 안에서 **VPC 사설 IP** 로 앱 서버에 붙는다(`install.conf` 에는 WAS 의 사설 IP).
- 비용: 공인 IPv4 개당 시간당 $0.005 ≈ **월 $3.6**, 3개 ≈ 월 $11. 2025-07-15 이후 만든 계정은 크레딧(6개월) 방식이라 크레딧이 남은 동안은 크레딧에서 빠진다(콘솔 › 결제 및 비용 관리 › 프리 티어에서 확인).
- EC2 의 퍼블릭 DNS 이름(`ec2-…compute.amazonaws.com`)은 공인 IP 의 별명이라 공인 IP 없이는 생기지 않는다.
- 나중에 22 를 닫고 싶으면: EC2 Instance Connect Endpoint(무료, 들어가는 길만)나 SSM Session Manager.

SSH 키: 앱 서버용 `.pem` 을 DB 서버 `/root/.ssh/` 에 두거나(600), 키를 서버에 두기 싫으면 `ssh -A` 로 들어와 `sudo --preserve-env=SSH_AUTH_SOCK ./roboyam-install.sh` 로 에이전트를 넘긴다(`install.conf` 의 `SSH_KEY` 를 비운다).

## 실행 순서

1. **묶음 만들기** — 로컬 PC(저장소 루트, Git Bash):
   ```bash
   installer-native/make-package.sh --build
   ```
   → `installer-native/dist/roboyam-native-<커밋>-<날짜>.tar.gz` (스크립트 + `application/` jar 2개)
2. **DB 서버로 올리고 풀기**
   ```bash
   scp installer-native/dist/roboyam-native-*.tar.gz rocky@<DB 공인 IP>:
   ssh rocky@<DB 공인 IP>
   tar xzf roboyam-native-*.tar.gz && cd roboyam-native
   cp install.conf.example install.conf && vi install.conf      # 앱 서버 사설 IP·도메인·SSH 키
   # certs/ 에 Cloudflare Origin CA 인증서 origin.pem · origin.key 를 넣는다
   ```
3. **점검 → 설치**
   ```bash
   sudo ./roboyam-install.sh --dry-run
   sudo ./roboyam-install.sh
   ```
   DB 비밀번호를 한 번 묻는다. 그다음은 자동이다:
   `[1/3]` DB 설치 → `[2/3]` WAS1 묶음 전송·설치·admin→api 기동(readiness 확인) → DB 배치 등록 확인 → `[3/3]` WAS1 의 `roboyam.env` 를 WAS2 로 넘겨 설치·기동 → 앱 서버의 임시 폴더 삭제.
   끝에 설치 마법사 주소·토큰이 나온다. **WAS1 의 `/etc/roboyam/roboyam.env` 를 서버 밖에 백업**한다.
4. **Cloudflare** — SSL 「Full (strict)」, 로드밸런싱 풀(WAS1·WAS2 공인 IP, 상태 확인 `GET /actuator/health/readiness` 200, Host `roboyam.com`), `admin` 은 WAS1.
5. **배포(이후)** — 새 묶음을 DB 서버에 풀고:
   ```bash
   sudo ./roboyam-install.sh --deploy
   ```
   DB 백업 → WAS1 admin → WAS1 api → WAS2 api 를 한 대씩 바꾸고 재기동한다. readiness 가 안 오면 그 서버는 이전 jar 로 되돌리고 거기서 멈춘다(DB 이관은 되돌리지 않는다).

한 단계만 다시: `--only db | was1 | was2`. 서버 한 대에서 손으로 하려면 각 스크립트를 직접 쓴다(아래 표, `--help`).

| 파일 | 하는 일 |
|---|---|
| `roboyam-install.sh` | **원스톱** — DB 서버에서 설치·배포 전체 |
| `make-package.sh` | 로컬에서 묶음 만들기(`--build` 로 빌드까지) |
| `install.conf.example` | 설정 예시 |
| `db/roboyam-db-install.sh` | DB: PGDG PostgreSQL 18·pg_cron, C.UTF-8, 메모리 자동 계산, pg_hba(앱 서버만), 앱 계정(superuser 아님)·확장·`cron` 권한, 백업 cron |
| `db/roboyam-db-backup.sh` · `backup-hook.example.sh` | 백업(`pg_dump -Fc`, 최근 N개) · R2 업로드 훅 예시 |
| `app/roboyam-app-install.sh` | 앱: Temurin 25, systemd(api·admin), 환경 파일(`DB_PASSWORD=ENC(...)`), Caddy(Origin CA·Cloudflare IP 만), 스왑 |
| `app/roboyam-deploy.sh` | 앱 서버에서 jar 교체 → 재시작 → readiness → 실패 시 되돌리기 |
| `lib/common.sh` | 공통 함수 |

메모리(자동): DB 2GB → shared_buffers 512MB·effective_cache_size 1536MB·work_mem 4MB, 4GB → 1GB·3GB·5MB.
앱 2GB 서버 → api+admin 768m/384m, api 만 1024m.

## 보안 그룹

| 서버 | 포트 | 출처 |
|---|---|---|
| WAS1·WAS2 | 443 | **Cloudflare IP 대역만**(https://www.cloudflare.com/ips/) |
| WAS1·WAS2 | 22 | **DB 서버 보안 그룹**(원스톱 스크립트) + 내 IP(문제 볼 때) |
| DB | 5432 | **WAS1·WAS2 보안 그룹만** |
| DB | 22 | **내 IP 만** |

80 은 열지 않는다. Caddy 도 Cloudflare 가 아닌 접속은 끊고, `/actuator` 는 readiness·liveness 만 내보낸다.
Cloudflare IP 가 바뀌면 앱 서버에서 `sudo bash app/roboyam-app-install.sh --refresh-cloudflare-ips` + 보안 그룹 갱신.

## 운영 전 확인

- DB: `sudo -u postgres psql -d roboyam -c "select cardinality(show_trgm('한글')) > 0"` → `t`, `select jobname, schedule, active from cron.job`, 관리자 › 배치에서 DB 배치가 「사용 불가」가 아닌지.
- 앱: `NODE_ID` 가 1·2·3 으로 서로 다른지, 두 서버 `roboyam.env` 가 같은지(`sudo sha256sum /etc/roboyam/roboyam.env`).
- 접속 수: 2 × 20 + 5 + 여유 < 200.
- 백업: `sudo /opt/roboyam/db/bin/roboyam-db-backup.sh` 로 R2 업로드 확인, **복원 연습** 한 번, EBS 스냅샷(DLM)도 따로.
- 방문자 IP: 로그·로그인 잠금에 실제 IP 가 찍히는지(Caddy 가 `X-Forwarded-For` 를 방문자 IP 하나로 넘긴다 — `roboyam.proxy.trusted-hops: 1`).
- **올린 이미지**: 지금은 서버 로컬 폴더(`/var/lib/roboyam/media`)라 WAS1 에서 올린 이미지를 WAS2 가 못 읽는다. R2 전환(서버 코드, 범위 밖) 전에는 두 대 운영 시 이미지가 깨질 수 있다.
- aarch64 지원: PostgreSQL 18·pg_cron·TimescaleDB 는 PGDG, Java 25 는 Adoptium, Caddy 는 COPR `@caddy/caddy` 로 설치한다.
- TimescaleDB 는 기본 미설치(지금 앱이 쓰지 않는다). 켤 때는 `install.conf` 에 `DB_EXTRA_ARGS="--timescale"` — PGDG `timescaledb_18` 을 설치하고, 검증에서 판(`timescaledb.license` = timescale 이면 압축·연속 집계 정책까지)을 알려 준다. Timescale 자체 저장소는 el9 aarch64 가 비어 있어 쓰지 않는다.
