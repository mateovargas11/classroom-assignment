# Análisis ANOVA

Este documento explica cómo usar los scripts para realizar análisis estadístico ANOVA de los resultados de los experimentos.

## Requisitos

Para ejecutar el análisis ANOVA, necesitas instalar las siguientes dependencias de Python:

```bash
pip3 install pandas numpy scipy
```

Si tienes restricciones del sistema, puedes usar un entorno virtual:

```bash
python3 -m venv venv
source venv/bin/activate  # En Windows: venv\Scripts\activate
pip install -r requirements.txt
```

## Uso

### 1. Generar datos para ANOVA

Para realizar un análisis ANOVA significativo, necesitas múltiples ejecuciones del mismo experimento. Puedes usar el script `run_multiple_replicates.py`:

```bash
python3 run_multiple_replicates.py <instancia> <poblacion> <cruzamiento> <mutacion> <num_replicas>
```

**Ejemplo:**
```bash
# Ejecutar 10 réplicas de un experimento
python3 run_multiple_replicates.py promedio_2024 50 0.8 0.01 10
```

O puedes usar `run_experiments.py` para ejecutar múltiples configuraciones diferentes:

```bash
python3 run_experiments.py
```

### 2. Ejecutar análisis ANOVA

Una vez que tengas datos de múltiples ejecuciones, ejecuta el análisis:

```bash
python3 anova_test.py
```

**Opciones:**
- `-o, --output`: Especifica el nombre del archivo de salida (default: `anova_results.csv`)
- `-v, --verbose`: Muestra información detallada

**Ejemplo:**
```bash
python3 anova_test.py -o resultados_anova.csv
```

### 3. Interpretar resultados

El script generará:

1. **Salida en consola**: Resumen de los resultados del ANOVA con:
   - Estadísticas descriptivas por grupo
   - F-estadístico y p-valor
   - Indicación de significancia estadística

2. **Archivo CSV**: Resultados detallados guardados en `output/anova_results.csv` (o el nombre que especifiques)

## Interpretación de resultados

### p-valor < 0.05
- ✅ **Diferencia estadísticamente significativa**
- Se rechaza la hipótesis nula (H0: no hay diferencia entre grupos)
- Hay evidencia de que al menos un grupo es diferente de los demás

### p-valor ≥ 0.05
- ❌ **No hay diferencia estadísticamente significativa**
- No se rechaza la hipótesis nula
- No hay evidencia suficiente de diferencias entre grupos

## Ejemplo de salida

```
================================================================================
RESULTADOS DEL ANÁLISIS ANOVA
================================================================================

────────────────────────────────────────────────────────────────────────────────
📊 Hipervolumen por POB
────────────────────────────────────────────────────────────────────────────────

📈 Estadísticas Descriptivas:
Grupo                          N      Media         Desv. Est.   Mediana      
--------------------------------------------------------------------------------
POB=50                         5      14.3301       0.0000       14.3301      
POB=100                        5      15.1234       0.2345       15.1000      

🔬 Resultados del Test ANOVA:
  F-estadístico: 12.345678
  p-valor: 0.001234
  Nivel de significancia (α): 0.05
  ✅ DIFERENCIA ESTADÍSTICAMENTE SIGNIFICATIVA
     (p < 0.05, se rechaza H0)
```

## Notas importantes

1. **Mínimo de datos**: Se necesitan al menos 2 grupos con datos para realizar ANOVA
2. **Múltiples réplicas**: Para un análisis robusto, se recomienda al menos 5-10 réplicas por configuración
3. **Comparaciones múltiples**: Si realizas múltiples comparaciones, considera usar corrección de Bonferroni o Tukey HSD (incluido en el script)

## Troubleshooting

### Error: "No se encontraron datos para analizar"
- Asegúrate de haber ejecutado experimentos primero
- Verifica que los archivos CSV estén en el directorio `output/`

### Error: "Se necesitan al menos 2 grupos"
- Ejecuta experimentos con diferentes configuraciones
- Usa `run_multiple_replicates.py` para generar múltiples réplicas

### Error: "ModuleNotFoundError: No module named 'pandas'"
- Instala las dependencias: `pip3 install pandas numpy scipy`
- O usa un entorno virtual (ver sección de Requisitos)
