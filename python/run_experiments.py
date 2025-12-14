#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script para ejecutar múltiples experimentos con diferentes configuraciones
del algoritmo NSGA-II y generar gráficas después de cada ejecución.
"""

import subprocess
import os
import glob
import sys
from pathlib import Path

# Configuración de parámetros
POPULATION_SIZES = [50, 100, 200]
CROSSOVER_PROBABILITIES = [0.6, 0.7, 0.8]
MUTATION_PROBABILITIES = [0.1, 0.01, 0.001]
INSTANCE_FILES = ["promedio_2024"]  # Por ahora solo un archivo

# Rutas de scripts
PLOT_EVOLUCION_SCRIPT = "output/plot_evolucion.py"
PLOT_PARETO_SCRIPT = "plot_pareto_front.py"
OUTPUT_DIR = "output"


def run_command(command, description):
    """Ejecuta un comando y muestra su salida."""
    print(f"\n{'='*70}")
    print(f"Ejecutando: {description}")
    print(f"Comando: {' '.join(command)}")
    print(f"{'='*70}\n")
    
    try:
        result = subprocess.run(
            command,
            check=True,
            capture_output=False,
            text=True
        )
        print(f"\n✓ {description} completado exitosamente\n")
        return True
    except subprocess.CalledProcessError as e:
        print(f"\n✗ Error al ejecutar: {description}")
        print(f"Código de salida: {e.returncode}\n")
        return False


def find_latest_file(pattern):
    """Encuentra el archivo más reciente que coincide con el patrón."""
    files = glob.glob(pattern)
    if not files:
        return None
    # Ordenar por tiempo de modificación (más reciente primero)
    files.sort(key=os.path.getmtime, reverse=True)
    return files[0]




def run_java_main(instance_name, population_size, crossover_prob, mutation_prob):
    """Ejecuta el programa Java Main con los parámetros especificados."""
    # Usar Maven para ejecutar el programa Java
    command = [
        "mvn",
        "compile",
        "exec:java",
        "-Dexec.mainClass=com.university.Main",
        "-Dexec.args=" + f"{instance_name} {population_size} {crossover_prob} {mutation_prob}",
        "-q"  # Modo silencioso para reducir output
    ]
    
    return run_command(command, f"Java Main - Instancia: {instance_name}, Población: {population_size}, "
                                f"Cruzamiento: {crossover_prob}, Mutación: {mutation_prob}")


def generate_output_filename(population_size, mutation_prob, crossover_prob, prefix="evolucion_nsga2"):
    """
    Genera un nombre de archivo único basado en los parámetros.
    Formato: evolucion_nsga2_50_01_06.png
    - 50 = tamaño de población
    - 01 = probabilidad de mutación (0.1 -> 01, 0.01 -> 001, 0.001 -> 0001)
    - 06 = probabilidad de cruzamiento (0.6 -> 06, 0.7 -> 07, 0.8 -> 08)
    """
    # Convertir probabilidad de mutación a string sin punto, manteniendo ceros
    # 0.1 -> "01", 0.01 -> "001", 0.001 -> "0001"
    mut_str = str(mutation_prob).replace('.', '')
    # Si no tiene ceros a la izquierda, agregar uno (0.1 -> "1" -> "01")
    if len(mut_str) == 1:
        mut_str = "0" + mut_str
    
    # Convertir probabilidad de cruzamiento a string sin punto
    # 0.6 -> "06", 0.7 -> "07", 0.8 -> "08"
    cross_str = str(int(crossover_prob * 10))
    if len(cross_str) == 1:
        cross_str = "0" + cross_str
    
    return f"{OUTPUT_DIR}/{prefix}_{population_size}_{mut_str}_{cross_str}.png"


def run_plot_scripts(instance_name, population_size, crossover_prob, mutation_prob):
    """Ejecuta los scripts de gráficas después de una ejecución."""
    # Generar nombres únicos para los archivos de salida
    evolucion_output = generate_output_filename(population_size, mutation_prob, crossover_prob, "evolucion_nsga2")
    pareto_output = generate_output_filename(population_size, mutation_prob, crossover_prob, "pareto_front")
    
    # Encontrar el archivo CSV de evolución más reciente
    evolucion_pattern = f"{OUTPUT_DIR}/{instance_name}_evolucion_*.csv"
    evolucion_csv = find_latest_file(evolucion_pattern)
    
    if not evolucion_csv:
        print(f"Advertencia: No se encontró archivo de evolución para {instance_name}")
    else:
        print(f"Archivo de evolución encontrado: {evolucion_csv}")
        # Ejecutar plot_evolucion.py con el archivo CSV y nombre de salida
        run_command(
            ["python3", PLOT_EVOLUCION_SCRIPT, evolucion_csv, evolucion_output],
            f"Generando gráfica de evolución para {instance_name}"
        )
    
    # Encontrar el archivo CSV del frente de Pareto más reciente
    pareto_pattern = f"{OUTPUT_DIR}/{instance_name}_pareto_front.csv"
    pareto_csv = find_latest_file(pareto_pattern)
    
    if not pareto_csv:
        print(f"Advertencia: No se encontró archivo de frente de Pareto para {instance_name}")
    else:
        print(f"Archivo de frente de Pareto encontrado: {pareto_csv}")
        # Ejecutar plot_pareto_front.py con el archivo específico y nombre de salida
        run_command(
            ["python3", PLOT_PARETO_SCRIPT, pareto_csv, pareto_output],
            f"Generando gráfica de frente de Pareto para {instance_name}"
        )


def main():
    """Función principal que ejecuta todos los experimentos."""
    print("╔════════════════════════════════════════════════════════════╗")
    print("║  EJECUTOR DE EXPERIMENTOS NSGA-II                         ║")
    print("╚════════════════════════════════════════════════════════════╝")
    print()
    print(f"Configuración:")
    print(f"  - Tamaños de población: {POPULATION_SIZES}")
    print(f"  - Probabilidades de cruzamiento: {CROSSOVER_PROBABILITIES}")
    print(f"  - Probabilidades de mutación: {MUTATION_PROBABILITIES}")
    print(f"  - Archivos de instancia: {INSTANCE_FILES}")
    print()
    
    total_combinations = len(POPULATION_SIZES) * len(CROSSOVER_PROBABILITIES) * \
                        len(MUTATION_PROBABILITIES) * len(INSTANCE_FILES)
    print(f"Total de combinaciones a ejecutar: {total_combinations}")
    print()
    
    # Asegurar que el directorio output existe
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    combination_count = 0
    successful = 0
    failed = 0
    
    # Iterar sobre todas las combinaciones
    for instance_file in INSTANCE_FILES:
        for pop_size in POPULATION_SIZES:
            for crossover_prob in CROSSOVER_PROBABILITIES:
                for mutation_prob in MUTATION_PROBABILITIES:
                    combination_count += 1
                    
                    print(f"\n{'#'*70}")
                    print(f"# COMBINACIÓN {combination_count}/{total_combinations}")
                    print(f"# Instancia: {instance_file}")
                    print(f"# Población: {pop_size}")
                    print(f"# Cruzamiento: {crossover_prob}")
                    print(f"# Mutación: {mutation_prob}")
                    print(f"{'#'*70}\n")
                    
                    # Ejecutar Java Main
                    if run_java_main(instance_file, pop_size, crossover_prob, mutation_prob):
                        successful += 1
                        
                        # Ejecutar scripts de gráficas
                        print(f"\n{'='*70}")
                        print(f"Generando gráficas para combinación {combination_count}...")
                        print(f"{'='*70}\n")
                        
                        run_plot_scripts(instance_file, pop_size, crossover_prob, mutation_prob)
                        
                        print(f"\n✓ Combinación {combination_count} completada exitosamente\n")
                    else:
                        failed += 1
                        print(f"\n✗ Combinación {combination_count} falló\n")
    
    # Resumen final
    print("\n" + "="*70)
    print("RESUMEN FINAL")
    print("="*70)
    print(f"Total de combinaciones: {total_combinations}")
    print(f"Exitosas: {successful}")
    print(f"Fallidas: {failed}")
    print("="*70 + "\n")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\nEjecución interrumpida por el usuario.")
        sys.exit(1)
    except Exception as e:
        print(f"\n\nError inesperado: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

