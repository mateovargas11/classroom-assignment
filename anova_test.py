#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script para realizar análisis ANOVA de los resultados de los experimentos.
Compara diferentes configuraciones de algoritmos (Greedy vs NSGA-II) y 
diferentes parámetros del algoritmo evolutivo.
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


def perform_normality_test_ks(groups: List[np.ndarray], group_names: List[str], 
                               metric_name: str) -> Dict:
    """
    Realiza el test de normalidad Kolmogorov-Smirnov para cada grupo.
    
    El test KS compara la distribución empírica de los datos con una distribución
    normal teórica con la misma media y desviación estándar.
    
    Args:
        groups: Lista de arrays con los datos de cada grupo
        group_names: Nombres de los grupos
        metric_name: Nombre de la métrica que se está analizando
    
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
    alpha = 0.05
    all_normal = True
    
    for group, name in zip(valid_groups, valid_names):
        # Calcular media y desviación estándar del grupo
        mean = np.mean(group)
        std = np.std(group, ddof=1)
        
        # Si la desviación estándar es 0, todos los valores son iguales
        if std == 0:
            # Si todos los valores son iguales, técnicamente no es normal
            # pero el test KS no puede ejecutarse
            results[name] = {
                'statistic': np.nan,
                'p_value': np.nan,
                'normal': False,
                'mean': mean,
                'std': std,
                'n': len(group),
                'note': 'Todos los valores son iguales (std=0)'
            }
            all_normal = False
        else:
            # Estandarizar los datos para comparar con N(0,1)
            standardized = (group - mean) / std
            
            # Realizar test de Kolmogorov-Smirnov
            # Compara la distribución empírica con N(0,1)
            statistic, p_value = stats.kstest(standardized, 'norm')
            
            # H0: los datos siguen una distribución normal
            # Si p < alpha, rechazamos H0 (no son normales)
            is_normal = p_value >= alpha
            
            if not is_normal:
                all_normal = False
            
            results[name] = {
                'statistic': statistic,
                'p_value': p_value,
                'normal': is_normal,
                'mean': mean,
                'std': std,
                'n': len(group)
            }
    
    return {
        'metric': metric_name,
        'alpha': alpha,
        'groups': valid_names,
        'test_results': results,
        'all_groups_normal': all_normal
    }


def perform_one_way_anova(groups: List[np.ndarray], group_names: List[str], 
                          metric_name: str) -> Dict:
    """
    Realiza un test ANOVA de una vía.
    
    Args:
        groups: Lista de arrays con los datos de cada grupo
        group_names: Nombres de los grupos
        metric_name: Nombre de la métrica que se está analizando
    
    Returns:
        Diccionario con los resultados del ANOVA
    """
    # Filtrar grupos vacíos
    valid_groups = []
    valid_names = []
    for group, name in zip(groups, group_names):
        if len(group) > 0:
            valid_groups.append(group)
            valid_names.append(name)
    
    if len(valid_groups) < 2:
        return {
            'error': f'Se necesitan al menos 2 grupos para ANOVA. Solo se encontraron {len(valid_groups)} grupos válidos.',
            'metric': metric_name
        }
    
    # Realizar ANOVA
    f_statistic, p_value = stats.f_oneway(*valid_groups)
    
    # Calcular estadísticas descriptivas
    stats_desc = {}
    for group, name in zip(valid_groups, valid_names):
        stats_desc[name] = {
            'n': len(group),
            'mean': np.mean(group),
            'std': np.std(group, ddof=1),
            'median': np.median(group),
            'min': np.min(group),
            'max': np.max(group)
        }
    
    # Determinar significancia
    alpha = 0.05
    significant = p_value < alpha
    
    return {
        'metric': metric_name,
        'f_statistic': f_statistic,
        'p_value': p_value,
        'significant': significant,
        'alpha': alpha,
        'groups': valid_names,
        'descriptive_stats': stats_desc
    }


def perform_post_hoc_tukey(groups: List[np.ndarray], group_names: List[str], 
                           metric_name: str) -> pd.DataFrame:
    """
    Realiza test post-hoc de Tukey HSD para comparaciones múltiples.
    """
    try:
        from scipy.stats import tukey_hsd
    except ImportError:
        # Si tukey_hsd no está disponible, usar t-tests con corrección de Bonferroni
        print("⚠️  tukey_hsd no disponible, usando t-tests con corrección de Bonferroni")
        return perform_bonferroni_correction(groups, group_names, metric_name)
    
    # Filtrar grupos vacíos
    valid_groups = []
    valid_names = []
    for group, name in zip(groups, group_names):
        if len(group) > 0:
            valid_groups.append(group)
            valid_names.append(name)
    
    if len(valid_groups) < 2:
        return pd.DataFrame()
    
    # Realizar test de Tukey
    result = tukey_hsd(*valid_groups)
    
    # Crear lista con resultados
    comparisons = []
    for i, name1 in enumerate(valid_names):
        for j, name2 in enumerate(valid_names):
            if i < j:
                comparisons.append({
                    'Grupo 1': name1,
                    'Grupo 2': name2,
                    'Diferencia': np.mean(valid_groups[i]) - np.mean(valid_groups[j]),
                    'p_value': result.pvalue[i, j],
                    'Significativo': result.pvalue[i, j] < 0.05
                })
    
    if PANDAS_AVAILABLE:
        return pd.DataFrame(comparisons)
    else:
        return comparisons


def perform_bonferroni_correction(groups: List[np.ndarray], group_names: List[str],
                                  metric_name: str):
    """
    Realiza comparaciones múltiples usando t-tests con corrección de Bonferroni.
    """
    valid_groups = []
    valid_names = []
    for group, name in zip(groups, group_names):
        if len(group) > 0:
            valid_groups.append(group)
            valid_names.append(name)
    
    if len(valid_groups) < 2:
        return pd.DataFrame()
    
    comparisons = []
    n_comparisons = len(valid_names) * (len(valid_names) - 1) // 2
    alpha_corrected = 0.05 / n_comparisons if n_comparisons > 0 else 0.05
    
    for i, name1 in enumerate(valid_names):
        for j, name2 in enumerate(valid_names):
            if i < j:
                t_stat, p_value = stats.ttest_ind(valid_groups[i], valid_groups[j])
                comparisons.append({
                    'Grupo 1': name1,
                    'Grupo 2': name2,
                    'Diferencia': np.mean(valid_groups[i]) - np.mean(valid_groups[j]),
                    'p_value': p_value,
                    'p_value_corregido': min(p_value * n_comparisons, 1.0),
                    'Significativo': p_value < alpha_corrected
                })
    
    if PANDAS_AVAILABLE:
        return pd.DataFrame(comparisons)
    else:
        return comparisons


def analyze_by_configuration(hypervolume_df: pd.DataFrame, 
                            evolution_df: pd.DataFrame) -> tuple:
    """
    Analiza los datos agrupados por configuración de parámetros.
    
    Returns:
        Tupla (results, normality_results) donde:
        - results: Diccionario con resultados de ANOVA
        - normality_results: Diccionario con resultados de test de normalidad
    """
    results = {}
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
        return ({'error': 'No hay datos disponibles para análisis'}, {})
    
    # Análisis de hipervolumen por configuración
    if has_hypervolume:
        # Agrupar por diferentes parámetros
        for param in ['POB', 'CRU', 'MUT']:
            if param in hypervolume_df.columns:
                groups = []
                group_names = []
                
                for value in hypervolume_df[param].unique():
                    subset = hypervolume_df[hypervolume_df[param] == value]
                    if len(subset) > 0:
                        groups.append(subset['HV'].values)
                        group_names.append(f"{param}={value}")
                
                if len(groups) >= 2:
                    # Realizar test de normalidad primero
                    normality_key = f'KS_HV_por_{param}'
                    normality_results[normality_key] = perform_normality_test_ks(
                        groups, group_names, f'Hipervolumen por {param}'
                    )
                    # Luego realizar ANOVA
                    results[f'ANOVA_HV_por_{param}'] = perform_one_way_anova(
                        groups, group_names, f'Hipervolumen por {param}'
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
                
                if len(groups) >= 2:
                    # Realizar test de normalidad primero
                    normality_key = f'KS_{metric}'
                    normality_results[normality_key] = perform_normality_test_ks(
                        groups, group_names, metric
                    )
                    # Luego realizar ANOVA
                    results[f'ANOVA_{metric}'] = perform_one_way_anova(
                        groups, group_names, metric
                    )
    
    return results, normality_results


def print_normality_test_results(normality_results: Dict):
    """
    Imprime los resultados del test de normalidad Kolmogorov-Smirnov.
    """
    if 'error' in normality_results:
        print(f"\n❌ Error en test de normalidad: {normality_results['error']}")
        return
    
    print("\n" + "="*80)
    print("TEST DE NORMALIDAD KOLMOGOROV-SMIRNOV")
    print("="*80)
    print(f"\n📊 Métrica: {normality_results['metric']}")
    print(f"   Nivel de significancia (α): {normality_results['alpha']}")
    print(f"\n{'─'*80}")
    print(f"{'Grupo':<30} {'N':<6} {'Estadístico KS':<18} {'p-valor':<12} {'Normal':<10}")
    print("-" * 80)
    
    for group_name, test_result in normality_results['test_results'].items():
        if 'note' in test_result:
            print(f"{group_name:<30} {test_result['n']:<6} {'N/A':<18} {'N/A':<12} {'❌ No':<10}")
            print(f"  → {test_result['note']}")
        else:
            normal_str = "✅ Sí" if test_result['normal'] else "❌ No"
            stat_str = f"{test_result['statistic']:.6f}"
            pval_str = f"{test_result['p_value']:.6f}"
            print(f"{group_name:<30} {test_result['n']:<6} {stat_str:<18} {pval_str:<12} {normal_str:<10}")
    
    print(f"\n{'─'*80}")
    if normality_results['all_groups_normal']:
        print("✅ Todos los grupos siguen una distribución normal (p ≥ α)")
        print("   → Se pueden aplicar tests paramétricos (ANOVA, t-test)")
    else:
        print("⚠️  Al menos un grupo NO sigue una distribución normal (p < α)")
        print("   → Se recomienda usar tests no paramétricos (Kruskal-Wallis, Mann-Whitney)")
        print("   → O transformar los datos antes de aplicar tests paramétricos")
    print()


def print_anova_results(results: Dict):
    """
    Imprime los resultados del ANOVA de forma legible.
    """
    print("\n" + "="*80)
    print("RESULTADOS DEL ANÁLISIS ANOVA")
    print("="*80)
    
    for key, result in results.items():
        if 'error' in result:
            print(f"\n❌ {key}: {result['error']}")
            continue
        
        print(f"\n{'─'*80}")
        print(f"📊 {result['metric']}")
        print(f"{'─'*80}")
        
        # Estadísticas descriptivas
        print("\n📈 Estadísticas Descriptivas:")
        print(f"{'Grupo':<30} {'N':<6} {'Media':<12} {'Desv. Est.':<12} {'Mediana':<12}")
        print("-" * 80)
        
        for group_name, stats_desc in result['descriptive_stats'].items():
            print(f"{group_name:<30} {stats_desc['n']:<6} "
                  f"{stats_desc['mean']:<12.4f} {stats_desc['std']:<12.4f} "
                  f"{stats_desc['median']:<12.4f}")
        
        # Resultados del ANOVA
        print(f"\n🔬 Resultados del Test ANOVA:")
        print(f"  F-estadístico: {result['f_statistic']:.6f}")
        print(f"  p-valor: {result['p_value']:.6f}")
        print(f"  Nivel de significancia (α): {result['alpha']}")
        
        if result['significant']:
            print(f"  ✅ DIFERENCIA ESTADÍSTICAMENTE SIGNIFICATIVA")
            print(f"     (p < {result['alpha']}, se rechaza H0)")
        else:
            print(f"  ❌ NO HAY DIFERENCIA ESTADÍSTICAMENTE SIGNIFICATIVA")
            print(f"     (p ≥ {result['alpha']}, no se rechaza H0)")
        
        print()


def save_results_to_csv(results: Dict, normality_results: Dict, output_file: str):
    """
    Guarda los resultados del ANOVA y test de normalidad en archivos CSV.
    """
    OUTPUT_DIR.mkdir(exist_ok=True)
    
    # Guardar resultados de ANOVA
    anova_path = OUTPUT_DIR / output_file
    rows_anova = []
    for key, result in results.items():
        if 'error' in result:
            continue
        
        for group_name, stats_desc in result['descriptive_stats'].items():
            rows_anova.append({
                'Analisis': key,
                'Metrica': result['metric'],
                'Grupo': group_name,
                'N': stats_desc['n'],
                'Media': stats_desc['mean'],
                'Desv_Est': stats_desc['std'],
                'Mediana': stats_desc['median'],
                'Min': stats_desc['min'],
                'Max': stats_desc['max'],
                'F_estadistico': result['f_statistic'],
                'p_valor': result['p_value'],
                'Significativo': result['significant']
            })
    
    if rows_anova:
        if PANDAS_AVAILABLE:
            df_anova = pd.DataFrame(rows_anova)
            df_anova.to_csv(anova_path, index=False)
        else:
            # Guardar sin pandas
            import csv
            if rows_anova:
                fieldnames = rows_anova[0].keys()
                with open(anova_path, 'w', newline='', encoding='utf-8') as f:
                    writer = csv.DictWriter(f, fieldnames=fieldnames)
                    writer.writeheader()
                    writer.writerows(rows_anova)
        print(f"\n💾 Resultados ANOVA guardados en: {anova_path}")
    else:
        print(f"\n⚠️  No hay resultados de ANOVA para guardar")
    
    # Guardar resultados de normalidad
    if normality_results:
        ks_path = OUTPUT_DIR / output_file.replace('.csv', '_normalidad_ks.csv')
        rows_ks = []
        for key, norm_result in normality_results.items():
            if 'error' in norm_result:
                continue
            
            for group_name, test_result in norm_result['test_results'].items():
                rows_ks.append({
                    'Analisis': key,
                    'Metrica': norm_result['metric'],
                    'Grupo': group_name,
                    'N': test_result['n'],
                    'Media': test_result.get('mean', np.nan if NUMPY_AVAILABLE else ''),
                    'Desv_Est': test_result.get('std', np.nan if NUMPY_AVAILABLE else ''),
                    'Estadistico_KS': test_result.get('statistic', np.nan if NUMPY_AVAILABLE else ''),
                    'p_valor': test_result.get('p_value', np.nan if NUMPY_AVAILABLE else ''),
                    'Es_Normal': test_result.get('normal', False),
                    'Nota': test_result.get('note', '')
                })
        
        if rows_ks:
            if PANDAS_AVAILABLE:
                df_ks = pd.DataFrame(rows_ks)
                df_ks.to_csv(ks_path, index=False)
            else:
                # Guardar sin pandas
                import csv
                fieldnames = rows_ks[0].keys()
                with open(ks_path, 'w', newline='', encoding='utf-8') as f:
                    writer = csv.DictWriter(f, fieldnames=fieldnames)
                    writer.writeheader()
                    writer.writerows(rows_ks)
            print(f"💾 Resultados test de normalidad guardados en: {ks_path}")


def main():
    """
    Función principal que ejecuta el análisis ANOVA.
    """
    parser = argparse.ArgumentParser(
        description='Realiza análisis ANOVA de los resultados de experimentos'
    )
    parser.add_argument(
        '--output', '-o',
        default='anova_results.csv',
        help='Nombre del archivo de salida para los resultados (default: anova_results.csv)'
    )
    parser.add_argument(
        '--verbose', '-v',
        action='store_true',
        help='Muestra información detallada'
    )
    
    args = parser.parse_args()
    
    print("╔════════════════════════════════════════════════════════════╗")
    print("║  ANÁLISIS ANOVA DE RESULTADOS                             ║")
    print("╚════════════════════════════════════════════════════════════╝")
    print()
    
    # Cargar datos
    print("📂 Cargando datos...")
    hypervolume_df = load_hypervolume_data()
    evolution_df = load_evolution_data()
    
    has_hypervolume = PANDAS_AVAILABLE and not hypervolume_df.empty if PANDAS_AVAILABLE else bool(hypervolume_df)
    has_evolution = PANDAS_AVAILABLE and not evolution_df.empty if PANDAS_AVAILABLE else bool(evolution_df)
    
    if not has_hypervolume and not has_evolution:
        print("\n❌ ERROR: No se encontraron datos para analizar.")
        print(f"   Buscando archivos en: {OUTPUT_DIR.absolute()}")
        print("\n💡 Sugerencia: Ejecuta primero algunos experimentos usando run_experiments.py")
        sys.exit(1)
    
    if has_hypervolume:
        count = len(hypervolume_df) if PANDAS_AVAILABLE else len(hypervolume_df)
        print(f"  ✓ Cargados {count} registros de hipervolumen")
    if has_evolution:
        count = len(evolution_df) if PANDAS_AVAILABLE else len(evolution_df)
        print(f"  ✓ Cargados {count} registros de evolución")
    
    # Realizar análisis
    print("\n🔬 Realizando análisis ANOVA...")
    results = analyze_by_configuration(hypervolume_df, evolution_df)
    
    if not results or 'error' in results:
        print("\n❌ No se pudo realizar el análisis ANOVA.")
        if 'error' in results:
            print(f"   Razón: {results['error']}")
        print("\n💡 Sugerencia: Se necesitan al menos 2 grupos con datos para realizar ANOVA.")
        print("   Ejecuta múltiples experimentos con diferentes configuraciones.")
        sys.exit(1)
    
    # Mostrar resultados
    print_anova_results(results)
    
    # Guardar resultados
    save_results_to_csv(results, normality_results, args.output)
    
    print("\n" + "="*80)
    print("✅ Análisis completado")
    print("="*80 + "\n")


if __name__ == "__main__":
    main()
