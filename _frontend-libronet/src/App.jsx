import React, { useState, useEffect } from 'react';
import Login from './components/Login';
import Navbar from './components/Navbar';
import BookCard from './components/BookCard';

function App() {
  // ==========================================
  // ESTADOS DE AUTENTICACIÓN
  // ==========================================
  const [usuarioActivo, setUsuarioActivo] = useState(null);
  const [modoAuditoria, setModoAuditoria] = useState(false);

  // ==========================================
  // ESTADOS DEL CATÁLOGO Y PRÉSTAMOS
  // ==========================================
  const [query, setQuery] = useState('');
  const [libros, setLibros] = useState([]);
  const [isSearching, setIsSearching] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [transaccionId, setTransaccionId] = useState(null);
  const [alerta, setAlerta] = useState({ tipo: '', mensaje: '', visible: false });

  // ==========================================
  // NUEVO ESTADO: HISTORIAL DE PRÉSTAMOS Y LOGÍSTICA
  // ==========================================
  const [prestamos, setPrestamos] = useState([]);
  const [activeTab, setActiveTab] = useState('logistica'); // 'logistica' o 'historial'
  const [expandedLoans, setExpandedLoans] = useState({});

  const toggleDetails = (id) => {
    setExpandedLoans((prev) => ({
      ...prev,
      [id]: !prev[id],
    }));
  };

  // Carga inicial al iniciar sesión
  useEffect(() => {
    if (usuarioActivo) {
      buscarEnCatalogo('', false);
      obtenerPrestamos(false);
    }
  }, [usuarioActivo]);

  // ==========================================
  // EFECTO: AUTO-POLLING EN SEGUNDO PLANO
  // ==========================================
  useEffect(() => {
    if (!usuarioActivo) return;

    // Realiza un refresco silencioso cada 5 segundos para sincronización concurrente
    const pollingInterval = setInterval(() => {
      buscarEnCatalogo(query, true);
      obtenerPrestamos(true);
    }, 5000);

    return () => clearInterval(pollingInterval);
  }, [usuarioActivo, query]);

  // ==========================================
  // LÓGICA DE RED Y LLAMADAS AL GATEWAY
  // ==========================================
  const buscarEnCatalogo = async (termino, isSilent = false) => {
    if (!isSilent) {
      setIsSearching(true);
      setSearchError('');
      setAlerta({ visible: false });
    }

    try {
      const response = await fetch(`/api/catalogo/buscar?query=${termino}`);
      if (!response.ok) throw new Error('El servicio de catálogo no está disponible.');
      const data = await response.json();
      setLibros(data);
    } catch (error) {
      if (!isSilent) {
        setSearchError('Error de conexión con el directorio central. Intente nuevamente.');
        setLibros([]);
      } else {
        console.warn('Silent Poll Falló: No se pudo conectar al Gateway.');
      }
    } finally {
      if (!isSilent) {
        setIsSearching(false);
      }
    }
  };

  const obtenerPrestamos = async (isSilent = false) => {
    try {
      const response = await fetch('/api/prestamos');
      if (!response.ok) throw new Error('No se pudo obtener el historial de préstamos.');
      const data = await response.json();
      
      // Ordenar por fecha descendente (más nuevos primero)
      data.sort((a, b) => new Date(b.fechaSolicitud) - new Date(a.fechaSolicitud));
      setPrestamos(data);
    } catch (error) {
      if (!isSilent) {
        console.warn('Error al cargar préstamos:', error.message);
      }
    }
  };

  const solicitarPrestamo = async (libroId, isDigital) => {
    setTransaccionId(libroId);
    setAlerta({ visible: false });

    // Actualización optimista en el cliente (UX instantánea)
    const originalLibros = [...libros];
    if (!isDigital) {
      setLibros(
        libros.map((l) => {
          if (l.id === libroId) {
            const isNorte = usuarioActivo.sede === 'Sede Norte';
            if (isNorte) {
              if (l.copiasNorte > 0) {
                return { ...l, copiasNorte: l.copiasNorte - 1 };
              } else if (l.copiasSur > 0) {
                return { ...l, copiasSur: l.copiasSur - 1 };
              }
            } else {
              if (l.copiasSur > 0) {
                return { ...l, copiasSur: l.copiasSur - 1 };
              } else if (l.copiasNorte > 0) {
                return { ...l, copiasNorte: l.copiasNorte - 1 };
              }
            }
          }
          return l;
        })
      );
    }

    try {
      const response = await fetch(`/api/prestamos/${libroId}?digital=${isDigital}`, {
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
        buscarEnCatalogo(query, true); // Refrescamos libros
        obtenerPrestamos(true);        // Refrescamos historial
      } else {
        setLibros(originalLibros);
        mostrarAlerta('danger', `Denegado por consistencia: ${responseText}`);
      }
    } catch (error) {
      setLibros(originalLibros);
      mostrarAlerta('danger', 'Error Crítico: El servicio central no responde o se encuentra particionado.');
    } finally {
      setTransaccionId(null);
    }
  };

  const actualizarEstadoPrestamo = async (id, nuevoEstado) => {
    try {
      const response = await fetch(`/api/prestamos/${id}/estado?estado=${nuevoEstado}`, {
        method: 'PUT'
      });
      if (response.ok) {
        obtenerPrestamos(true);
      } else {
        mostrarAlerta('danger', 'Error al actualizar el estado de logística.');
      }
    } catch (error) {
      mostrarAlerta('danger', 'Fallo de red al actualizar estado del envío.');
    }
  };

  const handleLogin = (userSession) => {
    setUsuarioActivo(userSession);
  };

  const handleLogout = () => {
    setUsuarioActivo(null);
    setLibros([]);
    setQuery('');
    setPrestamos([]);
  };

  const mostrarAlerta = (tipo, mensaje) => {
    setAlerta({ tipo, mensaje, visible: true });
    setTimeout(() => setAlerta({ visible: false }), 7000);
  };

  // ==========================================
  // RENDERIZADO CONDICIONAL DE VISTAS
  // ==========================================
  if (!usuarioActivo) {
    return <Login onLogin={handleLogin} />;
  }

  const prestamosActivos = prestamos.filter(
    (p) => p.estado === 'PENDIENTE_DE_ENVIO' || p.estado === 'EN_TRANSITO'
  );
  const prestamosHistorial = prestamos.filter(
    (p) => p.estado === 'ENTREGADO'
  );

  return (
    <div className="bg-light text-dark min-vh-100 pb-5">
      <Navbar 
        usuario={usuarioActivo} 
        onLogout={handleLogout} 
        modoAuditoria={modoAuditoria}
        setModoAuditoria={setModoAuditoria}
      />

      <div className="container-md mt-4">
        
        {/* Buscador de Catálogo */}
        <div className="card p-4 mb-4 bg-white text-dark border-light-subtle shadow-sm rounded-3">
          <h4 className="fw-bold text-dark mb-3 fs-5">
            <i className="bi bi-search text-primary me-2"></i>
            {modoAuditoria ? 'Buscar en el Catálogo Global (Gateway)' : 'Buscar en el Catálogo'}
          </h4>
          <form 
            onSubmit={(e) => { e.preventDefault(); buscarEnCatalogo(query, false); }} 
          >
            <div className="input-group input-group-lg">
              <span className="input-group-text bg-white border-subtle text-secondary" id="search-addon">
                <i className="bi bi-search"></i>
              </span>
              <input 
                type="text" 
                className="form-control bg-white text-dark border-subtle fs-6" 
                placeholder="Ej. Escalabilidad, Arquitectura..." 
                aria-describedby="search-addon"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
              <button type="submit" className="btn btn-primary px-4 fw-semibold shadow-sm" disabled={isSearching}>
                {isSearching ? (
                  <span className="spinner-border spinner-border-sm" role="status"></span>
                ) : (
                  <span>Buscar</span>
                )}
              </button>
            </div>
          </form>
        </div>

        {/* Alertas */}
        {alerta.visible && (
          <div 
            className={`alert alert-${alerta.tipo} alert-dismissible fade show p-3 mb-4 bg-white text-dark border-light-subtle shadow-sm rounded-3`} 
            role="alert"
          >
            <div className="d-flex align-items-center gap-2">
              <span className="fs-5">{alerta.tipo === 'success' ? <i className="bi bi-check-circle-fill text-success"></i> : <i className="bi bi-exclamation-triangle-fill text-danger"></i>}</span>
              <strong className="small">{alerta.mensaje}</strong>
            </div>
            <button 
              type="button" 
              className="btn-close" 
              onClick={() => setAlerta({ visible: false })}
            ></button>
          </div>
        )}

        {searchError && (
          <div className="alert alert-warning alert-dismissible fade show p-3 mb-4 bg-white text-dark border-light-subtle shadow-sm rounded-3" role="alert">
            <span className="fw-semibold small"><i className="bi bi-exclamation-triangle-fill me-2 text-warning"></i>{searchError}</span>
          </div>
        )}

        {/* Listado de Libros en Catálogo */}
        <div className="row g-4 mt-1">
          {libros.map((libro) => (
            <BookCard 
              key={libro.id} 
              libro={libro} 
              onLoan={solicitarPrestamo} 
              isProcessing={transaccionId === libro.id} 
              modoAuditoria={modoAuditoria}
              usuarioSede={usuarioActivo.sede}
            />
          ))}
          
          {usuarioActivo && !isSearching && libros.length === 0 && !searchError && (
            <div className="col-12 text-center py-5">
              <p className="text-secondary">No se encontraron libros. Intente otra búsqueda.</p>
            </div>
          )}
        </div>

        {/* Panel de Control de Logística y Envío */}
        <div className="card p-4 mt-5 mb-5 bg-white text-dark border-light-subtle shadow-sm rounded-3">
          <div className="d-flex justify-content-between align-items-center flex-wrap gap-3 mb-4">
            <div>
              <h5 className="fw-bold text-dark mb-1 fs-5">
                <i className="bi bi-clipboard-data text-primary me-2"></i>
                {modoAuditoria 
                  ? 'Panel de Control de Consistencia y Logística' 
                  : 'Gestión de Préstamos y Logística'
                }
              </h5>
              <p className="text-secondary small mb-0">
                {modoAuditoria 
                  ? 'Verificación de estados transaccionales, sincronización Cristian y acciones de despacho.' 
                  : 'Monitoreo de envíos activos e historial de transacciones de la sede.'
                }
              </p>
            </div>
          </div>

          {/* Selector de Pestañas */}
          <ul className="nav nav-tabs mb-4 border-light-subtle">
            <li className="nav-item">
              <button 
                className={`nav-link fw-semibold d-flex align-items-center gap-2 border-0 border-bottom px-4 py-2.5 ${
                  activeTab === 'logistica' 
                    ? 'active text-primary border-primary border-3 bg-transparent' 
                    : 'text-secondary bg-transparent'
                }`}
                style={{ transition: 'all 0.2s' }}
                onClick={() => setActiveTab('logistica')}
              >
                <i className="bi bi-truck"></i>
                <span>Logística Activa</span>
                <span className={`badge rounded-pill ${prestamosActivos.length > 0 ? 'bg-danger' : 'bg-secondary'} ms-1`}>
                  {prestamosActivos.length}
                </span>
              </button>
            </li>
            <li className="nav-item">
              <button 
                className={`nav-link fw-semibold d-flex align-items-center gap-2 border-0 border-bottom px-4 py-2.5 ${
                  activeTab === 'historial' 
                    ? 'active text-primary border-primary border-3 bg-transparent' 
                    : 'text-secondary bg-transparent'
                }`}
                style={{ transition: 'all 0.2s' }}
                onClick={() => setActiveTab('historial')}
              >
                <i className="bi bi-archive"></i>
                <span>Historial de Préstamos</span>
                <span className="badge bg-secondary rounded-pill ms-1">
                  {prestamosHistorial.length}
                </span>
              </button>
            </li>
          </ul>

          {activeTab === 'logistica' ? (
            prestamosActivos.length === 0 ? (
              <div className="text-center py-5 bg-light rounded-3 border border-dashed my-2">
                <div className="text-secondary mb-2 fs-2"><i className="bi bi-patch-check"></i></div>
                <p className="text-secondary fw-semibold mb-0">No hay tareas de logística activas en este momento.</p>
                <p className="text-secondary small">Todos los envíos interbibliotecarios han sido procesados.</p>
              </div>
            ) : (
              <div className="table-responsive">
                <table className="table table-hover align-middle mb-0 small">
                  <thead>
                    <tr className="text-secondary border-bottom border-light-subtle">
                      <th scope="col" className="pb-2">Libro</th>
                      <th scope="col" className="pb-2">Origen / Destino</th>
                      <th scope="col" className="pb-2">Rol de Sede</th>
                      <th scope="col" className="pb-2">Fecha Solicitud</th>
                      <th scope="col" className="pb-2">Estado</th>
                      <th scope="col" className="pb-2 text-end">Acciones</th>
                    </tr>
                  </thead>
                  <tbody className="border-0">
                    {prestamosActivos.map((p) => {
                      const isSolicitante = p.sedeSolicitante === usuarioActivo.sede;
                      const otraSede = usuarioActivo.sede === 'Sede Norte' ? 'Sede Sur' : 'Sede Norte';
                      
                      let rolBadgeClass = '';
                      let rolLabel = '';
                      let origenDestino = '';
                      let actionButton = null;

                      if (p.estado === 'PENDIENTE_DE_ENVIO') {
                        if (isSolicitante) {
                          rolBadgeClass = 'bg-secondary-subtle text-secondary-emphasis border border-secondary-subtle';
                          rolLabel = 'Entrante (Espera)';
                          origenDestino = `Traer desde ${otraSede}`;
                          actionButton = (
                            <span className="text-muted small"><i className="bi bi-hourglass-split me-1"></i>En preparación en origen</span>
                          );
                        } else {
                          rolBadgeClass = 'bg-warning-subtle text-warning-emphasis border border-warning-subtle';
                          rolLabel = 'Saliente (Despachar)';
                          origenDestino = `Enviar a ${p.sedeSolicitante}`;
                          actionButton = (
                            <button 
                              className="btn btn-sm btn-outline-warning text-dark border-warning fw-semibold rounded-pill py-1 px-3" 
                              onClick={() => actualizarEstadoPrestamo(p.id, 'EN_TRANSITO')}
                            >
                              <i className="bi bi-send me-1"></i>Despachar Envío
                            </button>
                          );
                        }
                      } else if (p.estado === 'EN_TRANSITO') {
                        if (isSolicitante) {
                          rolBadgeClass = 'bg-info-subtle text-info-emphasis border border-info-subtle';
                          rolLabel = 'Entrante (Recibir)';
                          origenDestino = `En tránsito desde ${otraSede}`;
                          actionButton = (
                            <button 
                              className="btn btn-sm btn-primary fw-semibold shadow-sm rounded-pill py-1 px-3" 
                              onClick={() => actualizarEstadoPrestamo(p.id, 'ENTREGADO')}
                            >
                              <i className="bi bi-box-seam me-1"></i>Entregar al Lector
                            </button>
                          );
                        } else {
                          rolBadgeClass = 'bg-success-subtle text-success-emphasis border border-success-subtle';
                          rolLabel = 'Saliente (En camino)';
                          origenDestino = `Enviado hacia ${p.sedeSolicitante}`;
                          actionButton = (
                            <span className="text-success small"><i className="bi bi-truck me-1"></i>En tránsito...</span>
                          );
                        }
                      }

                      return (
                        <tr key={p.id} className="border-bottom border-light-subtle">
                          <td className="text-dark fw-semibold">{p.libroTitulo}</td>
                          <td>
                            <span className="small text-secondary"><i className="bi bi-arrow-left-right me-1"></i>{origenDestino}</span>
                          </td>
                          <td>
                            <span className={`badge ${rolBadgeClass} rounded-pill px-2.5 py-1`}>
                              {rolLabel}
                            </span>
                          </td>
                          <td className="small text-secondary">
                            <div>{new Date(p.fechaSolicitud).toLocaleString()}</div>
                            
                            {modoAuditoria && (
                              <div className="mt-1">
                                <button 
                                  type="button"
                                  className="btn btn-xs btn-link text-info p-0 font-monospace text-decoration-none"
                                  style={{ fontSize: '0.72rem' }}
                                  onClick={() => toggleDetails(p.id)}
                                >
                                  {expandedLoans[p.id] ? '[- Ocultar Sync]' : '[+ Ver Sync]'}
                                </button>
                                {expandedLoans[p.id] && (
                                  <div className="card p-2 mt-1 bg-light border-light-subtle rounded text-start font-monospace text-dark position-absolute shadow-sm" style={{ fontSize: '0.72rem', minWidth: '220px', zIndex: 10 }}>
                                    <div className="text-success fw-bold">Cristian Corregido:</div>
                                    <div className="ps-2">{new Date(p.fechaSolicitud).toLocaleString()}</div>
                                    <div className="text-warning fw-bold mt-1">Sede Local:</div>
                                    <div className="ps-2">{new Date(p.fechaLocalSede).toLocaleString()}</div>
                                    <div className="mt-1">
                                      <strong>Drift:</strong> {p.relojDriftMs >= 0 ? `+${p.relojDriftMs}ms` : `${p.relojDriftMs}ms`}
                                    </div>
                                    <div><strong>RTT:</strong> {p.relojRttMs}ms</div>
                                  </div>
                                )}
                              </div>
                            )}
                          </td>
                          <td>
                            <span className={`badge ${
                              p.estado === 'EN_TRANSITO' 
                                ? 'bg-info-subtle text-info border border-info-subtle' 
                                : 'bg-warning-subtle text-warning border border-warning-subtle'
                            } rounded-pill px-2 py-1`}>
                              {p.estado === 'EN_TRANSITO' ? 'EN TRÁNSITO' : 'PENDIENTE'}
                            </span>
                          </td>
                          <td className="text-end">{actionButton}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )
          ) : (
            prestamosHistorial.length === 0 ? (
              <div className="text-center py-5 bg-light rounded-3 border border-dashed my-2">
                <p className="text-secondary fw-semibold mb-0">No se han registrado préstamos entregados en esta sesión.</p>
              </div>
            ) : (
              <div className="table-responsive">
                <table className="table table-hover align-middle mb-0 small">
                  <thead>
                    <tr className="text-secondary border-bottom border-light-subtle">
                      <th scope="col" className="pb-2">Libro</th>
                      <th scope="col" className="pb-2">Sede Solicitante</th>
                      <th scope="col" className="pb-2">Bibliotecario</th>
                      <th scope="col" className="pb-2">Fecha Préstamo</th>
                      <th scope="col" className="pb-2 text-end">Estado</th>
                    </tr>
                  </thead>
                  <tbody className="border-0">
                    {prestamosHistorial.map((p) => {
                      return (
                        <tr key={p.id} className="border-bottom border-light-subtle">
                          <td className="text-dark fw-semibold">{p.libroTitulo}</td>
                          <td>{p.sedeSolicitante}</td>
                          <td>{p.bibliotecario}</td>
                          <td className="small text-secondary">
                            <div>{new Date(p.fechaSolicitud).toLocaleString()}</div>
                            
                            {modoAuditoria && (
                              <div className="mt-1">
                                <button 
                                  type="button"
                                  className="btn btn-xs btn-link text-info p-0 font-monospace text-decoration-none"
                                  style={{ fontSize: '0.72rem' }}
                                  onClick={() => toggleDetails(p.id)}
                                >
                                  {expandedLoans[p.id] ? '[- Ocultar Sync]' : '[+ Ver Sync]'}
                                </button>
                                {expandedLoans[p.id] && (
                                  <div className="card p-2 mt-1 bg-light border-light-subtle rounded text-start font-monospace text-dark position-absolute shadow-sm" style={{ fontSize: '0.72rem', minWidth: '220px', zIndex: 10 }}>
                                    <div className="text-success fw-bold">Cristian Corregido:</div>
                                    <div className="ps-2">{new Date(p.fechaSolicitud).toLocaleString()}</div>
                                    <div className="text-warning fw-bold mt-1">Sede Local:</div>
                                    <div className="ps-2">{new Date(p.fechaLocalSede).toLocaleString()}</div>
                                    <div className="mt-1">
                                      <strong>Drift:</strong> {p.relojDriftMs >= 0 ? `+${p.relojDriftMs}ms` : `${p.relojDriftMs}ms`}
                                    </div>
                                    <div><strong>RTT:</strong> {p.relojRttMs}ms</div>
                                  </div>
                                )}
                              </div>
                            )}
                          </td>
                          <td className="text-end">
                            <span className="badge bg-success-subtle text-success-emphasis border border-success-subtle rounded-pill px-2.5 py-1">
                              <i className="bi bi-check-circle-fill me-1"></i>ENTREGADO
                            </span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )
          )}
        </div>
      </div>
    </div>
  );
}

export default App;