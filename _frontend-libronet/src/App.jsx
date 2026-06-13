import React, { useState } from 'react';

function App() {
  const [estado, setEstado] = useState('idle');
  const [mensaje, setMensaje] = useState('');
  
  // El ID debe coincidir exactamente con el que insertamos en PostgreSQL
  const libroId = '123e4567-e89b-12d3-a456-426614174000';

  const solicitarPrestamo = async () => {
    setEstado('cargando');
    setMensaje('');

    try {
      const response = await fetch(`http://localhost:8080/api/prestamos/${libroId}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        }
      });

      if (response.ok) {
        const data = await response.text();
        setEstado('exito');
        setMensaje(data);
      } else {
        const errorData = await response.text();
        throw new Error(errorData || "Fallo en la transacción");
      }
    } catch (error) {
      setEstado('error');
      setMensaje("Error de Conexión: El servicio central no responde o está saturado.");
    }
  };

  return (
    <div className="container mt-5" style={{ maxWidth: '600px' }}>
      
      {/* Encabezado del Sistema */}
      <div className="text-center mb-4">
        <h1 className="display-5 fw-bold text-dark">Biblioteca LibroNet</h1>
        <p className="lead text-secondary">Sistema Distribuido de Préstamos</p>
      </div>
      
      {/* Tarjeta Principal */}
      <div className="card shadow-sm border-0 bg-light">
        <div className="card-body p-4">
          <h4 className="card-title text-center mb-2">El Arte de la Escalabilidad</h4>
          <p className="card-text text-center text-muted mb-4">
            <small>Sede Actual: Conectado vía API Gateway</small>
          </p>
          
          {/* Botón con estado de carga nativo de Bootstrap */}
          <button 
            className="btn btn-primary w-100 py-2 fs-5 fw-semibold"
            onClick={solicitarPrestamo} 
            disabled={estado === 'cargando'}
          >
            {estado === 'cargando' ? (
              <>
                <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                Procesando Transacción...
              </>
            ) : (
              'Solicitar Préstamo'
            )}
          </button>
          
          {/* Alertas Condicionales de Bootstrap */}
          {estado === 'exito' && (
            <div className="alert alert-success mt-4 mb-0 shadow-sm" role="alert">
              <strong>Éxito:</strong> {mensaje}
            </div>
          )}
          
          {estado === 'error' && (
            <div className="alert alert-danger mt-4 mb-0 shadow-sm" role="alert">
              <strong>Alerta del Sistema:</strong> {mensaje}
            </div>
          )}

        </div>
      </div>
      
    </div>
  );
}

export default App;