#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script para graficar el frente de Pareto del algoritmo NSGA-II
Adaptado para el proyecto de asignación de salones
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import os
import glob
import sys

# Function to check if a solution dominates another
def dominates(solution_a, solution_b):
    """
    solution_a dominates solution_b if it is better in all objectives
    (or equal in some and better in others).
    For this problem:
    - f1 (asignaciones): minimize (lower is better)
    - f2 (separación): maximize (higher is better)
    """
    # solution_a dominates solution_b if:
    # - f1_a <= f1_b (same or fewer assignments)
    # - f2_a >= f2_b (same or better separation)
    # - At least one is strictly better
    f1_better = solution_a[0] <= solution_b[0]
    f2_better = solution_a[1] >= solution_b[1]
    f1_strictly_better = solution_a[0] < solution_b[0]
    f2_strictly_better = solution_a[1] > solution_b[1]
    
    return (f1_better and f2_better) and (f1_strictly_better or f2_strictly_better)


# Function to get non-dominated solutions
def get_non_dominated_solutions(solutions):
    """
    Encuentra todas las soluciones no dominadas (frente de Pareto).
    Retorna también las soluciones dominadas.
    """
    non_dominated = []
    dominated = []
    dominated_indices = []
    
    for i, sol_a in enumerate(solutions):
        is_dominated = False
        for j, sol_b in enumerate(solutions):
            if i != j and dominates(sol_b, sol_a):  # If sol_b dominates sol_a
                is_dominated = True
                break
        if is_dominated:
            dominated.append(sol_a)
            dominated_indices.append(i)
        else:
            non_dominated.append(sol_a)
    
    return np.array(non_dominated), np.array(dominated)


# Read all Pareto front files and combine them
def combine_pareto_fronts(file_list):
    """
    Lee múltiples archivos CSV y combina todas las soluciones.
    """
    all_solutions = []
    
    # Read each file and add the solutions to the list
    for file in file_list:
        if os.path.exists(file):
            df = pd.read_csv(file)
            # Asegurarse de que tiene las columnas correctas
            if 'f1' in df.columns and 'f2' in df.columns:
                solutions = df[['f1', 'f2']].values
                all_solutions.append(solutions)
            else:
                print(f"Advertencia: {file} no tiene las columnas 'f1' y 'f2'")
        else:
            print(f"Advertencia: {file} no existe")
    
    if not all_solutions:
        raise ValueError("No se encontraron archivos válidos con soluciones")
    
    # Concatenate all the solutions from all files
    all_solutions = np.concatenate(all_solutions, axis=0)
    
    return all_solutions


def plot_pareto_front(solutions, non_dominated, dominated, output_file='output/pareto_front.png'):
    """
    Grafica el frente de Pareto mostrando tanto soluciones dominadas como no dominadas.
    """
    plt.figure(figsize=(14, 10))
    
    # Plot dominated solutions first (so they appear behind)
    if len(dominated) > 0:
        plt.scatter(dominated[:, 0], dominated[:, 1], 
                   c='lightblue', alpha=0.6, s=50, 
                   label=f'Soluciones dominadas ({len(dominated)})',
                   edgecolors='steelblue', linewidths=0.5, zorder=1)
    
    # Plot non-dominated solutions (frente de Pareto) on top
    if len(non_dominated) > 0:
        # Sort by f1 for better visualization
        sorted_indices = np.argsort(non_dominated[:, 0])
        sorted_pareto = non_dominated[sorted_indices]
        
        # Draw line connecting Pareto front points
        plt.plot(sorted_pareto[:, 0], sorted_pareto[:, 1], 
                'r-', linewidth=2.5, alpha=0.8, label='Frente de Pareto', zorder=3)
        
        # Plot non-dominated points
        plt.scatter(sorted_pareto[:, 0], sorted_pareto[:, 1], 
                   c='red', s=100, alpha=0.9, 
                   label=f'Soluciones no dominadas ({len(non_dominated)})',
                   edgecolors='darkred', linewidths=2, zorder=5, marker='o')
    
    plt.xlabel('f1: Asignaciones (minimizar)', fontsize=13, fontweight='bold')
    plt.ylabel('f2: Separación promedio (días) (maximizar)', fontsize=13, fontweight='bold')
    plt.title('Frente de Pareto - Asignación de Salones\n(Soluciones Dominadas vs No Dominadas)', 
              fontsize=15, fontweight='bold', pad=20)
    plt.legend(loc='best', fontsize=11, framealpha=0.9, shadow=True)
    plt.grid(True, alpha=0.3, linestyle=':', linewidth=1)
    
    # Mejorar la visualización
    plt.tight_layout()
    
    # Crear directorio si no existe
    os.makedirs(os.path.dirname(output_file) if os.path.dirname(output_file) else '.', exist_ok=True)
    
    plt.savefig(output_file, dpi=200, bbox_inches='tight')
    print(f'\n✓ Gráfica guardada en: {output_file}')
    plt.show()


def main():
    """
    Función principal.
    """
    # Opción 1: Si se proporciona un archivo específico como argumento
    if len(sys.argv) > 1:
        input_file = sys.argv[1]
        if not os.path.exists(input_file):
            print(f"Error: El archivo {input_file} no existe")
            sys.exit(1)
        pareto_front_files = [input_file]
    else:
        # Opción 2: Buscar archivos en el directorio output/
        # Buscar archivos que coincidan con el patrón *_pareto_front.csv
        pareto_front_files = glob.glob('output/*_pareto_front.csv')
        
        # Si no se encuentran, buscar cualquier archivo FUN*.csv (formato del código original)
        if not pareto_front_files:
            pareto_front_files = glob.glob('output/FUN*.csv')
            if not pareto_front_files:
                # Buscar en subdirectorios
                pareto_front_files = glob.glob('**/FUN*.csv', recursive=True)
        
        # Si aún no se encuentran, usar un archivo por defecto
        if not pareto_front_files:
            default_file = 'output/promedio_2024_pareto_front.csv'
            if os.path.exists(default_file):
                pareto_front_files = [default_file]
            else:
                print("Error: No se encontraron archivos de frente de Pareto.")
                print("Ejecuta primero el algoritmo NSGA-II o proporciona un archivo como argumento:")
                print("  python plot_pareto_front.py output/instancia_pareto_front.csv")
                sys.exit(1)
    
    print(f"Archivos encontrados: {len(pareto_front_files)}")
    for f in pareto_front_files:
        print(f"  - {f}")
    
    # Combinar todas las soluciones
    print("\nLeyendo soluciones...")
    combined_pareto = combine_pareto_fronts(pareto_front_files)
    print(f"Total de soluciones leídas: {len(combined_pareto)}")
    
    # Encontrar soluciones no dominadas y dominadas
    print("\nCalculando frente de Pareto (soluciones no dominadas)...")
    non_dominated, dominated = get_non_dominated_solutions(combined_pareto)
    print(f"Soluciones no dominadas encontradas: {len(non_dominated)}")
    print(f"Soluciones dominadas encontradas: {len(dominated)}")
    
    # Mostrar estadísticas
    if len(non_dominated) > 0:
        print("\nEstadísticas del frente de Pareto (no dominadas):")
        print(f"  f1 (Asignaciones): min={non_dominated[:, 0].min():.2f}, max={non_dominated[:, 0].max():.2f}")
        print(f"  f2 (Separación):   min={non_dominated[:, 1].min():.2f}, max={non_dominated[:, 1].max():.2f}")
    
    if len(dominated) > 0:
        print("\nEstadísticas de soluciones dominadas:")
        print(f"  f1 (Asignaciones): min={dominated[:, 0].min():.2f}, max={dominated[:, 0].max():.2f}")
        print(f"  f2 (Separación):   min={dominated[:, 1].min():.2f}, max={dominated[:, 1].max():.2f}")
    
    # Guardar frente de Pareto aproximado
    non_dominated_df = pd.DataFrame(non_dominated, columns=['f1', 'f2'])
    output_csv = 'output/approximated_pareto_front.csv'
    non_dominated_df.to_csv(output_csv, index=False)
    print(f"\n✓ Frente de Pareto aproximado guardado en: {output_csv}")
    
    # Guardar también las soluciones dominadas si se desea
    if len(dominated) > 0:
        dominated_df = pd.DataFrame(dominated, columns=['f1', 'f2'])
        dominated_csv = 'output/dominated_solutions.csv'
        dominated_df.to_csv(dominated_csv, index=False)
        print(f"✓ Soluciones dominadas guardadas en: {dominated_csv}")
    
    # Graficar
    print("\nGenerando gráfica...")
    plot_pareto_front(combined_pareto, non_dominated, dominated)


if __name__ == '__main__':
    main()
