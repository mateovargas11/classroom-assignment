#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script para realizar el test de normalidad de Anderson-Darling.

El test de Anderson-Darling es una prueba estadística que evalúa si una muestra
de datos proviene de una distribución normal. Es más potente que el test de
Kolmogorov-Smirnov, especialmente para detectar desviaciones en las colas de
la distribución.

CARACTERÍSTICAS DEL TEST DE ANDERSON-DARLING:
- Es más sensible a las desviaciones en las colas de la distribución
- Pone más peso en las observaciones extremas
- Es más potente que el test de Kolmogorov-Smirnov para muestras pequeñas
- Es adecuado para validar supuestos de normalidad antes de aplicar tests paramétricos
"""

import csv
import sys
import argparse
from pathlib import Path
import glob
from typing import List, Dict, Tuple
import warnings

try:
    import pandas as pd
    PANDAS_AVAILABLE = True
except ImportError:
    PANDAS_AVAILABLE = False
    print("⚠️  pandas no está disponible. Usando biblioteca estándar de Python.")

try:
    import numpy as np
    NUMPY_AVAILABLE = True
except ImportError:
    NUMPY_AVAILABLE = False
    print("⚠️  numpy no está disponible. Instalación requerida para análisis estadístico.")
    sys.exit(1)

try:
    from scipy import stats
    SCIPY_AVAILABLE = True
except ImportError:
    SCIPY_AVAILABLE = False
    print("⚠️  scipy no está disponible. Instalación requerida para análisis estadístico.")
    sys.exit(1)

warnings.filterwarnings('ignore')

OUTPUT_DIR = Path("output")


def load_hypervolume_data():
    """
    Carga todos los datos de hipervolumen de los archivos CSV.
    """
    stats_file = OUTPUT_DIR / "promedio_2024_hypervolume_stats.csv"
    
    if not stats_file.exists():
        print(f"⚠️  No se encontró el archivo: {stats_file}")
        return pd.DataFrame() if PANDAS_AVAILABLE else []
    
    if PANDAS_AVAILABLE:
        df = pd.read_csv(stats_file)
        return df
    else:
        # Implementación sin pandas
        data = []
        with open(stats_file, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                data.append(row)
        return data


def load_evolution_data():
    """
    Carga datos de evolución de todos los archivos CSV disponibles.
    """
    pattern = str(OUTPUT_DIR / "promedio_2024_evolucion_*.csv")
    files = glob.glob(pattern)
    
    if not files:
        print(f"⚠️  No se encontraron archivos de evolución con el patrón: {pattern}")
        return pd.DataFrame() if PANDAS_AVAILABLE else []
    
    all_data = []
    for file in files:
        try:
            if PANDAS_AVAILABLE:
                df = pd.read_csv(file)
                df['archivo'] = Path(file).name
                all_data.append(df)
            else:
                # Implementación sin pandas
                with open(file, 'r', encoding='utf-8') as f:
                    reader = csv.DictReader(f)
                    rows = list(reader)
                    for row in rows:
                        row['archivo'] = Path(file).name
                    all_data.extend(rows)
        except Exception as e:
            print(f"⚠️  Error leyendo {file}: {e}")
    
    if not all_data:
        return pd.DataFrame() if PANDAS_AVAILABLE else []
    
    if PANDAS_AVAILABLE:
        return pd.concat(all_data, ignore_index=True)
    else:
        return all_data


def extract_final_metrics(evolution_df):
    """
    Extrae las métricas finales de cada ejecución de evolución.
    """
    if PANDAS_AVAILABLE:
        if evolution_df.empty:
            return pd.DataFrame()
    else:
        if not evolution_df:
            return []
    
    final_metrics = []
    
    if PANDAS_AVAILABLE:
        for archivo in evolution_df['archivo'].unique():
            df_subset = evolution_df[evolution_df['archivo'] == archivo]
            if df_subset.empty:
                continue
            
            last_gen = df_subset.iloc[-1]
            final_metrics.append({
                'archivo': archivo,
                'mejor_asignaciones': float(last_gen['mejor_asignaciones']),
                'promedio_asignaciones': float(last_gen['promedio_asignaciones']),
                'mejor_separacion': float(last_gen['mejor_separacion']),
                'promedio_separacion': float(last_gen['promedio_separacion']),
                'soluciones_factibles': int(last_gen['soluciones_factibles']),
                'tiempo_ms': int(last_gen['tiempo_ms'])
            })
        return pd.DataFrame(final_metrics)
    else:
        # Implementación sin pandas
        archivos = set(row['archivo'] for row in evolution_df)
        for archivo in archivos:
            subset = [row for row in evolution_df if row['archivo'] == archivo]
            if not subset:
                continue
            
            # Tomar la última generación
            last_gen = subset[-1]
            final_metrics.append({
                'archivo': archivo,
                'mejor_asignaciones': float(last_gen['mejor_asignaciones']),
                'promedio_asignaciones': float(last_gen['promedio_asignaciones']),
                'mejor_separacion': float(last_gen['mejor_separacion']),
                'promedio_separacion': float(last_gen['promedio_separacion']),
                'soluciones_factibles': int(last_gen['soluciones_factibles']),
                'tiempo_ms': int(last_gen['tiempo_ms'])
            })
        return final_metrics


def perform_anderson_darling_test(groups: List[np.ndarray], group_names: List[str], 
                                  metric_name: str, significance_level: float = 0.05) -> Dict:
    """
    Realiza el test de normalidad de Anderson-Darling para cada grupo.
    
    EL TEST DE ANDERSON-DARLING:
    ----------------------------
    El test de Anderson-Darling es una modificación del test de Kolmogorov-Smirnov
    que es más sensible a las desviaciones en las colas de la distribución.
    
    Hipótesis:
    - H0 (hipótesis nula): Los datos siguen una distribución normal
    - H1 (hipótesis alternativa): Los datos NO siguen una distribución normal
    
    Estadístico:
    El estadístico A² se calcula como:
        A² = -n - (1/n) * Σ(2i-1)[ln(F(Yi)) + ln(1-F(Yn+1-i))]
    
    donde:
    - n es el tamaño de la muestra
    - Yi son los valores ordenados de la muestra
    - F es la función de distribución acumulativa normal estándar
    
    Interpretación:
    - Si el estadístico A² es mayor que el valor crítico, se rechaza H0
    - Valores críticos dependen del nivel de significancia (α)
    - El test es más potente que Kolmogorov-Smirnov para muestras pequeñas
    
    Ventajas:
    - Más sensible a desviaciones en las colas (valores extremos)
    - Mejor potencia estadística que KS para muestras pequeñas
    - Pone más peso en las observaciones extremas
    
    Limitaciones:
    - Requiere al menos 8 observaciones (recomendado)
    - Los valores críticos están tabulados para niveles específicos de α
    
    Args:
        groups: Lista de arrays con los datos de cada grupo
        group_names: Nombres de los grupos
        metric_name: Nombre de la métrica que se está analizando
        significance_level: Nivel de significancia (default: 0.05)
    
    Returns:
        Diccionario con los resultados del test de normalidad
    """
    # Filtrar grupos vacíos
    valid_groups = []
    valid_names = []
    for group, name in zip(groups, group_names):
        if len(group) > 0:
            valid_groups.append(group)
            valid_names.append(name)
    
    if len(valid_groups) == 0:
        return {
            'error': 'No hay grupos válidos para el test de normalidad.',
            'metric': metric_name
        }
    
    results = {}
    all_normal = True
    
    # Valores críticos del test de Anderson-Darling para diferentes niveles de significancia
    # Estos son valores aproximados para el test de normalidad
    # scipy.stats.anderson retorna valores críticos para diferentes niveles
    critical_levels = {
        0.15: 0.576,
        0.10: 0.656,
        0.05: 0.787,
        0.025: 0.918,
        0.01: 1.092
    }
    
    # Obtener el valor crítico más cercano al nivel de significancia solicitado
    alpha_key = min(critical_levels.keys(), key=lambda x: abs(x - significance_level))
    critical_value = critical_levels[alpha_key]
    
    for group, name in zip(valid_groups, valid_names):
        n = len(group)
        
        # El test de Anderson-Darling requiere al menos 8 observaciones
        if n < 8:
            results[name] = {
                'statistic': np.nan,
                'critical_value': np.nan,
                'p_value': np.nan,
                'normal': False,
                'mean': np.mean(group),
                'std': np.std(group, ddof=1),
                'n': n,
                'note': f'Tamaño de muestra insuficiente (n={n} < 8). Se requieren al menos 8 observaciones.'
            }
            all_normal = False
            continue
        
        # Si la desviación estándar es 0, todos los valores son iguales
        std = np.std(group, ddof=1)
        if std == 0:
            results[name] = {
                'statistic': np.nan,
                'critical_value': np.nan,
                'p_value': np.nan,
                'normal': False,
                'mean': np.mean(group),
                'std': std,
                'n': n,
                'note': 'Todos los valores son iguales (std=0)'
            }
            all_normal = False
            continue
        
        # Realizar test de Anderson-Darling
        # scipy.stats.anderson retorna el estadístico y valores críticos
        try:
            result = stats.anderson(group, dist='norm')
            statistic = result.statistic
            
            # Obtener el valor crítico correspondiente al nivel de significancia
            # result.critical_values contiene valores para [15%, 10%, 5%, 2.5%, 1%]
            # result.significance_level contiene [15, 10, 5, 2.5, 1]
            critical_values = result.critical_values
            significance_levels = result.significance_level
            
            # Encontrar el valor crítico más cercano al nivel solicitado
            idx = min(range(len(significance_levels)), 
                     key=lambda i: abs(significance_levels[i] - (significance_level * 100)))
            critical_value_actual = critical_values[idx]
            actual_alpha = significance_levels[idx] / 100.0
            
            # Comparar estadístico con valor crítico
            # Si statistic > critical_value, rechazamos H0 (no es normal)
            is_normal = statistic <= critical_value_actual
            
            # Calcular p-valor aproximado (interpolación)
            # Si el estadístico está entre dos valores críticos, interpolamos
            p_value = None
            if statistic <= critical_values[-1]:  # Menor que el valor crítico más pequeño (1%)
                p_value = 0.01
            elif statistic >= critical_values[0]:  # Mayor que el valor crítico más grande (15%)
                p_value = 0.15
            else:
                # Interpolar entre los valores críticos
                for i in range(len(critical_values) - 1):
                    if critical_values[i+1] <= statistic <= critical_values[i]:
                        # Interpolación lineal en escala log
                        alpha1 = significance_levels[i] / 100.0
                        alpha2 = significance_levels[i+1] / 100.0
                        cv1 = critical_values[i]
                        cv2 = critical_values[i+1]
                        if cv1 != cv2:
                            ratio = (statistic - cv2) / (cv1 - cv2)
                            p_value = alpha2 + ratio * (alpha1 - alpha2)
                        else:
                            p_value = alpha1
                        break
            
            if p_value is None:
                p_value = actual_alpha
            
            if not is_normal:
                all_normal = False
            
            results[name] = {
                'statistic': statistic,
                'critical_value': critical_value_actual,
                'critical_alpha': actual_alpha,
                'p_value': p_value,
                'normal': is_normal,
                'mean': np.mean(group),
                'std': std,
                'n': n
            }
            
        except Exception as e:
            results[name] = {
                'statistic': np.nan,
                'critical_value': np.nan,
                'p_value': np.nan,
                'normal': False,
                'mean': np.mean(group),
                'std': std,
                'n': n,
                'note': f'Error al realizar el test: {str(e)}'
            }
            all_normal = False
    
    return {
        'metric': metric_name,
        'alpha': significance_level,
        'groups': valid_names,
        'test_results': results,
        'all_groups_normal': all_normal,
        'test_name': 'Anderson-Darling'
    }


def analyze_by_configuration(hypervolume_df, evolution_df, significance_level: float = 0.05):
    """
    Analiza los datos agrupados por configuración de parámetros usando el test de Anderson-Darling.
    
    Returns:
        Diccionario con resultados de test de normalidad
    """
    normality_results = {}
    
    # Verificar si hay datos
    has_hypervolume = False
    has_evolution = False
    
    if PANDAS_AVAILABLE:
        has_hypervolume = not hypervolume_df.empty
        has_evolution = not evolution_df.empty
    else:
        has_hypervolume = bool(hypervolume_df)
        has_evolution = bool(evolution_df)
    
    if not has_hypervolume and not has_evolution:
        return {'error': 'No hay datos disponibles para análisis'}
    
    # Análisis de hipervolumen por configuración
    if has_hypervolume:
        # Agrupar por diferentes parámetros
        for param in ['POB', 'CRU', 'MUT']:
            if param in hypervolume_df.columns:
                groups = []
                group_names = []
                
                if PANDAS_AVAILABLE:
                    for value in hypervolume_df[param].unique():
                        subset = hypervolume_df[hypervolume_df[param] == value]
                        if len(subset) > 0:
                            groups.append(subset['HV'].values)
                            group_names.append(f"{param}={value}")
                else:
                    # Implementación sin pandas
                    values = set(row[param] for row in hypervolume_df if param in row)
                    for value in values:
                        subset = [float(row['HV']) for row in hypervolume_df 
                                 if row.get(param) == str(value) and 'HV' in row]
                        if subset:
                            groups.append(np.array(subset))
                            group_names.append(f"{param}={value}")
                
                if len(groups) >= 1:  # Anderson-Darling puede aplicarse a un solo grupo
                    normality_key = f'AD_HV_por_{param}'
                    normality_results[normality_key] = perform_anderson_darling_test(
                        groups, group_names, f'Hipervolumen por {param}', significance_level
                    )
    
    # Análisis de métricas de evolución
    if has_evolution:
        final_metrics = extract_final_metrics(evolution_df)
        
        has_final_metrics = False
        if PANDAS_AVAILABLE:
            has_final_metrics = not final_metrics.empty
        else:
            has_final_metrics = bool(final_metrics)
        
        if has_final_metrics:
            # Agrupar por archivo (cada archivo representa una configuración)
            for metric in ['mejor_asignaciones', 'mejor_separacion', 'tiempo_ms']:
                groups = []
                group_names = []
                
                if PANDAS_AVAILABLE:
                    if metric in final_metrics.columns:
                        for archivo in final_metrics['archivo'].unique():
                            subset = final_metrics[final_metrics['archivo'] == archivo]
                            if len(subset) > 0:
                                groups.append(subset[metric].values)
                                group_names.append(archivo)
                else:
                    # Implementación sin pandas
                    archivos = set(row['archivo'] for row in final_metrics)
                    for archivo in archivos:
                        subset = [float(row[metric]) for row in final_metrics 
                                 if row['archivo'] == archivo and metric in row]
                        if subset:
                            groups.append(np.array(subset))
                            group_names.append(archivo)
                
                if len(groups) >= 1:
                    normality_key = f'AD_{metric}'
                    normality_results[normality_key] = perform_anderson_darling_test(
                        groups, group_names, metric, significance_level
                    )
    
    return normality_results


def print_test_results(normality_results: Dict):
    """
    Imprime los resultados del test de normalidad de Anderson-Darling.
    """
    if 'error' in normality_results:
        print(f"\n❌ Error en test de normalidad: {normality_results['error']}")
        return
    
    print("\n" + "="*90)
    print("TEST DE NORMALIDAD DE ANDERSON-DARLING")
    print("="*90)
    print(f"\n📊 Métrica: {normality_results['metric']}")
    print(f"   Nivel de significancia (α): {normality_results['alpha']}")
    print(f"\n{'─'*90}")
    print(f"{'Grupo':<30} {'N':<6} {'Estadístico A²':<18} {'Valor Crítico':<15} {'p-valor':<12} {'Normal':<10}")
    print("-" * 90)
    
    for group_name, test_result in normality_results['test_results'].items():
        if 'note' in test_result:
            print(f"{group_name:<30} {test_result['n']:<6} {'N/A':<18} {'N/A':<15} {'N/A':<12} {'❌ No':<10}")
            print(f"  → {test_result['note']}")
        else:
            normal_str = "✅ Sí" if test_result['normal'] else "❌ No"
            stat_str = f"{test_result['statistic']:.6f}"
            crit_str = f"{test_result['critical_value']:.6f}"
            pval_str = f"{test_result['p_value']:.4f}" if test_result['p_value'] is not None else "N/A"
            print(f"{group_name:<30} {test_result['n']:<6} {stat_str:<18} {crit_str:<15} {pval_str:<12} {normal_str:<10}")
    
    print(f"\n{'─'*90}")
    if normality_results['all_groups_normal']:
        print("✅ Todos los grupos siguen una distribución normal (A² ≤ valor crítico)")
        print("   → Se pueden aplicar tests paramétricos (ANOVA, t-test)")
    else:
        print("⚠️  Al menos un grupo NO sigue una distribución normal (A² > valor crítico)")
        print("   → Se recomienda usar tests no paramétricos (Kruskal-Wallis, Mann-Whitney)")
        print("   → O transformar los datos antes de aplicar tests paramétricos")
    print()


def save_results_to_csv(normality_results: Dict, output_file: str):
    """
    Guarda los resultados del test de Anderson-Darling en un archivo CSV.
    """
    OUTPUT_DIR.mkdir(exist_ok=True)
    
    if not normality_results or 'error' in normality_results:
        print(f"\n⚠️  No hay resultados para guardar")
        return
    
    rows = []
    for key, norm_result in normality_results.items():
        if 'error' in norm_result:
            continue
        
        for group_name, test_result in norm_result['test_results'].items():
            rows.append({
                'Analisis': key,
                'Metrica': norm_result['metric'],
                'Grupo': group_name,
                'N': test_result['n'],
                'Media': test_result.get('mean', np.nan if NUMPY_AVAILABLE else ''),
                'Desv_Est': test_result.get('std', np.nan if NUMPY_AVAILABLE else ''),
                'Estadistico_AD': test_result.get('statistic', np.nan if NUMPY_AVAILABLE else ''),
                'Valor_Critico': test_result.get('critical_value', np.nan if NUMPY_AVAILABLE else ''),
                'Alpha_Critico': test_result.get('critical_alpha', np.nan if NUMPY_AVAILABLE else ''),
                'p_valor': test_result.get('p_value', np.nan if NUMPY_AVAILABLE else ''),
                'Es_Normal': test_result.get('normal', False),
                'Nota': test_result.get('note', '')
            })
    
    if rows:
        output_path = OUTPUT_DIR / output_file
        if PANDAS_AVAILABLE:
            df = pd.DataFrame(rows)
            df.to_csv(output_path, index=False)
        else:
            # Guardar sin pandas
            if rows:
                fieldnames = rows[0].keys()
                with open(output_path, 'w', newline='', encoding='utf-8') as f:
                    writer = csv.DictWriter(f, fieldnames=fieldnames)
                    writer.writeheader()
                    writer.writerows(rows)
        print(f"\n💾 Resultados guardados en: {output_path}")
    else:
        print(f"\n⚠️  No hay resultados para guardar")


def print_explanation():
    """
    Imprime una explicación detallada del test de Anderson-Darling.
    """
    print("\n" + "="*90)
    print("EXPLICACIÓN DEL TEST DE ANDERSON-DARLING")
    print("="*90)
    print("""
¿QUÉ ES EL TEST DE ANDERSON-DARLING?
------------------------------------
El test de Anderson-Darling es una prueba estadística de normalidad que evalúa
si una muestra de datos proviene de una distribución normal. Es una modificación
del test de Kolmogorov-Smirnov que es más sensible a las desviaciones en las
colas (valores extremos) de la distribución.

HIPÓTESIS:
----------
• H₀ (Hipótesis nula): Los datos siguen una distribución normal
• H₁ (Hipótesis alternativa): Los datos NO siguen una distribución normal

ESTADÍSTICO A²:
--------------
El estadístico de Anderson-Darling se calcula como:

    A² = -n - (1/n) × Σ(2i-1)[ln(F(Yᵢ)) + ln(1-F(Yₙ₊₁₋ᵢ))]

donde:
• n = tamaño de la muestra
• Yᵢ = valores ordenados de la muestra (de menor a mayor)
• F = función de distribución acumulativa normal estándar
• ln = logaritmo natural

INTERPRETACIÓN:
---------------
• Si A² > valor crítico → Se rechaza H₀ (los datos NO son normales)
• Si A² ≤ valor crítico → No se rechaza H₀ (los datos pueden ser normales)

VENTAJAS:
---------
✓ Más sensible a desviaciones en las colas de la distribución
✓ Mejor potencia estadística que Kolmogorov-Smirnov para muestras pequeñas
✓ Pone más peso en las observaciones extremas
✓ Útil para validar supuestos de normalidad antes de tests paramétricos

LIMITACIONES:
-------------
✗ Requiere al menos 8 observaciones (recomendado)
✗ Los valores críticos están tabulados para niveles específicos de α
✗ Puede ser demasiado estricto para muestras grandes

CUÁNDO USARLO:
-------------
• Antes de aplicar tests paramétricos (ANOVA, t-test, regresión)
• Cuando necesitas detectar desviaciones en las colas
• Para validar supuestos de normalidad en análisis estadísticos
• Cuando tienes muestras pequeñas a medianas (n ≥ 8)

COMPARACIÓN CON OTROS TESTS:
----------------------------
• vs. Kolmogorov-Smirnov: AD es más potente, especialmente en las colas
• vs. Shapiro-Wilk: AD es más adecuado para muestras medianas/grandes
• vs. D'Agostino-Pearson: AD es más simple y directo

REFERENCIAS:
------------
Anderson, T. W., & Darling, D. A. (1954). A test of goodness of fit.
Journal of the American Statistical Association, 49(268), 765-769.
    """)
    print("="*90 + "\n")


def main():
    """
    Función principal que ejecuta el test de normalidad de Anderson-Darling.
    """
    parser = argparse.ArgumentParser(
        description='Realiza el test de normalidad de Anderson-Darling en los resultados de experimentos',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Ejemplos de uso:
  python anderson_darling_test.py
  python anderson_darling_test.py --alpha 0.01
  python anderson_darling_test.py --output ad_results.csv --explain
        """
    )
    parser.add_argument(
        '--output', '-o',
        default='anderson_darling_results.csv',
        help='Nombre del archivo de salida para los resultados (default: anderson_darling_results.csv)'
    )
    parser.add_argument(
        '--alpha', '-a',
        type=float,
        default=0.05,
        help='Nivel de significancia (default: 0.05)'
    )
    parser.add_argument(
        '--explain', '-e',
        action='store_true',
        help='Muestra una explicación detallada del test de Anderson-Darling'
    )
    parser.add_argument(
        '--verbose', '-v',
        action='store_true',
        help='Muestra información detallada'
    )
    
    args = parser.parse_args()
    
    print("╔════════════════════════════════════════════════════════════╗")
    print("║  TEST DE NORMALIDAD DE ANDERSON-DARLING                   ║")
    print("╚════════════════════════════════════════════════════════════╝")
    print()
    
    if args.explain:
        print_explanation()
    
    # Cargar datos
    print("📂 Cargando datos...")
    hypervolume_df = load_hypervolume_data()
    evolution_df = load_evolution_data()
    
    has_hypervolume = PANDAS_AVAILABLE and not hypervolume_df.empty if PANDAS_AVAILABLE else bool(hypervolume_df)
    has_evolution = PANDAS_AVAILABLE and not evolution_df.empty if PANDAS_AVAILABLE else bool(evolution_df)
    
    if not has_hypervolume and not has_evolution:
        print("\n❌ ERROR: No se encontraron datos para analizar.")
        print(f"   Buscando archivos en: {OUTPUT_DIR.absolute()}")
        print("\n💡 Sugerencia: Ejecuta primero algunos experimentos")
        sys.exit(1)
    
    if has_hypervolume:
        count = len(hypervolume_df) if PANDAS_AVAILABLE else len(hypervolume_df)
        print(f"  ✓ Cargados {count} registros de hipervolumen")
    if has_evolution:
        count = len(evolution_df) if PANDAS_AVAILABLE else len(evolution_df)
        print(f"  ✓ Cargados {count} registros de evolución")
    
    # Realizar análisis
    print(f"\n🔬 Realizando test de normalidad de Anderson-Darling (α={args.alpha})...")
    normality_results = analyze_by_configuration(hypervolume_df, evolution_df, args.alpha)
    
    if not normality_results or 'error' in normality_results:
        print("\n❌ No se pudo realizar el análisis.")
        if 'error' in normality_results:
            print(f"   Razón: {normality_results['error']}")
        sys.exit(1)
    
    # Mostrar resultados
    for key, result in normality_results.items():
        if 'error' not in result:
            print_test_results(result)
    
    # Guardar resultados
    save_results_to_csv(normality_results, args.output)
    
    print("\n" + "="*90)
    print("✅ Análisis completado")
    print("="*90 + "\n")


if __name__ == "__main__":
    main()
