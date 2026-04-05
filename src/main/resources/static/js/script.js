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
    // 1. Renderizar lo básico
    renderPistas(d.runways || []);
    renderPuertas(d.gates || []);

    // 2. Renderizar aviones (Si falla aquí es porque el DTO en Java está vacío)
    if (typeof renderVuelos === "function") {
        renderVuelos(d.planes || []);
    }

    renderMetricas(d.statistics || {});

    // 3. Actualizar el log (Asegúrate de tener UNA SOLA función appendLog)
    appendLog(d.log || []);

    actualizarStatus(d.simulationActive);
    actualizarBotones(d.simulationActive);

    // 4. Actualizar semáforo de forma segura
    const sem = document.getElementById('badgeSemforoPuertas');
    if (sem) {
        sem.textContent = `SEMÁFORO DE CONTEO: ${d.gatePermits ?? 0} PERMISOS`;
    }
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

    lista.innerHTML = aviones.map(a => `
        <div class="flight-item">
            <span class="fi-id">${a.idPlane}</span>
            <span class="fi-name">${a.namePlane}</span>
            <span class="fi-badge state-${a.statusDesc}">${a.status}</span>
            <span class="fi-res">P:${a.runwayNumber || '—'} / G:${a.gateNumber || '—'}</span>
        </div>`).join('');
}

// USA UNA SOLA VARIABLE GLOBAL
let sistemaPausadoPorError = false; // Asegúrate de que este nombre coincida en todo el archivo

function appendLog(logs) {
    const container = document.getElementById('logScroll');
    if (!container || !logs || logs.length === 0 || sistemaPausadoPorError) return;
    const isAtBottom = container.scrollHeight - container.clientHeight <= container.scrollTop + 1;
    let htmlAcumulado = "";

    logs.forEach(evento => {
        const time = evento.timestamp ? evento.timestamp.split('.')[0] : "";
        const msg = evento.message || evento.description || "";
        const tipo = (evento.type || "INFO").toUpperCase();

        // DETECCIÓN DEL ERROR CRÍTICO
        if (tipo === "RACE_CONDITION" || tipo === "CRITICAL") {

            htmlAcumulado += `
                <div class="log-entry log-error pulse-animation">
                    <span class="log-ts">${time}</span>
                    <span class="log-msg"><strong>[CONFLICTO]</strong> ${msg}</span>
                </div>`;
        } else {
            htmlAcumulado += `
                <div class="log-entry log-${tipo}">
                    <span class="log-ts">${time}</span>
                    <span class="log-msg">${msg}</span>
                </div>`;
        }
    });

    container.innerHTML = htmlAcumulado;
    container.scrollTop = container.scrollHeight;
    if (isAtBottom) {
        container.scrollTop = container.scrollHeight;
    }
}


function reincorporarSistema() {
    const modal = document.getElementById("modalCarrera");
    if (modal) modal.remove();

    document.querySelector('.app-layout').style.filter = 'none';
    sistemaPausadoPorError = false;

    // Reiniciamos el polling
    iniciarPolling();
}

function getEstadoDetalle(estado) {
    const mapa = {
        'WAITING_FOR_LANDING': { texto: 'EN COLA (LLEGADA)', clase: 'state-espera' },
        'LANDING': { texto: 'ATERRIZANDO', clase: 'state-pista' },
        'TOWARDS_GATE': { texto: 'RODANDO A PUERTA', clase: 'state-rodaje' },
        'AT_GATE': { texto: 'EN ESTACIÓN', clase: 'state-puerta' },
        'WAITING_FOR_TAKEOFF': { texto: 'COLA (DESPEGUE)', clase: 'state-espera' },
        'TAKEOFF': { texto: 'DESPEGANDO', clase: 'state-pista' },
        'COMPLETED': { texto: 'VUELO FINALIZADO', clase: 'state-ok' },
        'LOCKED': { texto: 'DEADLOCK!', clase: 'state-deadlock' } // Para el punto 2
    };
    return mapa[estado] || { texto: estado, clase: '' };
}

// ── LOG Y MÉTRICAS ──



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
    // 🔥 NUNCA se desactiva
    document.getElementById('btnIniciar').disabled = false;

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

async function demostrarCarrera() {
    await fetch('/api/race_condition', { method: 'POST' });
}

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