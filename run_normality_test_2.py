#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script para ejecutar 50 réplicas del experimento con diferentes semillas
y recopilar los valores de hipervolumen para análisis de normalidad.

Este script:
1. Ejecuta el programa Java Main.java 50 veces con diferentes semillas
2. Extrae el valor del hipervolumen de cada ejecución
3. Guarda los resultados en un archivo CSV para análisis estadístico
4. Permite verificar si los hipervolúmenes siguen una distribución normal
"""

import subprocess
import csv
import sys
import time
from pathlib import Path
from typing import List, Dict, Optional
import re

OUTPUT_DIR = Path("output/")
NUM_REPLICATES = 30
INSTANCE_NAME = "promedio_2024"
POPULATION_SIZE = 50
CROSSOVER_PROB = 0.6
MUTATION_PROB = 0.001


def run_java_experiment(seed: int, replicate_num: int, total_replicates: int) -> Optional[float]:
    """
    Ejecuta una réplica del experimento Java con una semilla específica.
    
    Args:
        seed: Semilla aleatoria para esta ejecución
        replicate_num: Número de réplica actual
        total_replicates: Total de réplicas a ejecutar
    
    Returns:
        El valor del hipervolumen si se encontró, None en caso contrario
    """
    print(f"\n{'='*80}")
    print(f"RÉPLICA {replicate_num}/{total_replicates} - Semilla: {seed}")
    print(f"{'='*80}\n")
    
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
        
        # Buscar el hipervolumen en la salida
        # El formato es: "✓ Hipervolumen calculado: X.XXXXXX"
        hypervolume = extract_hypervolume_from_output(result.stdout)
        
        if hypervolume is not None:
            print(f"✓ Réplica {replicate_num} completada - Hipervolumen: {hypervolume:.6f}")
            return hypervolume
        else:
            # Esperar un momento para que el archivo se escriba
            time.sleep(1)
            # Intentar leer del archivo CSV de estadísticas
            # El número de líneas esperadas es el número de réplicas exitosas + 1 (header)
            hypervolume = extract_hypervolume_from_csv(replicate_num)
            if hypervolume is not None:
                print(f"✓ Réplica {replicate_num} completada - Hipervolumen: {hypervolume:.6f} (desde CSV)")
                return hypervolume
            else:
                print(f"⚠ Réplica {replicate_num} completada pero no se encontró hipervolumen")
                return None
                
    except subprocess.TimeoutExpired:
        print(f"✗ Réplica {replicate_num} excedió el tiempo límite (10 minutos)")
        return None
    except subprocess.CalledProcessError as e:
        print(f"✗ Error en réplica {replicate_num}: código de salida {e.returncode}")
        if e.stderr:
            print(f"  Error: {e.stderr[:200]}")
        return None
    except Exception as e:
        print(f"✗ Error inesperado en réplica {replicate_num}: {e}")
        return None


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


def extract_hypervolume_from_csv(expected_line_count: int) -> Optional[float]:
    """
    Intenta extraer el hipervolumen del archivo CSV de estadísticas.
    Lee el último registro del archivo de estadísticas que corresponde a la ejecución actual.
    
    Args:
        expected_line_count: Número de líneas esperadas en el CSV (incluyendo header)
    """
    stats_file = OUTPUT_DIR / f"{INSTANCE_NAME}_hypervolume_stats.csv"
    
    if not stats_file.exists():
        return None
    
    try:
        # Leer el archivo CSV y obtener el último registro
        with open(stats_file, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            rows = list(reader)
            
            # Si el archivo tiene el número esperado de registros, tomar el último
            if len(rows) >= expected_line_count:
                last_row = rows[-1]
                if 'HV' in last_row:
                    return float(last_row['HV'])
            elif len(rows) > 0:
                # Si hay menos registros de los esperados, tomar el último disponible
                last_row = rows[-1]
                if 'HV' in last_row:
                    return float(last_row['HV'])
    except Exception as e:
        print(f"  ⚠ Error leyendo CSV: {e}")
    
    return None


def save_results(hypervolumes: List[float], seeds: List[int], output_file: str):
    """
    Guarda los resultados en un archivo CSV.
    
    Args:
        hypervolumes: Lista de valores de hipervolumen
        seeds: Lista de semillas correspondientes
        output_file: Nombre del archivo de salida
    """
    OUTPUT_DIR.mkdir(exist_ok=True)
    output_path = OUTPUT_DIR / output_file
    
    with open(output_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        writer.writerow(['Replica', 'Seed', 'Hypervolume'])
        
        for i, (seed, hv) in enumerate(zip(seeds, hypervolumes), 1):
            writer.writerow([i, seed, f"{hv:.6f}"])
    
    print(f"\n💾 Resultados guardados en: {output_path}")
    
    # Calcular estadísticas descriptivas
    if hypervolumes:
        import statistics
        mean_hv = statistics.mean(hypervolumes)
        median_hv = statistics.median(hypervolumes)
        std_hv = statistics.stdev(hypervolumes) if len(hypervolumes) > 1 else 0.0
        min_hv = min(hypervolumes)
        max_hv = max(hypervolumes)
        
        print(f"\n📊 Estadísticas Descriptivas:")
        print(f"   Número de réplicas exitosas: {len(hypervolumes)}/{NUM_REPLICATES}")
        print(f"   Media: {mean_hv:.6f}")
        print(f"   Mediana: {median_hv:.6f}")
        print(f"   Desviación estándar: {std_hv:.6f}")
        print(f"   Mínimo: {min_hv:.6f}")
        print(f"   Máximo: {max_hv:.6f}")


def main():
    """
    Función principal que ejecuta las 50 réplicas del experimento.
    """
    print("╔════════════════════════════════════════════════════════════╗")
    print("║  TEST DE NORMALIDAD - RECOPILACIÓN DE HIPERVOLÚMENES      ║")
    print("╚════════════════════════════════════════════════════════════╝")
    print()
    print(f"Configuración:")
    print(f"  - Instancia: {INSTANCE_NAME}")
    print(f"  - Tamaño de población: {POPULATION_SIZE}")
    print(f"  - Probabilidad de cruzamiento: {CROSSOVER_PROB}")
    print(f"  - Probabilidad de mutación: {MUTATION_PROB}")
    print(f"  - Número de réplicas: {NUM_REPLICATES}")
    print()
    
    hypervolumes = []
    seeds = []
    failed_replicates = []
    
    start_time = time.time()
    
    # Generar semillas: usar números consecutivos empezando desde 1
    # Esto asegura reproducibilidad
    for replicate_num in range(1, NUM_REPLICATES + 1):
        seed = replicate_num  # Usar el número de réplica como semilla
        
        hypervolume = run_java_experiment(seed, replicate_num, NUM_REPLICATES)
        
        if hypervolume is not None:
            hypervolumes.append(hypervolume)
            seeds.append(seed)
        else:
            failed_replicates.append(replicate_num)
        
        # Pequeña pausa entre ejecuciones para evitar problemas
        time.sleep(0.5)
    
    elapsed_time = time.time() - start_time
    
    print(f"\n{'='*80}")
    print(f"RESUMEN DE EJECUCIÓN")
    print(f"{'='*80}")
    print(f"Tiempo total: {elapsed_time/60:.2f} minutos")
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
