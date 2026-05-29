/* eslint-disable */

const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onValueCreated, onValueWritten } = require("firebase-functions/v2/database");
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
const averiasApp = admin.initializeApp({ databaseURL: DB_AVERIAS_URL }, "averias");
const usersApp = admin.initializeApp({ databaseURL: DB_USERS_URL }, "users");

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
    // pool ayuda a reutilizar conexiones
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

// Mismo emailKey que usas en Android para RTDB keys seguras
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

/** Tag consistente (sin tildes/espacios raros) para comparar agencias */
function normTag(x) {
  return String(x || "")
    .trim()
    .toUpperCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "") // quita tildes
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
  if (["true", "1", "yes", "si", "on"].includes(s)) return true;
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

  if (Array.isArray(user?.fcmTokens)) {
    user.fcmTokens.forEach(add);
  }

  if (user?.fcm?.tokens && typeof user.fcm.tokens === "object") {
    Object.values(user.fcm.tokens).forEach(add);
  }

  return Array.from(set);
}

// Soporta múltiples envoltorios del API ICE
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

  // cerrados
  if (e === "RESUELTO" || e === "RESUELTA" || e === "SOLUCIONADO") return "RESUELTA";

  // activos
  if (
    e === "NUEVO" || e === "PENDIENTE" ||
    e === "ACEPTADO" || e === "EN DESPLAZAMIENTO" ||
    e === "EN ATENCION" || e === "EN ATENCIÓN"
  ) return "PENDIENTE";

  // fallback
  return "PENDIENTE";
}

function isEstadoAppResuelta(estado) {
  if (!estado) return false;
  return estado.toString().trim().toUpperCase().includes("RESUEL");
}




/**
 * Decide si a este usuario se le debe enviar una avería según sus filtros.
 * Prioridad:
 * 1) notificationAgencies (si existe y trae algo)
 * 2) fallback a agenciaId/agencia
 */
function userWantsThisAveria(user, averiaAgencyTag) {
  const list = toStringArray(user.notificationAgencies).map(normTag).filter(Boolean);
  if (list.length > 0) return list.includes(averiaAgencyTag);

  const fallback = normTag(user.agenciaId || user.agencia);
  if (fallback) return fallback === averiaAgencyTag;

  // Sin filtros configurados => recibir todas
  return true;
}

// Reglas de notificación (alineadas con Android + Room + Firebase):
// - NUEVA: solo si NO existe en snapshot y queda como PENDIENTE
// - EXISTENTE: solo si CAMBIA a RESUELTA
function shouldNotify(prevEstado, newEstado, isNew) {
  const prev = normalizeEstado(prevEstado);
  const curr = normalizeEstado(newEstado);

  if (isNew) return curr === "PENDIENTE";
  if (prev !== curr && curr === "RESUELTA") return true;

  return false;
}

function generateCode() {
  return Math.floor(100000 + Math.random() * 900000).toString();
}

function extractEmail(data) {
  // 1) string directo
  if (typeof data === "string") return data;

  // 2) Firebase a veces envuelve string como { data: "email@..." }
  if (typeof data?.data === "string") return data.data;

  // 3) doble envoltorio raro: { data: { data: "email@..." } }
  if (typeof data?.data?.data === "string") return data.data.data;

  // 4) formatos normales {email:""} o {data:{email:""}}
  const direct =
    data?.email ||
    data?.correo ||
    data?.mail ||
    data?.userEmail;
  if (direct) return direct;

  const nested =
    data?.data?.email ||
    data?.data?.correo ||
    data?.data?.mail ||
    data?.data?.userEmail;

  return nested || "";
}

/* =========================================================
   HTML TEMPLATES (MISMA ESTÉTICA)
   ========================================================= */

function verificationEmailHtml(code) {
  return `
  <html>
    <body style="margin: 0; padding: 0; font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f7fc;">
      <table align="center" width="100%" cellpadding="0" cellspacing="0"
        style="max-width: 600px; background-color: #ffffff; border-radius: 10px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); margin: 40px auto;">
        <tr>
          <td style="padding: 20px; text-align: center;">
            <img src="https://i.imgur.com/tGUD2Vo.png" alt="ICE Logo" width="100" style="margin-bottom: 20px;">
            <h2 style="color: #004C8C; margin-bottom: 8px;">TecniApp ICE</h2>
            <p style="color: #555; font-size: 16px; margin-top: 0;">Verificación de cuenta</p>
          </td>
        </tr>

        <tr>
          <td style="padding: 20px;">
            <p style="color: #333; font-size: 15px;">
              Gracias por registrarte en <strong>TecniApp ICE</strong>. Tu código de verificación es el siguiente:
            </p>

            <div style="text-align: center; background-color: #0075C9; color: #ffffff; font-size: 28px; font-weight: bold;
              padding: 16px 0; border-radius: 8px; margin: 24px 0; letter-spacing: 3px;">
              ${code}
            </div>

            <p style="font-size: 14px; color: #555;">Este código es válido por <strong>5 minutos</strong>. No lo compartas con nadie.</p>
            <p style="font-size: 14px; color: #555;">Si no realizaste esta solicitud, puedes ignorar este mensaje.</p>
          </td>
        </tr>

        <tr>
          <td style="padding: 10px 20px;">
            <hr style="border: none; border-top: 1px solid #eee;">
            <p style="text-align: center; font-size: 12px; color: #999; margin-top: 14px;">
              © 2025 Arasoft Solutions · Todos los derechos reservados<br>
              Este correo fue generado automáticamente por TecniApp ICE
            </p>
          </td>
        </tr>
      </table>
    </body>
  </html>
  `.trim();
}

function reportEmailHtml({ reportName, downloadUrl, subtitle }) {
  const safeName = String(reportName || "Reporte").trim();
  const safeSubtitle = String(subtitle || "").trim();
  const hasDownloadUrl = Boolean(downloadUrl);

  return `
  <html>
    <body style="margin: 0; padding: 0; font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f7fc;">
      <table align="center" width="100%" cellpadding="0" cellspacing="0"
        style="max-width: 600px; background-color: #ffffff; border-radius: 10px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); margin: 40px auto;">
        <tr>
          <td style="padding: 20px; text-align: center;">
            <img src="https://i.imgur.com/tGUD2Vo.png" alt="ICE Logo" width="100" style="margin-bottom: 20px;">
            <h2 style="color: #004C8C; margin-bottom: 8px;">TecniApp ICE</h2>
            <p style="color: #555; font-size: 16px; margin-top: 0;">Envío de reportes</p>
          </td>
        </tr>

        <tr>
          <td style="padding: 20px;">
            <p style="color: #333; font-size: 15px; margin: 0 0 10px;">
              Te compartimos el reporte: <strong>${safeName}</strong>
            </p>
            ${
              safeSubtitle
                ? `<p style="color:#555; font-size: 14px; margin: 0 0 18px;">${safeSubtitle}</p>`
                : `<p style="color:#555; font-size: 14px; margin: 0 0 18px;">Puedes descargarlo desde el siguiente botón.</p>`
            }

            ${
              hasDownloadUrl
                ? `
            <div style="text-align:center; margin: 22px 0 8px;">
              <a href="${downloadUrl}"
                style="display:inline-block; background-color:#0075C9; color:#ffffff; text-decoration:none;
                  padding: 12px 18px; border-radius: 10px; font-weight: 600; font-size: 14px;">
                Descargar reporte
              </a>
            </div>

            <p style="font-size: 12px; color: #777; margin-top: 16px;">
              Si el botón no funciona, copia y pega este enlace en tu navegador:
              <br>
              <span style="word-break: break-all;">${downloadUrl}</span>
            </p>
                `.trim()
                : `
            <div style="text-align:center; margin: 22px 0 8px;">
              <span style="display:inline-block; background-color:#0075C9; color:#ffffff;
                padding: 12px 18px; border-radius: 10px; font-weight: 600; font-size: 14px;">
                Reporte adjunto
              </span>
            </div>
            <p style="font-size: 12px; color: #777; margin-top: 16px;">
              Adjuntamos el archivo del reporte en este correo.
            </p>
                `.trim()
            }
          </td>
        </tr>

        <tr>
          <td style="padding: 10px 20px;">
            <hr style="border: none; border-top: 1px solid #eee;">
            <p style="text-align: center; font-size: 12px; color: #999; margin-top: 14px;">
              © 2025 Arasoft Solutions · Todos los derechos reservados<br>
              Este correo fue generado automáticamente por TecniApp ICE
            </p>
          </td>
        </tr>
      </table>
    </body>
  </html>
  `.trim();
}

// =======================
// FECHAS (ICE -> millis)
// =======================

function isNoRegistra(v) {
  const s = String(v ?? "").trim().toLowerCase();
  return !s || s === "no registra" || s === "pendiente de verificar";
}

// Convierte "2026-01-03T17:36" -> millis CR (-06:00)
function toMillisCR(v) {
  if (v === null || v === undefined) return null;
  const s = String(v).trim();
  if (isNoRegistra(s)) return null;

  // yyyy-MM-ddTHH:mm
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(s)) {
    const iso = `${s}:00-06:00`; // Costa Rica
    const ms = Date.parse(iso);
    return Number.isFinite(ms) ? ms : null;
  }

  // yyyy-MM-ddTHH:mm:ss
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(s)) {
    const iso = `${s}-06:00`;
    const ms = Date.parse(iso);
    return Number.isFinite(ms) ? ms : null;
  }

  const ms = Date.parse(s);
  return Number.isFinite(ms) ? ms : null;
}


/* =========================================================
   CRON: AVERÍAS + FCM (API ICE -> RTDB -> FCM data-only)
   ========================================================= */

const ICE_URL = "https://agenciaelectricidad.cn.ice.go.cr/api/AveriasAranda/";

exports.syncAveriasYNotificar = onSchedule(
  { schedule: "every 5 minutes", timeZone: "America/Costa_Rica" },
  async () => {
    try {
      const dbA = dbAverias;
      const dbU = dbUsers;

      // 1) Consultar API ICE (SIN TOKEN)
      const resp = await axios.get(ICE_URL, { timeout: 20000 });
      const averias = envelopePayload(resp.data);

      console.log("Averías recibidas:", averias.length);

      // 2) Leer snapshot previo (solo para decidir notificaciones)
      const snapRef = dbA.ref("averias_last_snapshot");
      const snap = await snapRef.get();
      const last = snap.exists() ? snap.val() : {};

      const next = { ...last };
      const toNotify = [];

      const now = Date.now();

      for (const a of averias) {
        const caseId = String(a.noCaso || "").trim();
        if (!caseId) continue;

        const estadoRaw = String(a.estado || "").trim();
        const estado = normalizeEstado(estadoRaw) || "PENDIENTE";

        const agencia = String(a.agencia || "").trim();
        const nombreAgencia = String(a.nombreAgencia || agencia || "").trim();

        const agenciaTag = normTag(nombreAgencia || agencia);

        const prev = last[caseId];
        const prevEstado = prev ? prev.estado : "";
        const isNew = !prev;

        const averiaRef = dbA.ref("averias").child(caseId);
        const existingSnap = await averiaRef.get();
        const existing = existingSnap.val() || {};
        const bloquearPromocionAClorResuelta =
          isEstadoAppResuelta(existing.estado) &&
          normalizeEstado(existing.estadoClor) !== "RESUELTA" &&
          estado === "RESUELTA";
        const estadoClorEfectivo = bloquearPromocionAClorResuelta
          ? (normalizeEstado(existing.estadoClor) || "PENDIENTE")
          : estado;

        // Guardar snapshot SIEMPRE (normalizado) para evitar falsos cambios
        next[caseId] = {
          estado: estadoClorEfectivo,
          agenciaTag,
          ts: now,
        };

                // ✅ Guardar / actualizar avería en Realtime (fuente “viva”)
        // 🔥 CLOR separado: NO pisa campos del técnico (estado/observaciones/causa de la app)
        const payload = {
          caseId,

          // Neutros / API
          agencia,
          nombreAgencia,
          region: String(a.region || ""),
          nise: String(a.nise || ""),
          clientesAfectados: String(a.clientesAfectados || ""),
          lat: toNumberOrNull(a.latitud),   // ✅ NUMBER o null
          lng: toNumberOrNull(a.longitud),  // ✅ NUMBER o null
          agenciaTag,
          fechaInicioMillis: toMillisCR(a.fechaInicio) ?? 0, // ✅ NUMBER
          lastUpdated: Date.now(),

          // ✅ CLOR (separado)
          estadoClor: bloquearPromocionAClorResuelta ? (existing.estadoClor ?? "") : estadoClorEfectivo,
          observacionesClor: bloquearPromocionAClorResuelta
            ? (existing.observacionesClor ?? "")
            : String(a.observaciones || ""),
          causaClor: bloquearPromocionAClorResuelta
            ? (existing.causaClor ?? "")
            : String(a.causa || ""),
        };

        if (estadoClorEfectivo === "RESUELTA" && !bloquearPromocionAClorResuelta) {
          payload.estado = "Resuelta";
        }

        await averiaRef.update(payload);

        // ✅ Decide si notifica (nueva PENDIENTE, o cambio a RESUELTA)
        if (!shouldNotify(prevEstado, estadoClorEfectivo, isNew)) continue;

        // ✅ Data EXACTA (DATA-ONLY) — todo string para FCM
        const data = {
          caseId: String(caseId),

          // ✅ CLOR
          estadoClor: String(estadoClorEfectivo),
          estado: String(estadoClorEfectivo),

          agencia: String(agencia || ""),
          nombreAgencia: String(nombreAgencia || ""),
          region: String(a.region || ""),
          descripcion: String(a.observaciones || ""), // texto CLOR (San José)
          localizacion: "",

          nise: String(a.nise || ""),
          causa: String(a.causa || ""), // causa CLOR
          clientesAfectados: String(a.clientesAfectados || ""),

          lat: String(a.latitud || ""),
          lng: String(a.longitud || ""),

          agenciaTag: String(agenciaTag),
          fechaInicioMillis: String(toMillisCR(a.fechaInicio) ?? 0),
          lastUpdated: String(Date.now()),
        };

        toNotify.push({ caseId, agenciaTag, data });

}
      // 3) Guardar snapshot actualizado
      await snapRef.set(next);

      if (!toNotify.length) {
        console.log("Sin averías nuevas/cambio a resuelta para notificar");
        return;
      }

      // 4) Leer usuarios con FCM token
      const usersSnap = await dbU.ref("usuarios").get();
      const users = usersSnap.exists() ? usersSnap.val() : {};

      for (const item of toNotify) {
        // construir recipients (uid+token) para poder limpiar tokens inválidos
        const recipients = [];
        for (const uid of Object.keys(users)) {
          const u = users[uid];
          if (!u) continue;

          // respetar apagado global del usuario (si existe)
          if (!toBooleanOrDefault(u.notificationEnabled, true)) continue;

          const userTokens = extractUserTokens(u);
          if (!userTokens.length) continue;

          if (userWantsThisAveria(u, item.agenciaTag)) {
            userTokens.forEach((token) => recipients.push({ uid, token }));
          }
        }

        if (!recipients.length) {
          console.log(
            `Sin tokens para caseId=${item.caseId} agenciaTag=${item.agenciaTag}`
          );
          continue;
        }

        const tokens = recipients.map((r) => r.token);
        const tokenToUid = new Map(recipients.map((r) => [r.token, r.uid]));

        // ✅ DATA-ONLY (Android construye la notificación local con tus filtros)
        const res = await admin.messaging().sendEachForMulticast({
          tokens,
          data: item.data,
        });

        console.log(
          `FCM enviado caseId=${item.caseId} ok=${res.successCount} fail=${res.failureCount}`
        );

        // ✅ Limpieza de tokens inválidos
        const tokensToDelete = [];
        res.responses.forEach((r, idx) => {
          if (r.success) return;
          const code = r.error?.code || "";
          if (
            code === "messaging/registration-token-not-registered" ||
            code === "messaging/invalid-registration-token"
          ) {
            tokensToDelete.push(tokens[idx]);
          }
        });

        for (const badToken of tokensToDelete) {
          const uid = tokenToUid.get(badToken);
          if (!uid) continue;
          console.log(`Eliminando token inválido uid=${uid}`);

          const userRef = dbU.ref("usuarios").child(uid);
          const updates = {
            fcmToken: null,
            "fcm/currentToken": null,
          };

          const key = badToken
            .replace(/\./g, "_")
            .replace(/#/g, "_")
            .replace(/\$/g, "_")
            .replace(/\[/g, "_")
            .replace(/\]/g, "_")
            .replace(/\//g, "_");
          updates[`fcm/tokens/${key}`] = null;

          await userRef.update(updates);
        }
      }
    } catch (e) {
      console.error("syncAveriasYNotificar ERROR:", e);
    }
  }
);

/* =========================================================
   CALLABLE: ENVIAR CÓDIGO DE VERIFICACIÓN (REGISTRO)
   =========================================================
   Android hoy lee y verifica en:
   /verificationCodes/{emailKey(email)}
*/
exports.sendVerificationCode = onCall(
  { region: "us-central1", secrets: [MAIL_USER, MAIL_PASS] },
  async (request) => {
    try {
      const data = request.data;
      const email = String(extractEmail(data)).trim();
      if (!email) throw new HttpsError("invalid-argument", "Email requerido");

      // (Opcional) forzar dominio institucional
      // if (!email.toLowerCase().endsWith("@ice.go.cr")) {
      //   throw new HttpsError("permission-denied", "Solo correos @ice.go.cr");
      // }

      const code = generateCode();
      const now = Date.now();
      const expiresAt = now + 5 * 60 * 1000; // 5 min

      const key = emailKey(email);

      // Guardar EXACTO como su app espera: { code, createdAt, expiresAt }
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
   =========================================================
   Recomendado:
   - Android genera el archivo (xlsx/pdf) y lo envía en base64
   - Llama sendReport(email, reportName, fileBase64, fileName, subtitle)
   - (Opcional) downloadUrl si se desea incluir enlace
*/
exports.sendReport = onCall(
  { region: "us-central1", secrets: [MAIL_USER, MAIL_PASS] },
  async (request) => {
    const data = request.data;
    const email = String(extractEmail(data)).trim();
    const reportName = String(data?.reportName || "Reporte").trim();
    const downloadUrl = String(data?.downloadUrl || "").trim();
    const subtitle = String(data?.subtitle || "").trim(); // ej: "Rango: 01–07 Dic 2025"
    const fileBase64 = String(data?.fileBase64 || "").trim();
    const fileName = String(data?.fileName || "reporte.xlsx").trim();

    if (!email || (!downloadUrl && !fileBase64)) {
      throw new HttpsError("invalid-argument", "Datos incompletos");
    }

    const html = reportEmailHtml({ reportName, downloadUrl, subtitle });
    const attachments = fileBase64
      ? [
          {
            filename: fileName || "reporte.xlsx",
            content: Buffer.from(fileBase64, "base64"),
          },
        ]
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
  }
);



exports.notifyAveriaAssignedToCrew = onValueWritten({
  region: "us-central1",
  ref: "/averias/{caseId}",
  instance: "tecniapp-ice-averias"
}, async (event) => {
  const before = event.data?.before?.val() || {};
  const after = event.data?.after?.val() || {};
  if (!after) return;

  const estado = String(after.estado || "").trim().toUpperCase();
  const vehiculo = String(after.vehiculoAsignado || "").trim();
  const tecnicoUid = String(after.tecnicoAsignadoUid || "").trim();

  if (estado !== "ASIGNADA" || !vehiculo || tecnicoUid) return;

  const beforeVehiculo = String(before.vehiculoAsignado || "").trim();
  const beforeEstado = String(before.estado || "").trim().toUpperCase();
  const shouldNotify = beforeVehiculo !== vehiculo || beforeEstado !== "ASIGNADA";
  if (!shouldNotify) return;

  const usersSnap = await dbUsers.ref("usuarios").get();
  const usersVal = usersSnap.val() || {};

  const tokensSet = new Set();
  Object.values(usersVal).forEach((u) => {
    const placa = String(u?.placaVehiculo || "").trim();
    if (!placa) return;
    if (placa.toUpperCase() !== vehiculo.toUpperCase()) return;
    extractUserTokens(u).forEach((t) => tokensSet.add(t));
  });

  const tokens = Array.from(tokensSet);
  if (!tokens.length) return;

  const caseId = String(after.caseId || event.params.caseId || "").trim();
  const agencia = String(after.nombreAgencia || after.agencia || "").trim();
  const body = `Caso ${caseId || "s/n"} · ${agencia || "Avería asignada"} · Vehículo: ${vehiculo}`;

  const msg = {
    notification: {
      title: "Avería asignada",
      body,
    },
    data: {
      type: "AVERIA_ASSIGNED",
      destination: "nav_averias",
      caseId,
      vehiculoAsignado: vehiculo,
      estado: String(after.estado || "Asignada"),
      agencia: String(after.agencia || ""),
      nombreAgencia: String(after.nombreAgencia || ""),
      descripcion: String(after.localizacion || after.observaciones || ""),
      lastUpdated: String(after.lastUpdated || Date.now()),
    },
    tokens,
  };

  try {
    const r = await admin.messaging().sendEachForMulticast(msg);
    console.log("notifyAveriaAssignedToCrew sent", r.successCount, "of", tokens.length, "caseId", caseId, "vehiculo", vehiculo);
  } catch (e) {
    console.error("notifyAveriaAssignedToCrew error", e);
  }
});

exports.notifyProgramacionAssigned = onValueCreated({
  region: "us-central1",
  ref: "/programaciones/{subregion}/{vehiculoId}/{programacionId}",
  instance: "tecniapp-ice-programacion"
}, async (event) => {
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
      body: `Actividad: ${actividad} · Vehículo: ${placa}`
    },
    data: {
      type: "PROGRAMACION_ASSIGNED",
      destination: "nav_programacion",
      programacionId: String(data.programacionId || event.params.programacionId || "")
    },
    tokens
  };

  try {
    const r = await admin.messaging().sendEachForMulticast(msg);
    console.log("notifyProgramacionAssigned sent", r.successCount, "of", tokens.length);
  } catch (e) {
    console.error("notifyProgramacionAssigned error", e);
  }
});
