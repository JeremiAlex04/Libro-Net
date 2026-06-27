
-- TABLA LIBRO

CREATE TABLE IF NOT EXISTS libro (
    id UUID PRIMARY KEY,
    titulo VARCHAR(255),
    copias_norte INT NOT NULL DEFAULT 0,
    copias_sur INT NOT NULL DEFAULT 0,
    url_digital VARCHAR(500)
);

-- TABLA BIBLIOTECARIO

CREATE TABLE IF NOT EXISTS bibliotecario (
    username VARCHAR(255) PRIMARY KEY,
    password VARCHAR(255) NOT NULL,
    sede VARCHAR(255) NOT NULL,
    rol VARCHAR(255) NOT NULL
);

-- DATOS DE LIBROS

INSERT INTO libro (id, titulo, copias_norte, copias_sur, url_digital)
VALUES
('123e4567-e89b-12d3-a456-426614174000', 'El Arte de la Escalabilidad', 2, 1, 'https://example.com/books/escalabilidad.pdf'),
('223e4567-e89b-12d3-a456-426614174001', 'Sistemas Distribuidos', 0, 2, 'https://example.com/books/distribuidos.pdf'),

('11111111-1111-1111-1111-111111111111', 'Tradiciones Peruanas', 3, 2, 'https://biblioteca.example.com/tradiciones-peruanas.pdf'),
('22222222-2222-2222-2222-222222222222', 'La Ciudad y los Perros', 4, 3, 'https://biblioteca.example.com/la-ciudad-y-los-perros.pdf'),
('33333333-3333-3333-3333-333333333333', 'Conversacion en La Catedral', 2, 2, 'https://biblioteca.example.com/conversacion-en-la-catedral.pdf'),
('44444444-4444-4444-4444-444444444444', 'El Sexto', 3, 1, 'https://biblioteca.example.com/el-sexto.pdf'),
('55555555-5555-5555-5555-555555555555', 'Yawar Fiesta', 2, 4, 'https://biblioteca.example.com/yawar-fiesta.pdf'),
('66666666-6666-6666-6666-666666666666', 'Los Rios Profundos', 5, 2, 'https://biblioteca.example.com/los-rios-profundos.pdf'),
('77777777-7777-7777-7777-777777777777', 'Redoble por Rancas', 1, 3, 'https://biblioteca.example.com/redoble-por-rancas.pdf'),
('88888888-8888-8888-8888-888888888888', 'Pais de Jauja', 2, 2, 'https://biblioteca.example.com/pais-de-jauja.pdf'),
('99999999-9999-9999-9999-999999999999', 'No me Esperen en Abril', 4, 1, 'https://biblioteca.example.com/no-me-esperen-en-abril.pdf'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'La Palabra del Mudo', 3, 3, 'https://biblioteca.example.com/la-palabra-del-mudo.pdf')

ON CONFLICT (id) DO NOTHING;

-- DATOS DE BIBLIOTECARIOS

INSERT INTO bibliotecario (username, password, sede, rol)
VALUES
('admin_norte', '$2a$10$2iERKjtRWLWtVvQg.G86sOR6a5GWdalb10ZjlqUlv3Tcql/dtHXgO', 'Sede Norte', 'Bibliotecario'),
('admin_sur', '$2a$10$GC327vTuBOws82tVNi1JKOu5KOfKqLEcNEPsjeUhSAL8lN6vsrR4a', 'Sede Sur', 'Bibliotecario')
ON CONFLICT (username) DO NOTHING;

-- TABLA PRESTAMO

CREATE TABLE IF NOT EXISTS prestamo (
    id UUID PRIMARY KEY,
    libro_id UUID NOT NULL,
    libro_titulo VARCHAR(255) NOT NULL,
    bibliotecario VARCHAR(255) NOT NULL,
    sede_solicitante VARCHAR(255) NOT NULL,
    fecha_solicitud TIMESTAMP NOT NULL,
    fecha_local_sede TIMESTAMP,
    reloj_drift_ms BIGINT,
    reloj_rtt_ms BIGINT,
    estado VARCHAR(50) NOT NULL,
    autorizado_por_lider INT,

    CONSTRAINT fk_libro
        FOREIGN KEY (libro_id)
        REFERENCES libro(id)
);
