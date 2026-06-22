import React, { useState } from 'react';

function BookCard({ libro, onLoan, isProcessing, modoAuditoria, usuarioSede }) {
  const [showInterlibraryPanel, setShowInterlibraryPanel] = useState(false);

  const isNorte = usuarioSede === 'Sede Norte';
  const localStock = isNorte ? libro.copiasNorte : libro.copiasSur;
  const otherStock = isNorte ? libro.copiasSur : libro.copiasNorte;
  const otherSedeName = isNorte ? 'Sede Sur' : 'Sede Norte';

  const handleConfirmInterlibrary = () => {
    setShowInterlibraryPanel(false);
    onLoan(libro.id, false);
  };

  return (
    <div className="col-md-6 mb-4">
      <div className="card h-100 p-4 bg-white text-dark border-light-subtle shadow-sm d-flex flex-column rounded-3">
        <div className="d-flex justify-content-between align-items-start gap-2 mb-3">
          <div className="d-flex align-items-center gap-2">
            <div className="d-flex align-items-center justify-content-center bg-primary-subtle text-primary rounded-circle p-2 fs-5">
              <i className="bi bi-journal-bookmark-fill"></i>
            </div>
            <h5 className="fw-bold text-dark mb-0 fs-5">
              {libro.titulo}
            </h5>
          </div>
        </div>

        {/* Desglose de Stock por Sede */}
        <div className="d-flex flex-wrap gap-2 mb-3">
          <span className={`badge ${
            localStock > 0 
              ? 'bg-success-subtle text-success-emphasis border border-success-subtle' 
              : 'bg-danger-subtle text-danger-emphasis border border-danger-subtle'
          } rounded-pill px-2.5 py-1.5`}>
            <i className="bi bi-geo-alt me-1"></i>Local ({localStock})
          </span>
          <span className={`badge ${
            otherStock > 0 
              ? 'bg-info-subtle text-info-emphasis border border-info-subtle' 
              : 'bg-secondary-subtle text-secondary-emphasis border border-secondary-subtle'
          } rounded-pill px-2.5 py-1.5`}>
            <i className="bi bi-shuffle me-1"></i>{otherSedeName} ({otherStock})
          </span>
        </div>
        
        {modoAuditoria && (
          <div className="mb-4">
            <span className="text-secondary small d-block mb-1">
              <i className="bi bi-fingerprint me-1"></i>IDENTIFICADOR DE RECURSO (UUID)
            </span>
            <code className="text-info w-100 text-truncate d-block text-start border border-light-subtle py-2 px-2 bg-light rounded small">
              <i className="bi bi-upc-scan text-secondary me-2"></i>{libro.id}
            </code>
          </div>
        )}

        <div className="mt-auto d-flex flex-column gap-2">
          {/* Caso 1: Stock local disponible — Préstamo directo */}
          {localStock > 0 && (
            <button
              className="btn btn-primary w-100 py-2 rounded-3 fw-semibold shadow-sm"
              onClick={() => onLoan(libro.id, false)}
              disabled={isProcessing}
            >
              {isProcessing ? (
                <span className="d-flex align-items-center justify-content-center gap-2">
                  <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                  <span>Procesando Transacción...</span>
                </span>
              ) : (
                <><i className="bi bi-cart-plus me-2"></i>Solicitar Préstamo Físico (Local)</>
              )}
            </button>
          )}

          {/* Caso 2: Sin stock local, pero hay en otra sede — Flujo de dos fases */}
          {localStock === 0 && otherStock > 0 && (
            <div>
              {/* Botón de Consulta (Fase 1) */}
              {!showInterlibraryPanel ? (
                <button
                  className="btn btn-outline-warning text-dark border-warning w-100 py-2 rounded-3 fw-semibold"
                  onClick={() => setShowInterlibraryPanel(true)}
                  disabled={isProcessing}
                >
                  <i className="bi bi-search me-2"></i>
                  Consultar Disponibilidad en {otherSedeName}
                </button>
              ) : (
                /* Panel de Confirmación (Fase 2) */
                <div className="border border-warning-subtle rounded-3 p-3 bg-warning-subtle">
                  <div className="d-flex align-items-start gap-2 mb-3">
                    <i className="bi bi-info-circle-fill text-warning fs-5 mt-1 flex-shrink-0"></i>
                    <div>
                      <p className="fw-semibold text-dark mb-1 small">
                        Disponible en <strong>{otherSedeName}</strong>
                      </p>
                      <p className="text-secondary mb-0 small">
                        <i className="bi bi-box-seam me-1"></i>
                        <strong>{otherStock}</strong> {otherStock === 1 ? 'copia disponible' : 'copias disponibles'} para envío físico.
                        El libro no se encuentra en tu sede. Al confirmar, se reservará una copia de {otherSedeName} y quedará pendiente de despacho.
                      </p>
                    </div>
                  </div>
                  <div className="d-flex gap-2">
                    <button
                      className="btn btn-warning text-dark fw-semibold rounded-3 py-1.5 px-3 flex-grow-1 shadow-sm"
                      onClick={handleConfirmInterlibrary}
                      disabled={isProcessing}
                    >
                      {isProcessing ? (
                        <span className="d-flex align-items-center justify-content-center gap-2">
                          <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                          <span>Procesando...</span>
                        </span>
                      ) : (
                        <><i className="bi bi-truck me-2"></i>Confirmar Envío desde {otherSedeName}</>
                      )}
                    </button>
                    <button
                      className="btn btn-outline-secondary rounded-3 py-1.5 px-3"
                      onClick={() => setShowInterlibraryPanel(false)}
                      disabled={isProcessing}
                    >
                      <i className="bi bi-x-lg"></i>
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Caso 3: Sin stock en ninguna sede */}
          {localStock === 0 && otherStock === 0 && (
            <button
              className="btn btn-outline-secondary text-muted border-secondary-subtle w-100 py-2 rounded-3 fw-semibold"
              disabled
            >
              <i className="bi bi-slash-circle me-2"></i>Agotado en todas las sedes
            </button>
          )}

          {/* Botón Secundario (Digital) */}
          {libro.urlDigital && (
            <button
              className="btn btn-outline-info w-100 py-2 rounded-3 fw-semibold"
              onClick={() => onLoan(libro.id, true)}
              disabled={isProcessing}
            >
              <i className="bi bi-laptop me-2"></i>Derivar Copia Digital (E-Book)
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export default BookCard;
