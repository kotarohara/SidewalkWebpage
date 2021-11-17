# --- !Ups
ALTER TABLE label
    ADD COLUMN agree_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN disagree_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN notsure_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN correct BOOLEAN;

-- Populate table with current validation info.
UPDATE label
SET (agree_count, disagree_count, notsure_count, correct) = (n_agree, n_disagree, n_notsure, is_correct)
FROM (
     SELECT label_id,
            COUNT(CASE WHEN validation_result = 1 THEN 1 END) AS n_agree,
            COUNT(CASE WHEN validation_result = 2 THEN 1 END) AS n_disagree,
            COUNT(CASE WHEN validation_result = 3 THEN 1 END) AS n_notsure,
            CASE
                WHEN COUNT(CASE WHEN validation_result = 1 THEN 1 END) > COUNT(CASE WHEN validation_result = 2 THEN 1 END) THEN TRUE
                WHEN COUNT(CASE WHEN validation_result = 2 THEN 1 END) > COUNT(CASE WHEN validation_result = 1 THEN 1 END) THEN FALSE
                ELSE NULL
            END AS is_correct
     FROM label_validation
     GROUP BY label_id
 ) AS validation_count
WHERE label.label_id = validation_count.label_id;

# --- !Downs
ALTER TABLE label
    DROP COLUMN correct,
    DROP COLUMN notsure_count,
    DROP COLUMN disagree_count,
    DROP COLUMN agree_count;