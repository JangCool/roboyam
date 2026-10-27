#!/usr/bin/env bash
# =============================================================================
# server-setup.sh — 개발 서버(EC2, Ubuntu 24.04 arm64)를 처음 한 번 준비한다
# =============================================================================
#
# 하는 일
#   - Docker · Compose · AWS CLI 설치(ubuntu 계정이 sudo 없이 docker 를 쓰게), 컨테이너 로그 크기 제한(20MB x 5)
#   - 스왑 2GB(앱 두 개 + PostgreSQL 이 4GB 메모리를 같이 쓴다 — 순간적인 초과에 대비)
#   - 시간대 Asia/Seoul(백업 cron · 로그 시각)
#   - 보안 업데이트 자동 설치(unattended-upgrades — Ubuntu 기본값이 켜져 있는지 확인만 한다)
#   - 설치 폴더 /opt/roboyam/{dev,postgres,backups} 와 매일 04:00 백업 cron
#
# 사용법(서버에서, 처음 한 번):
#   sudo bash server-setup.sh
#
# 다시 돌려도 된다(이미 된 것은 건너뛴다). 끝나면 한 번 로그아웃했다가 다시 접속해야 docker 그룹이 적용된다.
# ⚠ 실제 서버에서 아직 시험하지 않았다(문법만 확인).
# =============================================================================
set -Eeuo pipefail

[ "$(id -u)" -eq 0 ] || { echo "오류: sudo 로 실행하세요: sudo bash $0" >&2; exit 1; }

APP_USER=${APP_USER:-ubuntu}
BASE=/opt/roboyam
SWAP_FILE=/swapfile
SWAP_SIZE=2G

id "$APP_USER" > /dev/null 2>&1 || { echo "오류: 계정 $APP_USER 가 없습니다(Ubuntu AMI 의 기본 계정은 ubuntu). APP_USER=<계정> 으로 지정하세요." >&2; exit 1; }

echo "[1/6] 패키지 설치 (docker · compose · buildx · cron)"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q docker.io docker-compose-v2 docker-buildx cron curl unattended-upgrades
# 컨테이너 로그가 디스크(30GB)를 채우지 않게 크기를 묶는다 — 이미 만든 컨테이너에는 적용되지 않으므로 앱을 띄우기 전에 한다
if [ ! -f /etc/docker/daemon.json ]; then
    mkdir -p /etc/docker
    printf '{\n  "log-driver": "json-file",\n  "log-opts": { "max-size": "20m", "max-file": "5" }\n}\n' > /etc/docker/daemon.json
fi
systemctl enable --now docker cron
systemctl restart docker
usermod -aG docker "$APP_USER"

echo "[2/6] AWS CLI"
if ! command -v aws > /dev/null 2>&1; then
    snap install aws-cli --classic
fi
aws --version

echo "[3/6] 스왑 $SWAP_SIZE"
if ! swapon --show=NAME --noheadings | grep -qx "$SWAP_FILE"; then
    [ -f "$SWAP_FILE" ] || { fallocate -l "$SWAP_SIZE" "$SWAP_FILE"; chmod 600 "$SWAP_FILE"; mkswap "$SWAP_FILE"; }
    swapon "$SWAP_FILE"
    grep -q "^$SWAP_FILE " /etc/fstab || echo "$SWAP_FILE none swap sw 0 0" >> /etc/fstab
fi
# 메모리가 남을 때는 스왑을 거의 쓰지 않게 한다(JVM 힙이 스왑으로 밀리면 GC 가 매우 느려진다)
echo 'vm.swappiness=10' > /etc/sysctl.d/99-roboyam.conf
sysctl -q -p /etc/sysctl.d/99-roboyam.conf

echo "[4/6] 시간대 Asia/Seoul"
timedatectl set-timezone Asia/Seoul

echo "[5/6] 보안 업데이트 자동 설치 확인"
if grep -qs 'APT::Periodic::Unattended-Upgrade "1"' /etc/apt/apt.conf.d/20auto-upgrades; then
    echo "  unattended-upgrades 켜져 있음"
else
    printf 'APT::Periodic::Update-Package-Lists "1";\nAPT::Periodic::Unattended-Upgrade "1";\n' > /etc/apt/apt.conf.d/20auto-upgrades
    echo "  unattended-upgrades 를 켰습니다"
fi

echo "[6/6] 설치 폴더 · 백업 cron"
mkdir -p "$BASE/dev/scripts" "$BASE/postgres" "$BASE/backups"
chown -R "$APP_USER:$APP_USER" "$BASE"
chmod 700 "$BASE/backups"
# 매일 04:00(한국시간) — backup.sh 는 release.sh 가 올린다. 아직 없으면 그날은 건너뛴다
cat > /etc/cron.d/roboyam-backup <<EOF
# ROBOYAM 개발 서버 DB · 올린 이미지 백업 (docker/dev/scripts/backup.sh)
0 4 * * * $APP_USER [ -x $BASE/dev/scripts/backup.sh ] && $BASE/dev/scripts/backup.sh >> $BASE/backups/backup.log 2>&1
EOF
chmod 644 /etc/cron.d/roboyam-backup

echo
echo "완료. 로그아웃했다가 다시 접속한 뒤 'docker ps' 가 sudo 없이 되는지 확인하세요."
