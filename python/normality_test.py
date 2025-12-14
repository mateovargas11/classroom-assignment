
import os
import pandas as pd
from scipy import stats

# ===============================
# Configuración
# ===============================

BASE_DIR = "output/"  # directorio raíz donde están las carpetas promedio_*

CSV_NAME = "promedio_2024_hypervolumes_normality_test.csv"
ALPHA = 0.05

p_cruzamientos = ["06", "07", "08"]
p_mutaciones = ["1", "01", "001"]
population_sizes = ["50", "100", "200"]

# ===============================
# Resultados globales
# ===============================

normal_count = 0
not_normal_count = 0
results = []

# ===============================
# Recorrido de directorios
# ===============================

for pc in p_cruzamientos:
    for pm in p_mutaciones:
        for pop in population_sizes:

            dir_name = f"promedio_{pc}_{pm}_{pop}"
            dir_path = os.path.join(BASE_DIR, dir_name)
            csv_path = os.path.join(dir_path, CSV_NAME)

            if not os.path.isdir(dir_path):
                print(f"[WARN] Directorio no encontrado: {dir_name}")
                continue

            if not os.path.isfile(csv_path):
                print(f"[WARN] CSV no encontrado en: {dir_name}")
                continue

            # ===============================
            # Leer CSV y extraer HV
            # ===============================

            df = pd.read_csv(csv_path)

            if "Hypervolume" not in df.columns:
                print(f"[ERROR] Columna 'Hypervolume' no encontrada en {csv_path}")
                continue

            hv = df["Hypervolume"].values

            # ===============================
            # Test de normalidad (Shapiro-Wilk)
            # ===============================

            stat, p_value = stats.shapiro(hv)

            is_normal = p_value >= ALPHA

            if is_normal:
                normal_count += 1
                result_str = "NORMAL"
            else:
                not_normal_count += 1
                result_str = "NO NORMAL"

            results.append({
                "config": dir_name,
                "p_value": p_value,
                "result": result_str
            })

            print(f"{dir_name:35s} -> {result_str} (p-value = {p_value:.5f})")

# ===============================
# Decisión final por mayoría
# ===============================

print("\n" + "=" * 60)
print("RESUMEN GLOBAL")
print("=" * 60)

print(f"Configuraciones NORMALES    : {normal_count}")
print(f"Configuraciones NO NORMALES : {not_normal_count}")

if normal_count > not_normal_count:
    decision = "USAR TESTS PARAMÉTRICOS"
else:
    decision = "USAR TESTS NO PARAMÉTRICOS"

print("\nDECISIÓN FINAL:")
print(decision)
print("=" * 60)

