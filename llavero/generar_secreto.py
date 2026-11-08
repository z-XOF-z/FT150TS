"""Crea la clave secreta del llavero TamaXOF y copia el protocolo a cada firmware.

    python -I generar_secreto.py           # crea la clave solo si no existe y copia el protocolo
    python -I generar_secreto.py --nueva   # clave nueva (hay que volver a grabar llavero Y moto)

La clave (secreto_tamaxof.h) NO se sube a GitHub: está en .gitignore.
"""
import secrets
import shutil
import sys
from pathlib import Path

AQUI = Path(__file__).resolve().parent
DESTINOS = [
    AQUI / "firmware" / "tamaxof",
    AQUI / "moto" / "probador_moto",
    AQUI.parent / "firmware" / "ft150ts_dashboard",
]
MAESTRO = AQUI / "comun" / "secreto_tamaxof.h"


def main():
    if "--nueva" in sys.argv or not MAESTRO.exists():
        clave = secrets.token_bytes(32)
        filas = ", ".join(f"0x{b:02X}" for b in clave)
        MAESTRO.write_text(
            "#pragma once\n"
            "// Clave del llavero TamaXOF. NO SUBIR A GITHUB. Igual en el llavero y en la moto.\n"
            f"static const uint8_t TX_SECRETO[32] = {{{filas}}};\n",
            encoding="utf-8",
        )
        print("Clave nueva creada.")
    for d in DESTINOS:
        if not d.exists():
            continue
        ino = d / f"{d.name}.ino"
        if d.name == "ft150ts_dashboard" and "tamaxof_moto.h" not in ino.read_text(encoding="utf-8"):
            continue   # el firmware de la moto todavía no trae el llavero
        for f in ("secreto_tamaxof.h", "protocolo_tamaxof.h"):
            shutil.copy2(AQUI / "comun" / f, d / f)
        if d.name != "tamaxof":
            shutil.copy2(AQUI / "moto" / "tamaxof_moto.h", d / "tamaxof_moto.h")
        print("copiado a", d)


if __name__ == "__main__":
    main()
