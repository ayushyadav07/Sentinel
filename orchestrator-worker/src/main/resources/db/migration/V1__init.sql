CREATE TABLE scan_run (
    job_id              UUID PRIMARY KEY,
    repo_url            VARCHAR(512) NOT NULL,
    commit_sha          VARCHAR(64),
    scanned_at          TIMESTAMPTZ NOT NULL,
    critical_count      INT NOT NULL DEFAULT 0,
    high_count          INT NOT NULL DEFAULT 0,
    medium_count        INT NOT NULL DEFAULT 0,
    low_count           INT NOT NULL DEFAULT 0,
    action_required     BOOLEAN NOT NULL DEFAULT FALSE,
    status              VARCHAR(32) NOT NULL,
    remediation_action  VARCHAR(32),
    remediation_url     VARCHAR(1024),
    failure_reason      TEXT
);

CREATE INDEX idx_scan_run_repo_url ON scan_run (repo_url);
CREATE INDEX idx_scan_run_scanned_at ON scan_run (scanned_at DESC);
