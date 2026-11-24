import type {ScamAssessment} from '../types/family';

const SHORTENERS = /\\b(?:bit\\.ly|tinyurl\\.com|t\\.co|goo\\.gl|ow\\.ly|is\\.gd|cutt\\.ly|rb\\.gy|shorturl\\.at)\\b/i;
const URL = /https?:\\/\\/[^\\s]+|www\\.[^\\s]+/gi;
const URGENCY = /\\b(?:urgente|inmediato|bloquead[oa]|suspendid[oa]|último aviso|ultimo aviso|verifica tu cuenta|actividad sospechosa|cargo no reconocido|paquete retenido|entrega fallida|aduana|pago pendiente|reembolso|banco|transferencia|token|nip|contraseña|contraseña|código de seguridad|codigo de seguridad|otp)\\b/i;
const CREDENTIALS = /\\b(?:comparte|envía|manda|confirma|dicta)\\b.{0,45}\\b(?:código|codigo|nip|token|contraseña|password|clave)\\b/i;
export function assessMessage(text: string): ScamAssessment {
 const reasons: string[] = [];
 if (SHORTENERS.test(text)) reasons.push('Incluye un enlace acortado.');
 const urls = text.match(URL) || [];
 if (urls.some(url => /^http:\\/\\//i.test(url) || /(?:login|verify|secure|bank|pago|paquete|tracking)[-_]/i.test(url))) reasons.push('Incluye una dirección web que requiere precaución.');
 if (URGENCY.test(text)) reasons.push('Utiliza lenguaje de urgencia o relacionado con datos bancarios.');
 if (CREDENTIALS.test(text)) reasons.push('Pide compartir códigos o credenciales.');
 return {risky: reasons.length > 0, reasons, safeText: reasons.length ? text.replace(URL, '[ENLACE OCULTO POR SEGURIDAD]') : text};
}
