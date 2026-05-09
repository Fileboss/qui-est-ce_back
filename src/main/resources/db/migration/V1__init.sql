CREATE TABLE pack (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255)
);

CREATE TABLE card (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    imageurl VARCHAR(255),
    pack_id BIGINT REFERENCES pack(id)
);
