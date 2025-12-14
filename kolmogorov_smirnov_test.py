#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script para realizar el test de normalidad de Kolmogorov-Smirnov.

El test de Kolmogorov-Smirnov es una prueba estadística no paramétrica que evalúa
si una muestra de datos proviene de una distribución normal. Compara la función
de distribución acumulativa empírica (ECDF) de los datos con la función de
distribución acumulativa (CDF) de una distribución normal.

CARACTERÍSTICAS DEL TEST DE KOLMOGOROV-SMIRNOV:
- Es una prueba no paramétrica (no asume distribución específica)
- Compara la distribución empírica con una distribución teórica
- Es sensible a diferencias en la ubicación, dispersión y forma
- Funciona bien con muestras medianas y grandes
"""

import csv
import sys
import argparse
from pathlib import Path
import glob
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


def load_data_from_csv(csv_file: str, column: str = None):
    """
    Carga datos desde un archivo CSV.
    
    Args:
        csv_file: Ruta al archivo CSV
        column: Nombre de la columna a extraer. Si es None, intenta detectar automáticamente.
    
    Returns:
        Array numpy con los datos de la columna especificada
    """
    csv_path = Path(csv_file)
    
    if not csv_path.exists():
        raise FileNotFoundError(f"No se encontró el archivo: {csv_file}")
    
    if PANDAS_AVAILABLE:
        df = pd.read_csv(csv_path)
        
        # Si no se especifica columna, intentar detectar automáticamente
        if column is None:
            # Buscar columnas numéricas (excluyendo índices como Replica, Seed)
            numeric_cols = df.select_dtypes(include=[np.number]).columns.tolist()
            # Preferir 'Hypervolume' si existe, sino la primera columna numérica
            if 'Hypervolume' in numeric_cols:
                column = 'Hypervolume'
            elif numeric_cols:
                column = numeric_cols[0]
            else:
                raise ValueError("No se encontraron columnas numéricas en el archivo CSV")
        
        if column not in df.columns:
            raise ValueError(f"La columna '{column}' no existe en el archivo. Columnas disponibles: {list(df.columns)}")
        
        data = df[column].dropna().values
        
        if len(data) == 0:
            raise ValueError(f"La columna '{column}' no contiene datos válidos")
        
        return data, column
    else:
        # Implementación sin pandas
        data = []
        with open(csv_path, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                if column is None:
                    # Detectar automáticamente la primera columna numérica
                    for key, value in row.items():
                        try:
                            float(value)
                            if column is None:
                                column = key
                                data.append(float(value))
                                break
                        except (ValueError, TypeError):
                            continue
                else:
                    if column in row:
                        try:
                            data.append(float(row[column]))
                        except (ValueError, TypeError):
                            continue
        
        if not data:
            raise ValueError(f"No se pudieron extraer datos de la columna '{column}'")
        
        return np.array(data), column


def perform_kolmogorov_smirnov_test(data: np.ndarray, significance_level: float = 0.05):
    """
    Realiza el test de normalidad de Kolmogorov-Smirnov.
    
    EL TEST DE KOLMOGOROV-SMIRNOV:
    ------------------------------
    El test de Kolmogorov-Smirnov compara la función de distribución acumulativa
    empírica (ECDF) de los datos con la función de distribución acumulativa (CDF)
    de una distribución normal.
    
    Hipótesis:
    - H0 (hipótesis nula): Los datos siguen una distribución normal
    - H1 (hipótesis alternativa): Los datos NO siguen una distribución normal
    
    Estadístico:
    El estadístico D se calcula como:
        D = max |F_n(x) - F(x)|
    
    donde:
    - F_n(x) es la función de distribución acumulativa empírica
    - F(x) es la función de distribución acumulativa teórica (normal)
    - El máximo se toma sobre todos los valores x
    
    Interpretación:
    - Si el p-valor < α → Se rechaza H0 (los datos NO son normales)
    - Si el p-valor ≥ α → No se rechaza H0 (los datos pueden ser normales)
    
    Args:
        data: Array con los datos a analizar
        significance_level: Nivel de significancia (default: 0.05)
    
    Returns:
        Diccionario con los resultados del test
    """
    n = len(data)
    
    if n < 3:
        return {
            'statistic': np.nan,
            'p_value': np.nan,
            'normal': False,
            'mean': np.mean(data),
            'std': np.std(data, ddof=1),
            'n': n,
            'note': f'Tamaño de muestra insuficiente (n={n} < 3). Se requieren al menos 3 observaciones.'
        }
    
    # Si la desviación estándar es 0, todos los valores son iguales
    std = np.std(data, ddof=1)
    if std == 0:
        return {
            'statistic': np.nan,
            'p_value': np.nan,
            'normal': False,
            'mean': np.mean(data),
            'std': std,
            'n': n,
            'note': 'Todos los valores son iguales (std=0)'
        }
    
    # Calcular media y desviación estándar de los datos
    mean = np.mean(data)
    std = np.std(data, ddof=1)
    
    # Realizar test de Kolmogorov-Smirnov contra distribución normal
    # scipy.stats.kstest compara la ECDF empírica con la CDF teórica
    # Usamos 'norm' para distribución normal, con los parámetros estimados de los datos
    try:
        # Normalizar los datos (estandarizar)
        normalized_data = (data - mean) / std
        
        # Realizar el test contra una distribución normal estándar
        statistic, p_value = stats.kstest(normalized_data, 'norm')
        
        # Determinar si los datos son normales
        is_normal = p_value >= significance_level
        
        return {
            'statistic': statistic,
            'p_value': p_value,
            'normal': is_normal,
            'mean': mean,
            'std': std,
            'n': n,
            'significance_level': significance_level
        }
        
    except Exception as e:
        return {
            'statistic': np.nan,
            'p_value': np.nan,
            'normal': False,
            'mean': mean,
            'std': std,
            'n': n,
            'note': f'Error al realizar el test: {str(e)}'
        }


def print_test_results(test_result: dict, column_name: str):
    """
    Imprime los resultados del test de normalidad de Kolmogorov-Smirnov.
    """
    print("\n" + "="*90)
    print("TEST DE NORMALIDAD DE KOLMOGOROV-SMIRNOV")
    print("="*90)
    print(f"\n📊 Columna analizada: {column_name}")
    print(f"   Nivel de significancia (α): {test_result.get('significance_level', 0.05)}")
    print(f"\n{'─'*90}")
    
    if 'note' in test_result:
        print(f"⚠️  {test_result['note']}")
        print(f"\n   Estadísticos descriptivos:")
        print(f"   • Tamaño de muestra (n): {test_result['n']}")
        print(f"   • Media: {test_result['mean']:.6f}")
        print(f"   • Desviación estándar: {test_result['std']:.6f}")
    else:
        print(f"{'Estadístico D':<20} {'p-valor':<20} {'Normal (α=' + str(test_result.get('significance_level', 0.05)) + ')':<20} {'n':<10}")
        print("-" * 90)
        
        normal_str = "✅ Sí" if test_result['normal'] else "❌ No"
        stat_str = f"{test_result['statistic']:.6f}"
        pval_str = f"{test_result['p_value']:.6f}"
        
        print(f"{stat_str:<20} {pval_str:<20} {normal_str:<20} {test_result['n']:<10}")
        
        print(f"\n{'─'*90}")
        print(f"📈 Estadísticos descriptivos:")
        print(f"   • Tamaño de muestra (n): {test_result['n']}")
        print(f"   • Media: {test_result['mean']:.6f}")
        print(f"   • Desviación estándar: {test_result['std']:.6f}")
        print(f"   • Mínimo: {np.min(test_result.get('data', [])):.6f}" if 'data' in test_result else "")
        print(f"   • Máximo: {np.max(test_result.get('data', [])):.6f}" if 'data' in test_result else "")
        
        print(f"\n{'─'*90}")
        if test_result['normal']:
            print("✅ Los datos siguen una distribución normal (p-valor ≥ α)")
            print("   → No se rechaza la hipótesis nula (H₀)")
            print("   → Se pueden aplicar tests paramétricos (ANOVA, t-test)")
        else:
            print("❌ Los datos NO siguen una distribución normal (p-valor < α)")
            print("   → Se rechaza la hipótesis nula (H₀)")
            print("   → Se recomienda usar tests no paramétricos (Kruskal-Wallis, Mann-Whitney)")
            print("   → O transformar los datos antes de aplicar tests paramétricos")
    
    print()


def find_all_test_files(base_dir: str, filename: str = "promedio_2024_hypervolumes_normality_test.csv"):
    """
    Busca todos los archivos con el nombre especificado en todas las subcarpetas.
    
    Args:
        base_dir: Directorio base donde buscar
        filename: Nombre del archivo a buscar
    
    Returns:
        Lista de rutas a los archivos encontrados
    """
    base_path = Path(base_dir)
    if not base_path.exists():
        return []
    
    # Buscar recursivamente todos los archivos con ese nombre
    pattern = str(base_path / "**" / filename)
    files = glob.glob(pattern, recursive=True)
    
    return sorted(files)


def process_all_files(base_dir: str, filename: str, column: str = None, 
                     significance_level: float = 0.05, verbose: bool = False):
    """
    Procesa todos los archivos encontrados y realiza el test en cada uno.
    
    Args:
        base_dir: Directorio base donde buscar archivos
        filename: Nombre del archivo a buscar
        column: Nombre de la columna a analizar
        significance_level: Nivel de significancia
        verbose: Mostrar información detallada
    
    Returns:
        Lista de diccionarios con los resultados de cada archivo
    """
    files = find_all_test_files(base_dir, filename)
    
    if not files:
        print(f"⚠️  No se encontraron archivos '{filename}' en {base_dir}")
        return []
    
    print(f"📂 Encontrados {len(files)} archivos para procesar\n")
    
    all_results = []
    
    for i, csv_file in enumerate(files, 1):
        folder_name = Path(csv_file).parent.name
        
        if verbose:
            print(f"[{i}/{len(files)}] Procesando: {folder_name}/{filename}")
        
        try:
            # Cargar datos
            data, column_name = load_data_from_csv(csv_file, column)
            
            # Realizar test
            test_result = perform_kolmogorov_smirnov_test(data, significance_level)
            test_result['folder'] = folder_name
            test_result['file_path'] = str(csv_file)
            test_result['column'] = column_name
            
            all_results.append(test_result)
            
            if verbose:
                status = "✅ Normal" if test_result.get('normal', False) else "❌ No normal"
                print(f"   {status} (p={test_result.get('p_value', 'N/A'):.6f}, n={test_result['n']})")
        
        except Exception as e:
            print(f"   ⚠️  Error procesando {folder_name}: {e}")
            all_results.append({
                'folder': folder_name,
                'file_path': str(csv_file),
                'error': str(e),
                'n': 0
            })
    
    return all_results


def save_batch_results_to_csv(all_results: list, output_file: str):
    """
    Guarda todos los resultados en un archivo CSV consolidado.
    """
    if not all_results:
        print("⚠️  No hay resultados para guardar")
        return
    
    output_path = Path(output_file)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    rows = []
    for result in all_results:
        if 'error' in result:
            row = {
                'Carpeta': result.get('folder', ''),
                'Archivo': Path(result.get('file_path', '')).name,
                'Columna': result.get('column', ''),
                'N': 0,
                'Media': '',
                'Desv_Est': '',
                'Estadistico_KS': '',
                'p_valor': '',
                'Alpha': '',
                'Es_Normal': False,
                'Nota': result.get('error', 'Error desconocido')
            }
        else:
            row = {
                'Carpeta': result.get('folder', ''),
                'Archivo': Path(result.get('file_path', '')).name,
                'Columna': result.get('column', ''),
                'N': result.get('n', 0),
                'Media': result.get('mean', np.nan if NUMPY_AVAILABLE else ''),
                'Desv_Est': result.get('std', np.nan if NUMPY_AVAILABLE else ''),
                'Estadistico_KS': result.get('statistic', np.nan if NUMPY_AVAILABLE else ''),
                'p_valor': result.get('p_value', np.nan if NUMPY_AVAILABLE else ''),
                'Alpha': result.get('significance_level', 0.05),
                'Es_Normal': result.get('normal', False),
                'Nota': result.get('note', '')
            }
        rows.append(row)
    
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
    
    print(f"\n💾 Resultados consolidados guardados en: {output_path}")


def print_batch_summary(all_results: list, significance_level: float = 0.05):
    """
    Imprime un resumen de todos los resultados.
    """
    if not all_results:
        return
    
    print("\n" + "="*90)
    print("RESUMEN DE RESULTADOS - TEST DE KOLMOGOROV-SMIRNOV")
    print("="*90)
    print(f"\n📊 Total de archivos procesados: {len(all_results)}")
    print(f"   Nivel de significancia (α): {significance_level}")
    print(f"\n{'─'*90}")
    
    # Contar resultados
    successful = [r for r in all_results if 'error' not in r]
    errors = [r for r in all_results if 'error' in r]
    normal = [r for r in successful if r.get('normal', False)]
    not_normal = [r for r in successful if not r.get('normal', False)]
    
    print(f"\n📈 Estadísticas:")
    print(f"   • Procesados exitosamente: {len(successful)}")
    print(f"   • Con errores: {len(errors)}")
    print(f"   • Distribución normal: {len(normal)} ({len(normal)/len(successful)*100:.1f}%)" if successful else "   • Distribución normal: 0")
    print(f"   • NO distribución normal: {len(not_normal)} ({len(not_normal)/len(successful)*100:.1f}%)" if successful else "   • NO distribución normal: 0")
    
    if successful:
        print(f"\n{'─'*90}")
        print(f"{'Carpeta':<25} {'N':<6} {'Estadístico D':<18} {'p-valor':<15} {'Normal':<10}")
        print("-" * 90)
        
        for result in sorted(successful, key=lambda x: x.get('folder', '')):
            folder = result.get('folder', '')
            n = result.get('n', 0)
            stat = result.get('statistic', np.nan)
            pval = result.get('p_value', np.nan)
            normal = result.get('normal', False)
            
            normal_str = "✅ Sí" if normal else "❌ No"
            stat_str = f"{stat:.6f}" if not np.isnan(stat) else "N/A"
            pval_str = f"{pval:.6f}" if not np.isnan(pval) else "N/A"
            
            print(f"{folder:<25} {n:<6} {stat_str:<18} {pval_str:<15} {normal_str:<10}")
    
    if errors:
        print(f"\n{'─'*90}")
        print("⚠️  Archivos con errores:")
        for result in errors:
            folder = result.get('folder', '')
            error = result.get('error', 'Error desconocido')
            print(f"   • {folder}: {error}")
    
    print(f"\n{'─'*90}\n")


def save_results_to_csv(test_result: dict, column_name: str, output_file: str):
    """
    Guarda los resultados del test de Kolmogorov-Smirnov en un archivo CSV.
    """
    output_path = Path(output_file)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    row = {
        'Columna': column_name,
        'N': test_result['n'],
        'Media': test_result.get('mean', np.nan if NUMPY_AVAILABLE else ''),
        'Desv_Est': test_result.get('std', np.nan if NUMPY_AVAILABLE else ''),
        'Estadistico_KS': test_result.get('statistic', np.nan if NUMPY_AVAILABLE else ''),
        'p_valor': test_result.get('p_value', np.nan if NUMPY_AVAILABLE else ''),
        'Alpha': test_result.get('significance_level', 0.05),
        'Es_Normal': test_result.get('normal', False),
        'Nota': test_result.get('note', '')
    }
    
    if PANDAS_AVAILABLE:
        df = pd.DataFrame([row])
        df.to_csv(output_path, index=False)
    else:
        # Guardar sin pandas
        fieldnames = row.keys()
        file_exists = output_path.exists()
        with open(output_path, 'a' if file_exists else 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            if not file_exists:
                writer.writeheader()
            writer.writerow(row)
    
    print(f"💾 Resultados guardados en: {output_path}")


def print_explanation():
    """
    Imprime una explicación detallada del test de Kolmogorov-Smirnov.
    """
    print("\n" + "="*90)
    print("EXPLICACIÓN DEL TEST DE KOLMOGOROV-SMIRNOV")
    print("="*90)
    print("""
¿QUÉ ES EL TEST DE KOLMOGOROV-SMIRNOV?
-------------------------------------
El test de Kolmogorov-Smirnov es una prueba estadística no paramétrica que evalúa
si una muestra de datos proviene de una distribución específica (en este caso,
distribución normal). Compara la función de distribución acumulativa empírica
(ECDF) de los datos con la función de distribución acumulativa (CDF) teórica.

HIPÓTESIS:
----------
• H₀ (Hipótesis nula): Los datos siguen una distribución normal
• H₁ (Hipótesis alternativa): Los datos NO siguen una distribución normal

ESTADÍSTICO D:
--------------
El estadístico de Kolmogorov-Smirnov se calcula como:

    D = max |F_n(x) - F(x)|

donde:
• F_n(x) = función de distribución acumulativa empírica (ECDF)
• F(x) = función de distribución acumulativa teórica (normal)
• El máximo se toma sobre todos los valores x en la muestra

INTERPRETACIÓN:
---------------
• Si p-valor < α → Se rechaza H₀ (los datos NO son normales)
• Si p-valor ≥ α → No se rechaza H₀ (los datos pueden ser normales)

VENTAJAS:
---------
✓ Es una prueba no paramétrica (no asume distribución específica)
✓ Compara toda la distribución, no solo momentos
✓ Funciona bien con muestras medianas y grandes
✓ Es relativamente robusto

LIMITACIONES:
-------------
✗ Puede ser menos potente que otros tests para muestras pequeñas
✗ Los parámetros de la distribución normal se estiman de los datos
✗ Puede ser demasiado conservador en algunos casos

CUÁNDO USARLO:
-------------
• Para validar supuestos de normalidad antes de tests paramétricos
• Cuando necesitas comparar la distribución completa
• Para muestras medianas y grandes (n ≥ 30 recomendado)
• Cuando quieres una prueba no paramétrica

COMPARACIÓN CON OTROS TESTS:
----------------------------
• vs. Shapiro-Wilk: KS es más adecuado para muestras grandes
• vs. Anderson-Darling: KS es menos sensible a las colas
• vs. D'Agostino-Pearson: KS compara toda la distribución, no solo momentos

REFERENCIAS:
------------
Kolmogorov, A. N. (1933). Sulla determinazione empirica di una legge di
distribuzione. Giornale dell'Istituto Italiano degli Attuari, 4, 83-91.

Smirnov, N. V. (1948). Table for estimating the goodness of fit of empirical
distributions. Annals of Mathematical Statistics, 19, 279-281.
    """)
    print("="*90 + "\n")


def main():
    """
    Función principal que ejecuta el test de normalidad de Kolmogorov-Smirnov.
    """
    parser = argparse.ArgumentParser(
        description='Realiza el test de normalidad de Kolmogorov-Smirnov en un archivo CSV o en todos los archivos de un directorio',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Ejemplos de uso:
  # Comportamiento por defecto (procesa todos los archivos en output/):
  python kolmogorov_smirnov_test.py
  
  # Analizar un archivo específico:
  python kolmogorov_smirnov_test.py output/promedio_06_1_50/promedio_2024_hypervolumes_normality_test.csv
  
  # Analizar todos los archivos en un directorio específico:
  python kolmogorov_smirnov_test.py --batch output/ --filename promedio_2024_hypervolumes_normality_test.csv
  
  # Con opciones adicionales:
  python kolmogorov_smirnov_test.py --batch output/ --alpha 0.01 --output resultados_ks.csv
        """
    )
    parser.add_argument(
        'csv_file',
        nargs='?',
        default=None,
        help='Ruta al archivo CSV con los datos (modo archivo único)'
    )
    parser.add_argument(
        '--batch', '-b',
        default=None,
        help='Modo lote: directorio base donde buscar archivos'
    )
    parser.add_argument(
        '--filename', '-f',
        default='promedio_2024_hypervolumes_normality_test.csv',
        help='Nombre del archivo a buscar en modo lote (default: promedio_2024_hypervolumes_normality_test.csv)'
    )
    parser.add_argument(
        '--column', '-c',
        default=None,
        help='Nombre de la columna a analizar. Si no se especifica, se detecta automáticamente.'
    )
    parser.add_argument(
        '--output', '-o',
        default=None,
        help='Nombre del archivo de salida para los resultados (opcional)'
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
        help='Muestra una explicación detallada del test de Kolmogorov-Smirnov'
    )
    parser.add_argument(
        '--verbose', '-v',
        action='store_true',
        help='Muestra información detallada'
    )
    
    args = parser.parse_args()
    
    print("╔════════════════════════════════════════════════════════════╗")
    print("║  TEST DE NORMALIDAD DE KOLMOGOROV-SMIRNOV                 ║")
    print("╚════════════════════════════════════════════════════════════╝")
    print()
    
    if args.explain:
        print_explanation()
    
    # Determinar modo de operación
    # Si no se especifica nada, usar modo batch por defecto en output/
    if not args.batch and not args.csv_file:
        # Comportamiento por defecto: modo batch en output/
        default_batch_dir = "output"
        if Path(default_batch_dir).exists():
            args.batch = default_batch_dir
            print("💡 No se especificaron argumentos. Usando modo lote por defecto en 'output/'")
        else:
            parser.print_help()
            print(f"\n❌ ERROR: No se especificaron argumentos y el directorio '{default_batch_dir}' no existe.")
            print("   Por favor, especifica un archivo CSV o usa --batch para procesar múltiples archivos.")
            sys.exit(1)
    
    if args.batch:
        # Modo lote: procesar todos los archivos en el directorio
        print(f"🔄 Modo lote: procesando todos los archivos '{args.filename}' en {args.batch}")
        
        all_results = process_all_files(
            args.batch, 
            args.filename, 
            args.column, 
            args.alpha, 
            args.verbose
        )
        
        if not all_results:
            print("\n❌ No se encontraron archivos para procesar.")
            sys.exit(1)
        
        # Mostrar resumen
        print_batch_summary(all_results, args.alpha)
        
        # Guardar resultados consolidados
        if args.output:
            save_batch_results_to_csv(all_results, args.output)
        else:
            # Guardar por defecto
            default_output = Path(args.batch) / "kolmogorov_smirnov_results.csv"
            save_batch_results_to_csv(all_results, str(default_output))
        
        print("="*90)
        print("✅ Análisis completado")
        print("="*90 + "\n")
    
    elif args.csv_file:
        # Modo archivo único
        print(f"📂 Cargando datos desde: {args.csv_file}")
        try:
            data, column_name = load_data_from_csv(args.csv_file, args.column)
            print(f"  ✓ Columna analizada: {column_name}")
            print(f"  ✓ Tamaño de muestra: {len(data)}")
        except Exception as e:
            print(f"\n❌ ERROR al cargar los datos: {e}")
            sys.exit(1)
        
        # Realizar test
        print(f"\n🔬 Realizando test de normalidad de Kolmogorov-Smirnov (α={args.alpha})...")
        test_result = perform_kolmogorov_smirnov_test(data, args.alpha)
        
        # Agregar los datos al resultado para estadísticos descriptivos
        test_result['data'] = data
        
        # Mostrar resultados
        print_test_results(test_result, column_name)
        
        # Guardar resultados si se especifica archivo de salida
        if args.output:
            save_results_to_csv(test_result, column_name, args.output)
        
        print("="*90)
        print("✅ Análisis completado")
        print("="*90 + "\n")


if __name__ == "__main__":
    main()
