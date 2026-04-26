DROP INDEX IF EXISTS jsonl_entry_field_index_entry_id_idx;
DROP INDEX IF EXISTS jsonl_entry_field_index_value_trgm_idx;
DROP INDEX IF EXISTS jsonl_entry_field_index_empty_idx;
DROP INDEX IF EXISTS jsonl_entry_field_index_null_idx;
DROP INDEX IF EXISTS jsonl_entry_field_index_key_idx;
DROP INDEX IF EXISTS jsonl_entry_field_index_path_ts_id_idx;

DROP TABLE IF EXISTS jsonl_entry_field_index;
