
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from scipy import stats

# ===============================
# Configuración
# ===============================

CSV_PATH = "output/promedio_08_001_50/promedio_2024_hypervolumes_normality_test.csv"

# ===============================
# Cargar datos
# ===============================

df = pd.read_csv(CSV_PATH)
hv = df["Hypervolume"].values

# ===============================
# Test de normalidad (Shapiro-Wilk)
# ===============================

stat, p_value = stats.shapiro(hv)

print("Shapiro–Wilk test")
print(f"Statistic = {stat:.5f}")
print(f"p-value   = {p_value:.5f}")

# ===============================
# 1) Q–Q plot
# ===============================

plt.figure()
stats.probplot(hv, dist="norm", plot=plt)
plt.title("Q–Q plot de Hypervolumen")
plt.xlabel("Cuantiles teóricos")
plt.ylabel("Cuantiles observados")
plt.grid(True)
plt.show()

# ===============================
# 2) Histograma + Normal
# ===============================

mu = np.mean(hv)
sigma = np.std(hv, ddof=1)

plt.figure()
plt.hist(hv, bins=10, density=True, alpha=0.7, label="Datos")
x = np.linspace(hv.min(), hv.max(), 100)
plt.plot(x, stats.norm.pdf(x, mu, sigma), label="Normal teórica")
plt.title("Histograma de Hypervolumen")
plt.xlabel("Hypervolumen")
plt.ylabel("Densidad")
plt.legend()
plt.grid(True)
plt.show()

# ===============================
# 3) Boxplot
# ===============================

plt.figure()
plt.boxplot(hv, vert=False)
plt.title("Boxplot de Hypervolumen")
plt.xlabel("Hypervolumen")
plt.grid(True)
plt.show()

