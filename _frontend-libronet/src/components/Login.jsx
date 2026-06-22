import React, { useState } from 'react';

function Login({ onLogin }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [sede, setSede] = useState('Sede Norte');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (username.trim() === '' || password.trim() === '') return;
    
    setError('');
    setIsLoading(true);

    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          username: username.trim(),
          password: password,
          sede: sede
        })
      });

      if (response.ok) {
        const data = await response.json();
        onLogin(data);
      } else {
        const errorText = await response.text();
        setError(errorText || 'Error de credenciales. Intente nuevamente.');
      }
    } catch (err) {
      setError('Fallo de red: El servidor de autenticación no responde.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-vh-100 d-flex align-items-center justify-content-center px-3 bg-light">
      <div className="col-12 col-sm-10 col-md-8 col-lg-6 col-xl-4">
        <div className="card p-4 p-md-5 w-100 bg-white text-dark border border-light-subtle shadow rounded-4">
          <div className="text-center mb-4">
            <div className="d-inline-block px-3 py-1 rounded-pill mb-2 badge bg-primary-subtle text-primary border border-primary-subtle fs-6">
              <i className="bi bi-cpu me-2"></i>Conexión de Nodos
            </div>
            <h2 className="fw-bold mb-1 text-dark fs-2">
              <i className="bi bi-book-half text-primary me-2"></i>LibroNet
            </h2>
            <p className="text-secondary small">Panel de Control de Sedes Distribuidas</p>
          </div>

          {error && (
            <div className="alert alert-danger p-3 mb-3 small rounded-3 d-flex align-items-center gap-2" role="alert">
              <i className="bi bi-exclamation-triangle-fill fs-5 text-danger"></i>
              <strong className="small">{error}</strong>
            </div>
          )}

          <form onSubmit={handleSubmit}>
            <div className="form-floating mb-3">
              <input 
                type="text" 
                className="form-control bg-white text-dark border-subtle" 
                id="usernameInput"
                placeholder="admin_norte"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                disabled={isLoading}
              />
              <label htmlFor="usernameInput" className="text-secondary">
                <i className="bi bi-person me-1"></i>Identificador del Bibliotecario
              </label>
            </div>

            <div className="form-floating mb-3">
              <input 
                type="password" 
                className="form-control bg-white text-dark border-subtle" 
                id="passwordInput"
                placeholder="Contraseña"
                value={password}
                onChange={(e) => setPassword(e.target.value)} 
                required
                disabled={isLoading}
              />
              <label htmlFor="passwordInput" className="text-secondary">
                <i className="bi bi-lock me-1"></i>Contraseña de Red (Simulada)
              </label>
            </div>

            <div className="form-floating mb-4">
              <select 
                className="form-select bg-white text-dark border-subtle"
                id="sedeSelect"
                value={sede}
                onChange={(e) => setSede(e.target.value)}
                disabled={isLoading}
              >
                <option value="Sede Norte">Sede Norte</option>
                <option value="Sede Sur">Sede Sur</option>
              </select>
              <label htmlFor="sedeSelect" className="text-secondary">
                <i className="bi bi-geo-alt me-1"></i>Conectarse a la Sede
              </label>
            </div>

            <button type="submit" className="btn btn-primary w-100 py-3 rounded-3 fw-semibold shadow-sm" disabled={isLoading}>
              {isLoading ? (
                <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
              ) : (
                <span><i className="bi bi-box-arrow-in-right me-2"></i>Iniciar Conexión de Sede</span>
              )}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}

export default Login;


