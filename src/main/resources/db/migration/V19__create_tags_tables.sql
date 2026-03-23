CREATE TABLE IF NOT EXISTS tags (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tags_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_tags_user_name UNIQUE (user_id, name)
);

CREATE INDEX IF NOT EXISTS idx_tags_user_id ON tags(user_id);

CREATE TABLE IF NOT EXISTS word_tags (
    word_id BIGINT NOT NULL,
    tag_id  BIGINT NOT NULL,
    CONSTRAINT pk_word_tags PRIMARY KEY (word_id, tag_id),
    CONSTRAINT fk_word_tags_word FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE,
    CONSTRAINT fk_word_tags_tag  FOREIGN KEY (tag_id)  REFERENCES tags(id)  ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_word_tags_tag_id ON word_tags(tag_id);
