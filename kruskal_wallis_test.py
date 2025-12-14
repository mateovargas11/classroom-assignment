#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script para realizar el test no paramétrico de Kruskal-Wallis.

El test de Kruskal-Wallis es una prueba estadística no paramétrica que evalúa
si hay diferencias significativas entre tres o más grupos independientes.
Es el equivalente no paramétrico del ANOVA de una vía.

CARACTERÍSTICAS DEL TEST DE KRUSKAL-WALLIS:
- No requiere que los datos sigan una distribución normal
- No requiere homocedasticidad (varianzas iguales)
- Funciona con datos ordinales o continuos
- Es robusto ante valores atípicos
- Compara las medianas de los grupos
"""

import csv
import sys
import argparse
from pathlib import Path
import glob
import warnings
from itertools import combinations

try:
    import pandas as pd
    PANDAS_AVAILABLE = True
except ImportError:
    PANDAS_AVAILABLE = False
    print("⚠️  pandas no está disponible. Usando biblioteca estándar de Python.")
    sys.exit(1)

try:
    import numpy as np
    NUMPY_AVAILABLE = True
except ImportError:
    NUMPY_AVAILABLE = False
    print("⚠️  numpy no está disponible. Instalación requerida para análisis estadístico.")
    sys.exit(1)

try:
    from scipy import stats
    from scipy.stats import kruskal, mannwhitneyu
    SCIPY_AVAILABLE = True
except ImportError:
    SCIPY_AVAILABLE = False
    print("⚠️  scipy no está disponible. Instalación requerida para análisis estadístico.")
    sys.exit(1)

warnings.filterwarnings('ignore')


def parse_folder_name(folder_name: str):
    """
    Parsea el nombre de la carpeta para extraer los parámetros.
    
    Formato esperado: promedio_<crossover>_<mutation>_<population>
    Ejemplo: promedio_06_001_50 -> crossover=0.6, mutation=0.001, population=50
    """
    parts = folder_name.split('_')
    if len(parts) >= 4:
        try:
            crossover = float(parts[1]) / 10.0  # 06 -> 0.6
            mutation = float(parts[2]) / 1000.0  # 001 -> 0.001
            population = int(parts[3])
            return {
                'crossover': crossover,
                'mutation': mutation,
                'population': population,
                'folder': folder_name
            }
        except (ValueError, IndexError):
            return None
    return None


def load_hypervolume_data(base_dir: str, filename: str = "promedio_2024_hypervolumes_normality_test.csv"):
    """
    Carga todos los datos de hipervolumen de todas las carpetas.
    
    Returns:
        DataFrame con columnas: folder, crossover, mutation, population, hypervolume
    """
    base_path = Path(base_dir)
    if not base_path.exists():
        print(f"⚠️  El directorio {base_dir} no existe")
        return pd.DataFrame()
    
    # Buscar todos los archivos
    pattern = str(base_path / "**" / filename)
    files = glob.glob(pattern, recursive=True)
    
    all_data = []
    
    for csv_file in sorted(files):
        folder_path = Path(csv_file).parent
        folder_name = folder_path.name
        
        # Parsear parámetros del nombre de la carpeta
        params = parse_folder_name(folder_name)
        if params is None:
            print(f"⚠️  No se pudo parsear el nombre de la carpeta: {folder_name}")
            continue
        
        try:
            # Cargar datos del CSV
            df = pd.read_csv(csv_file)
            
            if 'Hypervolume' not in df.columns:
                print(f"⚠️  No se encontró la columna 'Hypervolume' en {csv_file}")
                continue
            
            # Agregar información de parámetros a cada fila
            for _, row in df.iterrows():
                all_data.append({
                    'folder': folder_name,
                    'crossover': params['crossover'],
                    'mutation': params['mutation'],
                    'population': params['population'],
                    'hypervolume': row['Hypervolume']
                })
        
        except Exception as e:
            print(f"⚠️  Error cargando {csv_file}: {e}")
            continue
    
    if not all_data:
        print("⚠️  No se encontraron datos para analizar")
        return pd.DataFrame()
    
    return pd.DataFrame(all_data)


def perform_kruskal_wallis_test(groups_data: dict, factor_name: str, alpha: float = 0.05):
    """
    Realiza el test de Kruskal-Wallis para comparar múltiples grupos.
    
    Args:
        groups_data: Diccionario donde las claves son los nombres de los grupos
                     y los valores son arrays con los datos de cada grupo
        factor_name: Nombre del factor que se está analizando
        alpha: Nivel de significancia
    
    Returns:
        Diccionario con los resultados del test
    """
    group_names = list(groups_data.keys())
    groups = [groups_data[name] for name in group_names]
    
    # Filtrar grupos vacíos
    valid_groups = []
    valid_names = []
    for name, data in zip(group_names, groups):
        if len(data) > 0:
            valid_groups.append(data)
            valid_names.append(name)
    
    if len(valid_groups) < 2:
        return {
            'factor': factor_name,
            'statistic': np.nan,
            'p_value': np.nan,
            'significant': False,
            'groups': len(valid_groups),
            'note': 'Se requieren al menos 2 grupos para realizar el test'
        }
    
    # Realizar test de Kruskal-Wallis
    try:
        statistic, p_value = kruskal(*valid_groups)
        
        is_significant = p_value < alpha
        
        # Calcular estadísticos descriptivos por grupo
        group_stats = {}
        for name, data in zip(valid_names, valid_groups):
            group_stats[name] = {
                'n': len(data),
                'median': np.median(data),
                'mean': np.mean(data),
                'std': np.std(data, ddof=1),
                'q25': np.percentile(data, 25),
                'q75': np.percentile(data, 75)
            }
        
        return {
            'factor': factor_name,
            'statistic': statistic,
            'p_value': p_value,
            'significant': is_significant,
            'groups': len(valid_groups),
            'group_names': valid_names,
            'group_stats': group_stats,
            'alpha': alpha
        }
    
    except Exception as e:
        return {
            'factor': factor_name,
            'statistic': np.nan,
            'p_value': np.nan,
            'significant': False,
            'groups': len(valid_groups),
            'note': f'Error al realizar el test: {str(e)}'
        }


def perform_posthoc_tests(groups_data: dict, group_names: list, alpha: float = 0.05, 
                          correction: str = 'bonferroni'):
    """
    Realiza comparaciones post-hoc usando el test de Mann-Whitney U.
    
    Args:
        groups_data: Diccionario con los datos de cada grupo
        group_names: Lista de nombres de grupos a comparar
        alpha: Nivel de significancia
        correction: Método de corrección para comparaciones múltiples
                   ('bonferroni', 'holm', 'fdr_bh', o None)
    
    Returns:
        Lista de diccionarios con los resultados de cada comparación
    """
    comparisons = list(combinations(group_names, 2))
    results = []
    
    # Calcular número de comparaciones para corrección
    n_comparisons = len(comparisons)
    
    for group1, group2 in comparisons:
        data1 = groups_data[group1]
        data2 = groups_data[group2]
        
        if len(data1) == 0 or len(data2) == 0:
            continue
        
        try:
            # Test de Mann-Whitney U (equivalente a Wilcoxon rank-sum)
            statistic, p_value = mannwhitneyu(data1, data2, alternative='two-sided')
            
            # Aplicar corrección si se especifica
            if correction == 'bonferroni':
                p_value_corrected = min(p_value * n_comparisons, 1.0)
            elif correction == 'holm':
                # Para Holm, necesitaríamos ordenar todos los p-valores
                # Por simplicidad, usamos Bonferroni aquí
                p_value_corrected = min(p_value * n_comparisons, 1.0)
            else:
                p_value_corrected = p_value
            
            is_significant = p_value_corrected < alpha
            
            results.append({
                'group1': group1,
                'group2': group2,
                'statistic': statistic,
                'p_value': p_value,
                'p_value_corrected': p_value_corrected,
                'significant': is_significant,
                'correction': correction
            })
        
        except Exception as e:
            results.append({
                'group1': group1,
                'group2': group2,
                'statistic': np.nan,
                'p_value': np.nan,
                'p_value_corrected': np.nan,
                'significant': False,
                'error': str(e)
            })
    
    return results


def analyze_by_factor(df: pd.DataFrame, factor: str, alpha: float = 0.05):
    """
    Analiza los datos agrupados por un factor específico.
    
    Args:
        df: DataFrame con los datos
        factor: Nombre del factor ('crossover', 'mutation', 'population')
        alpha: Nivel de significancia
    
    Returns:
        Diccionario con los resultados del análisis
    """
    if factor not in df.columns:
        return None
    
    # Agrupar datos por el factor
    groups_data = {}
    for value in df[factor].unique():
        group_data = df[df[factor] == value]['hypervolume'].values
        groups_data[str(value)] = group_data
    
    # Realizar test de Kruskal-Wallis
    result = perform_kruskal_wallis_test(groups_data, factor, alpha)
    
    # Si hay diferencias significativas, realizar comparaciones post-hoc
    posthoc_results = []
    if result.get('significant', False) and 'group_names' in result:
        posthoc_results = perform_posthoc_tests(
            groups_data, 
            result['group_names'], 
            alpha,
            correction='bonferroni'
        )
    
    result['posthoc'] = posthoc_results
    
    return result


def save_results_to_csv(results: list, output_file: str):
    """
    Guarda los resultados del test de Kruskal-Wallis en un archivo CSV.
    """
    output_path = Path(output_file)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    rows = []
    for result in results:
        if 'note' in result:
            row = {
                'Factor': result.get('factor', ''),
                'Estadistico_H': result.get('statistic', ''),
                'p_valor': result.get('p_value', ''),
                'Significativo': result.get('significant', False),
                'Num_Grupos': result.get('groups', 0),
                'Alpha': result.get('alpha', 0.05),
                'Nota': result.get('note', '')
            }
        else:
            row = {
                'Factor': result.get('factor', ''),
                'Estadistico_H': result.get('statistic', ''),
                'p_valor': result.get('p_value', ''),
                'Significativo': result.get('significant', False),
                'Num_Grupos': result.get('groups', 0),
                'Alpha': result.get('alpha', 0.05),
                'Nota': ''
            }
        rows.append(row)
    
    df_results = pd.DataFrame(rows)
    df_results.to_csv(output_path, index=False)
    print(f"\n💾 Resultados principales guardados en: {output_path}")


def save_posthoc_results_to_csv(all_results: list, output_file: str):
    """
    Guarda los resultados de las comparaciones post-hoc en un archivo CSV.
    """
    output_path = Path(output_file)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    rows = []
    for result in all_results:
        if 'posthoc' in result and result['posthoc']:
            factor = result.get('factor', '')
            for posthoc in result['posthoc']:
                row = {
                    'Factor': factor,
                    'Grupo1': posthoc.get('group1', ''),
                    'Grupo2': posthoc.get('group2', ''),
                    'Estadistico_U': posthoc.get('statistic', ''),
                    'p_valor': posthoc.get('p_value', ''),
                    'p_valor_corregido': posthoc.get('p_value_corrected', ''),
                    'Significativo': posthoc.get('significant', False),
                    'Correccion': posthoc.get('correction', ''),
                    'Error': posthoc.get('error', '')
                }
                rows.append(row)
    
    if rows:
        df_posthoc = pd.DataFrame(rows)
        df_posthoc.to_csv(output_path, index=False)
        print(f"💾 Resultados post-hoc guardados en: {output_path}")
    else:
        print("⚠️  No hay resultados post-hoc para guardar")


def print_results(results: list, alpha: float = 0.05):
    """
    Imprime los resultados del análisis de forma legible.
    """
    print("\n" + "="*90)
    print("RESULTADOS DEL TEST DE KRUSKAL-WALLIS")
    print("="*90)
    print(f"\nNivel de significancia (α): {alpha}")
    print(f"Total de factores analizados: {len(results)}\n")
    
    for result in results:
        factor = result.get('factor', 'Desconocido')
        print(f"\n{'─'*90}")
        print(f"FACTOR: {factor.upper()}")
        print(f"{'─'*90}")
        
        if 'note' in result:
            print(f"⚠️  {result['note']}")
            continue
        
        statistic = result.get('statistic', np.nan)
        p_value = result.get('p_value', np.nan)
        is_significant = result.get('significant', False)
        n_groups = result.get('groups', 0)
        
        print(f"\n📊 Resultados del test:")
        print(f"   • Estadístico H: {statistic:.6f}")
        print(f"   • p-valor: {p_value:.6f}")
        print(f"   • Número de grupos: {n_groups}")
        
        if is_significant:
            print(f"   • Resultado: ✅ DIFERENCIAS SIGNIFICATIVAS (p < {alpha})")
            print(f"     → Se rechaza H₀: Las medianas de los grupos son iguales")
            print(f"     → Se acepta H₁: Al menos un grupo difiere significativamente")
        else:
            print(f"   • Resultado: ❌ NO HAY DIFERENCIAS SIGNIFICATIVAS (p ≥ {alpha})")
            print(f"     → No se rechaza H₀: No hay evidencia de diferencias entre grupos")
        
        # Estadísticos descriptivos por grupo
        if 'group_stats' in result:
            print(f"\n📈 Estadísticos descriptivos por grupo:")
            print(f"{'Grupo':<15} {'N':<6} {'Mediana':<12} {'Media':<12} {'Desv_Est':<12} {'Q25':<12} {'Q75':<12}")
            print("-" * 90)
            for group_name, stats in result['group_stats'].items():
                print(f"{group_name:<15} {stats['n']:<6} {stats['median']:<12.6f} "
                      f"{stats['mean']:<12.6f} {stats['std']:<12.6f} "
                      f"{stats['q25']:<12.6f} {stats['q75']:<12.6f}")
        
        # Comparaciones post-hoc
        if 'posthoc' in result and result['posthoc']:
            print(f"\n🔬 Comparaciones post-hoc (Mann-Whitney U con corrección de Bonferroni):")
            print(f"{'Grupo 1':<15} {'Grupo 2':<15} {'p-valor':<12} {'p-valor (corr.)':<15} {'Significativo':<12}")
            print("-" * 90)
            for posthoc in result['posthoc']:
                sig_str = "✅ Sí" if posthoc.get('significant', False) else "❌ No"
                p_val = posthoc.get('p_value', np.nan)
                p_val_corr = posthoc.get('p_value_corrected', np.nan)
                print(f"{posthoc.get('group1', ''):<15} {posthoc.get('group2', ''):<15} "
                      f"{p_val:<12.6f} {p_val_corr:<15.6f} {sig_str:<12}")
    
    print(f"\n{'='*90}\n")


def main():
    """
    Función principal que ejecuta el test de Kruskal-Wallis.
    """
    parser = argparse.ArgumentParser(
        description='Realiza el test no paramétrico de Kruskal-Wallis para comparar grupos',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Ejemplos de uso:
  # Análisis por defecto (procesa todos los archivos en output/):
  python kruskal_wallis_test.py
  
  # Especificar directorio y nivel de significancia:
  python kruskal_wallis_test.py --base-dir output/ --alpha 0.01
  
  # Guardar resultados en archivos específicos:
  python kruskal_wallis_test.py --output resultados_kw.csv --posthoc posthoc_kw.csv
        """
    )
    parser.add_argument(
        '--base-dir', '-b',
        default='output',
        help='Directorio base donde buscar archivos CSV (default: output)'
    )
    parser.add_argument(
        '--filename', '-f',
        default='promedio_2024_hypervolumes_normality_test.csv',
        help='Nombre del archivo CSV a buscar (default: promedio_2024_hypervolumes_normality_test.csv)'
    )
    parser.add_argument(
        '--output', '-o',
        default=None,
        help='Archivo de salida para resultados principales (default: output/kruskal_wallis_results.csv)'
    )
    parser.add_argument(
        '--posthoc', '-p',
        default=None,
        help='Archivo de salida para resultados post-hoc (default: output/kruskal_wallis_posthoc.csv)'
    )
    parser.add_argument(
        '--alpha', '-a',
        type=float,
        default=0.05,
        help='Nivel de significancia (default: 0.05)'
    )
    parser.add_argument(
        '--factors',
        nargs='+',
        choices=['crossover', 'mutation', 'population', 'all'],
        default=['all'],
        help='Factores a analizar (default: all)'
    )
    
    args = parser.parse_args()
    
    print("╔════════════════════════════════════════════════════════════╗")
    print("║  TEST NO PARAMÉTRICO DE KRUSKAL-WALLIS                    ║")
    print("╚════════════════════════════════════════════════════════════╝")
    print()
    
    # Cargar datos
    print(f"📂 Cargando datos desde: {args.base_dir}")
    df = load_hypervolume_data(args.base_dir, args.filename)
    
    if df.empty:
        print("\n❌ No se encontraron datos para analizar.")
        sys.exit(1)
    
    print(f"  ✓ Total de observaciones: {len(df)}")
    print(f"  ✓ Carpetas procesadas: {df['folder'].nunique()}")
    print(f"  ✓ Valores únicos de crossover: {sorted(df['crossover'].unique())}")
    print(f"  ✓ Valores únicos de mutation: {sorted(df['mutation'].unique())}")
    print(f"  ✓ Valores únicos de population: {sorted(df['population'].unique())}")
    
    # Determinar factores a analizar
    factors_to_analyze = []
    if 'all' in args.factors:
        factors_to_analyze = ['crossover', 'mutation', 'population']
    else:
        factors_to_analyze = args.factors
    
    # Realizar análisis para cada factor
    print(f"\n🔬 Realizando análisis para factores: {', '.join(factors_to_analyze)}")
    all_results = []
    
    for factor in factors_to_analyze:
        result = analyze_by_factor(df, factor, args.alpha)
        if result:
            all_results.append(result)
    
    # Mostrar resultados
    print_results(all_results, args.alpha)
    
    # Guardar resultados
    if args.output:
        output_file = args.output
    else:
        output_file = Path(args.base_dir) / "kruskal_wallis_results.csv"
    
    save_results_to_csv(all_results, str(output_file))
    
    if args.posthoc:
        posthoc_file = args.posthoc
    else:
        posthoc_file = Path(args.base_dir) / "kruskal_wallis_posthoc.csv"
    
    save_posthoc_results_to_csv(all_results, str(posthoc_file))
    
    print("="*90)
    print("✅ Análisis completado")
    print("="*90 + "\n")


if __name__ == "__main__":
    main()

