-- Audit: subcategories. A category may have a parent of the same type (one level only, checked by the backend).
-- Deleting a parent turns its subcategories into normal categories.
alter table tbl_categories add column if not exists parent_id bigint references tbl_categories (id) on delete set null;
create index if not exists idx_categories_parent on tbl_categories (parent_id);
