import os
import pandas as pd

# ===============================
# Configuración
# ===============================

BASE_DIR = "output/"
CSV_NAME = "promedio_2024_hypervolumes_normality_test.csv"

p_cruzamientos = ["06", "07", "08"]
p_mutaciones = ["1", "01", "001"]
population_sizes = ["50", "100", "200"]

results = []

# ===============================
# Recorrer configuraciones
# ===============================

for pc in p_cruzamientos:
    for pm in p_mutaciones:
        for pop in population_sizes:

            config_name = f"promedio_{pc}_{pm}_{pop}"
            csv_path = os.path.join(BASE_DIR, config_name, CSV_NAME)

            if not os.path.isfile(csv_path):
                print(f"[WARN] CSV no encontrado: {csv_path}")
                continue

            df = pd.read_csv(csv_path)

            if "Hypervolume" not in df.columns:
                print(f"[ERROR] Columna 'Hypervolume' no encontrada en {csv_path}")
                continue

            hv = df["Hypervolume"].values

            if len(hv) == 0:
                print(f"[WARN] Sin datos en {config_name}")
                continue

            mean_hv = hv.mean()

            results.append({
                "config": config_name,
                "mean_hv": mean_hv,
                "num_runs": len(hv)
            })

# ===============================
# Validación
# ===============================

if not results:
    raise RuntimeError("No se cargaron configuraciones válidas")

# ===============================
# Selección de la mejor configuración
# ===============================

df_results = pd.DataFrame(results)
df_results_sorted = df_results.sort_values(by="mean_hv", ascending=False)

best_config = df_results_sorted.iloc[0]

# ===============================
# Resultados
# ===============================

print("\n" + "=" * 70)
print("RESULTADOS POR CONFIGURACIÓN (ordenados por media de HV)")
print("=" * 70)

for _, row in df_results_sorted.iterrows():
    print(f"{row['config']:35s}  mean HV = {row['mean_hv']:.6f}")

print("\n" + "=" * 70)
print("MEJOR CONFIGURACIÓN SEGÚN MEDIA DE HYPERVOLUMEN")
print("=" * 70)
print(f"Configuración : {best_config['config']}")
print(f"Media HV      : {best_config['mean_hv']:.6f}")
print(f"Ejecuciones   : {best_config['num_runs']}")
print("=" * 70)

