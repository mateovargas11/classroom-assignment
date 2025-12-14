import pandas as pd
import numpy as np
import re
from pathlib import Path

# ===== CONFIGURACIÓN =====
BASE_DIR = Path("output/diciembre2024_08_001_100/")   # ← acá apuntás al directorio
CSV_REGEX = re.compile(r"diciembre_2024_evolucion_.*\.csv")

# =========================

def collect_best_fitness(base_dir: Path):
    obj1 = []
    obj2 = []

    csv_files = sorted(
        f for f in base_dir.iterdir()
        if f.is_file() and CSV_REGEX.match(f.name)
    )

    if len(csv_files) == 0:
        raise RuntimeError("No se encontraron CSVs que matcheen la regex")

    for csv in csv_files:
        df = pd.read_csv(csv)

        # Última generación
        last_gen = df.iloc[-1]

        obj1.append(last_gen["mejor_fitness_obj1"])
        obj2.append(last_gen["mejor_fitness_obj2"])

    return np.array(obj1), np.array(obj2)

def compute_stats(values: np.ndarray):
    return {
        "media": np.mean(values),
        "desviacion_std": np.std(values, ddof=1),  # muestral
        "mejor": np.min(values),
        "peor": np.max(values),
    }

if __name__ == "__main__":
    fitness_obj1, fitness_obj2 = collect_best_fitness(BASE_DIR)

    stats_obj1 = compute_stats(fitness_obj1)
    stats_obj2 = compute_stats(fitness_obj2)

    print(f"Resultados agregados ({len(fitness_obj1)} ejecuciones)\n")

    print("Objetivo 1")
    for k, v in stats_obj1.items():
        print(f"  {k}: {v:.6f}")

    print("\nObjetivo 2")
    for k, v in stats_obj2.items():
        print(f"  {k}: {v:.6f}")

