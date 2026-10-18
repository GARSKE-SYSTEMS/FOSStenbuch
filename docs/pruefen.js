// Prüfseite (pruefen.html): prüft PDF-Signaturen, Siegel und Zeitstempel von FOSStenbuch nur im Browser –
// ohne Hochladen, ohne Netzwerkanfragen, ohne fremde Skripte.
// Formate wie in der App, Byte für Byte: Siegel = logic/Seals.kt, PDF-Anhang = logic/export/Export.kt (pdfTrailer),
// Zeitstempel = logic/Tsa.kt.
'use strict';

const enc = new TextEncoder();

// ── Hilfen ───────────────────────────────────────────────────────────────────

const hex = b => Array.from(b, x => x.toString(16).padStart(2, '0')).join('');
const unhex = s => Uint8Array.from(String(s).match(/../g) || [], h => parseInt(h, 16));
const unb64 = s => Uint8Array.from(atob(s), c => c.charCodeAt(0)); // wirft bei ungültigem base64
const same = (a, b) => a.length === b.length && a.every((x, i) => x === b[i]);
const concat = (...parts) => { const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0)); let i = 0; for (const p of parts) out.set(p, i), i += p.length; return out; };
const sha = async (hash, data) => new Uint8Array(await crypto.subtle.digest(hash, data));
const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
// Mit Zeitzone: Das PDF zeigt dieselben Zeitpunkte in der Zeitzone des Handys
const time = ms => new Date(ms).toLocaleString('de-DE', { timeZoneName: 'short' });
const mark = (good, text) => `<span class="${good ? 'ok' : 'bad'}">${good ? '✓' : '✗'} ${text}</span>`;
const warn = text => `<span class="warn">⚠ ${text}</span>`;

/** Fingerabdruck eines öffentlichen Schlüssels (SPKI, base64) wie Seals.fingerprint: erste 16 Byte von SHA-256, Vierergruppen */
const fingerprint = async pub => hex((await sha('SHA-256', unb64(pub))).subarray(0, 16)).toUpperCase().match(/.{4}/g).join(' ');

// ── DER (für Signaturen, Zeitstempel und Zertifikate) ───────────────────────

/** DER-Element ab b[at]: tag, content (Inhalt), encoded (ganzes Element), kids (enthaltene Elemente) */
function der(b, at = 0) {
  if (at + 2 > b.length) throw new Error('DER-Daten zu kurz');
  let i = at + 1, len = b[i++];
  if (len & 0x80) {
    const n = len & 0x7f;
    if (n < 1 || n > 4) throw new Error('ungültige DER-Länge');
    len = 0;
    for (let k = 0; k < n; k++) len = len * 256 + b[i++];
  }
  const start = i, end = i + len;
  if (end > b.length) throw new Error('DER-Daten zu kurz');
  return {
    tag: b[at], end, content: b.subarray(start, end), encoded: b.subarray(at, end),
    get kids() { const list = []; for (let p = start; p < end; p = list[list.length - 1].end) list.push(der(b, p)); return list; }
  };
}

function oid(e) {
  const c = e.content, parts = c[0] < 80 ? [Math.floor(c[0] / 40), c[0] % 40] : [2, c[0] - 80];
  for (let i = 1, v = 0; i < c.length; i++) {
    v = v * 128 + (c[i] & 0x7f);
    if (c[i] < 0x80) { parts.push(v); v = 0; }
  }
  return parts.join('.');
}

const HASHES = { '2.16.840.1.101.3.4.2.1': 'SHA-256', '2.16.840.1.101.3.4.2.2': 'SHA-384', '2.16.840.1.101.3.4.2.3': 'SHA-512' };
const CURVES = { '1.2.840.10045.3.1.7': ['P-256', 32], '1.3.132.0.34': ['P-384', 48], '1.3.132.0.35': ['P-521', 66] };
const P256 = CURVES['1.2.840.10045.3.1.7'];
/** Signaturverfahren in CMS: [RSA oder EC, Hash] – null = Hash aus digestAlgorithm (z.B. Sectigo: rsaEncryption) */
const SIGNATURES = {
  '1.2.840.113549.1.1.1': ['RSA', null], '1.2.840.113549.1.1.11': ['RSA', 'SHA-256'],
  '1.2.840.113549.1.1.12': ['RSA', 'SHA-384'], '1.2.840.113549.1.1.13': ['RSA', 'SHA-512'],
  '1.2.840.10045.2.1': ['EC', null], '1.2.840.10045.4.3.2': ['EC', 'SHA-256'],
  '1.2.840.10045.4.3.3': ['EC', 'SHA-384'], '1.2.840.10045.4.3.4': ['EC', 'SHA-512']
};
const NAMES = {
  '2.5.4.3': 'CN', '2.5.4.5': 'SERIALNUMBER', '2.5.4.6': 'C', '2.5.4.7': 'L', '2.5.4.8': 'ST', '2.5.4.10': 'O',
  '2.5.4.11': 'OU', '2.5.4.13': 'description', '2.5.4.97': 'organizationIdentifier', '1.2.840.113549.1.9.1': 'E'
};

/** Distinguished Name als Text, z.B. "C=DE, O=Free TSA, CN=www.freetsa.org" (BMPString ist UTF-16) */
const dn = e => e.kids.flatMap(rdn => rdn.kids).map(a =>
  `${NAMES[oid(a.kids[0])] || oid(a.kids[0])}=${new TextDecoder(a.kids[1].tag === 0x1e ? 'utf-16be' : 'utf-8').decode(a.kids[1].content)}`
).join(', ');

/** ECDSA-Signatur DER (SEQUENCE { r, s }) → r||s mit fester Länge, wie WebCrypto sie erwartet */
function raw(sig, size) {
  const out = new Uint8Array(2 * size);
  der(sig).kids.forEach((n, i) => {
    let v = n.content;
    while (v.length > size && v[0] === 0) v = v.subarray(1);
    if (v.length > size || i > 1) throw new Error('ungültige Signatur');
    out.set(v, (i + 1) * size - v.length);
  });
  return out;
}

async function verifyEc(spki, [curve, size], hash, sig, data) {
  const key = await crypto.subtle.importKey('spki', spki, { name: 'ECDSA', namedCurve: curve }, false, ['verify']);
  return crypto.subtle.verify({ name: 'ECDSA', hash }, key, raw(sig, size), data);
}

/** Signatur der App: ECDSA P-256 mit SHA-256; kaputte Schlüssel oder Signaturen gelten als ungültig */
async function verifyApp(publicKey, signature, data) {
  try {
    return await verifyEc(unb64(publicKey), P256, 'SHA-256', unb64(signature), data);
  } catch {
    return false;
  }
}

// ── PDF ──────────────────────────────────────────────────────────────────────

const latin1 = b => new TextDecoder('iso-8859-1').decode(b); // ein Zeichen je Byte: Index im Text = Index im PDF
const MARKER = '\n%FOSStenbuch-Signatur ';
/**
 * Rest ab der Markierung, genau so wie Export.pdfTrailer ihn schreibt (ExportTest prüft das gegen diesen Ausdruck).
 * Bewusst kein JSON.parse: Zusätzliche Felder oder Angehängtes könnten eine unsignierte PDF-Änderung
 * (Objekte, xref, startxref) enthalten, die PDF-Programme trotzdem anwenden.
 */
const TRAILER = /^\{"v":1,"alg":"ECDSA-P256-SHA256","pub":"([A-Za-z0-9+\/]+={0,2})","sig":"([A-Za-z0-9+\/]+={0,2})"\}\nstartxref\n(\d+)\n%%EOF\n$/;

/**
 * Signatur am Ende eines PDFs (Export.pdfTrailer): signiert sind alle Bytes vor der LETZTEN Markierung, danach folgt
 * nur noch startxref mit demselben Wert wie im PDF selbst. Liefert { state: 'ok' | 'bad' | 'none', html, end } –
 * end = Ende des signierten Teils (nur bei gültiger Signatur).
 */
async function checkSignature(b, text) {
  const at = text.lastIndexOf(MARKER);
  if (at < 0) return { state: 'none' };
  const m = TRAILER.exec(text.slice(at + MARKER.length));
  if (!m) return { state: 'bad', html: 'Signaturzeile unbekannt oder verändert, oder nach der Signatur wurde etwas angehängt – der angezeigte Inhalt kann verändert sein.' };
  const fp = `Schlüssel-Fingerabdruck: <code>${await fingerprint(m[1])}</code>`;
  if (!await verifyApp(m[1], m[2], b.subarray(0, at))) {
    return { state: 'bad', html: `Signatur ungültig – das PDF wurde nach dem Signieren verändert oder die Signatur ist beschädigt.<br>${fp}` };
  }
  if ([...text.slice(0, at).matchAll(/startxref\s+(\d+)\s+%%EOF/g)].pop()?.[1] !== m[3]) {
    return { state: 'bad', html: `Das Dateiende wurde nach dem Signieren verändert (startxref).<br>${fp}` };
  }
  return { state: 'ok', html: `Signatur gültig – das PDF ist seit dem Signieren unverändert.<br>${fp}`, end: at };
}

const PROOF = '\n%FOSStenbuch-Pruefdaten\n';

/** Prüfdaten (Export.pdfTrailer): base64-Kommentarzeilen nach der letzten Markierung vor [end], zlib, JSON; null = keine */
async function readProof(text, end) {
  const at = text.lastIndexOf(PROOF, end - PROOF.length);
  if (at < 0) return null;
  let b64 = '';
  for (const line of text.slice(at + PROOF.length, end).split('\n')) {
    if (!/^%[A-Za-z0-9+\/]+={0,2}$/.test(line)) break;
    b64 += line.slice(1);
  }
  if (!window.DecompressionStream) throw new Error('Der Browser kann die Prüfdaten nicht entpacken – bitte einen aktuellen Browser verwenden.');
  let root = null;
  try {
    root = JSON.parse(await new Response(new Blob([unb64(b64)]).stream().pipeThrough(new DecompressionStream('deflate'))).text());
  } catch { /* unten gemeldet */ }
  if (root?.format !== 'FOSStenbuch-Pruefdaten' || root.version !== 1 || !Array.isArray(root.trips)) throw new Error('Prüfdaten im PDF unbekannt oder beschädigt');
  // Die App schreibt weder automatische noch doppelte Fahrten und Nummern und Zeiten nur als Zahl (1 und "1" ergäben
  // dasselbe Siegel) – sonst ließe sich eine Fahrt vor der Prüfung verstecken oder ein Siegel doppelt verwenden
  const int = v => Number.isSafeInteger(v), bad = t => t?.isGhost || !int(t?.id) || !int(t.date) || !(t.endTime == null || int(t.endTime)) || typeof t.isCancelled !== 'boolean';
  if (root.ghostTrips?.length || root.trips.some(bad) || (root.seals || []).some(s => !int(s?.tripId)) || new Set(root.trips.map(t => t.id)).size !== root.trips.length) {
    throw new Error('Prüfdaten im PDF ungültig (automatische, doppelte oder fehlerhafte Fahrten) – das PDF wurde verändert');
  }
  return root;
}

/** Ohne Signatur: Folgt auf die Prüfdaten genau das Dateiende aus Export.pdfTrailer (startxref wie im PDF)? */
const PROOF_END = /^(?:%[A-Za-z0-9+\/]+={0,2}\n)+\nstartxref\n(\d+)\n%%EOF\n$/;
function endsAsExported(text) {
  const at = text.lastIndexOf(PROOF), m = PROOF_END.exec(text.slice(at + PROOF.length));
  return !!m && [...text.slice(0, at).matchAll(/startxref\s+(\d+)\s+%%EOF/g)].pop()?.[1] === m[1];
}

// ── Zeitstempel (RFC 3161) ───────────────────────────────────────────────────

/** Zertifikat: Seriennummer, Aussteller, Inhaber, SPKI, Schlüssel-ID (Erweiterung 2.5.29.14) und eigene Signatur */
function certificate(c) {
  const tbs = c.kids[0].kids, o = tbs[0].tag === 0xa0 ? 1 : 0;
  const ext = tbs.find(e => e.tag === 0xa3)?.kids[0].kids.find(e => oid(e.kids[0]) === '2.5.29.14');
  return {
    der: c.encoded, serial: tbs[o].content, issuer: tbs[o + 2], subject: tbs[o + 4], spki: tbs[o + 5],
    tbs: c.kids[0].encoded, alg: oid(c.kids[1].kids[0]), sig: c.kids[2].content.subarray(1), // BIT STRING ohne Füllbyte-Angabe
    keyId: ext && der(ext.kids[ext.kids.length - 1].content).content
  };
}

/**
 * Vertrauensanker der in der App vorgegebenen Dienste (SHA-256 des Zertifikats). Sectigo Qualified liefert seine
 * Wurzel nicht mit, deshalb ist dort die Zwischen-CA der Anker. Neue Anker hier und in der App-Liste nachtragen.
 */
const ANCHORS = {
  A6379E7CECC05FAA3CBF076013D745E327BBBAA38C0B9AF22469D4701D18AABC: 'FreeTSA',
  B53AC15CC1AFB6E2AC06828F555BB3BF5BAD8B2BAC1733CE4CB7AAFE729356DE: 'Sectigo',
  CD0A3E00A1BDAE3A159FA9E3D70F8E664F560FDCE16357CBE440ED0B0D88244F: 'Sectigo Qualified (eIDAS)'
};

/** Ist [c] mit dem Schlüssel von [issuer] signiert? */
async function signedBy(c, issuer) {
  const [kind, hash] = SIGNATURES[c.alg] || [];
  if (!kind || !hash) return false;
  try {
    if (kind === 'EC') return await verifyEc(issuer.spki.encoded, CURVES[oid(issuer.spki.kids[0].kids[1])], hash, c.sig, c.tbs);
    const alg = { name: 'RSASSA-PKCS1-v1_5', hash };
    return await crypto.subtle.verify(alg, await crypto.subtle.importKey('spki', issuer.spki.encoded, alg, false, ['verify']), c.sig, c.tbs);
  } catch {
    return false;
  }
}

/** Name des bekannten Dienstes, auf dessen Anker die Kette aus den Zertifikaten des Zeitstempels führt, sonst null */
async function anchor(cert, all) {
  for (let c = cert, i = 0; c && i < 5; i++) {
    const name = ANCHORS[hex(await sha('SHA-256', c.der)).toUpperCase()];
    if (name) return name;
    const issuer = all.find(x => x !== c && same(x.subject.encoded, c.issuer.encoded));
    c = issuer && await signedBy(c, issuer) ? issuer : null;
  }
  return null;
}

/**
 * Prüft einen TimeStampToken (CMS ContentInfo) gegen den erwarteten Fingerabdruck [expected] (Seals.imprint):
 * messageImprint (SHA-256), messageDigest = Hash von eContent mit dem Verfahren aus digestAlgorithm, Signatur über
 * signedAttrs (mit SET-Tag 0x31) mit dem Zertifikat aus dem Token, Kette bis zu einem bekannten Anker (ANCHORS).
 * Gültigkeitszeiträume und Sperrlisten prüft erst openssl ts -verify.
 */
async function checkToken(token, expected) {
  const ci = der(token).kids;
  if (oid(ci[0]) !== '1.2.840.113549.1.7.2') throw new Error('kein CMS SignedData');
  const sd = ci[1].kids[0].kids; // version, digestAlgorithms, encapContentInfo, [0] Zertifikate, [1] CRLs, signerInfos
  const eContent = sd[2].kids[1].kids[0].content; // TSTInfo
  const tst = der(eContent).kids; // version, policy, messageImprint, serialNumber, genTime, …
  const imprint = tst[2].kids;
  const m = /^(\d{4})(\d\d)(\d\d)(\d\d)(\d\d)(\d\d)(?:\.(\d+))?Z$/.exec(new TextDecoder().decode(tst[4].content));
  if (!m) throw new Error('ungültige Zeitangabe');
  const r = {
    time: Date.UTC(m[1], m[2] - 1, m[3], m[4], m[5], m[6], ((m[7] || '') + '00').slice(0, 3)),
    imprint: HASHES[oid(imprint[0].kids[0])] === 'SHA-256' && same(imprint[1].content, expected)
  };
  const si = sd[sd.length - 1].kids[0].kids; // version, sid, digestAlgorithm, [0] signedAttrs, signatureAlgorithm, signature
  const hash = HASHES[oid(si[2].kids[0])];
  if (!hash || si[3].tag !== 0xa0) throw new Error('Hashverfahren nicht unterstützt oder ohne signierte Attribute');
  const md = si[3].kids.find(a => oid(a.kids[0]) === '1.2.840.113549.1.9.4');
  r.digest = !!md && same(md.kids[1].kids[0].content, await sha(hash, eContent));
  // Zertifikat des Unterzeichners: Aussteller + Seriennummer (sid v1) oder Schlüssel-ID ([0])
  const sid = si[1];
  const all = sd.filter(e => e.tag === 0xa0).flatMap(e => e.kids).filter(e => e.tag === 0x30).map(certificate);
  const cert = all.find(c => sid.tag === 0x30
    ? same(c.serial, sid.kids[1].content) && same(c.issuer.encoded, sid.kids[0].encoded)
    : c.keyId && same(c.keyId, sid.content));
  if (!cert) throw new Error('Zertifikat des Dienstes fehlt im Zeitstempel');
  r.subject = dn(cert.subject);
  r.issuer = dn(cert.issuer);
  r.cert = hex(await sha('SHA-256', cert.der)).toUpperCase().match(/../g).join(':');
  r.anchor = await anchor(cert, all);
  const [kind, sigHash] = SIGNATURES[oid(si[4].kids[0])] || [];
  if (!kind) throw new Error(`Signaturverfahren ${oid(si[4].kids[0])} nicht unterstützt`);
  const signed = new Uint8Array(si[3].encoded);
  signed[0] = 0x31; // signiert ist die DER-Form als SET, nicht das [0] IMPLICIT im SignerInfo
  try {
    if (kind === 'RSA') {
      const alg = { name: 'RSASSA-PKCS1-v1_5', hash: sigHash || hash };
      const key = await crypto.subtle.importKey('spki', cert.spki.encoded, alg, false, ['verify']);
      r.signature = await crypto.subtle.verify(alg, key, si[5].content, signed);
    } else {
      const curve = CURVES[oid(cert.spki.kids[0].kids[1])];
      r.signature = !!curve && await verifyEc(cert.spki.encoded, curve, sigHash || hash, si[5].content, signed);
    }
  } catch {
    r.signature = false;
  }
  r.ok = r.imprint && r.digest && r.signature;
  return r;
}

// ── Siegel und Fahrten (Backup und PDF) ──────────────────────────────────────

const FORMAT = 'FOSStenbuch-Siegel-1';

/** Nutzdaten eines Siegels – Byte für Byte wie Seals.payload: je Feld "<UTF-8-Bytelänge>:<Wert>," bzw. "~," für null */
const payload = (t, kind, createdAt) => enc.encode([
  FORMAT, kind, createdAt, t.id, t.vehicleId, t.date, t.endTime, t.startLocation, t.endLocation,
  t.route, t.businessPartner, t.purpose, t.notes, t.startOdometer, t.endOdometer, t.isCancelled === true, t.cancellationReason
].map(v => v == null ? '~,' : `${enc.encode(String(v)).length}:${v},`).join(''));

/** Abstand als Text: "12 min", "3 Std.", "9 Tage" */
const after = ms => ms < 3600e3 ? `${Math.max(0, Math.round(ms / 60e3))} min` : ms < 2 * 86400e3 ? `${Math.round(ms / 3600e3)} Std.` : `${Math.round(ms / 86400e3)} Tage`;
/** Abfahrt – Ankunft, bei gleichem Tag die Ankunft nur mit Uhrzeit */
const span = t => t.endTime == null ? time(t.date) : `${time(t.date)} – ${new Date(t.date).toDateString() === new Date(t.endTime).toDateString()
  ? new Date(t.endTime).toLocaleTimeString('de-DE', { timeZoneName: 'short' }) : time(t.endTime)}`;
const WEEK = 7 * 86400e3, SKEW = 15 * 60e3; // erlaubte Abweichung zwischen der Uhr des Geräts und der des Dienstes

/**
 * Prüft ein Siegel gegen die Fahrt [t]. Gebunden ist es, wenn seine Nutzdaten zu den heutigen Daten passen oder zum
 * Stand vor dem Stornieren (das ändert nur Storno und Grund) und eine Signatur gültig ist – nur dann zählen Signatur und
 * Zeitstempel für diese Fahrt. Das jüngste Siegel muss passen. Liefert { good, html, unknown, ts, bound }.
 */
async function checkSeal(s, t, latest, keys, certs) {
  const parts = [`Siegel ${esc(s.id)}: ${esc(s.kind)}, Gerätezeit ${time(s.createdAt)}`];
  let good = true, unknown = false, ts = null, data = null, before = null;
  if (t) {
    const now = payload(t, s.kind, s.createdAt);
    before = !latest && t.isCancelled === true ? payload({ ...t, isCancelled: false, cancellationReason: null }, s.kind, s.createdAt) : null;
    for (const p of [now, before]) if (p && !data && hex(await sha('SHA-256', p)) === s.hash) data = p;
    if (latest) parts.push(data ? mark(true, 'Daten') : mark(good = false, 'Daten passen nicht (nach dem Siegeln verändert)'));
    else parts.push(data ? mark(true, data === before ? 'Daten vor dem Stornieren' : 'Daten') : 'früherer Stand (nicht gegen die heutigen Daten prüfbar)');
  } else parts.push(mark(good = false, 'Fahrt fehlt'));
  let bound = !!data;
  if (s.publicKey) {
    const fp = await fingerprint(s.publicKey).catch(() => 'ungültiger Schlüssel');
    keys.set(fp, (keys.get(fp) || 0) + 1);
    if (data && s.signature) {
      const valid = await verifyApp(s.publicKey, s.signature, data);
      good = good && valid;
      bound = valid;
      parts.push(mark(valid, `Signatur (${fp})`));
    } else if (s.signature) parts.push(`signiert (${fp})`);
  } else if (s.signature && data) parts.push(mark(good = bound = false, 'Signatur ohne Schlüssel'));
  if (s.tsToken) {
    let r;
    try {
      // Was der Dienst bestätigt hat (Seals.imprint): SHA-256(Hash + Signatur + Schlüssel), ohne Signatur der Siegel-Hash
      const expected = s.signature ? await sha('SHA-256', concat(unhex(s.hash), unb64(s.signature), unb64(s.publicKey || ''))) : unhex(s.hash);
      r = await checkToken(unb64(s.tsToken), expected);
      const host = (/^\w+:\/\/([^/:?#]+)/.exec(s.tsUrl || '') || [])[1];
      const at = `${time(r.time)}${host ? ` (${esc(host)})` : ''}`;
      certs.set(r.cert, { subject: r.subject, issuer: r.issuer, anchor: r.anchor, count: (certs.get(r.cert)?.count || 0) + 1 });
      if (s.tsTime != null && s.tsTime !== r.time) r.ok = false, r.error = 'gespeicherte Zeit weicht ab';
      const why = r.error || (!r.imprint ? 'gehört nicht zu diesem Siegel' : !r.digest || !r.signature ? 'Signatur des Dienstes ungültig' : '');
      unknown = r.ok && !r.anchor;
      if (r.ok && bound) ts = r.time;
      parts.push(unknown ? warn(`Zeitstempel ${at} – unbekannter Dienst (Zertifikat nicht auf FreeTSA oder Sectigo zurückführbar)`)
        : mark(r.ok, `Zeitstempel ${at}${r.ok ? ` · ${esc(r.anchor)}` : ''}${why ? ` – ${why}` : ''}`));
      parts.push(`<small>Fingerabdruck für openssl: <code>${hex(expected)}</code> · <a download="siegel-${esc(s.id)}.der" href="data:application/octet-stream;base64,${esc(s.tsToken)}">Zeitstempel speichern</a></small>`);
    } catch (e) {
      r = { ok: false };
      parts.push(mark(false, `Zeitstempel unlesbar (${esc(e.message)})`));
    }
    good = good && r.ok;
  }
  return { good, html: parts.join(' · '), unknown, ts, bound };
}

/** Prüft die Siegel aller Fahrten – aus einem Backup oder aus den Prüfdaten eines PDFs ([pdf]) – und liefert HTML */
async function checkTrips(root, pdf) {
  const trips = root.trips.concat(root.ghostTrips || []);
  // "withheld" (Notiz und Geschäftspartner einer Privatfahrt fehlen) gibt es nur in den Prüfdaten des PDFs
  const held = t => pdf && t?.withheld === true;
  const seals = (root.seals || []).slice().sort((a, b) => a.id - b.id);
  if (!seals.length && !pdf) return '<p>Dieses Backup enthält keine Siegel (nur Fahrten aus der Zeit vor den Siegeln oder ältere App-Version).</p>';
  const byTrip = new Map();
  for (const s of seals) {
    if (!byTrip.has(s.tripId)) byTrip.set(s.tripId, []);
    byTrip.get(s.tripId).push(s);
  }
  const plates = new Map((root.vehicles || []).map(v => [v.id, v.licensePlate]));
  const tripsById = new Map(trips.map(t => [t.id, t]));
  const keys = new Map(), certs = new Map(), rows = [];
  let ok = 0, bad = 0, warned = 0, partial = 0, none = 0, unstamped = 0;
  // Automatische (noch nicht übernommene) Fahrten gehören nicht ins Fahrtenbuch und haben keine Siegel
  const ids = [...new Set([...trips.filter(t => !t.isGhost).map(t => t.id), ...byTrip.keys()])];
  for (const id of ids) {
    const t = tripsById.get(id), list = byTrip.get(id) || [];
    // Im PDF die Nr. aus den Einzelnachweisen, im Backup die Fahrt-ID und das (nicht gesiegelte) Kennzeichen
    const info = (pdf && t ? `Nr. ${root.trips.indexOf(t) + 1}` : `Fahrt ${esc(id)}`) + ' · ' + (t
      ? `${span(t)} · ${pdf ? '' : `${esc(plates.get(t.vehicleId) ?? 'ohne Fahrzeug')} · `}${esc(t.startLocation)} → ${esc(t.endLocation)} · Km ${esc(t.startOdometer ?? '?')}–${esc(t.endOdometer ?? '?')}${t.isCancelled === true ? ' (storniert)' : ''}`
      : 'fehlt');
    if (!list.length) {
      held(t) ? partial++ : none++;
      rows.push([t.date, held(t) ? `<li>${warn('nur mit dem Backup prüfbar')} · ${info} · Notiz oder Geschäftspartner der Privatfahrt stehen nicht im PDF</li>`
        : `<li>– ${info} · kein Siegel</li>`]);
      continue;
    }
    const checked = [];
    for (const s of list) checked.push(await checkSeal(s, t, s === list[list.length - 1], keys, certs));
    const good = checked.every(c => c.good);
    // Zeitnähe: erster gültiger Zeitstempel eines an die Daten gebundenen Siegels – angezeigt ab der Ankunft, die
    // 7 Tage ab der Abfahrt (sonst ließe sich eine Fahrt mit zurückdatierter Abfahrt und heutiger Ankunft "zeitnah" stempeln)
    const stamped = checked.find(c => c.bound && c.ts != null)?.ts, delay = stamped != null ? stamped - (t.endTime ?? t.date) : null;
    const late = stamped != null && stamped - t.date > WEEK;
    if (stamped == null) unstamped++;
    // Ein unsigniertes Siegel kann jeder neu berechnen: Warnen, wenn das jüngste ohne Signatur oder mit einem
    // anderen Schlüssel als das vorige signierte entstand (Schlüssel entfernt/gewechselt – oder Daten verändert)
    const latest = list[list.length - 1], signed = list.slice(0, -1).filter(s => s.signature).pop();
    const warning = (signed && (!latest.signature ? 'jüngstes Siegel ohne Signatur'
      : latest.publicKey !== signed.publicKey ? 'jüngstes Siegel mit anderem Schlüssel' : ''))
      || (checked.some(c => c.unknown) ? 'Zeitstempel eines unbekannten Dienstes' : '')
      || (delay < -SKEW ? 'Zeitstempel vor der Ankunft (Uhr des Geräts falsch?)' : late ? 'Zeitstempel erst mehr als 7 Tage nach der Abfahrt' : '');
    const key = latest.signature && latest.publicKey ? ` · Schlüssel ${await fingerprint(latest.publicKey).catch(() => 'ungültig')}` : '';
    const when = delay == null ? ' · ohne Zeitstempel' : delay < -SKEW ? ` · Zeitstempel ${after(-delay)} vor Ankunft` : ` · Zeitstempel ${after(delay)} nach Ankunft`;
    !good ? bad++ : warning ? warned++ : ok++;
    const head = !good ? mark(false, 'nicht ok') : warning ? warn(warning) : mark(true, 'ok');
    rows.push([t ? t.date : Infinity,
      `<li><details><summary>${head} · ${info}${key}${when}</summary><ul>${checked.map(c => `<li>${c.html}</li>`).join('')}</ul></details></li>`]);
  }
  rows.sort((a, b) => a[0] - b[0] || 0);
  const list = (map, f) => map.size ? `<ul>${[...map].map(f).join('')}</ul>` : '<p>keine</p>';
  const from = root.exportedAt ? time(root.exportedAt) : '?';
  return `<p>${pdf ? `Prüfdaten im PDF vom ${from} (Gerätezeit) · ${root.trips.length} Fahrten` : `Backup vom ${from} (Gerätezeit)`} · ${seals.length} Siegel · ${mark(bad === 0, `${ok} Fahrten ok, ${bad} nicht ok`)}${warned ? ` · ${warn(`${warned} mit Warnung`)}` : ''}${partial ? ` · ${warn(`${partial} nur mit dem Backup prüfbar`)}` : ''} · ${none} ohne Siegel · ${unstamped} ohne Zeitstempel</p>
${pdf ? '<p>Geprüft werden die Daten in der PDF-Datei, nicht der gedruckte Text: Die Zahl der Fahrten muss mit dem Schlussvermerk des PDFs („Ende des Fahrtenbuchs – … Fahrten“) übereinstimmen; vergleichen Sie außerdem einige Fahrten (Nr., Datum, Km).</p>' : ''}
<p><strong>Signaturschlüssel</strong> (Fingerabdruck – mit dem des Fahrtenbuchführers vergleichen):</p>
${list(keys, ([fp, n]) => `<li><code>${fp}</code> – ${n} Siegel</li>`)}
<p><strong>Zertifikate der Zeitstempeldienste</strong> (Inhaber, Aussteller, SHA-256):</p>
${list(certs, ([fp, c]) => `<li>${c.anchor ? mark(true, esc(c.anchor)) : warn('unbekannter Dienst')} – ${esc(c.subject)}<br><small>Aussteller: ${esc(c.issuer)}<br>SHA-256: <code>${fp}</code> – ${c.count} Zeitstempel</small></li>`)}
<p><strong>Fahrten</strong> (antippen für die einzelnen Siegel):</p>
<ul class="trips">${rows.map(r => r[1]).join('')}</ul>`;
}

/** Prüft ein Backup (JSON) */
async function checkBackup(text) {
  let root = null;
  try { root = JSON.parse(text); } catch { /* unten gemeldet */ }
  if (!(root?.version >= 1) || !Array.isArray(root.trips)) throw new Error('Keine FOSStenbuch-Backup-Datei');
  return checkTrips(root, false);
}

// ── Seite ────────────────────────────────────────────────────────────────────

function wire(input, output, check) {
  document.getElementById(input).addEventListener('change', async e => {
    const out = document.getElementById(output), file = e.target.files[0];
    if (!file) return;
    out.innerHTML = '<p>Wird geprüft …</p>';
    try {
      if (!window.crypto?.subtle) throw new Error('Der Browser bietet hier keine Kryptografie an – bitte die Seite über https öffnen.');
      out.innerHTML = await check(file);
    } catch (err) {
      out.innerHTML = `<p>${mark(false, `Fehler: ${esc(err.message)}`)}</p>`;
    }
  });
}

wire('pdf', 'pdf-result', async f => {
  const b = new Uint8Array(await f.arrayBuffer()), text = latin1(b);
  const sig = await checkSignature(b, text);
  // Bei gültiger Signatur zählen nur Prüfdaten im signierten Teil
  const proof = await readProof(text, sig.end ?? text.length);
  if (sig.state === 'none' && !proof) {
    throw new Error('Weder Signatur noch Prüfdaten gefunden – es ist nicht das ausführliche PDF aus FOSStenbuch (oder aus einer älteren Version), oder die Datei wurde in einem PDF-Programm neu gespeichert (z. B. zum Unterschreiben), wobei beides verloren geht. Prüfbar ist nur die unveränderte Datei aus der App.');
  }
  const head = sig.state !== 'none' ? mark(sig.state === 'ok', sig.html)
    : !proof || endsAsExported(text) ? '– PDF nicht signiert (ohne Schlüssel exportiert oder Signatur entfernt). Die Fahrten sind trotzdem einzeln über ihre Siegel und Zeitstempel geprüft.'
    : warn('PDF nicht signiert, und nach dem Export wurde etwas angehängt (z. B. in einem PDF-Programm gespeichert) – der angezeigte Inhalt kann von den geprüften Daten abweichen.');
  return `<p>${head}</p>` + (proof ? await checkTrips(proof, true) : '<p>Keine Prüfdaten – die enthält nur das ausführliche PDF.</p>');
});
wire('backup', 'backup-result', async f => checkBackup(await f.text()));
