UPDATE rally
SET championship = converted.championship_json
FROM (
    SELECT kind, external_id, json_insert('[]','$[#]', championship) AS championship_json FROM rally WHERE championship IS NOT NULL
    UNION
    SELECT kind, external_id, json_array() AS championship_json FROM rally WHERE championship IS NULL
) AS converted
WHERE rally.kind = converted.kind
  AND rally.external_id = converted.external_id;
