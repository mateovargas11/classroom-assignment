#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script para ejecutar 50 réplicas del experimento con diferentes semillas EN PARALELO
y recopilar los valores de hipervolumen para análisis de normalidad.

Este script:
1. Ejecuta el programa Java Main.java 50 veces en paralelo con diferentes semillas
2. Extrae el valor del hipervolumen de cada ejecución
3. Guarda los resultados en un archivo CSV para análisis estadístico
4. Permite verificar si los hipervolúmenes siguen una distribución normal

VENTAJAS DE LA PARALELIZACIÓN:
- Reduce significativamente el tiempo de ejecución (hasta N veces más rápido, donde N = número de cores)
- Ejecuta múltiples réplicas simultáneamente
- Mantiene la reproducibilidad usando semillas diferentes
"""

import subprocess
import csv
import sys
import time
from pathlib import Path
from typing import List, Dict, Optional, Tuple
import re
from concurrent.futures import ProcessPoolExecutor, as_completed
import os

OUTPUT_DIR = Path("output/paralelos")
NUM_REPLICATES = 50
INSTANCE_NAME = "promedio_2024"
POPULATION_SIZE = 50
CROSSOVER_PROB = 0.8
MUTATION_PROB = 0.001

# Configuración de paralelización
MAX_WORKERS = os.cpu_count() or 4  # Usar todos los cores disponibles, mínimo 4


def run_java_experiment(seed: int, replicate_num: int, total_replicates: int) -> Tuple[int, Optional[float], float]:
    """
    Ejecuta una réplica del experimento Java con una semilla específica.
    Esta función está diseñada para ejecutarse en paralelo.
    
    Args:
        seed: Semilla aleatoria para esta ejecución
        replicate_num: Número de réplica actual
        total_replicates: Total de réplicas a ejecutar
    
    Returns:
        Tupla (replicate_num, hypervolume, elapsed_time)
    """
    start_time = time.time()
    
    command = [
        "mvn",
        "compile",
        "exec:java",
        "-Dexec.mainClass=com.university.Main",
        "-Dexec.args=" + f"{INSTANCE_NAME} {POPULATION_SIZE} {CROSSOVER_PROB} {MUTATION_PROB} {seed}",
        "-q"  # Modo silencioso
    ]
    
    try:
        # Ejecutar el comando y capturar la salida
        result = subprocess.run(
            command,
            check=True,
            capture_output=True,
            text=True,
            timeout=600  # Timeout de 10 minutos por ejecución
        )
        
        elapsed_time = time.time() - start_time
        
        # Buscar el hipervolumen en la salida
        hypervolume = extract_hypervolume_from_output(result.stdout)
        
        if hypervolume is not None:
            return (replicate_num, hypervolume, elapsed_time)
        else:
            # Intentar leer del archivo CSV de estadísticas
            # Esperar un momento para que el archivo se escriba
            time.sleep(0.5)
            hypervolume = extract_hypervolume_from_csv()
            if hypervolume is not None:
                return (replicate_num, hypervolume, elapsed_time)
            else:
                return (replicate_num, None, elapsed_time)
                
    except subprocess.TimeoutExpired:
        elapsed_time = time.time() - start_time
        return (replicate_num, None, elapsed_time)
    except subprocess.CalledProcessError as e:
        elapsed_time = time.time() - start_time
        return (replicate_num, None, elapsed_time)
    except Exception as e:
        elapsed_time = time.time() - start_time
        return (replicate_num, None, elapsed_time)


def extract_hypervolume_from_output(output: str) -> Optional[float]:
    """
    Extrae el valor del hipervolumen de la salida del programa Java.
    
    Busca líneas como:
    - "✓ Hipervolumen calculado: 32.592000"
    - "Hipervolumen calculado: 32.592000"
    """
    # Patrones para buscar el hipervolumen
    patterns = [
        r'Hipervolumen calculado:\s*([\d.]+)',
        r'✓ Hipervolumen calculado:\s*([\d.]+)',
        r'hypervolume[:\s]+([\d.]+)',
        r'HV[:\s]+([\d.]+)',
    ]
    
    for pattern in patterns:
        match = re.search(pattern, output, re.IGNORECASE)
        if match:
            try:
                return float(match.group(1))
            except ValueError:
                continue
    
    return None


def extract_hypervolume_from_csv() -> Optional[float]:
    """
    Intenta extraer el hipervolumen del archivo CSV de estadísticas.
    Lee el último registro del archivo de estadísticas.
    """
    stats_file = OUTPUT_DIR / f"{INSTANCE_NAME}_hypervolume_stats.csv"
    
    if not stats_file.exists():
        return None
    
    try:
        # Leer el archivo CSV y obtener el último registro
        with open(stats_file, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            rows = list(reader)
            
            if rows:
                # Tomar el último registro (el más reciente)
                last_row = rows[-1]
                if 'HV' in last_row:
                    return float(last_row['HV'])
    except Exception as e:
        pass  # Silenciar errores en lectura paralela
    
    return None


def save_results(hypervolumes: List[Tuple[int, float]], seeds: List[int], output_file: str):
    """
    Guarda los resultados en un archivo CSV.
    
    Args:
        hypervolumes: Lista de tuplas (replicate_num, hypervolume)
        seeds: Lista de semillas correspondientes
        output_file: Nombre del archivo de salida
    """
    OUTPUT_DIR.mkdir(exist_ok=True)
    output_path = OUTPUT_DIR / output_file
    
    # Ordenar por número de réplica
    hypervolumes_sorted = sorted(hypervolumes, key=lambda x: x[0])
    
    with open(output_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        writer.writerow(['Replica', 'Seed', 'Hypervolume'])
        
        for (replicate_num, hv), seed in zip(hypervolumes_sorted, seeds):
            writer.writerow([replicate_num, seed, f"{hv:.6f}"])
    
    print(f"\n💾 Resultados guardados en: {output_path}")
    
    # Calcular estadísticas descriptivas
    if hypervolumes:
        import statistics
        hv_values = [hv for _, hv in hypervolumes_sorted]
        mean_hv = statistics.mean(hv_values)
        median_hv = statistics.median(hv_values)
        std_hv = statistics.stdev(hv_values) if len(hv_values) > 1 else 0.0
        min_hv = min(hv_values)
        max_hv = max(hv_values)
        
        print(f"\n📊 Estadísticas Descriptivas:")
        print(f"   Número de réplicas exitosas: {len(hypervolumes)}/{NUM_REPLICATES}")
        print(f"   Media: {mean_hv:.6f}")
        print(f"   Mediana: {median_hv:.6f}")
        print(f"   Desviación estándar: {std_hv:.6f}")
        print(f"   Mínimo: {min_hv:.6f}")
        print(f"   Máximo: {max_hv:.6f}")


def main():
    """
    Función principal que ejecuta las 50 réplicas del experimento en paralelo.
    """
    print("╔════════════════════════════════════════════════════════════╗")
    print("║  TEST DE NORMALIDAD - RECOPILACIÓN DE HIPERVOLÚMENES      ║")
    print("║  (EJECUCIÓN PARALELA)                                      ║")
    print("╚════════════════════════════════════════════════════════════╝")
    print()
    print(f"Configuración:")
    print(f"  - Instancia: {INSTANCE_NAME}")
    print(f"  - Tamaño de población: {POPULATION_SIZE}")
    print(f"  - Probabilidad de cruzamiento: {CROSSOVER_PROB}")
    print(f"  - Probabilidad de mutación: {MUTATION_PROB}")
    print(f"  - Número de réplicas: {NUM_REPLICATES}")
    print(f"  - Workers paralelos: {MAX_WORKERS}")
    print()
    
    # Generar semillas: usar números consecutivos empezando desde 1
    seeds = list(range(1, NUM_REPLICATES + 1))
    
    hypervolumes = []
    failed_replicates = []
    
    start_time = time.time()
    
    # Ejecutar réplicas en paralelo
    with ProcessPoolExecutor(max_workers=MAX_WORKERS) as executor:
        # Enviar todos los trabajos
        future_to_seed = {
            executor.submit(run_java_experiment, seed, replicate_num, NUM_REPLICATES): (seed, replicate_num)
            for replicate_num, seed in enumerate(seeds, 1)
        }
        
        # Procesar resultados conforme se completan
        completed = 0
        for future in as_completed(future_to_seed):
            seed, replicate_num = future_to_seed[future]
            try:
                rep_num, hypervolume, elapsed_time = future.result()
                
                if hypervolume is not None:
                    hypervolumes.append((rep_num, hypervolume))
                    print(f"✓ Réplica {rep_num}/{NUM_REPLICATES} completada en {elapsed_time:.1f}s - "
                          f"Hipervolumen: {hypervolume:.6f} (Semilla: {seed})")
                else:
                    failed_replicates.append(rep_num)
                    print(f"⚠ Réplica {rep_num}/{NUM_REPLICATES} completada pero no se encontró hipervolumen "
                          f"(Semilla: {seed})")
                
                completed += 1
                print(f"   Progreso: {completed}/{NUM_REPLICATES} réplicas completadas")
                
            except Exception as e:
                failed_replicates.append(replicate_num)
                print(f"✗ Error en réplica {replicate_num}: {e}")
                completed += 1
    
    elapsed_time = time.time() - start_time
    
    print(f"\n{'='*80}")
    print(f"RESUMEN DE EJECUCIÓN")
    print(f"{'='*80}")
    print(f"Tiempo total: {elapsed_time/60:.2f} minutos ({elapsed_time:.1f} segundos)")
    print(f"Tiempo promedio por réplica: {elapsed_time/NUM_REPLICATES:.1f} segundos")
    print(f"Réplicas exitosas: {len(hypervolumes)}/{NUM_REPLICATES}")
    
    if failed_replicates:
        print(f"Réplicas fallidas: {failed_replicates}")
    
    if not hypervolumes:
        print("\n❌ ERROR: No se obtuvieron hipervolúmenes de ninguna réplica.")
        print("   Verifica que el programa Java se ejecute correctamente.")
        sys.exit(1)
    
    # Guardar resultados
    output_file = f"{INSTANCE_NAME}_hypervolumes_normality_test.csv"
    save_results(hypervolumes, seeds, output_file)
    
    print(f"\n{'='*80}")
    print(f"✅ RECOPILACIÓN COMPLETADA")
    print(f"{'='*80}")
    print(f"\n💡 Próximos pasos:")
    print(f"   1. Ejecuta el test de Anderson-Darling:")
    print(f"      python anderson_darling_test.py")
    print(f"   2. O analiza los datos manualmente usando el archivo:")
    print(f"      {OUTPUT_DIR / output_file}")
    print()


if __name__ == "__main__":
    main()
