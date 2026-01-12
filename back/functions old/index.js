const { onSchedule } = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");
const axios = require("axios");

admin.initializeApp();

// ✅ Base URL real (la misma que usas en Android) + endpoint real AveriasAranda/
const ICE_BASE_URL = "https://agenciaelectricidad.cn.ice.go.cr/api/";
const ICE_URL = `${ICE_BASE_URL}AveriasAranda/`;

const norm = (s) => String(s || "").trim().toUpperCase();

function envelopePayload(respData) {
  // Tu app soporta: data, items, averias, Averias, respuesta :contentReference[oaicite:6]{index=6}
  if (!respData) return [];
  return (
    respData.data ||
    respData.items ||
    respData.averias ||
    respData.Averias ||
    respData.respuesta ||
    []
  );
}

/**
 * Reglas (como tu lógica):
 * - Notificar SOLO si es NUEVA/PENDIENTE que antes no existía
 * - O si pasó a RESUELTO (cambio de estado a RESUELTO)
 * - No spamear por estados tipo ASIGNADO, etc.
 */
function shouldNotify(prevEstado, newEstado, isNew) {
  const e = norm(newEstado);
  const p = norm(prevEstado);

  if (isNew) return e === "NUEVO" || e === "PENDIENTE";
  if (p !== e && e === "RESUELTO") return true;
  return false;
}

exports.syncAveriasYNotificar = onSchedule(
  { schedule: "every 5 minutes", timeZone: "America/Costa_Rica" },
  async () => {
    try {
      const db = admin.database();

      // 1) Llamar API ICE (SIN TOKEN)
      const resp = await axios.get(ICE_URL, { timeout: 20000 });
      const averias = envelopePayload(resp.data);
      console.log("ICE averias payload:", Array.isArray(averias) ? averias.length : 0);

      // 2) Leer snapshot anterior (para detectar nuevos/cambios)
      const snapRef = db.ref("averias_last_snapshot");
      const snap = await snapRef.get();
      const last = snap.exists() ? snap.val() : {};

      const next = { ...last };
      const toNotify = [];

      for (const a of averias) {
        // Mapeo real según IceApi.kt :contentReference[oaicite:7]{index=7}
        const caseId = String(a.noCaso || "").trim(); // ✅ noCaso
        if (!caseId) continue;

        const estado = String(a.estado || "").trim();
        const agencia = String(a.agencia || "").trim();
        const nombreAgencia = String(a.nombreAgencia || agencia || "").trim();

        const prev = last[caseId];
        const prevEstado = prev && prev.estado ? prev.estado : "";
        const isNew = !prev;

        // Guardar snapshot siempre (para no repetir cambios)
        next[caseId] = {
          estado: estado,
          agencia: agencia,
          ts: Date.now(),
        };

        if (!shouldNotify(prevEstado, estado, isNew)) continue;

        // Data keys deben calzar con tu TecniAppMessagingService (caseId/estado/agencia/...) ✅
        const data = {
          caseId: caseId,
          estado: estado || "NUEVO",
          agencia: agencia,
          nombreAgencia: nombreAgencia,
          region: String(a.region || ""),
          descripcion: String(a.observaciones || ""), // ✅ observaciones
          localizacion: "", // tu API no trae "localizacion" como tal
          nise: String(a.nise || ""),
          causa: String(a.causa || ""),
          clientesAfectados: String(a.clientesAfectados || ""),
          lat: String(a.latitud || ""),  // ✅ latitud
          lng: String(a.longitud || ""), // ✅ longitud
          agenciaTag: agencia,           // para filtros
        };

        toNotify.push({ caseId, agencia, estado, data });
      }

      // Guardar snapshot actualizado
      await snapRef.set(next);
      console.log("Averias para notificar:", toNotify.length);

      if (!toNotify.length) return;

      // 3) Leer usuarios con tokens
      const usersSnap = await db.ref("usuarios").get();
      const users = usersSnap.exists() ? usersSnap.val() : {};

      // 4) Enviar FCM por agencia (ajusta a subregion si lo usas)
      for (const item of toNotify) {
        const tokens = [];

        for (const uid of Object.keys(users)) {
          const u = users[uid];
          const token = u?.fcmToken;
          if (!token) continue;

          // matching por agenciaId/agencia (normalizado)
          const uAgencia = norm(u.agenciaId || u.agencia || "");
          const aAgencia = norm(item.agencia);

          if (uAgencia && aAgencia && uAgencia === aAgencia) {
            tokens.push(token);
          }
        }

        if (!tokens.length) {
          console.log(`Sin tokens para caseId=${item.caseId} agencia=${item.agencia}`);
          continue;
        }

        const res = await admin.messaging().sendEachForMulticast({
          tokens,
          data: item.data,
          notification: {
            title: `Avería ${item.data.caseId} (${item.data.estado})`,
            body: item.data.descripcion || item.data.nombreAgencia || "Nueva avería",
          },
        });

        console.log(
          `FCM enviado caseId=${item.caseId} ok=${res.successCount} fail=${res.failureCount}`
        );
      }
    } catch (e) {
      console.error("syncAveriasYNotificar ERROR:", e);
    }
  }
);
