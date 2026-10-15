# Comprueba qué hay instalado de verdad en el entorno y con qué va a calcular.
#
# No basta con saber que hay una tarjeta NVIDIA: la rueda de PyTorch tiene que estar compilada
# para ella. Por eso se le pregunta a la propia biblioteca en lugar de deducirlo.

import json

datos = {
    "separador": None,
    "torch": None,
    "cuda": False,
    "directml": False,
    "dispositivo": None,
    "motivo": None,
}

try:
    import importlib.metadata as metadatos

    datos["separador"] = metadatos.version("audio-separator")
except Exception as error:
    datos["motivo"] = "audio-separator no está instalado: %s" % error

try:
    import torch

    datos["torch"] = torch.__version__
    if torch.cuda.is_available():
        datos["cuda"] = True
        datos["dispositivo"] = torch.cuda.get_device_name(0)
    else:
        # Esto es lo que de verdad importa: por qué no se puede usar la tarjeta.
        datos["motivo"] = "torch %s no ve ninguna tarjeta CUDA" % torch.__version__
except Exception as error:
    datos["motivo"] = "No se ha podido cargar torch: %s" % error

if not datos["cuda"]:
    try:
        import torch_directml

        if torch_directml.is_available():
            datos["directml"] = True
            datos["dispositivo"] = torch_directml.device_name(0)
            datos["motivo"] = None
    except Exception:
        pass

print("ENTORNO=" + json.dumps(datos))
