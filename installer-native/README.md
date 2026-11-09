# installer-native — 운영 서버 직접 설치 (Docker 없이)

AWS 서울 EC2 3대(Rocky Linux 9, aarch64)에 로보얌을 **yum/dnf + systemd** 로 올리는 스크립트다.
Docker 설치형(`installer/`)·compose(`docker/`)와 별개이며, 그쪽은 고치지 않았다.

> ⚠ 실서버에서 아직 시험하지 않았다(`bash -n` 문법 검사만). 처음 설치는 `--dry-run` 으로 계획부터 본다.

## 구성

```
방문자 ─HTTPS→ Cloudflare (DNS·CDN·로드밸런싱·방문자 인증서)
                 │  Full (strict), Origin CA 인증서
                 ├─ roboyam.com ──→ 로드밸런싱 풀 [WAS1, WAS2]  /actuator/health/readiness 로 상태 확인
                 └─ admin.roboyam.com ──→ WAS1
WAS1 (t4g.small)  Caddy:443 → api 127.0.0.1:8080 (NODE_ID 1) + admin 127.0.0.1:8081 (NODE_ID 3)
WAS2 (t4g.small)  Caddy:443 → api 127.0.0.1:8080 (NODE_ID 2)
DB   (t4g.medium, 시험 기간 2GB)  PostgreSQL 18 + pg_cron :5432 (사설 IP)
```

| 폴더 | 파일 | 하는 일 |
|---|---|---|
| `db/` | `roboyam-db-install.sh` | PGDG 저장소로 PostgreSQL 18·pg_cron 설치, C.UTF-8 initdb, 메모리 자동 계산, pg_hba(앱 서버 대역만), 앱 계정(superuser 아님)·확장·`cron` 권한, 백업 cron |
| | `roboyam-db-backup.sh` | `pg_dump -Fc` + 역할 덤프, 최근 N개 보관, 훅 호출 |
| | `backup-hook.example.sh` | R2 업로드 훅 예시(rclone / aws cli) |
| `app/` | `roboyam-app-install.sh` | Temurin 25, 계정 `roboyam`, 환경 파일, systemd(api·admin), Caddy(Origin CA·호스트 분기·Cloudflare IP 만), 스왑 |
| | `roboyam-deploy.sh` | jar 교체 → 재시작 → readiness → 실패 시 이전 jar 로 되돌림, 이전 jar 보관 |
| `lib/` | `common.sh` | 공통 함수 |

## 실행 순서

서버마다 이 폴더를 통째로 올린다(`scp -r installer-native ec2-user@<서버>:`). 모두 root(`sudo`)로 실행한다.

1. **jar 만들기**(로컬) — 화면을 static 에 넣고 bootJar:
   `pnpm --filter user-web generate && ./gradlew :server:api:bootJar`,
   `pnpm --filter admin-web generate && ./gradlew :server:admin:bootJar`
2. **DB 서버**
   ```bash
   sudo ./db/roboyam-db-install.sh --allow 10.0.1.10/32 --allow 10.0.1.11/32 --dry-run
   sudo ./db/roboyam-db-install.sh --allow 10.0.1.10/32 --allow 10.0.1.11/32 --backup-hook /root/backup-hook.sh
   ```
   앱 계정 비밀번호를 정한다(어디에도 저장하지 않는다). 끝에 앱용 `DB_URL` 이 나온다.
3. **WAS1** (api + admin, 공통 환경 파일을 새로 만든다 — 같은 DB 비밀번호를 입력하면 `ENC(...)` 로 암호화된다)
   ```bash
   sudo ./app/roboyam-app-install.sh --with-admin --node-id 1 --admin-node-id 3 \
     --api-jar roboyam-api.jar --admin-jar roboyam-admin.jar --db-host 10.0.1.20 \
     --domain roboyam.com --domain www.roboyam.com --admin-domain admin.roboyam.com \
     --origin-cert origin.pem --origin-key origin.key --swap 2G
   ```
   관리자 앱을 먼저 띄운다(Flyway 이관·DB 배치 등록). `/etc/roboyam/roboyam.env` 를 **서버 밖에 백업**한다.
4. **WAS2** — WAS1 의 `/etc/roboyam/roboyam.env` 를 복사해 와서(설치 뒤 복사본은 지운다)
   ```bash
   sudo ./app/roboyam-app-install.sh --node-id 2 --shared-env roboyam.env --api-jar roboyam-api.jar \
     --domain roboyam.com --domain www.roboyam.com --origin-cert origin.pem --origin-key origin.key
   ```
5. **Cloudflare** — SSL 「Full (strict)」, Origin CA 인증서 발급(위 `origin.pem/key`), 로드밸런싱 풀(WAS1·WAS2 공인 IP, 상태 확인 `GET /actuator/health/readiness` 200, Host 헤더 `roboyam.com`), `admin` 은 WAS1 로.
6. **배포**(이후) — 서버마다 한 대씩: `sudo roboyam-deploy admin roboyam-admin.jar` → `sudo roboyam-deploy api roboyam-api.jar`.
   되돌리기 `roboyam-deploy api --rollback`, 목록 `roboyam-deploy api --list`.

메모리(자동): DB 2GB → shared_buffers 512MB·effective_cache_size 1536MB·work_mem 4MB, 4GB → 1GB·3GB·5MB.
앱 2GB 서버 → api+admin 768m/384m, api 만 1024m (`--api-xmx`·`--admin-xmx` 로 바꾼다).

## 보안 그룹

| 서버 | 인바운드 | 출처 |
|---|---|---|
| WAS1·WAS2 | 443 | **Cloudflare IP 대역만**(https://www.cloudflare.com/ips/ — 접두사 목록으로 만들어 두면 편하다) |
| WAS1·WAS2 | 22 | 내 IP 만(또는 SSM Session Manager 를 쓰고 닫는다) |
| DB | 5432 | **WAS1·WAS2 의 보안 그룹만** |
| DB | 22 | 내 IP 만 |

80 은 열지 않는다(Caddy 가 듣지 않는다). Caddy 도 Cloudflare 가 아닌 접속은 끊고(이중 방어), `/actuator` 는 readiness·liveness 만 내보낸다.
Cloudflare IP 대역이 바뀌면 `sudo ./app/roboyam-app-install.sh --refresh-cloudflare-ips` 와 보안 그룹을 같이 고친다.

## 운영 전 확인

- DB: `sudo -u postgres psql -d roboyam -c "select cardinality(show_trgm('한글')) > 0"` → `t`, `select jobname, schedule, active from cron.job` 에 DB 배치가 보이는지(관리자 앱이 뜬 뒤), 관리자 › 배치에서 DB 배치가 「사용 불가」가 아닌지.
- 앱: 두 서버의 `NODE_ID` 가 서로·관리자 앱(3)과 다른지, `roboyam.env` 가 두 서버에서 같은지(`sha256sum`).
- 접속 수: 2 × `DB_POOL_SIZE`(20) + 관리자 5 + 여유 < `max_connections`(200).
- 백업: `sudo /opt/roboyam/db/bin/roboyam-db-backup.sh` 를 한 번 손으로 돌려 R2 에 올라가는지, **복원 연습**을 한 번 한다. EBS 스냅샷(DLM)도 따로 건다.
- 키 백업: `roboyam.env` 의 `ROBOYAM_CRYPTO_KEY`·`ROBOYAM_DATA_KEY`·`ROBOYAM_INDEX_KEY` 를 서버 밖에 보관.
- Cloudflare 로 들어온 방문자 IP 가 앱 로그·로그인 잠금에 실제 IP 로 찍히는지(Caddy 가 `X-Forwarded-For` 를 방문자 IP 하나로 넘긴다 — `roboyam.proxy.trusted-hops: 1`).
- **올린 이미지**: 지금은 서버 로컬 폴더(`/var/lib/roboyam/media`)라 WAS1 에서 관리자가 올린 이미지를 WAS2 가 못 읽는다. R2 저장소 전환(서버 코드, 이 작업 범위 밖) 전에는 두 대 운영 시 이미지가 깨질 수 있다.
- TimescaleDB 는 기본으로 설치하지 않는다. `--timescale` 은 PGDG 의 `timescaledb_18`(aarch64 있음)을 쓰며, Apache 판이면 압축·연속 집계 정책 같은 기능이 없다 — 쓰기 전에 확인한다.
