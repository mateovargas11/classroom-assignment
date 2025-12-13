#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script para graficar la evolución del algoritmo NSGA-II
Generado automáticamente por EvolutionTracker
"""

import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

# Cargar datos
df = pd.read_csv('output/promedio_2024_evolucion_2025-12-13_09-12-49.csv')
df = df.sort_values('generacion').reset_index(drop=True)

print(f'Datos cargados: {len(df)} registros')
print(f'Rango de generaciones: {df["generacion"].min()} - {df["generacion"].max()}')

# Crear figura con subplots
fig, axes = plt.subplots(2, 2, figsize=(14, 10))
fig.suptitle('Evolución del Algoritmo NSGA-II', fontsize=14, fontweight='bold')

# Gráfica 1: Asignaciones (Objetivo 1)
ax1 = axes[0, 0]
ax1.plot(df['generacion'], df['mejor_asignaciones'], 'b-', label='Mejor', linewidth=2, marker='o', markersize=4)
ax1.plot(df['generacion'], df['promedio_asignaciones'], 'b--', alpha=0.6, label='Promedio', linewidth=1.5)
ax1.set_xlabel('Generación', fontsize=11)
ax1.set_ylabel('Asignaciones', fontsize=11)
ax1.set_title('Objetivo 1: Minimizar Asignaciones', fontsize=12, fontweight='bold')
ax1.legend(loc='best', fontsize=10)
ax1.grid(True, alpha=0.3, linestyle=':')

# Gráfica 2: Separación (Objetivo 2)
ax2 = axes[0, 1]
ax2.plot(df['generacion'], df['mejor_separacion'], 'g-', label='Mejor', linewidth=2, marker='s', markersize=4)
ax2.plot(df['generacion'], df['promedio_separacion'], 'g--', alpha=0.6, label='Promedio', linewidth=1.5)
ax2.set_xlabel('Generación', fontsize=11)
ax2.set_ylabel('Separación (días)', fontsize=11)
ax2.set_title('Objetivo 2: Maximizar Separación', fontsize=12, fontweight='bold')
ax2.legend(loc='best', fontsize=10)
ax2.grid(True, alpha=0.3, linestyle=':')

# Gráfica 3: Soluciones factibles
ax3 = axes[1, 0]
ax3.plot(df['generacion'], df['soluciones_factibles'], 'r-', linewidth=2, marker='^', markersize=4, label='Factibles')
ax3.axhline(y=df['poblacion_total'].iloc[0], color='gray', linestyle='--', linewidth=1.5, label='Población total')
ax3.set_xlabel('Generación', fontsize=11)
ax3.set_ylabel('Cantidad', fontsize=11)
ax3.set_title('Soluciones Factibles por Generación', fontsize=12, fontweight='bold')
ax3.legend(loc='best', fontsize=10)
ax3.grid(True, alpha=0.3, linestyle=':')

# Gráfica 4: Evolución en el espacio de objetivos
ax4 = axes[1, 1]
scatter = ax4.scatter(df['mejor_asignaciones'], df['mejor_separacion'], c=df['generacion'], cmap='viridis', alpha=0.7, s=60, edgecolors='black', linewidths=0.5)
ax4.set_xlabel('Asignaciones (menor es mejor)', fontsize=11)
ax4.set_ylabel('Separación (mayor es mejor)', fontsize=11)
ax4.set_title('Evolución en el Espacio de Objetivos', fontsize=12, fontweight='bold')
cbar = plt.colorbar(scatter, ax=ax4)
cbar.set_label('Generación', fontsize=10)
ax4.grid(True, alpha=0.3, linestyle=':')

plt.tight_layout()
plt.savefig('output/evolucion_nsga2.png', dpi=150, bbox_inches='tight')
print('\n✓ Gráfica guardada en: output/evolucion_nsga2.png')
plt.show()
