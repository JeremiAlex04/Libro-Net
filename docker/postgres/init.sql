CREATE TABLE IF NOT EXISTS libro (
    id UUID PRIMARY KEY,
    titulo VARCHAR(255),
    copias_disponibles INT
);

INSERT INTO libro (id, titulo, copias_disponibles)
VALUES ('123e4567-e89b-12d3-a456-426614174000', 'El Arte de la Escalabilidad', 3)
ON CONFLICT (id) DO NOTHING;

INSERT INTO libro (id, titulo, copias_disponibles)
VALUES ('223e4567-e89b-12d3-a456-426614174001', 'Sistemas Distribuidos', 2)
ON CONFLICT (id) DO NOTHING;
