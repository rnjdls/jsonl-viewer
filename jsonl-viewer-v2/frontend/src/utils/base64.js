/**
 * Attempts to decode a base64 string into human-readable text.
 *
 * Returns the decoded string if:
 *   - The input looks like valid base64 (charset + length).
 *   - At least 80% of the decoded bytes are printable characters.
 *
 * Returns `null` otherwise (binary data, too short, bad charset, etc.)
 *
 * @param {string} str
 * @returns {string | null}
 */
export function tryBase64Decode(str) {
  if (typeof str !== "string" || str.length < 4) return null;

  const clean = str.replace(/\s/g, "");

  // Must consist entirely of base64 characters (with optional padding).
  if (!/^[A-Za-z0-9+/]={0,2}$/.test(clean)) return null;

  try {
    const decoded = atob(clean);

    // Reject if mostly non-printable (binary payload).
    const printableCount = Array.from(decoded).filter((ch) => {
      const code = ch.charCodeAt(0);
      return code >= 32 || code === 9 || code === 10 || code === 13;
    }).length;

    if (printableCount / decoded.length < 0.8) return null;

    return decoded;
  } catch {
    return null;
  }
}
