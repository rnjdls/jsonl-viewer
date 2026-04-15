export async function copyText(text) {
  const copyValue = typeof text === "string" ? text : String(text ?? "");

  if (typeof navigator !== "undefined" && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(copyValue);
      return;
    } catch {
      // Fall through to the legacy approach below.
    }
  }

  if (typeof document === "undefined" || !document.body) {
    throw new Error("Clipboard is unavailable in this environment.");
  }

  const textArea = document.createElement("textarea");
  textArea.value = copyValue;
  textArea.setAttribute("readonly", "");
  textArea.style.position = "fixed";
  textArea.style.opacity = "0";
  textArea.style.left = "-9999px";
  textArea.style.top = "0";

  document.body.appendChild(textArea);
  textArea.focus();
  textArea.select();
  textArea.setSelectionRange(0, textArea.value.length);

  const copied = document.execCommand("copy");
  textArea.remove();

  if (!copied) {
    throw new Error("Failed to copy text to clipboard.");
  }
}
