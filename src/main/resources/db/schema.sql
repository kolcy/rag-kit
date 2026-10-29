CREATE DATABASE IF NOT EXISTS ragkit DEFAULT CHARSET utf8mb4;
USE ragkit;

CREATE TABLE IF NOT EXISTS knowledge_chunk (
                                               id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                                               doc_key     VARCHAR(255) NOT NULL COMMENT '文档相对路径，替换删除用',
                                               doc_hash    CHAR(64)     NOT NULL COMMENT '内容 SHA-256，增量判断用',
                                               chunk_index INT          NOT NULL,
                                               content     TEXT         NOT NULL,
                                               embedding   TEXT         NOT NULL COMMENT 'bge-m3 1024 维，JSON 数组',
                                               created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                               UNIQUE KEY uk_doc_chunk (doc_key, chunk_index),
                                               KEY idx_doc_hash (doc_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;