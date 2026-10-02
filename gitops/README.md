# Infrastructure workflow installation

Copy `.github/workflows/infrastructure.yml` and `terraform-modules/` into the
GitOps repository before enabling TERRAFORM_ENABLED. Configure GitHub environments
(dev, staging, prod), requiring reviewers for prod. Each environment needs:
AWS_REGION, TERRAFORM_ROLE_ARN, TF_STATE_BUCKET, DB_SUBNET_IDS and
DB_SECURITY_GROUP_IDS (JSON arrays). Use AWS OIDC trust restricted to this repository
and approved environment. The encrypted state bucket needs versioning and locking.

Install External Secrets Operator with a `developerhub-aws` ClusterSecretStore
using workload identity. RDS manages its password in Secrets Manager. Git stores
only the managed secret ARN, endpoint, port and database name. Private subnets and
security groups must allow PostgreSQL only from the approved cluster workload.

Dev destruction requires the platform admin token and the workflow's environment
approval. Repositories and audit records are retained. Shared team namespaces are
not deleted as part of single-service cleanup.

