#!/bin/bash

# GitHub repository to ECR repository name mapping script
# Usage: ./get-ecr-repository.sh <github-repository>
# Example: ./get-ecr-repository.sh "organization/c4ang-customer-service"

set -e

GITHUB_REPO=$1

if [ -z "$GITHUB_REPO" ]; then
  echo "Error: GitHub repository name is required"
  echo "Usage: $0 <github-repository>"
  exit 1
fi

# Extract repository name (remove organization prefix)
REPO_NAME=$(echo "$GITHUB_REPO" | cut -d'/' -f2)

# Repository to ECR mapping
# Add your repository mappings here
case "$REPO_NAME" in
  "c4ang-customer-service")
    ECR_REPOSITORY="c4ang-customer-service"
    ;;
   "c4ang-order-service")
     ECR_REPOSITORY="c4ang-order-service"
     ;;
   "c4ang-product-service")
     ECR_REPOSITORY="c4ang-product-service"
     ;;
   "c4ang-payment-service")
     ECR_REPOSITORY="c4ang-payment-service"
     ;;
   "c4ang-store-service")
     ECR_REPOSITORY="c4ang-store-service"
     ;;
  *)
    # Default: use repository name as-is
    ECR_REPOSITORY="$REPO_NAME"
    ;;
esac

echo "$ECR_REPOSITORY"
