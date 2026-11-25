#!/usr/bin/env bash
# Dumps the Sitewatch database and streams it to S3. Run as root by the systemd timer (sitewatch-backup.timer).
#
# The dump never touches the disk: pg_dump runs inside the db container and its output goes through a pipe to
# `aws s3 cp -`. (The snap-packaged AWS CLI can write nothing at all when its output is redirected to a file under
# SSM, so a pipe is also the safe way here.) The instance role may only add objects under postgres/.
set -euo pipefail
export PATH="$PATH:/snap/bin"

region="${AWS_REGION:-us-east-1}"
container="${SITEWATCH_DB_CONTAINER:-sitewatch-db-1}"
account="$(aws sts get-caller-identity --query Account --output text)"
bucket="${BACKUP_BUCKET:-sitewatch-backups-$account}"
key="postgres/sitewatch-$(date -u +%Y-%m-%dT%H%M%SZ).dump"

log() { echo "[backup $(date -u +%H:%M:%S)] $*"; }

log "Dumping the database to s3://$bucket/$key"
# pipefail makes a failing pg_dump fail the whole pipeline, so a truncated dump is never reported as success.
docker exec "$container" pg_dump -U sitewatch -d sitewatch --format=custom --compress=6 \
  | aws s3 cp - "s3://$bucket/$key" --region "$region" --only-show-errors

log "Uploaded $key"
