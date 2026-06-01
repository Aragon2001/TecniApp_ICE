/* eslint-disable */

const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onValueCreated } = require("firebase-functions/v2/database");
const { defineSecret } = require("firebase-functions/params");

require("firebase-admin/database");

const admin = require("firebase-admin");
const axios = require("axios");
const nodemailer = require("nodemailer");

const MAIL_USER = defineSecret("MAIL_USER");
const MAIL_PASS = defineSecret("MAIL_PASS");

const DB_AVERIAS_URL = "https://tecniapp-ice-averias.firebaseio.com";
const DB_USERS_URL = "https://tecniapp-ice-user.firebaseio.com";

admin.initializeApp();

const averiasApp = admin.initializeApp(
  { databaseURL: DB_AVERIAS_URL },
  "averias"
);

const usersApp = admin.initializeApp(
  { databaseURL: DB_USERS_URL },
  "users"
);

const dbAverias = admin.database(averiasApp);
const dbUsers = admin.database(usersApp);

let cachedTransporter = null;
let cachedAuthKey = null;

function getTransporter(user, pass) {
  const key = `${user}:${pass}`;
  if (cachedTransporter && cachedAuthKey === key) return cachedTransporter;
  cachedAuthKey = key;
  cachedTransporter = nodemailer.createTransport({
    service: "gmail",
    auth: { user, pass },
    pool: true,
    maxConnections: 1,
    maxMessages: 50,
  });
  return cachedTransporter;
}

async function sendMail({ to, subject, html, user, pass, attachments }) {
  const transporter = getTransporter(user, pass);
  const t = Date.now();
  const info = await transporter.sendMail({
    from: `"TecniApp ICE" <${user}>`,
    to,
    subject,
    html,
    attachments,
  });
  console.log("sendMail ms:", Date.now() - t, "messageId:", info?.messageId);
  return info;
}

/* =========================================================
   HELPERS
   ========================================================= */

function toNumberOrNull(v) {
  if (v === null || v === undefined) return null;
  const s = String(v).trim().replace(",", ".");
  if (!s) return null;
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
}

function emailKey(email) {
  return String(email || "")
    .trim()
    .toLowerCase()
    .replace(/\./g, ",")
    .replace(/#/g, "_")
    .replace(/\$/g, "_")
    .replace(/\[/g, "_")
    .replace(/\]/g, "_");
}

function norm(v) {
  return String(v || "").trim().toUpperCase();
}

function normTag(x) {
  return String(x || "")
    .trim()
    .toUpperCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^A-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

function toStringArray(v) {
  if (!v) return [];
  if (Array.isArray(v)) return v;
  return [String(v)];
}

function toBooleanOrDefault(v, fallback = true) {
  if (v === null || v === undefined) return fallback;
  if (typeof v === "boolean") return v;
  const s = String(v).trim().toLowerCase();
  if (!s) return fallback;
  if (["true", "1", "yes", "si", "sí", "on"].includes(s)) return true;
  if (["false", "0", "no", "off"].includes(s)) return false;
  return fallback;
}

function extractUserTokens(user) {
  const set = new Set();
  const add = (v) => {
    const t = String(v || "").trim();
    if (t) set.add(t);
  };
  add(user?.fcmToken);
  add(user?.fcm?.currentToken);
  if (Array.isArray(user?.fcmTokens)) user.fcmTokens.forEach(add);
  if (user?.fcm?.tokens && typeof user.fcm.tokens === "object") {
    Object.values(user.fcm.tokens).forEach(add);
  }
  return Array.from(set);
}

function envelopePayload(respData) {
  if (!respData) return [];
  return (
    respData.respuesta ||
    respData.data ||
    respData.items ||
    respData.averias ||
    respData.Averias ||
    []
  );
}

function normalizeEstado(estado) {
  if (!estado) return "";
  const e = estado.toString().trim().toUpperCase();
  if (e === "RESUELTO" || e === "RESUELTA" || e === "SOLUCIONADO") return "RESUELTA";
  if (
    e === "NUEVO" ||
    e === "PENDIENTE" ||
    e === "ACEPTADO" ||
    e === "EN DESPLAZAMIENTO" ||
    e === "EN ATENCION" ||
    e === "EN ATENCIÓN"
  ) return "PENDIENTE";
  return "PENDIENTE";
}

function isEstadoAppResuelta(estado) {
  if (!estado) return false;
  return estado.toString().trim().toUpperCase().includes("RESUEL");
}

/**
 * FIX: excluye el campo `estado` de la comparación cuando no viene
 * explícitamente en el payload, evitando falsos positivos de cambio.
 */
function hasMeaningfulChanges(existing, payload) {
  const fields = [
    "estadoClor",
    "observacionesClor",
    "causaClor",
    "agencia",
    "nombreAgencia",
    "region",
    "nise",
    "clientesAfectados",
    "lat",
    "lng",
    "fechaInicioMillis",
  ];

  // Solo comparar `estado` si el payload lo trae explícitamente
  if ("estado" in payload) {
    fields.push("estado");
  }

  return fields.some((field) => {
    const prev = existing?.[field];
    const next = payload?.[field];
    if (prev === next) return false;
    if (
      (prev === null || prev === undefined) &&
      (next === null || next === undefined)
    ) return false;
    return String(prev ?? "") !== String(next ?? "");
  });
}

/**
 * FIX PRINCIPAL: ahora el filtro tiene 3 niveles de prioridad:
 *
 * 1. notificationAgencies (lista explícita de agencias) → filtra por agencia exacta.
 * 2. region del usuario → filtra todas las averías cuya region coincida.
 * 3. agenciaId / agencia (fallback legacy) → agencia exacta.
 * 4. Sin filtros → recibe todo.
 *
 * Esto resuelve el problema de Guácimo: si el usuario tiene region="HUETAR_ATLANTICA",
 * recibirá averías de Limón, Siquirres, Batán, Guácimo, etc.
 */
function userWantsThisAveria(user, averiaAgencyTag, averiaRegionTag) {
  // Nivel 1: lista explícita de agencias
  const agencyList = toStringArray(user?.notificationAgencies)
    .map(normTag)
    .filter(Boolean);

  if (agencyList.length > 0) {
    return agencyList.includes(averiaAgencyTag);
  }

  // Nivel 2: región del usuario
  const userRegionTag = normTag(user?.region || user?.regionId || "");
  if (userRegionTag) {
    return userRegionTag === averiaRegionTag;
  }

  // Nivel 3: agencia exacta (fallback legacy)
  const fallbackAgency = normTag(user?.agenciaId || user?.agencia || "");
  if (fallbackAgency) {
    return fallbackAgency === averiaAgencyTag;
  }

  // Sin filtros: recibe todo
  return true;
}

/**
 * FIX NOTIFICACIONES:
 *
 * Problema original: el snapshot se llenaba en el primer ciclo con todas
 * las averías existentes. En ciclos posteriores ninguna era "nueva" según
 * el snapshot, así que shouldNotify nunca retornaba true para averías nuevas
 * que aparecían mientras el cron corría.
 *
 * Solución: si la avería no existe en RTDB (`existingIsNew`), es genuinamente
 * nueva y debe notificarse. Si ya existe en RTDB pero nunca fue notificada
 * (no tiene `lastNotifiedAt`), también se notifica una vez.
 *
 * Para cambios a RESUELTA la lógica sigue igual: prevEstado != RESUELTA
 * y newEstado == RESUELTA → notificar.
 */
function shouldNotify(prevEstado, newEstado, existingIsNew, existingHasBeenNotified) {
  const prev = normalizeEstado(prevEstado);
  const curr = normalizeEstado(newEstado);

  // Avería que nunca existió en RTDB → nueva real
  if (existingIsNew && curr === "PENDIENTE") return true;

  // Avería que existe en RTDB pero nunca fue notificada → notificar ahora
  if (!existingIsNew && !existingHasBeenNotified && curr === "PENDIENTE") return true;

  // Cambio a resuelta
  if (!existingIsNew && prev !== curr && curr === "RESUELTA") return true;

  return false;
}

function generateCode() {
  return Math.floor(100000 + Math.random() * 900000).toString();
}

function extractEmail(data) {
  if (typeof data === "string") return data;
  if (typeof data?.data === "string") return data.data;
  if (typeof data?.data?.data === "string") return data.data.data;
  const direct = data?.email || data?.correo || data?.mail || data?.userEmail;
  if (direct) return direct;
  const nested =
    data?.data?.email ||
    data?.data?.correo ||
    data?.data?.mail ||
    data?.data?.userEmail;
  return nested || "";
}

/* =========================================================
   HTML TEMPLATES
   ========================================================= */

function verificationEmailHtml(code) {
  return `
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Código de verificación – TecniApp ICE</title>
  <style>
    @import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600&family=DM+Mono:wght@500&display=swap');
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background-color: #0a0f1e; font-family: 'DM Sans', -apple-system, BlinkMacSystemFont, sans-serif; }
    .email-wrapper { background: #0a0f1e; padding: 48px 16px; }
    .email-card { max-width: 560px; margin: 0 auto; background: #0d1425; border-radius: 20px; border: 1px solid rgba(255,255,255,0.06); overflow: hidden; }
    .hero { background: linear-gradient(135deg, #003e8a 0%, #0052b3 60%, #0070e0 100%); padding: 40px 40px 36px; position: relative; overflow: hidden; }
    .hero::before { content: ''; position: absolute; top: -60px; right: -60px; width: 200px; height: 200px; border-radius: 50%; background: rgba(255,255,255,0.04); }
    .hero::after { content: ''; position: absolute; bottom: -40px; left: -40px; width: 140px; height: 140px; border-radius: 50%; background: rgba(255,255,255,0.03); }
    .hero-inner { position: relative; z-index: 1; }
    .brand-row { display: flex; align-items: center; gap: 14px; margin-bottom: 28px; }
    .brand-logo { width: 44px; height: 44px; background: rgba(255,255,255,0.15); border-radius: 12px; display: flex; align-items: center; justify-content: center; border: 1px solid rgba(255,255,255,0.2); }
    .brand-logo img { width: 28px; height: 28px; display: block; }
    .brand-name { font-size: 16px; font-weight: 600; color: #ffffff; letter-spacing: -0.02em; }
    .brand-sub { font-size: 12px; color: rgba(255,255,255,0.55); margin-top: 1px; font-weight: 400; }
    .hero-title { font-size: 26px; font-weight: 600; color: #ffffff; letter-spacing: -0.03em; line-height: 1.25; }
    .hero-title span { color: rgba(255,255,255,0.6); font-weight: 400; }
    .body-section { padding: 36px 40px; }
    .intro-text { font-size: 15px; color: rgba(255,255,255,0.55); line-height: 1.65; margin-bottom: 32px; }
    .intro-text strong { color: rgba(255,255,255,0.85); font-weight: 500; }
    .code-block { background: #111827; border: 1px solid rgba(255,255,255,0.08); border-radius: 14px; padding: 28px 24px; margin-bottom: 24px; text-align: center; }
    .code-label { font-size: 11px; font-weight: 600; letter-spacing: 0.1em; text-transform: uppercase; color: rgba(255,255,255,0.3); margin-bottom: 16px; }
    .code-digits { font-family: 'DM Mono', 'Courier New', monospace; font-size: 44px; font-weight: 500; letter-spacing: 0.22em; color: #4d9fff; display: block; line-height: 1; margin-bottom: 18px; }
    .code-timer { display: inline-flex; align-items: center; gap: 6px; background: rgba(255,171,0,0.1); border: 1px solid rgba(255,171,0,0.2); border-radius: 20px; padding: 5px 14px; font-size: 12px; color: #ffc340; font-weight: 500; }
    .code-timer svg { width: 13px; height: 13px; flex-shrink: 0; }
    .info-row { display: flex; gap: 10px; margin-bottom: 30px; }
    .info-pill { flex: 1; background: #111827; border: 1px solid rgba(255,255,255,0.06); border-radius: 10px; padding: 14px 16px; display: flex; align-items: flex-start; gap: 10px; }
    .pill-icon { width: 30px; height: 30px; border-radius: 8px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
    .pill-icon svg { width: 16px; height: 16px; }
    .pill-icon.blue { background: rgba(77,159,255,0.1); }
    .pill-icon.blue svg { color: #4d9fff; }
    .pill-icon.red { background: rgba(255,80,80,0.1); }
    .pill-icon.red svg { color: #ff5050; }
    .pill-title { font-size: 12px; color: rgba(255,255,255,0.8); font-weight: 500; line-height: 1.3; }
    .pill-desc { font-size: 11px; color: rgba(255,255,255,0.35); margin-top: 2px; line-height: 1.4; }
    .divider { height: 1px; background: rgba(255,255,255,0.05); margin: 0 40px; }
    .footer { padding: 24px 40px 28px; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
    .footer-brand { font-size: 12px; color: rgba(255,255,255,0.2); font-weight: 500; }
    .footer-meta { font-size: 11px; color: rgba(255,255,255,0.15); text-align: right; line-height: 1.5; }
    @media (max-width: 520px) {
      .hero, .body-section { padding: 28px 24px; }
      .footer { padding: 20px 24px 24px; flex-direction: column; text-align: center; }
      .footer-meta { text-align: center; }
      .divider { margin: 0 24px; }
      .info-row { flex-direction: column; }
      .hero-title { font-size: 22px; }
      .code-digits { font-size: 36px; }
    }
  </style>
</head>
<body>
  <div class="email-wrapper">
    <div class="email-card">
      <div class="hero">
        <div class="hero-inner">
          <div class="brand-row">
            <div class="brand-logo"><img src="https://i.imgur.com/tGUD2Vo.png" alt="ICE"></div>
            <div>
              <div class="brand-name">TecniApp ICE</div>
              <div class="brand-sub">Portal técnico eléctrico</div>
            </div>
          </div>
          <div class="hero-title">Verificación<br><span>de identidad</span></div>
        </div>
      </div>
      <div class="body-section">
        <p class="intro-text">Ingresa este código en <strong>TecniApp ICE</strong> para confirmar tu dirección de correo y activar tu cuenta. El código es de uso único.</p>
        <div class="code-block">
          <div class="code-label">Tu código de acceso</div>
          <span class="code-digits">${code}</span>
          <div class="code-timer">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            Válido por 5 minutos
          </div>
        </div>
        <div class="info-row">
          <div class="info-pill">
            <div class="pill-icon blue">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            </div>
            <div>
              <div class="pill-title">Código de un solo uso</div>
              <div class="pill-desc">Expira automáticamente tras el primer ingreso</div>
            </div>
          </div>
          <div class="info-pill">
            <div class="pill-icon red">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
            </div>
            <div>
              <div class="pill-title">No compartas este código</div>
              <div class="pill-desc">ICE nunca te lo solicitará por llamada</div>
            </div>
          </div>
        </div>
        <p style="font-size: 13px; color: rgba(255,255,255,0.25); line-height: 1.6;">Si no solicitaste este código, puedes ignorar este mensaje. Tu cuenta permanece segura.</p>
      </div>
      <div class="divider"></div>
      <div class="footer">
        <div class="footer-brand">© 2025 Arasoft Solutions</div>
        <div class="footer-meta">Correo generado automáticamente<br>TecniApp ICE · Sistema de averías</div>
      </div>
    </div>
  </div>
</body>
</html>
  `.trim();
}

function reportEmailHtml({ reportName, downloadUrl, subtitle }) {
  const safeName = String(reportName || "Reporte").trim();
  const safeSubtitle = String(subtitle || "").trim();
  const hasDownloadUrl = Boolean(downloadUrl);

  return `
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${safeName} – TecniApp ICE</title>
  <style>
    @import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600&display=swap');
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background-color: #0a0f1e; font-family: 'DM Sans', -apple-system, BlinkMacSystemFont, sans-serif; }
    .email-wrapper { background: #0a0f1e; padding: 48px 16px; }
    .email-card { max-width: 560px; margin: 0 auto; background: #0d1425; border-radius: 20px; border: 1px solid rgba(255,255,255,0.06); overflow: hidden; }
    .hero { background: linear-gradient(135deg, #06402b 0%, #0a5c3c 60%, #0d7a50 100%); padding: 40px 40px 36px; position: relative; overflow: hidden; }
    .hero::before { content: ''; position: absolute; top: -50px; right: -50px; width: 180px; height: 180px; border-radius: 50%; background: rgba(255,255,255,0.03); }
    .hero::after { content: ''; position: absolute; bottom: -30px; left: 40px; width: 100px; height: 100px; border-radius: 50%; background: rgba(255,255,255,0.03); }
    .hero-inner { position: relative; z-index: 1; }
    .brand-row { display: flex; align-items: center; gap: 14px; margin-bottom: 28px; }
    .brand-logo { width: 44px; height: 44px; background: rgba(255,255,255,0.12); border-radius: 12px; display: flex; align-items: center; justify-content: center; border: 1px solid rgba(255,255,255,0.18); }
    .brand-logo img { width: 28px; height: 28px; display: block; }
    .brand-name { font-size: 16px; font-weight: 600; color: #ffffff; letter-spacing: -0.02em; }
    .brand-sub { font-size: 12px; color: rgba(255,255,255,0.5); margin-top: 1px; }
    .report-chip { display: inline-flex; align-items: center; gap: 6px; background: rgba(255,255,255,0.1); border: 1px solid rgba(255,255,255,0.15); border-radius: 20px; padding: 4px 12px 4px 8px; font-size: 11px; font-weight: 600; letter-spacing: 0.06em; text-transform: uppercase; color: rgba(255,255,255,0.7); margin-bottom: 12px; }
    .report-chip svg { width: 12px; height: 12px; flex-shrink: 0; }
    .hero-title { font-size: 24px; font-weight: 600; color: #ffffff; letter-spacing: -0.03em; line-height: 1.2; }
    .hero-subtitle { font-size: 14px; color: rgba(255,255,255,0.5); margin-top: 8px; line-height: 1.5; }
    .body-section { padding: 36px 40px; }
    .intro-text { font-size: 15px; color: rgba(255,255,255,0.5); line-height: 1.65; margin-bottom: 28px; }
    .intro-text strong { color: rgba(255,255,255,0.8); font-weight: 500; }
    .cta-wrapper { text-align: center; margin: 4px 0 28px; }
    .cta-btn { display: inline-flex; align-items: center; gap: 10px; background: #0d7a50; color: #ffffff; text-decoration: none; padding: 14px 28px; border-radius: 12px; font-size: 15px; font-weight: 600; letter-spacing: -0.01em; border: 1px solid rgba(255,255,255,0.1); }
    .cta-btn svg { width: 18px; height: 18px; flex-shrink: 0; }
    .attachment-pill { display: flex; align-items: center; gap: 14px; background: #111827; border: 1px solid rgba(255,255,255,0.07); border-radius: 12px; padding: 16px 20px; margin-bottom: 28px; }
    .att-icon { width: 40px; height: 40px; background: rgba(13,122,80,0.15); border-radius: 10px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
    .att-icon svg { width: 20px; height: 20px; color: #22c77e; }
    .att-name { font-size: 14px; font-weight: 500; color: rgba(255,255,255,0.8); }
    .att-sub { font-size: 12px; color: rgba(255,255,255,0.3); margin-top: 2px; }
    .url-fallback { background: #0d1120; border: 1px solid rgba(255,255,255,0.05); border-radius: 10px; padding: 14px 16px; margin-bottom: 24px; }
    .url-label { font-size: 11px; font-weight: 600; letter-spacing: 0.08em; text-transform: uppercase; color: rgba(255,255,255,0.2); margin-bottom: 6px; }
    .url-text { font-size: 12px; color: rgba(77,159,255,0.7); word-break: break-all; line-height: 1.5; }
    .divider { height: 1px; background: rgba(255,255,255,0.05); margin: 0 40px; }
    .footer { padding: 24px 40px 28px; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
    .footer-brand { font-size: 12px; color: rgba(255,255,255,0.2); font-weight: 500; }
    .footer-meta { font-size: 11px; color: rgba(255,255,255,0.15); text-align: right; line-height: 1.5; }
    @media (max-width: 520px) {
      .hero, .body-section { padding: 28px 24px; }
      .footer { padding: 20px 24px 24px; flex-direction: column; text-align: center; }
      .footer-meta { text-align: center; }
      .divider { margin: 0 24px; }
      .hero-title { font-size: 20px; }
    }
  </style>
</head>
<body>
  <div class="email-wrapper">
    <div class="email-card">
      <div class="hero">
        <div class="hero-inner">
          <div class="brand-row">
            <div class="brand-logo"><img src="https://i.imgur.com/tGUD2Vo.png" alt="ICE"></div>
            <div>
              <div class="brand-name">TecniApp ICE</div>
              <div class="brand-sub">Portal técnico eléctrico</div>
            </div>
          </div>
          <div class="report-chip">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
            Reporte disponible
          </div>
          <div class="hero-title">${safeName}</div>
          ${safeSubtitle ? `<div class="hero-subtitle">${safeSubtitle}</div>` : ""}
        </div>
      </div>
      <div class="body-section">
        <p class="intro-text">Tu reporte <strong>${safeName}</strong> ha sido generado exitosamente y está listo para su revisión. ${hasDownloadUrl ? "Puedes descargarlo usando el botón a continuación." : "Lo encontrarás adjunto en este correo."}</p>
        ${hasDownloadUrl ? `
        <div class="cta-wrapper">
          <a href="${downloadUrl}" class="cta-btn">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
            Descargar reporte
          </a>
        </div>
        <div class="url-fallback">
          <div class="url-label">Enlace alternativo</div>
          <div class="url-text">${downloadUrl}</div>
        </div>
        ` : `
        <div class="attachment-pill">
          <div class="att-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
          </div>
          <div>
            <div class="att-name">${safeName}</div>
            <div class="att-sub">Archivo adjunto · Excel (.xlsx)</div>
          </div>
        </div>
        `}
        <p style="font-size: 13px; color: rgba(255,255,255,0.2); line-height: 1.6;">Este reporte fue generado automáticamente por TecniApp ICE. Si tienes dudas, contacta a soporte técnico.</p>
      </div>
      <div class="divider"></div>
      <div class="footer">
        <div class="footer-brand">© 2025 Arasoft Solutions</div>
        <div class="footer-meta">Correo generado automáticamente<br>TecniApp ICE · Sistema de averías</div>
      </div>
    </div>
  </div>
</body>
</html>
  `.trim();
}

/* =========================================================
   FECHAS ICE -> MILLIS
   ========================================================= */

function isNoRegistra(v) {
  const s = String(v ?? "").trim().toLowerCase();
  return !s || s === "no registra" || s === "pendiente de verificar";
}

function toMillisCR(v) {
  if (v === null || v === undefined) return null;
  const s = String(v).trim();
  if (isNoRegistra(s)) return null;
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(s)) {
    const ms = Date.parse(`${s}:00-06:00`);
    return Number.isFinite(ms) ? ms : null;
  }
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(s)) {
    const ms = Date.parse(`${s}-06:00`);
    return Number.isFinite(ms) ? ms : null;
  }
  const ms = Date.parse(s);
  return Number.isFinite(ms) ? ms : null;
}

/* =========================================================
   HELPER: enviar FCM en chunks de 500 (límite de Firebase)
   ========================================================= */

async function sendFcmChunked(tokens, data, tokenToUid, dbU) {
  const CHUNK = 500;
  let totalOk = 0;
  let totalFail = 0;

  for (let i = 0; i < tokens.length; i += CHUNK) {
    const chunk = tokens.slice(i, i + CHUNK);

    const res = await admin.messaging().sendEachForMulticast({ tokens: chunk, data });
    totalOk += res.successCount;
    totalFail += res.failureCount;

    const tokensToDelete = [];
    res.responses.forEach((r, idx) => {
      if (r.success) return;
      const code = r.error?.code || "";
      if (
        code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-registration-token"
      ) {
        tokensToDelete.push(chunk[idx]);
      }
    });

    for (const badToken of tokensToDelete) {
      const uid = tokenToUid.get(badToken);
      if (!uid) continue;
      console.log(`Eliminando token inválido uid=${uid}`);
      const userRef = dbU.ref("usuarios").child(uid);
      const key = badToken
        .replace(/\./g, "_").replace(/#/g, "_").replace(/\$/g, "_")
        .replace(/\[/g, "_").replace(/\]/g, "_").replace(/\//g, "_");
      await userRef.update({
        fcmToken: null,
        "fcm/currentToken": null,
        [`fcm/tokens/${key}`]: null,
      });
    }
  }

  return { totalOk, totalFail };
}

/* =========================================================
   CRON: AVERÍAS + FCM
   ========================================================= */

const ICE_URL = "https://agenciaelectricidad.cn.ice.go.cr/api/AveriasAranda/";

exports.syncAveriasYNotificar = onSchedule(
  {
    schedule: "every 5 minutes",
    timeZone: "America/Costa_Rica",
  },
  async () => {
    const executionStartedAt = Date.now();
    let totalCasesRead = 0;
    let totalCasesUpdated = 0;
    let totalCasesSkipped = 0;

    try {
      const dbA = dbAverias;
      const dbU = dbUsers;

      // 1. Consultar API ICE
      const resp = await axios.get(ICE_URL, { timeout: 20000 });
      const averias = envelopePayload(resp.data);
      console.log("Averías recibidas:", averias.length);

      // 2. Leer snapshot previo (para detectar cambios de estado)
      const snapRef = dbA.ref("averias_last_snapshot");
      const snap = await snapRef.get();
      const last = snap.exists() ? snap.val() : {};
      const next = { ...last };

      // FIX N+1: leer TODAS las averías existentes en un solo round-trip
      const allExistingSnap = await dbA.ref("averias").get();
      const allExisting = allExistingSnap.val() || {};

      const toNotify = [];
      const now = Date.now();

      for (const a of averias) {
        totalCasesRead += 1;

        const caseId = String(a.noCaso || "").trim();
        if (!caseId) continue;

        const estadoRaw = String(a.estado || "").trim();
        const estado = normalizeEstado(estadoRaw) || "PENDIENTE";

        const agencia = String(a.agencia || "").trim();
        const nombreAgencia = String(a.nombreAgencia || agencia || "").trim();
        const region = String(a.region || "").trim();

        const agenciaTag = normTag(nombreAgencia || agencia);
        // FIX REGIÓN: también calculamos el tag de región para filtrar por ella
        const regionTag = normTag(region);

        const prev = last[caseId];
        const prevEstado = prev ? prev.estado : "";

        const existing = allExisting[caseId] || {};
        const existingIsNew = !allExisting[caseId];
        // Una avería "nunca notificada" es la que existe pero no tiene lastNotifiedAt
        const existingHasBeenNotified = Boolean(existing.lastNotifiedAt);

        const bloquearPromocionAClorResuelta =
          isEstadoAppResuelta(existing.estado) &&
          normalizeEstado(existing.estadoClor) !== "RESUELTA" &&
          estado === "RESUELTA";

        const estadoClorEfectivo = bloquearPromocionAClorResuelta
          ? normalizeEstado(existing.estadoClor) || "PENDIENTE"
          : estado;

        next[caseId] = {
          estado: estadoClorEfectivo,
          agenciaTag,
          regionTag,
          ts: now,
        };

        const payload = {
          caseId,
          agencia,
          nombreAgencia,
          region,
          nise: String(a.nise || ""),
          clientesAfectados: String(a.clientesAfectados || ""),
          lat: toNumberOrNull(a.latitud),
          lng: toNumberOrNull(a.longitud),
          agenciaTag,
          regionTag,
          fechaInicioMillis: toMillisCR(a.fechaInicio) ?? 0,
          lastUpdated: now,
          estadoClor: bloquearPromocionAClorResuelta
            ? existing.estadoClor ?? ""
            : estadoClorEfectivo,
          observacionesClor: bloquearPromocionAClorResuelta
            ? existing.observacionesClor ?? ""
            : String(a.observaciones || ""),
          causaClor: bloquearPromocionAClorResuelta
            ? existing.causaClor ?? ""
            : String(a.causa || ""),
          source: "clor_sync",
        };

        if (estadoClorEfectivo === "RESUELTA" && !bloquearPromocionAClorResuelta) {
          payload.estado = "Resuelta";
        }

        if (hasMeaningfulChanges(existing, payload)) {
          await dbA.ref("averias").child(caseId).update(payload);
          totalCasesUpdated += 1;
        } else {
          totalCasesSkipped += 1;
        }

        // FIX shouldNotify: ahora pasa existingIsNew y existingHasBeenNotified
        if (!shouldNotify(prevEstado, estadoClorEfectivo, existingIsNew, existingHasBeenNotified)) {
          continue;
        }

        const data = {
          caseId: String(caseId),
          estadoClor: String(estadoClorEfectivo),
          estado: String(estadoClorEfectivo),
          agencia: String(agencia || ""),
          nombreAgencia: String(nombreAgencia || ""),
          region: String(region || ""),
          descripcion: String(a.observaciones || ""),
          localizacion: "",
          nise: String(a.nise || ""),
          causa: String(a.causa || ""),
          clientesAfectados: String(a.clientesAfectados || ""),
          lat: String(a.latitud || ""),
          lng: String(a.longitud || ""),
          agenciaTag: String(agenciaTag),
          regionTag: String(regionTag),
          fechaInicioMillis: String(toMillisCR(a.fechaInicio) ?? 0),
          lastUpdated: String(now),
        };

        toNotify.push({ caseId, agenciaTag, regionTag, data });
      }

      // 3. Guardar snapshot actualizado
      await snapRef.set(next);

      if (!toNotify.length) {
        console.log("Sin averías nuevas/cambio a resuelta para notificar");
        return;
      }

      // 4. Leer usuarios con tokens FCM
      const usersSnap = await dbU.ref("usuarios").get();
      const users = usersSnap.exists() ? usersSnap.val() : {};

      for (const item of toNotify) {
        const recipients = [];

        for (const uid of Object.keys(users)) {
          const u = users[uid];
          if (!u) continue;
          if (!toBooleanOrDefault(u.notificationEnabled, true)) continue;

          const userTokens = extractUserTokens(u);
          if (!userTokens.length) continue;

          // FIX: pasamos regionTag a userWantsThisAveria
          if (userWantsThisAveria(u, item.agenciaTag, item.regionTag)) {
            userTokens.forEach((token) => recipients.push({ uid, token }));
          }
        }

        if (!recipients.length) {
          console.log(`Sin tokens para caseId=${item.caseId} agenciaTag=${item.agenciaTag} regionTag=${item.regionTag}`);
          continue;
        }

        const tokens = recipients.map((r) => r.token);
        const tokenToUid = new Map(recipients.map((r) => [r.token, r.uid]));

        // FIX: envío paginado en chunks de 500
        const { totalOk, totalFail } = await sendFcmChunked(tokens, item.data, tokenToUid, dbU);
        console.log(`FCM enviado caseId=${item.caseId} ok=${totalOk} fail=${totalFail}`);

        // Marcar la avería como notificada para evitar re-notificaciones
        await dbA.ref("averias").child(item.caseId).update({
          lastNotifiedAt: now,
        });
      }
    } catch (e) {
      console.error("syncAveriasYNotificar ERROR:", e);
    } finally {
      console.log({
        totalCasesRead,
        totalCasesUpdated,
        totalCasesSkipped,
        executionDurationMs: Date.now() - executionStartedAt,
      });
    }
  }
);

/* =========================================================
   CALLABLE: ENVIAR CÓDIGO DE VERIFICACIÓN
   ========================================================= */

exports.sendVerificationCode = onCall(
  {
    region: "us-central1",
    secrets: [MAIL_USER, MAIL_PASS],
  },
  async (request) => {
    try {
      const data = request.data;
      const email = String(extractEmail(data)).trim();

      if (!email) throw new HttpsError("invalid-argument", "Email requerido");

      const code = generateCode();
      const now = Date.now();
      const expiresAt = now + 5 * 60 * 1000;
      const key = emailKey(email);

      await dbUsers.ref("verificationCodes").child(key).set({
        code,
        createdAt: now,
        expiresAt,
      });

      await sendMail({
        to: email,
        subject: "Código de verificación – TecniApp ICE",
        html: verificationEmailHtml(code),
        user: MAIL_USER.value(),
        pass: MAIL_PASS.value(),
      });

      return { success: true };
    } catch (e) {
      console.error("sendVerificationCode ERROR:", e);
      if (e instanceof HttpsError) throw e;
      throw new HttpsError("internal", e?.message || "Error interno");
    }
  }
);

/* =========================================================
   CALLABLE: ENVIAR REPORTE POR CORREO
   ========================================================= */

exports.sendReport = onCall(
  {
    region: "us-central1",
    secrets: [MAIL_USER, MAIL_PASS],
  },
  async (request) => {
    try {
      const data = request.data;
      const email = String(extractEmail(data)).trim();
      const reportName = String(data?.reportName || "Reporte").trim();
      const downloadUrl = String(data?.downloadUrl || "").trim();
      const subtitle = String(data?.subtitle || "").trim();
      const fileBase64 = String(data?.fileBase64 || "").trim();
      const fileName = String(data?.fileName || "reporte.xlsx").trim();

      if (!email || (!downloadUrl && !fileBase64)) {
        throw new HttpsError("invalid-argument", "Datos incompletos");
      }

      const html = reportEmailHtml({ reportName, downloadUrl, subtitle });
      const attachments = fileBase64
        ? [{ filename: fileName, content: Buffer.from(fileBase64, "base64") }]
        : undefined;

      await sendMail({
        to: email,
        subject: `Reporte ${reportName} – TecniApp ICE`,
        html,
        user: MAIL_USER.value(),
        pass: MAIL_PASS.value(),
        attachments,
      });

      return { success: true };
    } catch (e) {
      console.error("sendReport ERROR:", e);
      if (e instanceof HttpsError) throw e;
      throw new HttpsError("internal", e?.message || "Error interno enviando reporte");
    }
  }
);

/* =========================================================
   TRIGGER: NOTIFICAR PROGRAMACIÓN ASIGNADA
   ========================================================= */

exports.notifyProgramacionAssigned = onValueCreated(
  {
    region: "us-central1",
    ref: "/programaciones/{subregion}/{vehiculoId}/{programacionId}",
    instance: "tecniapp-ice-programacion",
  },
  async (event) => {
    const data = event.data?.val();
    if (!data) return;

    const tecnicoId = String(data.tecnicoId || "").trim();
    if (!tecnicoId) return;

    const userSnap = await dbUsers.ref("usuarios").child(tecnicoId).get();
    const user = userSnap.val() || {};
    const tokens = extractUserTokens(user);
    if (!tokens.length) return;

    const actividad = String(data.actividad || "Nueva tarea");
    const placa = String(data.placa || data.vehiculoId || "");

    const msg = {
      notification: {
        title: "Nueva tarea asignada",
        body: `Actividad: ${actividad} · Vehículo: ${placa}`,
      },
      data: {
        type: "PROGRAMACION_ASSIGNED",
        destination: "nav_programacion",
        programacionId: String(data.programacionId || event.params.programacionId || ""),
      },
      tokens,
    };

    try {
      // FIX: también limpia tokens inválidos en programaciones
      const tokenToUid = new Map(tokens.map((t) => [t, tecnicoId]));
      const { totalOk, totalFail } = await sendFcmChunked(tokens, msg.data, tokenToUid, dbUsers);

      // Para programaciones enviamos con notification payload directamente
      // sendFcmChunked usa solo `data`, así que para este caso hacemos el envío manual:
      const r = await admin.messaging().sendEachForMulticast(msg);
      console.log("notifyProgramacionAssigned sent", r.successCount, "of", tokens.length);

      // Limpiar tokens inválidos
      const badTokens = [];
      r.responses.forEach((resp, idx) => {
        if (!resp.success) {
          const code = resp.error?.code || "";
          if (
            code === "messaging/registration-token-not-registered" ||
            code === "messaging/invalid-registration-token"
          ) badTokens.push(tokens[idx]);
        }
      });

      for (const badToken of badTokens) {
        const key = badToken
          .replace(/\./g, "_").replace(/#/g, "_").replace(/\$/g, "_")
          .replace(/\[/g, "_").replace(/\]/g, "_").replace(/\//g, "_");
        await dbUsers.ref("usuarios").child(tecnicoId).update({
          fcmToken: null,
          "fcm/currentToken": null,
          [`fcm/tokens/${key}`]: null,
        });
      }
    } catch (e) {
      console.error("notifyProgramacionAssigned error", e);
    }
  }
);


