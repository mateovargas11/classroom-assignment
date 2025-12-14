import os
import pandas as pd
from scipy.stats import kruskal

# ===============================
# Configuración
# ===============================

BASE_DIR = "output/"
CSV_NAME = "promedio_2024_hypervolumes_normality_test.csv"
ALPHA = 0.05

p_cruzamientos = ["06", "07", "08"]
p_mutaciones = ["1", "01", "001"]
population_sizes = ["50", "100", "200"]

# ===============================
# Cargar datos
# ===============================

groups = []
group_names = []

for pc in p_cruzamientos:
    for pm in p_mutaciones:
        for pop in population_sizes:

            dir_name = f"promedio_{pc}_{pm}_{pop}"
            csv_path = os.path.join(BASE_DIR, dir_name, CSV_NAME)

            if not os.path.isfile(csv_path):
                print(f"[WARN] CSV no encontrado: {csv_path}")
                continue

            df = pd.read_csv(csv_path)

            if "Hypervolume" not in df.columns:
                print(f"[ERROR] Columna 'Hypervolume' no encontrada en {csv_path}")
                continue

            hv = df["Hypervolume"].values

            if len(hv) == 0:
                print(f"[WARN] Sin datos en {dir_name}")
                continue

            groups.append(hv)
            group_names.append(dir_name)

# ===============================
# Validación mínima
# ===============================

if len(groups) < 2:
    raise RuntimeError("Se requieren al menos dos grupos para Kruskal–Wallis")

print(f"Grupos cargados correctamente: {len(groups)}")

# ===============================
# Kruskal–Wallis
# ===============================

statistic, p_value = kruskal(*groups)

print("\n" + "=" * 60)
print("TEST DE KRUSKAL–WALLIS")
print("=" * 60)
print(f"Estadístico H = {statistic:.5f}")
print(f"p-value       = {p_value:.5e}")

if p_value < ALPHA:
    print("\nResultado: HAY diferencias estadísticamente significativas entre configuraciones")
else:
    print("\nResultado: NO se detectan diferencias estadísticamente significativas")

print("=" * 60)

