import React from 'react';

function Navbar({ usuario, onLogout, modoAuditoria, setModoAuditoria }) {
  // Mapeo dinámico para mostrar información del nodo en modo auditoría
  const nombreSede = modoAuditoria 
    ? (usuario.sede === 'Sede Norte' ? 'Sede Norte (Nodo 8081)' : 'Sede Sur (Nodo 8083)')
    : usuario.sede;

  return (
    <nav className="navbar navbar-expand-lg navbar-light bg-white py-3 mb-5 border-bottom shadow-sm">
      <div className="container">
        <div className="d-flex align-items-center gap-2">
          <span className="navbar-brand mb-0 h1 text-dark fw-bold fs-4">
            <i className="bi bi-book-half text-primary me-2"></i>
            {modoAuditoria ? 'LibroNet Gateway' : 'LibroNet'}
          </span>
          
          {modoAuditoria && (
            <span className="badge bg-info-subtle text-info-emphasis border border-info-subtle d-none d-sm-inline-block rounded-pill px-2.5 py-1">
              V1.0 - CONSISTENCIA CP
            </span>
          )}
        </div>
        
        <div className="d-flex align-items-center gap-3">
          {/* Interruptor de Modo Auditoría */}
          <div className="form-check form-switch me-2 mb-0 d-flex align-items-center gap-2">
            <input 
              className="form-check-input mt-0 cursor-pointer" 
              type="checkbox" 
              role="switch" 
              id="auditModeSwitch" 
              checked={modoAuditoria}
              onChange={(e) => setModoAuditoria(e.target.checked)}
            />
            <label className="form-check-label small fw-semibold text-secondary user-select-none cursor-pointer" htmlFor="auditModeSwitch">
              <i className="bi bi-cpu me-1"></i>Modo Auditoría
            </label>
          </div>

          <div className="d-flex align-items-center px-3 py-2 bg-light border border-light-subtle text-dark rounded-pill small">
            <span className="me-2 text-primary"><i className="bi bi-person-circle fs-5"></i></span> 
            <span className="fw-bold text-dark me-2">{usuario.nombre}</span> 
            <span className="text-secondary opacity-50">|</span>
            <span className="ms-2 text-secondary">{nombreSede}</span>
          </div>
          
          <button 
            className="btn btn-sm btn-outline-danger py-2 px-3 rounded-pill fw-semibold" 
            onClick={onLogout}
          >
            <i className="bi bi-box-arrow-right me-2"></i>
            {modoAuditoria ? 'Desconectar Nodo' : 'Cerrar Sesión'}
          </button>
        </div>
      </div>
    </nav>
  );
}

export default Navbar;

