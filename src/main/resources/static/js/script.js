/**
 * script.js — Lógica de AeroSim unificada
 * Maneja el polling y el renderizado de los componentes del index.html
 */

'use strict';

const POLL_MS = 500;
let pollingId = null;

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
            // Silencio en caso de error
        }
    }, POLL_MS);
}

function actualizarUI(d) {
    renderPistas(d.runways || []);
    renderPuertas(d.gates || []);
    renderVuelos(d.planes || []);
    renderMetricas(d.statistics || {});
    appendLog(d.log || []);
    actualizarStatus(d.simulationActive);
    actualizarBotones(d.simulationActive);
}

// ── RENDERIZADO DE RECURSOS ──

function renderPistas(pistas) {
    const g = document.getElementById('pistasGrid');
    g.innerHTML = pistas.map(p => {
        const cls = p.available ? 'libre' : 'ocupada';
        const txt = p.available ? '● LIBRE' : '● OCUPADA';
        const info = p.idPlane ? `${p.idPlane}` : 'Sin avión';
        return `
            <div class="runway-card ${cls}">
                <div class="rw-num">PISTA ${p.number}</div>
                <div class="rw-status">${txt}</div>
                <div class="rw-avion">${info}</div>
                <div class="rw-usos">Usos: ${p.usesTotal || 0}</div>
            </div>`;
    }).join('');
}

function renderPuertas(puertas) {
    const g = document.getElementById('puertasGrid');
    g.innerHTML = puertas.map(p => {
        const cls = p.available ? 'libre' : 'ocupada';
        const txt = p.available ? '○ Disponible' : '● Asignada';
        return `
            <div class="gate-card ${cls}">
                <div class="gate-num">PUERTA ${p.number}</div>
                <div class="gate-status">${txt}</div>
                <div class="gate-avion">${p.idPlane || '—'}</div>
            </div>`;
    }).join('');
}

// ── RENDERIZADO DE VUELOS (Hilos) ──

function renderVuelos(aviones) {
    const lista = document.getElementById('flightList');
    const badge = document.getElementById('badgeVuelos');
    badge.textContent = `${aviones.length} HILOS`;

    if (aviones.length === 0) {
        lista.innerHTML = `<div class="empty-msg"><span>Sin vuelos activos.</span></div>`;
        return;
    }

    lista.innerHTML = aviones.map(a => {
        // CORRECCIÓN 1: Usar 'statusDesc' para el mapeo de emojis/colores
        const estadoInfo = getEstadoDetalle(a.statusDesc);

        return `
        <div class="flight-item">
            <span class="fi-id">${a.idPlane}</span>
            <span class="fi-name">${a.namePlane}</span>
            <span class="fi-badge ${estadoInfo.clase}">${estadoInfo.texto}</span>
            <span class="fi-res">
                ${a.runwayNumber !== -1 ? 'Pista: ' + a.runwayNumber : ''} 
                ${a.gateNumber !== -1 ? ' Puerta: ' + a.gateNumber : ''}
            </span>
        </div>`;
    }).join('');
}

function getEstadoDetalle(estado) {
    const mapa = {
        'WAITING_FOR_LANDING': { texto: 'ESPERANDO PISTA', clase: 'state-ESPERANDO_PISTA' },
        'LANDING': { texto: 'ATERRIZANDO', clase: 'state-EN_PISTA' },
        'TOWARDS_GATE': { texto: 'RODANDO', clase: 'state-HACIA_PUERTA' },
        'AT_GATE': { texto: 'EN PUERTA', clase: 'state-EN_PUERTA' },
        'WAITING_FOR_TAKEOFF': { texto: 'PREPARANDO SALIDA', clase: 'state-ESPERANDO_PISTA' },
        'TAKEOFF': { texto: 'DESPEGANDO', clase: 'state-EN_PISTA' },
        'COMPLETED': { texto: 'FINALIZADO', clase: 'state-COMPLETADO' }
    };
    return mapa[estado] || { texto: estado, clase: '' };
}

// ── LOG Y MÉTRICAS ──

function appendLog(logs) {
    const container = document.getElementById('logScroll');
    if (!logs || logs.length === 0) return;

    container.innerHTML = logs.map(evento => {
        const time = evento.timestamp ? evento.timestamp.split('.')[0] : "";
        const msg = evento.message || "Evento de sistema";
        const tipo = evento.type || "INFO";
        const avion = evento.idPlane ? `[${evento.idPlane}] ` : "";

        return `
            <div class="log-entry log-${tipo.toUpperCase()}">
                <span class="log-ts">${time}</span>
                <span class="log-msg"><strong>${avion}</strong>${msg}</span>
            </div>
        `;
    }).join('');
    container.scrollTop = container.scrollHeight;
}

function renderMetricas(s) {
    document.getElementById('mPistasLibres').textContent = s.runwayFree ?? '0';
    document.getElementById('mPuertasLibres').textContent = s.gateFree ?? '0';
    document.getElementById('mEsperando').textContent = s.waitRunway ?? '0';
    document.getElementById('mCompletados').textContent = s.planesCompleted ?? '0';
}

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

// ── ACCIONES API ──

async function iniciarSimulacion() { await post('/api/start'); }
async function detenerSimulacion() { await post('/api/stop'); }
async function reiniciarSimulacion() {
    if (confirm('¿Reiniciar aeropuerto?')) {
        await post('/api/reset');
        limpiarLog();
    }
}

async function agregarAvion() {
    const inp = document.getElementById('nombreAvion');
    const name = inp.value.trim() || 'Avianca';
    inp.value = '';
    await post('/api/plane', { name: name });
}

async function demostrarCarrera() { await post('/api/race_condition'); }
function setAerolinea(n) { document.getElementById('nombreAvion').value = n; }
function limpiarLog() { document.getElementById('logScroll').innerHTML = ''; }

async function post(url, body) {
    try {
        await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: body ? JSON.stringify(body) : undefined
        });
    } catch (e) { console.error("Error API:", e); }
}

function renderPlaceholders() {
    renderPistas([{ number: 1, available: true }, { number: 2, available: true }]);
    renderPuertas([{ number: 1, available: true }, { number: 2, available: true }, { number: 3, available: true }]);
}