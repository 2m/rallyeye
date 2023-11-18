CREATE TABLE rally (
    kind INTEGER NOT NULL,
    external_id TEXT NOT NULL,
    name TEXT NOT NULL,
    retrieved_at INTEGER NOT NULL,
    PRIMARY KEY(kind, external_id)
);

CREATE TABLE results (
    rally_kind INTEGER NOT NULL,
    rally_external_id TEXT NOT NULL,

    stage_number INTEGER NOT NULL,
    stage_name TEXT NOT NULL,

    driver_country TEXT NOT NULL,
    driver_primary_name TEXT NOT NULL,
    driver_secondary_name TEXT,
    codriver_country TEXT,
    codriver_primary_name TEXT,
    codriver_secondary_name TEXT,

    `group` TEXT NOT NULL,
    car TEXT NOT NULL,

    stage_time_ms INTEGER NOT NULL,
    super_rally BOOLEAN NOT NULL,
    finished BOOLEAN NOT NULL,
    comment TEXT,
    nominal BOOLEAN NOT NULL,

    FOREIGN KEY(rally_kind, rally_external_id) REFERENCES rally(kind, external_id)
    PRIMARY KEY(rally_kind, rally_external_id, stage_number, driver_primary_name)
);

CREATE INDEX results_kind_external_id ON results(rally_kind, rally_external_id);
