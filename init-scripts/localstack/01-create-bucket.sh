#!/bin/bash
# Executed by LocalStack when it is ready (mounted into /etc/localstack/init/ready.d).
# Creates the bucket used by resource-service. Safe to re-run.
set -e

BUCKET="${S3_BUCKET:-resources}"

echo "Creating S3 bucket: ${BUCKET}"
awslocal s3api create-bucket --bucket "${BUCKET}" || echo "Bucket ${BUCKET} already exists"
