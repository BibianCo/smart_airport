/**
 * app.js — Lógica del frontend AeroSim (Spring Boot Edition)
 *
 * Estrategia: Polling cada 500ms a GET /api/estado
 * Spring Boot sirve este archivo desde src/main/resources/static/
 * y expone la API REST en /api/**.
 *
 * El frontend es un SPA (Single Page App) sin frameworks —
 * manipulación directa del DOM para máximo rendimiento.
 */

'use strict';

// ── Config ──────────────────────────────────────
const POLL_MS = 500;
let pollingId = null;
let logCache = [];     // cache local del log para no redibujar todo

// ═══════════════════════════════════════════════
//  ARRANQUE
// ═══════════════════════════════════════════════

document.addEventListener('DOMContentLoaded', () => {
    iniciarPolling();
    renderPlaceholders();
});

function iniciarPolling() {
    pollingId = setInterval(async () => {
        try {
            const res = await fetch('/api/state');
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const data = await res.json();
            actualizarUI(data);
        } catch (e) {
            // Servidor no disponible — no hacer nada visible
        }
    }, POLL_MS);
}

// ═══════════════════════════════════════════════
//  ACTUALIZACIÓN DE UI
// ═══════════════════════════════════════════════

/**
 * Punto de entrada de actualización.
 * Recibe el DTO EstadoAeropuerto mapeado por Spring/Jackson.
 *
 * @param {Object} d - EstadoAeropuerto JSON
 */
function actualizarUI(d) {
    renderPistas(d.runways || []);
    renderPuertas(d.gates || []);
    renderVuelos(d.planes || []);
    renderMetricas(d.statistics || {});
    appendLog(d.log || []);
    actualizarStatus(d.simulationActive);
    actualizarBotones(d.simulationActive);
}

// ─── PISTAS ────────────────────────────────────

/**
 * Renderiza las tarjetas de pistas de aterrizaje.
 * Cada pista muestra si su semáforo binario está adquirido (ocupada) o libre.
 *
 * @param {Array} runways - Lista de PistaDto
 */
function renderPistas(pistas) {
    const g = document.getElementById('pistasGrid');
    g.innerHTML = pistas.map(p => {
        const cls = p.available ? 'libre' : 'ocupada';
        const txt = p.available ? '● LIBRE' : '● OCUPADA';
        const info = p.idPlane ? `${p.idPlane} · ${p.namePlane}` : 'Sin avión';
        return `
      <div class="runway-card ${cls}">
        <div class="rw-num">PISTA ${p.number}</div>
        <div class="rw-status">${txt}</div>
        <div class="rw-avion">${info}</div>
        <div class="rw-usos">Usos: ${p.usesTotal}</div>
        <div class="rw-icon">✈</div>
      </div>`;
    }).join('');
}

// ─── PUERTAS ───────────────────────────────────

/**
 * Renderiza las tarjetas de puertas de embarque.
 * El semáforo de conteo controla cuántas pueden estar ocupadas.
 *
 * @param {Array} puertas - Lista de PuertaDto
 */
function renderPuertas(puertas) {
    const g = document.getElementById('puertasGrid');
    g.innerHTML = puertas.map(p => {
        const cls = p.available ? 'libre' : 'ocupada';
        const txt = p.available ? '○ Libre' : '● Ocupada';
        const info = p.idPlane || '—';
        return `
      <div class="gate-card ${cls}">
        <div class="gate-num">PUERTA ${p.number}</div>
        <div class="gate-status">${txt}</div>
        <div class="gate-avion">${info}</div>
      </div>`;
    }).join('');
}

// ─── VUELOS ACTIVOS ─────────────────────────────

/**
 * Renderiza la lista de aviones activos (hilos en ejecución).
 * Cada fila muestra ID, aerolínea, estado y recursos asignados.
 *
 * @param {Array} aviones - Lista de AvionDto
 */
function renderVuelos(aviones) {
    const lista = document.getElementById('flightList');
    const badge = document.getElementById('badgeVuelos');
    badge.textContent = `${aviones.length} HILO${aviones.length !== 1 ? 'S' : ''}`;

    if (aviones.length === 0) {
        lista.innerHTML = `<div class="empty-msg"><span>Sin vuelos activos.</span></div>`;
        return;
    }

    lista.innerHTML = aviones.map(a => {

        const pista = a.runwayNumber > 0 ? `P${a.runwayNumber}` : '—';
        const puerta = a.gateNumber > 0 ? `G${a.gateNumber}` : '—';
        return `
      <div class="flight-item">
        <span class="fi-id">${a.idPlane}</span>
        <span class="fi-name">${a.namePlane}</span>
        <span class="fi-badge state-${a.statusDesc}">${a.status}</span>
        <span class="fi-res">${pista} / ${puerta}</span>
      </div>`;
    }).join('');
}

// ─── MÉTRICAS ──────────────────────────────────

/**
 * Actualiza los contadores numéricos con animación de escala.
 *
 * @param {Object} s - EstadisticasDto
 */
function renderMetricas(s) {
    setMet('mPistasLibres', s.runwayFree ?? '0');
    setMet('mPuertasLibres', s.gateFree ?? '0');
    setMet('mEsperando', s.waitRunway ?? '0');
    setMet('mCompletados', s.planesCompleted ?? '0');
}

function setMet(id, val) {
    const el = document.getElementById(id);
    if (!el || el.textContent === String(val)) return;
    el.style.transform = 'scale(1.25)';
    el.textContent = val;
    setTimeout(() => el.style.transform = 'scale(1)', 150);
}

// ─── LOG ───────────────────────────────────────

/**
 * Agrega al log solo las entradas nuevas (no redibuja todo).
 * Mantiene auto-scroll al final.
 *
 * @param {Array} eventos - Lista de EventoLogDto más recientes
 */
function appendLog(eventos) {
    if (!eventos || eventos.length === 0) return;

    const scroll = document.getElementById('logScroll');
    const yaHay = logCache.length;
    const nuevos = eventos.slice(yaHay);

    nuevos.forEach(ev => {
        const div = document.createElement('div');
        div.className = `log-entry log-${ev.tipo}`;
        div.innerHTML = `
      <span class="log-ts">${ev.timestamp}</span>
      <span class="log-msg">${ev.avionId ? `[${ev.avionId}] ` : ''}${ev.mensaje}</span>`;
        scroll.appendChild(div);
    });

    logCache = eventos;

    // Auto-scroll
    requestAnimationFrame(() => {
        scroll.scrollTop = scroll.scrollHeight;
    });
}

function limpiarLog() {
    logCache = [];
    document.getElementById('logScroll').innerHTML = '';
}

// ─── STATUS ────────────────────────────────────

function actualizarStatus(activo) {
    const blk = document.getElementById('statusBlock');
    const txt = document.getElementById('statusText');
    if (activo) {
        blk.classList.add('active');
        txt.textContent = 'SIMULACIÓN ACTIVA';
    } else {
        blk.classList.remove('active');
        txt.textContent = 'SISTEMA DETENIDO';
    }
}

function actualizarBotones(activo) {
    document.getElementById('btnIniciar').disabled = activo;
    document.getElementById('btnDetener').disabled = !activo;
}

// ═══════════════════════════════════════════════
//  ACCIONES — llaman a la API REST de Spring Boot
// ═══════════════════════════════════════════════

/**
 * POST /api/iniciar — Activa el SimulacionScheduler de Spring.
 */
async function iniciarSimulacion() {
    await post('/api/start');
}

/**
 * POST /api/detener — Desactiva la generación automática.
 */
async function detenerSimulacion() {
    await post('/api/stop');
}

/**
 * POST /api/reiniciar — Reinicia el AeropuertoService.
 */
async function reiniciarSimulacion() {
    if (!confirm('¿Reiniciar el sistema? Se perderán todos los datos.')) return;
    logCache = [];
    document.getElementById('logScroll').innerHTML = '';
    await post('/api/reset');
}

/**
 * POST /api/avion — Agrega un avión con el nombre del input.
 */
async function agregarAvion() {
    const inp = document.getElementById('nombreAvion');
    const name = inp.value.trim() || 'Anónimo';
    inp.value = '';
    await post('/api/plane', { name: name }); // Enviamos 'name' para que coincida con getName() en Java
}

function setAerolinea(n) {
    document.getElementById('nombreAvion').value = n;
}

/**
 * POST /api/carrera — Lanza la demo de condición de carrera.
 */
async function demostrarCarrera() {
    await post('/api/race_condition');
}

// ═══════════════════════════════════════════════
//  UTILIDADES
// ═══════════════════════════════════════════════

/**
 * Realiza un POST JSON a la API REST de Spring Boot.
 *
 * @param {string} url  - Endpoint relativo (ej: /api/iniciar)
 * @param {Object} body - Body opcional
 */
async function post(url, body) {
    try {
        await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: body ? JSON.stringify(body) : undefined
        });
    } catch (e) {
        console.warn('Error en POST', url, e.message);
    }
}

/**
 * Muestra placeholders visuales mientras conecta.
 */
function renderPlaceholders() {
    // Usar renderPistas en lugar de renderRunways
    renderPistas([1, 2].map(n => ({
        number: n,
        available: true,
        idPlane: null,
        namePlane: null,
        usesTotal: 0
    })));

    // Usar renderPuertas en lugar de renderGates
    renderPuertas([1, 2, 3].map(n => ({
        number: n,
        available: true,
        idPlane: null,
        namePlane: null
    })));
}