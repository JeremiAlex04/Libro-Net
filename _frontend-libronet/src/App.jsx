import React, { useState, useEffect } from 'react';

function App() {
  // ==========================================
  // ESTADOS DE AUTENTICACIÓN (NUEVO)
  // ==========================================
  const [usuarioActivo, setUsuarioActivo] = useState(null);
  const [loginForm, setLoginForm] = useState({ username: '', password: '', sede: 'Sede Norte (Nodo 8081)' });

  // ==========================================
  // ESTADOS DEL CATÁLOGO Y PRÉSTAMOS
  // ==========================================
  const [query, setQuery] = useState('');
  const [libros, setLibros] = useState([]);
  const [isSearching, setIsSearching] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [transaccionId, setTransaccionId] = useState(null);
  const [alerta, setAlerta] = useState({ tipo: '', mensaje: '', visible: false });
  const [dekkerLog, setDekkerLog] = useState([]);
  const [isRunningDekker, setIsRunningDekker] = useState(false);

  // Cargar catálogo solo si hay usuario logueado
  useEffect(() => {
    if (usuarioActivo) {
      buscarEnCatalogo('');
    }
  }, [usuarioActivo]);

  // ==========================================
  // LÓGICA DE AUTENTICACIÓN
  // ==========================================
  const handleLogin = (e) => {
    e.preventDefault();
    if (loginForm.username.trim() === '') return;
    
    // Simulamos el inicio de sesión asignando el usuario y la sede seleccionada
    setUsuarioActivo({
      nombre: loginForm.username,
      sede: loginForm.sede,
      rol: 'Bibliotecario'
    });
  };

  const handleLogout = () => {
    setUsuarioActivo(null);
    setLibros([]);
    setQuery('');
  };

  // ==========================================
  // LÓGICA DE RED (SISTEMA DISTRIBUIDO)
  // ==========================================
  const buscarEnCatalogo = async (termino) => {
    setIsSearching(true);
    setSearchError('');
    setAlerta({ visible: false });

    try {
      const response = await fetch(`/api/catalogo/buscar?query=${termino}`);
      if (!response.ok) throw new Error('El servicio de catálogo no está disponible.');
      const data = await response.json();
      setLibros(data);
    } catch (error) {
      setSearchError('Error de conexión con el directorio central. Intente nuevamente.');
      setLibros([]);
    } finally {
      setIsSearching(false);
    }
  };

  const solicitarPrestamo = async (libroId, titulo) => {
    setTransaccionId(libroId);
    setAlerta({ visible: false });

    try {
      const response = await fetch(`/api/prestamos/${libroId}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Sede': usuarioActivo.sede,
          'X-Bibliotecario': usuarioActivo.nombre
        }
      });

      const responseText = await response.text();

      if (response.ok) {
        mostrarAlerta('success', responseText);
        buscarEnCatalogo(query);
      } else {
        mostrarAlerta('danger', `Denegado: ${responseText}`);
      }
    } catch (error) {
      mostrarAlerta('danger', 'Error Crítico: El servicio central no responde.');
    } finally {
      setTransaccionId(null);
    }
  };

  const mostrarAlerta = (tipo, mensaje) => {
    setAlerta({ tipo, mensaje, visible: true });
    setTimeout(() => setAlerta({ visible: false }), 7000);
  };

  const ejecutarSimulacionDekker = async () => {
    setIsRunningDekker(true);
    setDekkerLog([]);

    try {
      const response = await fetch('/api/simulacion/dekker');
      if (!response.ok) throw new Error('Simulación no disponible');
      const data = await response.json();
      setDekkerLog(data);
    } catch {
      setDekkerLog(['Error: no se pudo conectar con el módulo de simulación académica.']);
    } finally {
      setIsRunningDekker(false);
    }
  };

  // ==========================================
  // VISTA 1: PANTALLA DE LOGIN (No autenticado)
  // ==========================================
  if (!usuarioActivo) {
    return (
      <div className="bg-light min-vh-100 d-flex align-items-center justify-content-center">
        <div className="card shadow-lg border-0" style={{ maxWidth: '450px', width: '100%' }}>
          <div className="card-header bg-dark text-white text-center py-4">
            <h3 className="mb-0 fw-bold">LibroNet</h3>
            <p className="mb-0 text-secondary">Acceso a Nodos de Sede</p>
          </div>
          <div className="card-body p-4 p-md-5">
            <form onSubmit={handleLogin}>
              <div className="mb-3">
                <label className="form-label fw-bold text-secondary">Usuario</label>
                <input 
                  type="text" 
                  className="form-control form-control-lg" 
                  placeholder="Ej. admin_norte"
                  value={loginForm.username}
                  onChange={(e) => setLoginForm({...loginForm, username: e.target.value})}
                  required
                />
              </div>
              <div className="mb-3">
                <label className="form-label fw-bold text-secondary">Contraseña (Simulada)</label>
                <input type="password" className="form-control form-control-lg" value="******" readOnly />
              </div>
              <div className="mb-4">
                <label className="form-label fw-bold text-secondary">Conectar a Nodo (Sede)</label>
                <select 
                  className="form-select form-select-lg"
                  value={loginForm.sede}
                  onChange={(e) => setLoginForm({...loginForm, sede: e.target.value})}
                >
                  <option value="Sede Norte (Nodo 8081)">Sede Norte (Nodo 8081)</option>
                  <option value="Sede Sur (Nodo 8083)">Sede Sur (Nodo 8083)</option>
                </select>
              </div>
              <button type="submit" className="btn btn-primary btn-lg w-100 fw-bold">
                Iniciar Sesión en Sede
              </button>
            </form>
          </div>
        </div>
      </div>
    );
  }

  // ==========================================
  // VISTA 2: PANEL DE CONTROL PRINCIPAL (Autenticado)
  // ==========================================
  return (
    <div className="bg-light min-vh-100 pb-5">
      {/* Navbar Multi-tenant */}
      <nav className="navbar navbar-dark bg-dark shadow-sm mb-5">
        <div className="container">
          <span className="navbar-brand mb-0 h1">LibroNet Gateway</span>
          <div className="d-flex align-items-center">
            <span className="badge bg-primary me-3 fs-6">
              👤 {usuarioActivo.nombre} | {usuarioActivo.sede}
            </span>
            <button className="btn btn-sm btn-outline-light" onClick={handleLogout}>
              Desconectar Nodo
            </button>
          </div>
        </div>
      </nav>

      <div className="container" style={{ maxWidth: '900px' }}>
        
        {/* Sección de Búsqueda */}
        <div className="card shadow-sm border-0 mb-4">
          <div className="card-body p-4">
            <h4 className="fw-bold text-secondary mb-3">Buscar en el Catálogo Global</h4>
            <form onSubmit={(e) => { e.preventDefault(); buscarEnCatalogo(query); }} className="d-flex gap-2">
              <input 
                type="text" 
                className="form-control form-control-lg" 
                placeholder="Ej. Escalabilidad..." 
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
              <button type="submit" className="btn btn-primary px-4" disabled={isSearching}>
                {isSearching ? <span className="spinner-border spinner-border-sm"></span> : 'Buscar'}
              </button>
            </form>
          </div>
        </div>

        {/* Alertas Globales */}
        {alerta.visible && (
          <div className={`alert alert-${alerta.tipo} alert-dismissible fade show shadow-sm`} role="alert">
            {alerta.mensaje}
            <button type="button" className="btn-close" onClick={() => setAlerta({ visible: false })}></button>
          </div>
        )}

        {searchError && (
          <div className="alert alert-warning shadow-sm" role="alert">
            {searchError}
          </div>
        )}

        {/* Resultados del Catálogo */}
        <div className="row g-4 mt-1">
          {libros.map((libro) => (
            <div key={libro.id} className="col-md-6">
              <div className="card h-100 shadow-sm border-0">
                <div className="card-body d-flex flex-column">
                  <div className="d-flex justify-content-between align-items-start mb-2">
                    <h5 className="card-title fw-bold text-dark mb-0">{libro.titulo}</h5>
                    <span className={`badge ${libro.copiasDisponibles > 0 ? 'bg-success' : 'bg-danger'}`}>
                      {libro.copiasDisponibles} copias
                    </span>
                  </div>
                  <p className="card-text text-muted small mb-4 font-monospace">UUID: {libro.id}</p>

                  <div className="mt-auto">
                    <button 
                      className={`btn w-100 fw-semibold ${libro.copiasDisponibles > 0 ? 'btn-outline-primary' : 'btn-outline-secondary'}`}
                      onClick={() => solicitarPrestamo(libro.id, libro.titulo)}
                      disabled={libro.copiasDisponibles === 0 || transaccionId === libro.id}
                    >
                      {transaccionId === libro.id ? 'Procesando Transacción...' : 'Aprobar Préstamo en esta Sede'}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Simulación académica (Dekker V5) */}
        <div className="card shadow-sm border-0 mt-5">
          <div className="card-body p-4">
            <h5 className="fw-bold text-secondary mb-2">Simulación Académica — Algoritmo de Dekker V5</h5>
            <p className="text-muted small mb-3">
              Módulo híbrido en memoria compartida (JVM). No interfiere con las transacciones reales en PostgreSQL.
            </p>
            <button
              className="btn btn-outline-dark"
              onClick={ejecutarSimulacionDekker}
              disabled={isRunningDekker}
            >
              {isRunningDekker ? 'Ejecutando simulación...' : 'Ejecutar Dekker V5'}
            </button>
            {dekkerLog.length > 0 && (
              <pre className="bg-dark text-light p-3 rounded mt-3 mb-0 small" style={{ maxHeight: '280px', overflowY: 'auto' }}>
                {dekkerLog.join('\n')}
              </pre>
            )}
          </div>
        </div>

      </div>
    </div>
  );
}

export default App;