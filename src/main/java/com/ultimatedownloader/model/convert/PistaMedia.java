package com.ultimatedownloader.model.convert;

import com.ultimatedownloader.util.Formatos;
import com.ultimatedownloader.util.Idiomas;
import com.ultimatedownloader.util.Textos;

/**
 * Una pista dentro de un archivo: vídeo, audio o subtítulos. Lo que no venga de ffprobe queda
 * a 0 o a null.
 *
 * «indiceEnTipo» es la posición entre las de su mismo tipo, que es lo que quiere -map 0:a:N.
 */
public record PistaMedia(
        int indice,
        int indiceEnTipo,
        TipoPista tipo,
        String codec,
        String perfil,
        int ancho,
        int alto,
        double fps,
        String formatoPixel,
        int bits,
        int frecuenciaHz,
        int canales,
        String disposicion,
        String formatoMuestra,
        long bitrate,
        String idioma,
        String titulo) {

    public String resolucionTexto() {
        return ancho > 0 && alto > 0 ? ancho + "×" + alto : "—";
    }

    public String frecuenciaTexto() {
        return frecuenciaHz > 0
                ? String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f kHz",
                        frecuenciaHz / 1000.0)
                : "—";
    }

    public String canalesTexto() {
        if (disposicion != null && !disposicion.isBlank()) {
            return switch (disposicion) {
                case "mono" -> Textos.t("canales.mono");
                case "stereo" -> Textos.t("canales.estereo");
                default -> disposicion;
            };
        }
        return switch (canales) {
            case 0 -> "—";
            case 1 -> Textos.t("canales.mono");
            case 2 -> Textos.t("canales.estereo");
            default -> Textos.t("canales.varios", canales);
        };
    }

    public String idiomaTexto() {
        if (idioma == null || idioma.isBlank() || "und".equalsIgnoreCase(idioma)) {
            return null;
        }
        String nombre = Idiomas.nombre(idioma);
        return nombre != null ? nombre : idioma;
    }

    /**
     * Profundidad de bits efectiva del audio, deducida del formato de muestra cuando ffprobe
     * no la declara: s16 son 16 bits, s32 y flt son 32.
     */
    public int bitsEfectivos() {
        if (bits > 0) {
            return bits;
        }
        if (formatoMuestra == null) {
            return 0;
        }
        String f = formatoMuestra.toLowerCase(java.util.Locale.ROOT).replace("p", "");
        return switch (f) {
            case "u8", "s8" -> 8;
            case "s16" -> 16;
            case "s32", "flt" -> 32;
            case "dbl" -> 64;
            default -> 0;
        };
    }

    /**
     * En un códec con pérdida los «bits» que anuncia ffprobe son el formato interno del
     * decodificador, no lo que hay guardado: enseñarlos haría creer que un AAC es de 32 bits.
     */
    public boolean sinPerdida() {
        if (codec == null) {
            return false;
        }
        String c = codec.toLowerCase(java.util.Locale.ROOT);
        return c.startsWith("pcm_") || c.startsWith("dsd_")
                || switch (c) {
                    case "flac", "alac", "wavpack", "tta", "truehd", "mlp", "ape", "shorten" -> true;
                    default -> false;
                };
    }

    /** Resumen de una línea para la ficha del archivo. */
    public String resumen() {
        StringBuilder sb = new StringBuilder();
        sb.append(tipo.nombreLegible()).append(": ").append(codec != null ? codec : "?");
        switch (tipo) {
            case VIDEO -> {
                sb.append(" · ").append(resolucionTexto());
                if (fps > 0) {
                    sb.append(" · ").append(Formatos.fps(fps)).append(" fps");
                }
                if (formatoPixel != null) {
                    sb.append(" · ").append(formatoPixel);
                }
            }
            case AUDIO -> {
                sb.append(" · ").append(frecuenciaTexto()).append(" · ").append(canalesTexto());
                if (sinPerdida() && bitsEfectivos() > 0) {
                    sb.append(" · ").append(bitsEfectivos()).append(" bits");
                }
            }
            default -> {
                // Para subtítulos y demás basta con el códec.
            }
        }
        if (bitrate > 0) {
            sb.append(" · ").append(Formatos.bitrate(bitrate / 1000.0));
        }
        String idiomaTexto = idiomaTexto();
        if (idiomaTexto != null) {
            sb.append(" · ").append(idiomaTexto);
        }
        return sb.toString();
    }
}
