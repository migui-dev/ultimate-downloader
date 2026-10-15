package com.ultimatedownloader.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * Ajustes que se guardan entre sesiones. POJO mutable a propósito: Jackson lo lee y lo
 * escribe directo, y los valores por defecto son los de los campos, así que un fichero
 * antiguo al que le falten claves se carga igual.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Settings {

    /** Última carpeta de destino usada. */
    private String carpetaDestino;

    /** Carpetas recientes, de la más reciente a la más antigua. */
    private List<String> carpetasRecientes = new ArrayList<>();

    /** Plantilla del nombre de archivo, con marcadores entre llaves. */
    private String plantillaNombre = "{titulo}";

    private DownloadMode modoPorDefecto = DownloadMode.VIDEO_Y_AUDIO;
    private VideoContainer contenedorPorDefecto = VideoContainer.MKV;
    private AudioOutputFormat formatoAudioPorDefecto = AudioOutputFormat.ORIGINAL;

    /** true para el tema oscuro. */
    private boolean temaOscuro = true;

    private int descargasSimultaneas = 2;
    private int fragmentosSimultaneos = 4;

    private boolean incrustarMiniatura = true;
    private boolean incrustarMetadatos = true;
    private boolean descargarSubtitulos;
    private boolean subtitulosAutomaticos;
    private boolean incrustarSubtitulos = true;
    private List<String> idiomasSubtitulos = new ArrayList<>(List.of("es"));

    private CookieBrowser navegadorCookies = CookieBrowser.NINGUNO;
    private String rutaPerfilCookies;

    /** Rutas indicadas a mano para los binarios; null para buscarlos automáticamente. */
    private String rutaYtDlp;
    private String rutaFfmpeg;

    /** Avisar cuando el contenedor elegido no admita los códecs seleccionados. */
    private boolean avisarIncompatibilidad = true;

    /** Código ISO del idioma, o «sistema» para seguir al de Windows. */
    private String idioma = "sistema";

    /** Modelos de separación marcados como favoritos, por nombre de fichero. */
    private List<String> modelosFavoritos = new ArrayList<>();

    public String getCarpetaDestino() {
        return carpetaDestino;
    }

    public void setCarpetaDestino(String carpetaDestino) {
        this.carpetaDestino = carpetaDestino;
    }

    public List<String> getCarpetasRecientes() {
        return carpetasRecientes;
    }

    public void setCarpetasRecientes(List<String> carpetasRecientes) {
        this.carpetasRecientes = carpetasRecientes != null ? carpetasRecientes : new ArrayList<>();
    }

    public String getPlantillaNombre() {
        return plantillaNombre;
    }

    public void setPlantillaNombre(String plantillaNombre) {
        this.plantillaNombre = plantillaNombre;
    }

    public DownloadMode getModoPorDefecto() {
        return modoPorDefecto;
    }

    public void setModoPorDefecto(DownloadMode modoPorDefecto) {
        this.modoPorDefecto = modoPorDefecto != null ? modoPorDefecto : DownloadMode.VIDEO_Y_AUDIO;
    }

    public VideoContainer getContenedorPorDefecto() {
        return contenedorPorDefecto;
    }

    public void setContenedorPorDefecto(VideoContainer contenedorPorDefecto) {
        this.contenedorPorDefecto = contenedorPorDefecto != null ? contenedorPorDefecto : VideoContainer.MKV;
    }

    public AudioOutputFormat getFormatoAudioPorDefecto() {
        return formatoAudioPorDefecto;
    }

    public void setFormatoAudioPorDefecto(AudioOutputFormat formatoAudioPorDefecto) {
        this.formatoAudioPorDefecto = formatoAudioPorDefecto != null
                ? formatoAudioPorDefecto
                : AudioOutputFormat.ORIGINAL;
    }

    public boolean isTemaOscuro() {
        return temaOscuro;
    }

    public void setTemaOscuro(boolean temaOscuro) {
        this.temaOscuro = temaOscuro;
    }

    public int getDescargasSimultaneas() {
        return descargasSimultaneas;
    }

    public void setDescargasSimultaneas(int descargasSimultaneas) {
        this.descargasSimultaneas = Math.clamp(descargasSimultaneas, 1, 8);
    }

    public int getFragmentosSimultaneos() {
        return fragmentosSimultaneos;
    }

    public void setFragmentosSimultaneos(int fragmentosSimultaneos) {
        this.fragmentosSimultaneos = Math.clamp(fragmentosSimultaneos, 1, 16);
    }

    public boolean isIncrustarMiniatura() {
        return incrustarMiniatura;
    }

    public void setIncrustarMiniatura(boolean incrustarMiniatura) {
        this.incrustarMiniatura = incrustarMiniatura;
    }

    public boolean isIncrustarMetadatos() {
        return incrustarMetadatos;
    }

    public void setIncrustarMetadatos(boolean incrustarMetadatos) {
        this.incrustarMetadatos = incrustarMetadatos;
    }

    public boolean isDescargarSubtitulos() {
        return descargarSubtitulos;
    }

    public void setDescargarSubtitulos(boolean descargarSubtitulos) {
        this.descargarSubtitulos = descargarSubtitulos;
    }

    public boolean isSubtitulosAutomaticos() {
        return subtitulosAutomaticos;
    }

    public void setSubtitulosAutomaticos(boolean subtitulosAutomaticos) {
        this.subtitulosAutomaticos = subtitulosAutomaticos;
    }

    public boolean isIncrustarSubtitulos() {
        return incrustarSubtitulos;
    }

    public void setIncrustarSubtitulos(boolean incrustarSubtitulos) {
        this.incrustarSubtitulos = incrustarSubtitulos;
    }

    public List<String> getIdiomasSubtitulos() {
        return idiomasSubtitulos;
    }

    public void setIdiomasSubtitulos(List<String> idiomasSubtitulos) {
        this.idiomasSubtitulos = idiomasSubtitulos != null ? idiomasSubtitulos : new ArrayList<>();
    }

    public CookieBrowser getNavegadorCookies() {
        return navegadorCookies;
    }

    public void setNavegadorCookies(CookieBrowser navegadorCookies) {
        this.navegadorCookies = navegadorCookies != null ? navegadorCookies : CookieBrowser.NINGUNO;
    }

    public String getRutaPerfilCookies() {
        return rutaPerfilCookies;
    }

    public void setRutaPerfilCookies(String rutaPerfilCookies) {
        this.rutaPerfilCookies = rutaPerfilCookies;
    }

    public String getRutaYtDlp() {
        return rutaYtDlp;
    }

    public void setRutaYtDlp(String rutaYtDlp) {
        this.rutaYtDlp = rutaYtDlp;
    }

    public String getRutaFfmpeg() {
        return rutaFfmpeg;
    }

    public void setRutaFfmpeg(String rutaFfmpeg) {
        this.rutaFfmpeg = rutaFfmpeg;
    }

    public boolean isAvisarIncompatibilidad() {
        return avisarIncompatibilidad;
    }

    public String getIdioma() {
        return idioma;
    }

    public void setIdioma(String idioma) {
        this.idioma = idioma != null && !idioma.isBlank() ? idioma : "sistema";
    }

    public List<String> getModelosFavoritos() {
        return modelosFavoritos;
    }

    public void setModelosFavoritos(List<String> modelosFavoritos) {
        this.modelosFavoritos = modelosFavoritos != null ? modelosFavoritos : new ArrayList<>();
    }

    public void setAvisarIncompatibilidad(boolean avisarIncompatibilidad) {
        this.avisarIncompatibilidad = avisarIncompatibilidad;
    }

    /** Añade una carpeta al principio de las recientes, sin repetir y con un máximo de ocho. */
    public void registrarCarpetaReciente(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            return;
        }
        carpetasRecientes.removeIf(r -> r.equalsIgnoreCase(ruta));
        carpetasRecientes.add(0, ruta);
        while (carpetasRecientes.size() > 8) {
            carpetasRecientes.remove(carpetasRecientes.size() - 1);
        }
    }
}
