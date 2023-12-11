ALTER TABLE results
  ADD penalty_inside_stage_ms INTEGER NOT NULL DEFAULT 0;

ALTER TABLE results
  ADD penalty_outside_stage_ms INTEGER NOT NULL DEFAULT 0;
