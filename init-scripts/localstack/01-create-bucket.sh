#!/bin/bash
# Executed by LocalStack when it is ready (mounted into /etc/localstack/init/ready.d).
# Creates the buckets of the STAGING and PERMANENT storages (they match the storages pre-created by
# storage-service, see init-scripts/storage-db/init.sql). Safe to re-run.
set -e

for BUCKET in "${STAGING_BUCKET:-staging-bucket}" "${PERMANENT_BUCKET:-permanent-bucket}"; do
  echo "Creating S3 bucket: ${BUCKET}"
  awslocal s3api create-bucket --bucket "${BUCKET}" || echo "Bucket ${BUCKET} already exists"
done
