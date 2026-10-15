package com.ultimatedownloader.ui;

import com.ultimatedownloader.model.separacion.OpcionesSeparacion;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;

/**
 * Panel plegable con lo que expone cada arquitectura. Lo que se deje en blanco no se le pasa
 * al motor: así nadie empeora un resultado por tocar una casilla sin saber.
 */
public final class SepararAvanzadoController {

    private Runnable alCambiar = () -> { };

    @FXML private TitledPane panelAvanzado;

    @FXML private TextField campoNormalizacion;
    @FXML private TextField campoAmplificacion;
    @FXML private TextField campoTrozo;
    @FXML private CheckBox casillaInvertirEspectro;
    @FXML private CheckBox casillaAutocast;
    @FXML private CheckBox casillaFp16;
    @FXML private CheckBox casillaTorchCompile;

    @FXML private TextField campoMdxSegmento;
    @FXML private TextField campoMdxSolape;
    @FXML private TextField campoMdxLote;
    @FXML private TextField campoMdxHop;
    @FXML private CheckBox casillaMdxDenoise;

    @FXML private TextField campoVrVentana;
    @FXML private TextField campoVrAgresividad;
    @FXML private TextField campoVrLote;
    @FXML private TextField campoVrUmbral;
    @FXML private CheckBox casillaVrTta;
    @FXML private CheckBox casillaVrAgudos;
    @FXML private CheckBox casillaVrPostProceso;

    @FXML private TextField campoDemucsSegmento;
    @FXML private TextField campoDemucsShifts;
    @FXML private TextField campoDemucsSolape;

    @FXML private TextField campoMdxcSegmento;
    @FXML private TextField campoMdxcSolape;
    @FXML private TextField campoMdxcLote;
    @FXML private TextField campoMdxcTono;
    @FXML private CheckBox casillaMdxcForzarSegmento;

    @FXML
    private void initialize() {
        for (TextField campo : campos()) {
            campo.textProperty().addListener((obs, antes, ahora) -> alCambiar.run());
        }
        for (CheckBox casilla : casillas()) {
            casilla.selectedProperty().addListener((obs, antes, ahora) -> alCambiar.run());
        }

        // El motor no deja usar las dos a la vez; se refleja aquí para no fallar al ejecutar.
        casillaAutocast.selectedProperty().addListener((obs, antes, activa) ->
                casillaFp16.setDisable(activa));
        casillaFp16.selectedProperty().addListener((obs, antes, activa) ->
                casillaAutocast.setDisable(activa));
    }

    /** Qué hacer cuando se toca cualquier opción; lo fija el módulo para rehacer su resumen. */
    public void alCambiar(Runnable accion) {
        this.alCambiar = accion != null ? accion : () -> { };
    }

    public TitledPane panel() {
        return panelAvanzado;
    }

    public OpcionesSeparacion opciones() {
        return new OpcionesSeparacion(
                Controles.decimal(campoNormalizacion), Controles.decimal(campoAmplificacion),
                casillaInvertirEspectro.isSelected(), casillaAutocast.isSelected(),
                casillaFp16.isSelected(), casillaTorchCompile.isSelected(),
                Controles.entero(campoTrozo),

                Controles.entero(campoMdxSegmento), Controles.decimal(campoMdxSolape),
                Controles.entero(campoMdxLote), Controles.entero(campoMdxHop),
                casillaMdxDenoise.isSelected(),

                Controles.entero(campoVrLote), Controles.entero(campoVrVentana),
                Controles.entero(campoVrAgresividad), casillaVrTta.isSelected(),
                casillaVrAgudos.isSelected(), casillaVrPostProceso.isSelected(),
                Controles.decimal(campoVrUmbral),

                Controles.entero(campoDemucsSegmento), Controles.entero(campoDemucsShifts),
                Controles.decimal(campoDemucsSolape),

                Controles.entero(campoMdxcSegmento), casillaMdxcForzarSegmento.isSelected(),
                Controles.entero(campoMdxcSolape), Controles.entero(campoMdxcLote),
                Controles.entero(campoMdxcTono));
    }

    public void aplicar(OpcionesSeparacion o) {
        Controles.escribir(campoNormalizacion, o.normalizacion());
        Controles.escribir(campoAmplificacion, o.amplificacion());
        Controles.escribir(campoTrozo, o.duracionTrozo());
        casillaInvertirEspectro.setSelected(o.invertirEspectro());
        casillaAutocast.setSelected(o.usarAutocast());
        casillaFp16.setSelected(o.usarFp16());
        casillaTorchCompile.setSelected(o.usarTorchCompile());

        Controles.escribir(campoMdxSegmento, o.mdxTamanoSegmento());
        Controles.escribir(campoMdxSolape, o.mdxSolapamiento());
        Controles.escribir(campoMdxLote, o.mdxLote());
        Controles.escribir(campoMdxHop, o.mdxHop());
        casillaMdxDenoise.setSelected(o.mdxDenoise());

        Controles.escribir(campoVrLote, o.vrLote());
        Controles.escribir(campoVrVentana, o.vrVentana());
        Controles.escribir(campoVrAgresividad, o.vrAgresividad());
        casillaVrTta.setSelected(o.vrTta());
        casillaVrAgudos.setSelected(o.vrAgudos());
        casillaVrPostProceso.setSelected(o.vrPostProceso());
        Controles.escribir(campoVrUmbral, o.vrUmbralPostProceso());

        Controles.escribir(campoDemucsSegmento, o.demucsSegmento());
        Controles.escribir(campoDemucsShifts, o.demucsShifts());
        Controles.escribir(campoDemucsSolape, o.demucsSolapamiento());

        Controles.escribir(campoMdxcSegmento, o.mdxcSegmento());
        casillaMdxcForzarSegmento.setSelected(o.mdxcForzarSegmento());
        Controles.escribir(campoMdxcSolape, o.mdxcSolapamiento());
        Controles.escribir(campoMdxcLote, o.mdxcLote());
        Controles.escribir(campoMdxcTono, o.mdxcTono());
    }

    @FXML
    private void restablecerAvanzado() {
        aplicar(OpcionesSeparacion.porDefecto());
    }

    private List<TextField> campos() {
        return List.of(campoNormalizacion, campoAmplificacion, campoTrozo,
                campoMdxSegmento, campoMdxSolape, campoMdxLote, campoMdxHop,
                campoVrVentana, campoVrAgresividad, campoVrLote, campoVrUmbral,
                campoDemucsSegmento, campoDemucsShifts, campoDemucsSolape,
                campoMdxcSegmento, campoMdxcSolape, campoMdxcLote, campoMdxcTono);
    }

    private List<CheckBox> casillas() {
        return List.of(casillaInvertirEspectro, casillaAutocast, casillaFp16,
                casillaTorchCompile, casillaMdxDenoise, casillaVrTta, casillaVrAgudos,
                casillaVrPostProceso, casillaMdxcForzarSegmento);
    }
}
