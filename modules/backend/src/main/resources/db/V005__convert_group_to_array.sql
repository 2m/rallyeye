UPDATE results
SET `group` = CASE
    WHEN `group` = '' THEN json_array() ELSE json_insert('[]','$[#]', `group`)
END;
