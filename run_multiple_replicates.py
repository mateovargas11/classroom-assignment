#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script para ejecutar múltiples réplicas del mismo experimento.
Esto es necesario para realizar análisis estadísticos como ANOVA.
"""

import subprocess
import os
import sys
from pathlib import Path
import time

OUTPUT_DIR = Path("output")


def run_experiment(instance_name: str, population_size: int, 
                   crossover_prob: float, mutation_prob: float,
                   replicate_num: int, total_replicates: int):
    """
    Ejecuta una réplica del experimento.
    """
    print(f"\n{'='*70}")
    print(f"RÉPLICA {replicate_num}/{total_replicates}")
    print(f"Instancia: {instance_name}")
    print(f"Población: {population_size}, Cruzamiento: {crossover_prob}, Mutación: {mutation_prob}")
    print(f"{'='*70}\n")
    
    command = [
        "mvn",
        "compile",
        "exec:java",
        "-Dexec.mainClass=com.university.Main",
        "-Dexec.args=" + f"{instance_name} {population_size} {crossover_prob} {mutation_prob}",
        "-q"
    ]
    
    try:
        result = subprocess.run(
            command,
            check=True,
            capture_output=False,
            text=True
        )
        print(f"✓ Réplica {replicate_num} completada exitosamente\n")
        return True
    except subprocess.CalledProcessError as e:
        print(f"✗ Error en réplica {replicate_num}: {e.returncode}\n")
        return False


def rename_output_files(instance_name: str, replicate_num: int):
    """
    Renombra los archivos de salida para incluir el número de réplica.
    """
    # Archivos que necesitan renombrarse
    files_to_rename = [
        f"{instance_name}_evolucion_*.csv",
        f"{instance_name}_pareto_front.csv",
        f"{instance_name}_hypervolume_stats.csv"
    ]
    
    import glob
    for pattern in files_to_rename:
        pattern_path = str(OUTPUT_DIR / pattern)
        files = glob.glob(pattern_path)
        
        for file_path in files:
            file = Path(file_path)
            # Solo renombrar si no tiene ya un número de réplica
            if f"_rep{replicate_num}" not in file.stem:
                new_name = file.stem + f"_rep{replicate_num}" + file.suffix
                new_path = file.parent / new_name
                try:
                    file.rename(new_path)
                    print(f"  Renombrado: {file.name} -> {new_name}")
                except Exception as e:
                    print(f"  ⚠️  No se pudo renombrar {file.name}: {e}")


def main():
    """
    Función principal.
    """
    if len(sys.argv) < 6:
        print("Uso: python run_multiple_replicates.py <instancia> <poblacion> <cruzamiento> <mutacion> <num_replicas>")
        print("\nEjemplo:")
        print("  python run_multiple_replicates.py promedio_2024 50 0.8 0.01 10")
        sys.exit(1)
    
    instance_name = sys.argv[1]
    population_size = int(sys.argv[2])
    crossover_prob = float(sys.argv[3])
    mutation_prob = float(sys.argv[4])
    num_replicates = int(sys.argv[5])
    
    print("╔════════════════════════════════════════════════════════════╗")
    print("║  EJECUTOR DE MÚLTIPLES RÉPLICAS                           ║")
    print("╚════════════════════════════════════════════════════════════╝")
    print()
    print(f"Configuración:")
    print(f"  - Instancia: {instance_name}")
    print(f"  - Población: {population_size}")
    print(f"  - Cruzamiento: {crossover_prob}")
    print(f"  - Mutación: {mutation_prob}")
    print(f"  - Número de réplicas: {num_replicates}")
    print()
    
    OUTPUT_DIR.mkdir(exist_ok=True)
    
    successful = 0
    failed = 0
    
    for replicate_num in range(1, num_replicates + 1):
        if run_experiment(instance_name, population_size, crossover_prob, 
                         mutation_prob, replicate_num, num_replicates):
            successful += 1
            # Pequeña pausa entre réplicas
            if replicate_num < num_replicates:
                time.sleep(1)
        else:
            failed += 1
    
    print("\n" + "="*70)
    print("RESUMEN")
    print("="*70)
    print(f"Total de réplicas: {num_replicates}")
    print(f"Exitosas: {successful}")
    print(f"Fallidas: {failed}")
    print("="*70)
    print("\n💡 Ahora puedes ejecutar anova_test.py para analizar los resultados")


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
