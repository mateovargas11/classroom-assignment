#!/usr/bin/env python3
"""
Script para graficar la telemetría del algoritmo NSGA-II.

Este script genera gráficos de:
1. Evolución del fitness promedio y mejor fitness por generación
2. Evolución del tamaño del frente de Pareto
3. Frente de Pareto final (y evolución si se desea)

Uso:
    python plot_telemetry.py <archivo_base>
    
Ejemplo:
    python plot_telemetry.py output/promedio_2024_nsga2_telemetry
"""

import sys
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from pathlib import Path

def plot_fitness_evolution(generations_file, output_dir):
    """Grafica la evolución del fitness promedio y mejor fitness."""
    df = pd.read_csv(generations_file)
    
    # Verificar que hay datos
    if len(df) == 0:
        print(f"  ⚠ Advertencia: El archivo {generations_file} está vacío o solo tiene encabezados")
        return
    
    print(f"  Procesando {len(df)} generaciones...")
    
    fig, axes = plt.subplots(2, 2, figsize=(15, 10))
    fig.suptitle('Evolución del Fitness - NSGA-II', fontsize=16, fontweight='bold')
    
    # Gráfico 1: Objetivo 1 (Asignaciones)
    ax1 = axes[0, 0]
    ax1.plot(df['generation'], df['avg_fitness_obj1'], 'b-', label='Promedio', linewidth=2, alpha=0.7)
    ax1.plot(df['generation'], df['best_fitness_obj1'], 'r-', label='Mejor', linewidth=2)
    ax1.set_xlabel('Generación')
    ax1.set_ylabel('Fitness Objetivo 1 (Asignaciones)')
    ax1.set_title('Evolución Objetivo 1: Minimizar Asignaciones')
    ax1.legend()
    ax1.grid(True, alpha=0.3)
    
    # Gráfico 2: Objetivo 2 (Separación)
    # NOTA: El objetivo 2 está negado en el código (objective2 = -separationScore)
    # porque jMetal minimiza todos los objetivos. Para visualización, invertimos el signo.
    ax2 = axes[0, 1]
    ax2.plot(df['generation'], -df['avg_fitness_obj2'], 'b-', label='Promedio', linewidth=2, alpha=0.7)
    ax2.plot(df['generation'], -df['best_fitness_obj2'], 'r-', label='Mejor', linewidth=2)
    ax2.set_xlabel('Generación')
    ax2.set_ylabel('Separación Promedio (días)')
    ax2.set_title('Evolución Objetivo 2: Maximizar Separación')
    ax2.legend()
    ax2.grid(True, alpha=0.3)
    # Ahora valores más altos son mejores (más separación)
    
    # Gráfico 3: Tamaño del frente de Pareto
    ax3 = axes[1, 0]
    ax3.plot(df['generation'], df['pareto_front_size'], 'g-', linewidth=2)
    ax3.set_xlabel('Generación')
    ax3.set_ylabel('Tamaño del Frente de Pareto')
    ax3.set_title('Evolución del Tamaño del Frente de Pareto')
    ax3.grid(True, alpha=0.3)
    
    # Gráfico 4: Comparación de ambos objetivos (mejor fitness)
    ax4 = axes[1, 1]
    ax4_twin = ax4.twinx()
    
    line1 = ax4.plot(df['generation'], df['best_fitness_obj1'], 'r-', label='Mejor Obj1 (Asignaciones)', linewidth=2)
    line2 = ax4_twin.plot(df['generation'], -df['best_fitness_obj2'], 'b-', label='Mejor Obj2 (Separación)', linewidth=2)
    
    ax4.set_xlabel('Generación')
    ax4.set_ylabel('Fitness Objetivo 1 (Asignaciones)', color='r')
    ax4_twin.set_ylabel('Separación (días)', color='b')
    ax4.set_title('Comparación de Mejores Fitness')
    ax4.tick_params(axis='y', labelcolor='r')
    ax4_twin.tick_params(axis='y', labelcolor='b')
    ax4.grid(True, alpha=0.3)
    
    # Leyenda combinada
    lines = line1 + line2
    labels = [l.get_label() for l in lines]
    ax4.legend(lines, labels, loc='upper left')
    
    plt.tight_layout()
    
    output_file = output_dir / 'fitness_evolution.png'
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"  ✓ Gráfico de evolución guardado en: {output_file}")
    plt.close()


def plot_pareto_front(pareto_file, output_dir, show_evolution=False):
    """Grafica el frente de Pareto."""
    df = pd.read_csv(pareto_file)
    
    # Verificar que hay datos
    if len(df) == 0:
        print(f"  ⚠ Advertencia: El archivo {pareto_file} está vacío o solo tiene encabezados")
        return
    
    print(f"  Procesando {len(df)} puntos del frente de Pareto...")
    
    if show_evolution:
        # Gráfico animado por generación
        fig, axes = plt.subplots(1, 2, figsize=(16, 6))
        
        # Gráfico 1: Frente de Pareto final
        ax1 = axes[0]
        final_gen = df['generation'].max()
        final_pareto = df[df['generation'] == final_gen]
        
        # Ordenar por objetivo 1 para mejor visualización
        final_pareto = final_pareto.sort_values('objective1')
        
        # El objetivo 2 está negado en el código, así que lo invertimos para visualización
        ax1.scatter(final_pareto['objective1'], -final_pareto['objective2'], 
                   c='red', s=50, alpha=0.7, edgecolors='black', linewidth=0.5)
        ax1.plot(final_pareto['objective1'], -final_pareto['objective2'], 
                'r--', alpha=0.5, linewidth=1)
        ax1.set_xlabel('Objetivo 1: Asignaciones (minimizar)', fontsize=12)
        ax1.set_ylabel('Objetivo 2: Separación Promedio (días) - Maximizar', fontsize=12)
        ax1.set_title(f'Frente de Pareto Final (Generación {final_gen})', fontsize=14, fontweight='bold')
        ax1.grid(True, alpha=0.3)
        # No invertir el eje Y porque ya invertimos el signo del objetivo 2
        
        # Gráfico 2: Evolución del frente de Pareto
        ax2 = axes[1]
        generations = sorted(df['generation'].unique())
        colors = plt.cm.viridis(np.linspace(0, 1, len(generations)))
        
        for i, gen in enumerate(generations[::max(1, len(generations)//20)]):  # Mostrar cada 5% de generaciones
            gen_data = df[df['generation'] == gen].sort_values('objective1')
            ax2.plot(gen_data['objective1'], -gen_data['objective2'], 
                    'o-', alpha=0.3, linewidth=1, markersize=3, color=colors[i])
        
        # Frente final más destacado
        final_pareto = df[df['generation'] == final_gen].sort_values('objective1')
        ax2.plot(final_pareto['objective1'], -final_pareto['objective2'], 
                'ro-', linewidth=2, markersize=6, label='Frente Final', alpha=0.8)
        
        ax2.set_xlabel('Objetivo 1: Asignaciones (minimizar)', fontsize=12)
        ax2.set_ylabel('Objetivo 2: Separación Promedio (días) - Maximizar', fontsize=12)
        ax2.set_title('Evolución del Frente de Pareto', fontsize=14, fontweight='bold')
        ax2.legend()
        ax2.grid(True, alpha=0.3)
        # No invertir el eje Y porque ya invertimos el signo del objetivo 2
        
    else:
        # Solo frente final
        fig, ax = plt.subplots(1, 1, figsize=(10, 8))
        
        final_gen = df['generation'].max()
        final_pareto = df[df['generation'] == final_gen].sort_values('objective1')
        
        ax.scatter(final_pareto['objective1'], -final_pareto['objective2'], 
                  c='red', s=100, alpha=0.7, edgecolors='black', linewidth=1, zorder=3)
        ax.plot(final_pareto['objective1'], -final_pareto['objective2'], 
               'r--', alpha=0.5, linewidth=2, zorder=2)
        
        ax.set_xlabel('Objetivo 1: Asignaciones (minimizar)', fontsize=14, fontweight='bold')
        ax.set_ylabel('Objetivo 2: Separación (maximizar)', fontsize=14, fontweight='bold')
        ax.set_title(f'Frente de Pareto Final - Generación {final_gen}', fontsize=16, fontweight='bold')
        ax.grid(True, alpha=0.3, zorder=1)
        ax.invert_yaxis()
        
        # Anotar algunos puntos clave
        if len(final_pareto) > 0:
            # Punto con mejor objetivo 1
            best_obj1 = final_pareto.iloc[0]
            ax.annotate(f'Mejor Obj1\n({best_obj1["objective1"]:.1f}, {-best_obj1["objective2"]:.2f})',
                       xy=(best_obj1['objective1'], -best_obj1['objective2']),
                       xytext=(10, 10), textcoords='offset points',
                       bbox=dict(boxstyle='round,pad=0.5', facecolor='yellow', alpha=0.7),
                       arrowprops=dict(arrowstyle='->', connectionstyle='arc3,rad=0'))
            
            # Punto con mejor objetivo 2 (menor valor porque está negado = mayor separación)
            best_obj2_idx = final_pareto['objective2'].idxmin()
            best_obj2 = final_pareto.loc[best_obj2_idx]
            ax.annotate(f'Mejor Separación\n({best_obj2["objective1"]:.1f}, {-best_obj2["objective2"]:.2f} días)',
                       xy=(best_obj2['objective1'], -best_obj2['objective2']),
                       xytext=(10, -20), textcoords='offset points',
                       bbox=dict(boxstyle='round,pad=0.5', facecolor='lightblue', alpha=0.7),
                       arrowprops=dict(arrowstyle='->', connectionstyle='arc3,rad=0'))
    
    plt.tight_layout()
    
    output_file = output_dir / 'pareto_front.png'
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"  ✓ Gráfico del frente de Pareto guardado en: {output_file}")
    plt.close()


def main():
    if len(sys.argv) < 2:
        print("Uso: python plot_telemetry.py <archivo_base>")
        print("Ejemplo: python plot_telemetry.py output/promedio_2024_nsga2_telemetry")
        sys.exit(1)
    
    base_path = Path(sys.argv[1])
    output_dir = base_path.parent
    
    generations_file = Path(str(base_path) + "_generations.csv")
    pareto_file = Path(str(base_path) + "_pareto_front.csv")
    
    if not generations_file.exists():
        print(f"Error: No se encontró el archivo {generations_file}")
        sys.exit(1)
    
    if not pareto_file.exists():
        print(f"Error: No se encontró el archivo {pareto_file}")
        sys.exit(1)
    
    # Verificar que los archivos no estén vacíos
    with open(generations_file, 'r') as f:
        lines = f.readlines()
        if len(lines) <= 1:
            print(f"Error: El archivo {generations_file} está vacío (solo tiene encabezados)")
            print("Esto indica que no se capturaron datos durante la ejecución.")
            print("Verifica que el algoritmo se ejecutó correctamente.")
            sys.exit(1)
    
    print(f"\nGenerando gráficos desde:")
    print(f"  - Métricas: {generations_file} ({len(lines)-1} generaciones)")
    with open(pareto_file, 'r') as f:
        pareto_lines = f.readlines()
        print(f"  - Frente de Pareto: {pareto_file} ({len(pareto_lines)-1} puntos)")
    print()
    
    # Generar gráficos
    plot_fitness_evolution(generations_file, output_dir)
    plot_pareto_front(pareto_file, output_dir, show_evolution=True)
    
    print("\n✓ Todos los gráficos generados exitosamente!")
    print(f"\nArchivos generados en: {output_dir}")
    print("  - fitness_evolution.png")
    print("  - pareto_front.png")


if __name__ == "__main__":
    main()

