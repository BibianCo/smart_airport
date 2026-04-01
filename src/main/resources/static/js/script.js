/**
 * app.js — Lógica principal de la interfaz web del Aeropuerto Inteligente
 *
 * Responsabilidades:
 * - Polling al servidor Java cada 500ms para obtener el estado
 * - Renderizar pistas, puertas, aviones y log en el DOM
 * - Controlar las acciones del usuario (iniciar, detener, agregar avión, etc.)
 * - Detectar el modo de demostración (sin servidor) para modo preview
 *
 * Patrón: El servidor Java expone /api/estado en JSON.
 * El frontend hace polling y actualiza el DOM sin recargar la página.
 */

// ── Configuración ──
const API_BASE = 'http://localhost:8080';
const POLL_INTERVAL_MS = 500;

// ── Estado local ──
let estadoAnterior = null;
let intervaloPolling = null;
let logEntradas = [];
let modoDemo = false; // true si el servidor Java no está disponible

// ══════════════════════════════════════════════════
//  INICIALIZACIÓN
// ══════════════════════════════════════════════════

document.addEventListener('DOMContentLoaded', () => {
    inicializarPlaceholders();
    iniciarPolling();
    console.log('AeroSim iniciado. Conectando con servidor Java en', API_BASE);
});

/**
 * Muestra placeholders de carga mientras se conecta al servidor.
 */
function inicializarPlaceholders() {
    renderizarPistas([
        { numero: 1, disponible: true, avionActual: null, usosTotales: 0 },
        { numero: 2, disponible: true, avionActual: null, usosTotales: 0 },
        { numero: 3, disponible: true, avionActual: null, usosTotales: 0 },
    ]);
    renderizarPuertas([
        { numero: 1, disponible: true, avionActual: null },
        { numero: 2, disponible: true, avionActual: null },
        { numero: 3, disponible: true, avionActual: null },
        { numero: 4, disponible: true, avionActual: null },
        { numero: 5, disponible: true, avionActual: null },
    ]);
}

// ══════════════════════════════════════════════════
//  POLLING
// ══════════════════════════════════════════════════

/**
 * Inicia el polling periódico al servidor Java.
 * Si el servidor no responde, activa el modo demostración.
 */
function iniciarPolling() {
    intervaloPolling = setInterval(async () => {
        try {
            const respuesta = await fetch(`${API_BASE}/api/estado`, {
                signal: AbortSignal.timeout(1000)
            });
            if (!respuesta.ok) throw new Error('Respuesta no OK');
            const estado = await respuesta.json();
            modoDemo = false;
            actualizarUI(estado);
        } catch (err) {
            if (!modoDemo) {
                modoDemo = true;
                mostrarModoDemo();
            }
        }
    }, POLL_INTERVAL_MS);
}

/**
 * Actualiza toda la interfaz con el estado recibido del servidor.
 * @param {Object} estado - Objeto JSON con el estado del aeropuerto
 */
function actualizarUI(estado) {
    renderizarPistas(estado.pistas || []);
    renderizarPuertas(estado.puertas || []);
    renderizarAviones(estado.avionesActivos || []);
    actualizarEstadisticas(estado.estadisticas || {});
    actualizarLog(estado.log || []);
    actualizarStatusPill(estado.simulacionActiva);
    actualizarBotones(estado.simulacionActiva);
}

// ══════════════════════════════════════════════════
//  RENDERIZADO DE PISTAS
// ══════════════════════════════════════════════════

/**
 * Renderiza las tarjetas de pistas de aterrizaje.
 * Cada pista muestra si está libre u ocupada y por qué avión.
 *
 * @param {Array} pistas - Lista de objetos pista
 */
function renderizarPistas(pistas) {
    const grid = document.getElementById('pistasGrid');
    grid.innerHTML = pistas.map(pista => {
        const libre = pista.disponible;
        const avionInfo = pista.avionActual
            ? `${pista.avionActual.id} · ${pista.avionActual.nombre}`
            : 'Libre';
        return `
      <div class="pista-card ${libre ? 'libre' : 'ocupada'}">
        <div class="pista-numero">PISTA ${pista.numero}</div>
        <div class="pista-estado">${libre ? '● Disponible' : '● En uso'}</div>
        <div class="pista-avion">${avionInfo}</div>
        <div class="pista-usos">Usos totales: ${pista.usosTotales}</div>
        <div class="pista-icon">✈</div>
      </div>
    `;
    }).join('');
}

// ══════════════════════════════════════════════════
//  RENDERIZADO DE PUERTAS
// ══════════════════════════════════════════════════

/**
 * Renderiza las tarjetas de puertas de embarque.
 * Las puertas son gestionadas por el semáforo de conteo.
 *
 * @param {Array} puertas - Lista de objetos puerta
 */
function renderizarPuertas(puertas) {
    const grid = document.getElementById('puertasGrid');
    grid.innerHTML = puertas.map(puerta => {
        const libre = puerta.disponible;
        const avionInfo = puerta.avionActual
            ? `${puerta.avionActual.id}`
            : '—';
        return `
      <div class="puerta-card ${libre ? 'libre' : 'ocupada'}">
        <div class="puerta-numero">PUERTA ${puerta.numero}</div>
        <div class="puerta-estado">${libre ? '○ Libre' : '● Ocupada'}</div>
        <div class="puerta-avion">${avionInfo}</div>
      </div>
    `;
    }).join('');
}

// ══════════════════════════════════════════════════
//  RENDERIZADO DE AVIONES
// ══════════════════════════════════════════════════

/**
 * Renderiza la lista de aviones activos (hilos en ejecución).
 * Cada avión muestra su ID, nombre, estado y recursos asignados.
 *
 * @param {Array} aviones - Lista de aviones activos
 */
function renderizarAviones(aviones) {
    const lista = document.getElementById('avionesList');
    const badge = document.getElementById('badgeAviones');

    badge.textContent = `${aviones.length} hilo${aviones.length !== 1 ? 's' : ''}`;

    if (aviones.length === 0) {
        lista.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">✈</div>
        <div class="empty-text">No hay aviones activos.<br>Inicia la simulación o agrega uno manualmente.</div>
      </div>`;
        return;
    }

    lista.innerHTML = aviones.map(avion => {
        const recursosPista = avion.pistaAsignada > 0 ? `P${avion.pistaAsignada}` : '—';
        const recursosPuerta = avion.puertaAsignada > 0 ? `G${avion.puertaAsignada}` : '—';

        return `
      <div class="avion-item">
        <span class="avion-id">${avion.id}</span>
        <span class="avion-nombre">${avion.nombre}</span>
        <span class="avion-estado-badge estado-${avion.estado}">${avion.estadoDesc}</span>
        <span class="avion-recursos">Pista:${recursosPista} Puerta:${recursosPuerta}</span>
      </div>
    `;
    }).join('');
}

// ══════════════════════════════════════════════════
//  ESTADÍSTICAS
// ══════════════════════════════════════════════════

/**
 * Actualiza los contadores estadísticos del panel izquierdo.
 *
 * @param {Object} stats - Objeto con las estadísticas
 */
function actualizarEstadisticas(stats) {
    setValorConAnimacion('statPistasLibres', stats.pistasLibres ?? '—');
    setValorConAnimacion('statPuertasLibres', stats.puertasLibres ?? '—');
    setValorConAnimacion('statEsperando', stats.esperandoPista ?? '—');
    setValorConAnimacion('statCompletados', stats.avionesCompletados ?? '—');
}

/**
 * Actualiza un elemento de estadística con animación de cambio.
 */
function setValorConAnimacion(id, valor) {
    const el = document.getElementById(id);
    if (!el) return;
    if (el.textContent !== String(valor)) {
        el.style.transform = 'scale(1.2)';
        el.textContent = valor;
        setTimeout(() => { el.style.transform = 'scale(1)'; }, 150);
    }
}

// ══════════════════════════════════════════════════
//  LOG DE EVENTOS
// ══════════════════════════════════════════════════

/**
 * Actualiza el log de eventos concurrentes.
 * Solo agrega entradas nuevas para evitar parpadeo.
 *
 * @param {Array} eventosNuevos - Lista de eventos del servidor
 */
function actualizarLog(eventosNuevos) {
    if (!eventosNuevos || eventosNuevos.length === 0) return;

    const container = document.getElementById('logContainer');
    const totalActual = logEntradas.length;

    // Agregar solo eventos nuevos
    const nuevos = eventosNuevos.slice(Math.max(0, eventosNuevos.length - (eventosNuevos.length - totalActual)));

    // Sincronizar log local con el del servidor
    logEntradas = eventosNuevos;

    container.innerHTML = '';
    eventosNuevos.forEach(evento => {
        const div = document.createElement('div');
        div.className = `log-entry ${evento.tipo}`;
        div.innerHTML = `
      <span class="log-time">${evento.timestamp}</span>
      <span class="log-msg">${evento.avionId ? `[${evento.avionId}] ` : ''}${evento.mensaje}</span>
    `;
        container.appendChild(div);
    });

    // Auto-scroll al final
    container.scrollTop = container.scrollHeight;
}

/**
 * Limpia el log visualmente (el servidor mantiene el historial).
 */
function limpiarLog() {
    const container = document.getElementById('logContainer');
    container.innerHTML = '<div class="log-entry info"><span class="log-time">—</span><span class="log-msg">Log limpiado.</span></div>';
    logEntradas = [];
}

// ══════════════════════════════════════════════════
//  STATUS Y BOTONES
// ══════════════════════════════════════════════════

function actualizarStatusPill(activo) {
    const pill = document.getElementById('statusPill');
    const texto = document.getElementById('statusText');
    if (activo) {
        pill.classList.add('activo');
        texto.textContent = 'Simulación activa';
    } else {
        pill.classList.remove('activo');
        texto.textContent = 'Detenido';
    }
}

function actualizarBotones(activo) {
    document.getElementById('btnIniciar').disabled = activo;
    document.getElementById('btnDetener').disabled = !activo;
}

// ══════════════════════════════════════════════════
//  ACCIONES DEL USUARIO
// ══════════════════════════════════════════════════

/**
 * Inicia la simulación automática de aviones.
 */
async function iniciarSimulacion() {
    await apiPost('/api/iniciar');
}

/**
 * Detiene la simulación automática.
 */
async function detenerSimulacion() {
    await apiPost('/api/detener');
}

/**
 * Reinicia toda la simulación.
 */
async function reiniciarSimulacion() {
    if (!confirm('¿Reiniciar la simulación? Se perderán todos los datos actuales.')) return;
    logEntradas = [];
    await apiPost('/api/reiniciar');
}

/**
 * Agrega un nuevo avión manualmente.
 */
async function agregarAvion() {
    const input = document.getElementById('nombreAvion');
    const nombre = input.value.trim() || 'Avión-Manual';
    input.value = '';
    await apiPost('/api/avion', { nombre });
}

/**
 * Establece el nombre de aerolínea desde los chips sugeridos.
 */
function setAerolinea(nombre) {
    document.getElementById('nombreAvion').value = nombre;
}

/**
 * Dispara la demostración de condición de carrera.
 */
async function demostrarCarrera() {
    await apiPost('/api/carrera');
}

// ══════════════════════════════════════════════════
//  MODO DEMO (sin servidor)
// ══════════════════════════════════════════════════

/**
 * Activa el modo demostración cuando el servidor Java no está disponible.
 * Simula el comportamiento con datos generados en el navegador.
 */
function mostrarModoDemo() {
    const container = document.getElementById('logContainer');
    container.innerHTML = `
    <div class="log-entry CONDICION_CARRERA">
      <span class="log-time">${ahora()}</span>
      <span class="log-msg">⚠ Servidor Java no encontrado en localhost:8080</span>
    </div>
    <div class="log-entry INFO">
      <span class="log-time">${ahora()}</span>
      <span class="log-msg">Ejecuta Main.java para activar el backend. Mostrando modo preview.</span>
    </div>
  `;

    // Iniciar simulación visual demo
    iniciarSimulacionDemo();
}

/**
 * Simulación visual demo en el navegador (sin backend Java).
 * Demuestra cómo se vería la interfaz en funcionamiento.
 */
function iniciarSimulacionDemo() {
    const aerolineas = ['AeroCol', 'Latam', 'Avianca', 'Copa', 'Wingo', 'EasyFly'];
    const estados = ['ESPERANDO_PISTA', 'EN_PISTA', 'HACIA_PUERTA', 'EN_PUERTA'];
    const estadosDesc = { ESPERANDO_PISTA: 'Esperando pista', EN_PISTA: 'En pista', HACIA_PUERTA: 'Hacia puerta', EN_PUERTA: 'En puerta' };

    let contadorDemo = 1;
    let avionesDemo = [];
    let pistasDemo = [
        { numero: 1, disponible: true, avionActual: null, usosTotales: 0 },
        { numero: 2, disponible: true, avionActual: null, usosTotales: 0 },
        { numero: 3, disponible: true, avionActual: null, usosTotales: 0 },
    ];
    let puertasDemo = [1, 2, 3, 4, 5].map(n => ({ numero: n, disponible: true, avionActual: null }));
    let completadosDemo = 0;
    let logDemo = [];

    function addLog(tipo, msg, avionId) {
        logDemo.push({ tipo, mensaje: msg, avionId: avionId || '', timestamp: ahora() });
        if (logDemo.length > 80) logDemo = logDemo.slice(-80);

        const container = document.getElementById('logContainer');
        const div = document.createElement('div');
        div.className = `log-entry ${tipo}`;
        div.innerHTML = `<span class="log-time">${ahora()}</span><span class="log-msg">${avionId ? `[${avionId}] ` : ''}${msg}</span>`;
        container.appendChild(div);
        container.scrollTop = container.scrollHeight;
    }

    function tick() {
        // Agregar avión
        if (avionesDemo.length < 6 && Math.random() < 0.4) {
            const id = `AV-${String(contadorDemo++).padStart(3, '0')}`;
            const nombre = aerolineas[Math.floor(Math.random() * aerolineas.length)];
            const avion = { id, nombre, estado: 'ESPERANDO_PISTA', estadoDesc: 'Esperando pista', pistaAsignada: -1, puertaAsignada: -1 };
            avionesDemo.push(avion);
            addLog('INFO', 'Avión ingresó al sistema', id);
        }

        // Avanzar estados
        avionesDemo.forEach(avion => {
            const r = Math.random();
            if (avion.estado === 'ESPERANDO_PISTA' && r < 0.5) {
                const pistaLibre = pistasDemo.find(p => p.disponible);
                if (pistaLibre) {
                    pistaLibre.disponible = false;
                    pistaLibre.avionActual = { id: avion.id, nombre: avion.nombre };
                    pistaLibre.usosTotales++;
                    avion.pistaAsignada = pistaLibre.numero;
                    avion.estado = 'EN_PISTA';
                    avion.estadoDesc = 'En pista';
                    addLog('PISTA_OCUPADA', `Aterrizando en pista ${pistaLibre.numero}`, avion.id);
                }
            } else if (avion.estado === 'EN_PISTA' && r < 0.3) {
                const pista = pistasDemo.find(p => p.numero === avion.pistaAsignada);
                if (pista) { pista.disponible = true; pista.avionActual = null; }
                avion.pistaAsignada = -1;
                avion.estado = 'HACIA_PUERTA';
                avion.estadoDesc = 'Hacia puerta';
                addLog('PISTA_LIBERADA', `Pista ${pista ? pista.numero : '?'} liberada`, avion.id);
            } else if (avion.estado === 'HACIA_PUERTA' && r < 0.5) {
                const puertaLibre = puertasDemo.find(p => p.disponible);
                if (puertaLibre) {
                    puertaLibre.disponible = false;
                    puertaLibre.avionActual = { id: avion.id, nombre: avion.nombre };
                    avion.puertaAsignada = puertaLibre.numero;
                    avion.estado = 'EN_PUERTA';
                    avion.estadoDesc = 'En puerta';
                    addLog('PUERTA_OCUPADA', `Estacionado en puerta ${puertaLibre.numero}`, avion.id);
                }
            } else if (avion.estado === 'EN_PUERTA' && r < 0.25) {
                const puerta = puertasDemo.find(p => p.numero === avion.puertaAsignada);
                if (puerta) { puerta.disponible = true; puerta.avionActual = null; }
                avion.puertaAsignada = -1;
                avion.estado = 'COMPLETADO';
                avion.estadoDesc = 'Completado';
                completadosDemo++;
                addLog('AVION_COMPLETADO', 'Avión completó su ciclo', avion.id);
            }
        });

        // Limpiar completados
        avionesDemo = avionesDemo.filter(a => a.estado !== 'COMPLETADO');

        // Actualizar UI
        renderizarPistas(pistasDemo);
        renderizarPuertas(puertasDemo);
        renderizarAviones(avionesDemo);
        actualizarEstadisticas({
            pistasLibres: pistasDemo.filter(p => p.disponible).length,
            puertasLibres: puertasDemo.filter(p => p.disponible).length,
            esperandoPista: avionesDemo.filter(a => a.estado === 'ESPERANDO_PISTA').length,
            avionesCompletados: completadosDemo,
        });
        actualizarStatusPill(true);
    }

    setInterval(tick, 1200);
}

// ══════════════════════════════════════════════════
//  UTILIDADES
// ══════════════════════════════════════════════════

/**
 * Hace una petición POST a la API del servidor Java.
 *
 * @param {string} endpoint - Ruta del endpoint
 * @param {Object} body     - Cuerpo de la petición (opcional)
 */
async function apiPost(endpoint, body) {
    try {
        const opciones = {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
        };
        if (body) opciones.body = JSON.stringify(body);
        const resp = await fetch(`${API_BASE}${endpoint}`, opciones);
        return await resp.json();
    } catch (err) {
        console.warn('Error en API:', endpoint, err.message);
    }
}

/**
 * Retorna la hora actual en formato HH:mm:ss.mmm
 */
function ahora() {
    return new Date().toTimeString().slice(0, 8) + '.' +
        String(new Date().getMilliseconds()).padStart(3, '0');
}

// Enviar con Enter en el campo de nombre
document.addEventListener('DOMContentLoaded', () => {
    const input = document.getElementById('nombreAvion');
    if (input) {
        input.addEventListener('keydown', e => {
            if (e.key === 'Enter') agregarAvion();
        });
    }
});