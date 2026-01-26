/* eslint-disable */

const { onSchedule } = require("firebase-functions/v2/scheduler");
const functions = require("firebase-functions"); // v1 para https.onCall
const admin = require("firebase-admin");
const axios = require("axios");
const nodemailer = require("nodemailer");

admin.initializeApp();

/* =========================================================
   CONFIG MAIL (Firebase Functions Config)
   =========================================================
   Configure en consola:
   firebase functions:config:set mail.user="tecniappice@gmail.com"
   firebase functions:config:set mail.pass="CLAVE_APP_GMAIL"
*/
function getMailConfig() {
  const cfg = functions.config();
  const user =
    cfg?.mail?.user ||
    cfg?.email?.user ||
    cfg?.email?.userEntity ||
    process.env.MAIL_USER ||
    process.env.mail_user ||
    process.env.EMAIL_USER ||
    process.env.EMAIL_USER_ENTITY ||
    process.env.SMTP_USER ||
    "";
  const pass =
    cfg?.mail?.pass ||
    cfg?.email?.pass ||
    process.env.MAIL_PASS ||
    process.env.mail_pass ||
    process.env.EMAIL_PASS ||
    process.env.SMTP_PASS ||
    "";
  if (!user || !pass) {
    throw new Error(
      "Faltan credenciales. Configure con: firebase functions:config:set mail.user=... mail.pass=... (o email.userEntity/email.pass) o variables MAIL_USER/MAIL_PASS."
    );
  }
  return { user, pass };
}

function createTransporter() {
  const { user, pass } = getMailConfig();
  return nodemailer.createTransport({
    service: "gmail",
    auth: { user, pass },
  });
}

async function sendMail({ to, subject, html }) {
  const transporter = createTransporter();
  const fromUser = getMailConfig().user;

  await transporter.sendMail({
    from: `"TecniApp ICE" <${fromUser}>`,
    to,
    subject,
    html,
  });
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
  if (typeof data === "string") {
    return data;
  }
  return (
    data?.email ||
    data?.correo ||
    data?.mail ||
    data?.userEmail ||
    ""
  );
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
      const db = admin.database();

      // 1) Consultar API ICE (SIN TOKEN)
      const resp = await axios.get(ICE_URL, { timeout: 20000 });
      const averias = envelopePayload(resp.data);

      console.log("Averías recibidas:", averias.length);

      // 2) Leer snapshot previo (solo para decidir notificaciones)
      const snapRef = db.ref("averias_last_snapshot");
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

        // Guardar snapshot SIEMPRE (normalizado) para evitar falsos cambios
        next[caseId] = {
          estado,
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
          estadoClor: estado, // "PENDIENTE" | "RESUELTA"
          observacionesClor: String(a.observaciones || ""),
          causaClor: String(a.causa || ""),
        };

        if (estado === "RESUELTA") {
          payload.estado = "Resuelta";
        }

        await db.ref("averias").child(caseId).update(payload);

        // ✅ Decide si notifica (nueva PENDIENTE, o cambio a RESUELTA)
        if (!shouldNotify(prevEstado, estado, isNew)) continue;

        // ✅ Data EXACTA (DATA-ONLY) — todo string para FCM
        const data = {
          caseId: String(caseId),

          // ✅ CLOR
          estadoClor: String(estado),
          estado: String(estado),

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
      const usersSnap = await db.ref("usuarios").get();
      const users = usersSnap.exists() ? usersSnap.val() : {};

      for (const item of toNotify) {
        // construir recipients (uid+token) para poder limpiar tokens inválidos
        const recipients = [];
        for (const uid of Object.keys(users)) {
          const u = users[uid];
          if (!u || !u.fcmToken) continue;

          // respetar apagado global del usuario (si existe)
          if (u.notificationEnabled === false) continue;

          if (userWantsThisAveria(u, item.agenciaTag)) {
            recipients.push({ uid, token: u.fcmToken });
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
          await db.ref("usuarios").child(uid).child("fcmToken").remove();
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
exports.sendVerificationCode = functions.https.onCall(async (data, context) => {
  const email = String(extractEmail(data)).trim();
  if (!email) {
    throw new functions.https.HttpsError("invalid-argument", "Email requerido");
  }

  // (Opcional) forzar dominio institucional
  // if (!email.toLowerCase().endsWith("@ice.go.cr")) {
  //   throw new functions.https.HttpsError("permission-denied", "Solo correos @ice.go.cr");
  // }

  const code = generateCode();
  const now = Date.now();
  const expiresAt = now + 5 * 60 * 1000; // 5 min

  const key = emailKey(email);

  // Guardar EXACTO como su app espera: { code, createdAt, expiresAt }
  await admin.database().ref("verificationCodes").child(key).set({
    code,
    createdAt: now,
    expiresAt,
  });

  const html = verificationEmailHtml(code);

  await sendMail({
    to: email,
    subject: "Código de verificación – TecniApp ICE",
    html,
  });

  return { success: true };
});

/* =========================================================
   CALLABLE: ENVIAR REPORTE POR CORREO
   =========================================================
   Recomendado:
   - Android genera el archivo (xlsx/pdf), lo sube a Storage
   - Obtiene downloadUrl
   - Llama sendReport(email, reportName, downloadUrl, subtitle)
*/
exports.sendReport = functions.https.onCall(async (data, context) => {
  const email = String(extractEmail(data)).trim();
  const reportName = String(data?.reportName || "Reporte").trim();
  const downloadUrl = String(data?.downloadUrl || "").trim();
  const subtitle = String(data?.subtitle || "").trim(); // ej: "Rango: 01–07 Dic 2025"

  if (!email || !downloadUrl) {
    throw new functions.https.HttpsError("invalid-argument", "Datos incompletos");
  }

  const html = reportEmailHtml({ reportName, downloadUrl, subtitle });

  await sendMail({
    to: email,
    subject: `Reporte ${reportName} – TecniApp ICE`,
    html,
  });

  return { success: true };
});
