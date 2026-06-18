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

// ─── TRANSPORTER ────────────────────────────────────────────────────────────
// FIX: aumentado maxConnections a 3 para envíos concurrentes, añadidos
// socketTimeout y connectionTimeout para no bloquear en cold starts.
// cachedAuthKey invalida el cache si cambian las credenciales (rotación de
// secrets).  Siempre revisar que el transporter esté vivo antes de reutilizar.

let cachedTransporter = null;
let cachedAuthKey = null;

function getTransporter(user, pass) {
  const key = `${user}:${pass}`;
  if (cachedTransporter && cachedAuthKey === key) return cachedTransporter;
  // Crear transporter nuevo (también descarta el anterior si el key cambió)
  cachedAuthKey = key;
  cachedTransporter = nodemailer.createTransport({
    service: "gmail",
    auth: { user, pass },
    pool: true,
    maxConnections: 3,   // FIX: antes era 1; permite envíos paralelos
    maxMessages: 100,
    socketTimeout: 10000,      // FIX: nuevo — cierra si el socket se congela
    connectionTimeout: 8000,   // FIX: nuevo — falla rápido en cold start
  });
  return cachedTransporter;
}

// FIX: sendMailWithRetry — reintenta hasta `retries` veces.
// En cada fallo descarta el transporter cacheado (puede estar con conexión
// muerta) para que el próximo intento abra una conexión fresca.
async function sendMailWithRetry({ to, subject, html, user, pass, attachments }, retries = 2) {
  let lastError;
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      const transporter = getTransporter(user, pass);
      const t = Date.now();
      const info = await transporter.sendMail({
        from: `"TecniApp ICE" <${user}>`,
        to,
        subject,
        html,
        attachments,
      });
      console.log(`sendMail ok attempt=${attempt} ms=${Date.now() - t} id=${info?.messageId}`);
      return info;
    } catch (e) {
      lastError = e;
      console.warn(`sendMail attempt=${attempt} failed: ${e?.message}`);
      // Descartar transporter para forzar reconexión en el siguiente intento
      cachedTransporter = null;
      cachedAuthKey = null;
    }
  }
  throw lastError;
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

function userWantsThisAveria(user, averiaAgencyTag, averiaRegionTag) {
  const agencyList = toStringArray(user?.notificationAgencies)
    .map(normTag)
    .filter(Boolean);

  if (agencyList.length > 0) {
    return agencyList.includes(averiaAgencyTag);
  }

  const userRegionTag = normTag(user?.region || user?.regionId || "");
  if (userRegionTag) {
    return userRegionTag === averiaRegionTag;
  }

  const fallbackAgency = normTag(user?.agenciaId || user?.agencia || "");
  if (fallbackAgency) {
    return fallbackAgency === averiaAgencyTag;
  }

  return true;
}

function shouldNotify(prevEstado, newEstado, existingIsNew, existingHasBeenNotified) {
  const prev = normalizeEstado(prevEstado);
  const curr = normalizeEstado(newEstado);

  if (existingIsNew && curr === "PENDIENTE") return true;
  if (!existingIsNew && !existingHasBeenNotified && curr === "PENDIENTE") return true;
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
    .hero-inner { position: relative; z-index: 1; }
    .brand-row { display: flex; align-items: center; gap: 14px; margin-bottom: 28px; }
    .brand-logo { width: 44px; height: 44px; background: rgba(255,255,255,0.12); border-radius: 12px; display: flex; align-items: center; justify-content: center; border: 1px solid rgba(255,255,255,0.18); }
    .brand-logo img { width: 28px; height: 28px; display: block; }
    .brand-name { font-size: 16px; font-weight: 600; color: #ffffff; letter-spacing: -0.02em; }
    .brand-sub { font-size: 12px; color: rgba(255,255,255,0.5); margin-top: 1px; }
    .hero-title { font-size: 24px; font-weight: 600; color: #ffffff; letter-spacing: -0.03em; line-height: 1.2; }
    .hero-subtitle { font-size: 14px; color: rgba(255,255,255,0.5); margin-top: 8px; line-height: 1.5; }
    .body-section { padding: 36px 40px; }
    .intro-text { font-size: 15px; color: rgba(255,255,255,0.5); line-height: 1.65; margin-bottom: 28px; }
    .intro-text strong { color: rgba(255,255,255,0.8); font-weight: 500; }
    .divider { height: 1px; background: rgba(255,255,255,0.05); margin: 0 40px; }
    .footer { padding: 24px 40px 28px; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
    .footer-brand { font-size: 12px; color: rgba(255,255,255,0.2); font-weight: 500; }
    .footer-meta { font-size: 11px; color: rgba(255,255,255,0.15); text-align: right; line-height: 1.5; }
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
          <div class="hero-title">${safeName}</div>
          ${safeSubtitle ? `<div class="hero-subtitle">${safeSubtitle}</div>` : ""}
        </div>
      </div>
      <div class="body-section">
        <p class="intro-text">Tu reporte <strong>${safeName}</strong> ha sido generado exitosamente. ${hasDownloadUrl ? "Puedes descargarlo usando el enlace a continuación." : "Lo encontrarás adjunto en este correo."}</p>
        ${hasDownloadUrl ? `<p style="font-size:13px;color:rgba(77,159,255,0.8);word-break:break-all;">${downloadUrl}</p>` : ""}
        <p style="font-size: 13px; color: rgba(255,255,255,0.2); line-height: 1.6; margin-top:20px;">Este reporte fue generado automáticamente por TecniApp ICE.</p>
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
   FCM CHUNKED
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

      const resp = await axios.get(ICE_URL, { timeout: 20000 });
      const averias = envelopePayload(resp.data);
      console.log("Averías recibidas:", averias.length);

      const snapRef = dbA.ref("averias_last_snapshot");
      const snap = await snapRef.get();
      const last = snap.exists() ? snap.val() : {};
      const next = { ...last };

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
        const regionTag = normTag(region);

        const prev = last[caseId];
        const prevEstado = prev ? prev.estado : "";

        const existing = allExisting[caseId] || {};
        const existingIsNew = !allExisting[caseId];
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

      await snapRef.set(next);

      if (!toNotify.length) {
        console.log("Sin averías nuevas/cambio a resuelta para notificar");
        return;
      }

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

          if (userWantsThisAveria(u, item.agenciaTag, item.regionTag)) {
            userTokens.forEach((token) => recipients.push({ uid, token }));
          }
        }

        if (!recipients.length) {
          console.log(`Sin tokens para caseId=${item.caseId}`);
          continue;
        }

        const tokens = recipients.map((r) => r.token);
        const tokenToUid = new Map(recipients.map((r) => [r.token, r.uid]));

        const { totalOk, totalFail } = await sendFcmChunked(tokens, item.data, tokenToUid, dbU);
        console.log(`FCM enviado caseId=${item.caseId} ok=${totalOk} fail=${totalFail}`);

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
   FIX: escritura en RTDB ANTES de enviar el correo para evitar
   race condition (usuario recibe código antes de que exista en DB).
   FIX: usa sendMailWithRetry para reintentar en caso de fallo SMTP.
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

      // FIX: primero escribir en RTDB, después enviar el correo.
      // Antes estaban casi en paralelo; si el correo llegaba antes de que
      // RTDB confirmara la escritura, verifyCode encontraba el nodo vacío.
      await dbUsers.ref("verificationCodes").child(key).set({
        code,
        createdAt: now,
        expiresAt,
      });

      // FIX: usar sendMailWithRetry para recuperarse de conexiones muertas
      await sendMailWithRetry({
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

      await sendMailWithRetry({
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
   CALLABLE: NOTIFICAR SUPERVISOR — CAMBIO DE MEDIDOR
   ========================================================= */

function cambioMedidorEmailHtml({ numeroCaso, agencia, tecnicoNombre, fechaCambio, medidorAnterior, lecturaAnterior, lecturaAnteriorVisible, medidorInstalado, lecturaNueva, materialUsado }) {
  const lecturaAnteriorDisplay = lecturaAnteriorVisible === false
    ? "No aplica"
    : (String(lecturaAnterior || "").trim() || "—");

  return `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <title>Cambio de medidor registrado</title>
</head>
<body style="margin:0;padding:0;background-color:#f4f6f8;font-family:Arial,Helvetica,sans-serif;color:#1f2933;">
  <table width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f6f8;padding:24px 0;">
    <tr><td align="center">
      <table width="640" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 8px 24px rgba(0,0,0,0.08);">
        <tr>
          <td style="background:linear-gradient(135deg,#004b8d,#0077c8);padding:28px 32px;color:#ffffff;">
            <h1 style="margin:0;font-size:24px;">Cambio de medidor registrado</h1>
            <p style="margin:8px 0 0;font-size:14px;">Notificación automática generada desde TecniApp ICE</p>
          </td>
        </tr>
        <tr>
          <td style="padding:28px 32px;">
            <p style="font-size:16px;line-height:1.6;margin:0 0 20px;">Se ha registrado un cambio de medidor durante la atención de una avería.</p>
            <table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;margin-bottom:24px;">
              <tr><td style="padding:12px;background-color:#f9fafb;border:1px solid #e5e7eb;font-weight:bold;">Número de caso</td><td style="padding:12px;border:1px solid #e5e7eb;">${numeroCaso}</td></tr>
              <tr><td style="padding:12px;background-color:#f9fafb;border:1px solid #e5e7eb;font-weight:bold;">Agencia</td><td style="padding:12px;border:1px solid #e5e7eb;">${agencia}</td></tr>
              <tr><td style="padding:12px;background-color:#f9fafb;border:1px solid #e5e7eb;font-weight:bold;">Técnico responsable</td><td style="padding:12px;border:1px solid #e5e7eb;">${tecnicoNombre}</td></tr>
              <tr><td style="padding:12px;background-color:#f9fafb;border:1px solid #e5e7eb;font-weight:bold;">Fecha y hora</td><td style="padding:12px;border:1px solid #e5e7eb;">${fechaCambio}</td></tr>
            </table>
            <h2 style="font-size:18px;margin:0 0 12px;color:#004b8d;">Detalle del cambio</h2>
            <table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;">
              <tr><td style="padding:14px;background-color:#fff7ed;border:1px solid #fed7aa;font-weight:bold;">Medidor anterior</td><td style="padding:14px;border:1px solid #fed7aa;">${medidorAnterior || "—"}</td></tr>
              <tr><td style="padding:14px;background-color:#fff7ed;border:1px solid #fed7aa;font-weight:bold;">Lectura anterior</td><td style="padding:14px;border:1px solid #fed7aa;">${lecturaAnteriorDisplay}</td></tr>
              <tr><td style="padding:14px;background-color:#ecfdf5;border:1px solid #a7f3d0;font-weight:bold;">Medidor nuevo instalado</td><td style="padding:14px;border:1px solid #a7f3d0;">${medidorInstalado}</td></tr>
              <tr><td style="padding:14px;background-color:#ecfdf5;border:1px solid #a7f3d0;font-weight:bold;">Lectura nueva</td><td style="padding:14px;border:1px solid #a7f3d0;">${String(lecturaNueva || "").trim() || "—"}</td></tr>
              <tr><td style="padding:14px;background-color:#eff6ff;border:1px solid #bfdbfe;font-weight:bold;">Material utilizado</td><td style="padding:14px;border:1px solid #bfdbfe;">${materialUsado || "—"}</td></tr>
            </table>
            <p style="font-size:13px;line-height:1.6;color:#6b7280;margin:24px 0 0;">Este correo fue generado automáticamente por TecniApp ICE como respaldo operativo del cambio realizado en campo.</p>
          </td>
        </tr>
        <tr>
          <td style="background-color:#f9fafb;padding:18px 32px;text-align:center;font-size:12px;color:#6b7280;">TecniApp ICE · Arasoft Solutions</td>
        </tr>
      </table>
    </td></tr>
  </table>
</body>
</html>`;
}

exports.notifyCambioMedidorSupervisor = onCall(
  {
    region: "us-central1",
    secrets: [MAIL_USER, MAIL_PASS],
  },
  async (request) => {
    try {
      const data = request.data || {};
      const caseId = String(data.caseId || "").trim();
      if (!caseId) throw new HttpsError("invalid-argument", "caseId requerido");

      const agencia = String(data.agencia || data.nombreAgencia || "").trim();
      const agenciaTag = normTag(agencia);
      const tecnicoNombre = String(data.tecnicoNombre || "Técnico").trim();
      const tecnicoUid = String(data.tecnicoUid || "").trim();

      // Verificar si la notificación ya fue enviada
      const cambioRef = dbAverias.ref("averias").child(caseId).child("cambioMedidor");
      const cambioSnap = await cambioRef.get();
      const cambioVal = cambioSnap.exists() ? cambioSnap.val() : {};

      if (cambioVal.notificacionSupervisorEnviada === true) {
        console.log(`notifyCambioMedidorSupervisor: ya enviada para caseId=${caseId}`);
        return { success: true, skipped: true };
      }

      // Buscar supervisores de la misma agencia
      const usersSnap = await dbUsers.ref("usuarios").get();
      const users = usersSnap.exists() ? usersSnap.val() : {};

      const destinatarios = [];
      for (const uid of Object.keys(users)) {
        if (uid === tecnicoUid) continue;
        const u = users[uid];
        if (!u) continue;
        const rol = String(u.rol || "").trim().toLowerCase();
        const esSupervisor = rol.includes("supervisor") || rol.includes("admin");
        if (!esSupervisor) continue;
        const email = String(u.email || u.correo || "").trim();
        if (!email) continue;
        const userAgenciaTag = normTag(u.agencia || u.agenciaId || "");
        if (agenciaTag && userAgenciaTag && userAgenciaTag !== agenciaTag) continue;
        destinatarios.push(email);
      }

      const now = new Date().toISOString();

      if (!destinatarios.length) {
        console.warn(`notifyCambioMedidorSupervisor: sin supervisores para agencia=${agencia} caseId=${caseId}`);
        await cambioRef.update({
          notificacionSupervisorEnviada: false,
          notificacionSupervisorPendiente: false,
          errorNotificacionSupervisor: `Sin supervisores activos para agencia ${agencia}`,
          fechaNotificacionSupervisor: now,
        });
        return { success: false, reason: "no_supervisors" };
      }

      const html = cambioMedidorEmailHtml({
        numeroCaso: caseId,
        agencia,
        tecnicoNombre,
        fechaCambio: String(data.fechaCambio || now).trim(),
        medidorAnterior: String(data.medidorAnterior || "").trim(),
        lecturaAnterior: data.lecturaAnterior,
        lecturaAnteriorVisible: data.lecturaAnteriorVisible,
        medidorInstalado: String(data.medidorInstalado || "").trim(),
        lecturaNueva: data.lecturaNueva,
        materialUsado: String(data.materialUsado || "").trim(),
      });

      const subject = `Cambio de medidor registrado - Caso ${caseId}`;
      const to = destinatarios.join(",");

      try {
        await sendMailWithRetry({
          to,
          subject,
          html,
          user: MAIL_USER.value(),
          pass: MAIL_PASS.value(),
        });
        await cambioRef.update({
          notificacionSupervisorEnviada: true,
          notificacionSupervisorPendiente: false,
          fechaNotificacionSupervisor: now,
        });
        console.log(`notifyCambioMedidorSupervisor: correo enviado a ${to} para caseId=${caseId}`);
        return { success: true };
      } catch (mailErr) {
        console.error(`notifyCambioMedidorSupervisor: error enviando correo caseId=${caseId}`, mailErr);
        await cambioRef.update({
          notificacionSupervisorEnviada: false,
          notificacionSupervisorPendiente: true,
          errorNotificacionSupervisor: String(mailErr?.message || "Error de correo"),
        });
        return { success: false, reason: "mail_error" };
      }
    } catch (e) {
      console.error("notifyCambioMedidorSupervisor ERROR:", e);
      if (e instanceof HttpsError) throw e;
      throw new HttpsError("internal", e?.message || "Error interno");
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
      const r = await admin.messaging().sendEachForMulticast(msg);
      console.log("notifyProgramacionAssigned sent", r.successCount, "of", tokens.length);

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