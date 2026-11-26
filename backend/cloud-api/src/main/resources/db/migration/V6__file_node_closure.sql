-- T036: 邻接表 + 闭包表（file_node_closure）加速祖先/后代查询、权限继承与子树迁移。
CREATE TABLE file_node_closure (
    space_id BIGINT NOT NULL,
    ancestor_id BIGINT NOT NULL,
    descendant_id BIGINT NOT NULL,
    depth INT NOT NULL,
    PRIMARY KEY (space_id, ancestor_id, descendant_id),
    KEY idx_closure_descendant (space_id, descendant_id, ancestor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 为历史已有节点回填完整闭包关系（含自身 depth=0 及所有祖先链）。
INSERT IGNORE INTO file_node_closure (space_id, ancestor_id, descendant_id, depth)
WITH RECURSIVE tree AS (
    SELECT space_id, id AS ancestor_id, id AS descendant_id, 0 AS depth, parent_id
    FROM file_node
    UNION ALL
    SELECT t.space_id, p.id AS ancestor_id, t.descendant_id, t.depth + 1 AS depth, p.parent_id
    FROM tree t
    JOIN file_node p ON p.id = t.parent_id AND p.space_id = t.space_id
    WHERE t.parent_id > 0 AND t.depth < 256
)
SELECT space_id, ancestor_id, descendant_id, depth FROM tree;
