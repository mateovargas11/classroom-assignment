package com.university.telemetry;

import com.university.problem.ClassroomAssignmentProblem;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Clase para rastrear la evolución del algoritmo NSGA-II.
 * Captura métricas por generación para análisis y gráficas.
 */
public class EvolutionTracker {

    private int generation = 0;
    private final List<GenerationData> history = new ArrayList<>();
    private final long startTime;
    private final ClassroomAssignmentProblem problem;

    public EvolutionTracker(ClassroomAssignmentProblem problem) {
        this.problem = problem;
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Actualiza el tracker con los datos de una generación.
     * 
     * @param population La población de la generación
     */
    public void update(List<IntegerSolution> population) {
        generation++;

        double bestObj1 = Double.MAX_VALUE;
        double bestObj2 = Double.MAX_VALUE;
        double sumObj2Feasible = 0;
        int feasibleCount = 0;

        int bestRealAssignments = Integer.MAX_VALUE;
        double sumRealAssignments = 0;

        // También trackear mejor de todas las soluciones (incluso infactibles)
        double bestObj1All = Double.MAX_VALUE;
        double bestObj2All = Double.MAX_VALUE;
        int bestRealAssignmentsAll = Integer.MAX_VALUE;
        double sumObj2All = 0;
        double sumRealAssignmentsAll = 0;

        for (IntegerSolution sol : population) {
            double obj1 = sol.objectives()[0];
            double obj2 = sol.objectives()[1];

            // Verificar factibilidad
            boolean feasible = true;
            for (double c : sol.constraints()) {
                if (c < 0) {
                    feasible = false;
                    break;
                }
            }

            // Calcular asignaciones reales para todas las soluciones
            int realAssignments = countRealAssignments(sol);
            sumObj2All += obj2;
            sumRealAssignmentsAll += realAssignments;

            // Trackear mejor de todas las soluciones
            if (obj1 < bestObj1All) {
                bestObj1All = obj1;
                bestRealAssignmentsAll = realAssignments;
            }
            if (obj2 < bestObj2All) {
                bestObj2All = obj2;
            }

            // Solo considerar factibles para mejor y promedio factible
            if (feasible) {
                sumObj2Feasible += obj2;
                feasibleCount++;
                sumRealAssignments += realAssignments;

                // Mejor solo de soluciones factibles (según objetivo 1)
                if (obj1 < bestObj1) {
                    bestObj1 = obj1;
                    bestRealAssignments = realAssignments;
                }
                if (obj2 < bestObj2) {
                    bestObj2 = obj2;
                }
            }
        }

        // Promedio solo de soluciones factibles
        double avgObj2 = feasibleCount > 0 ? sumObj2Feasible / feasibleCount : 0;
        double avgRealAssignments = feasibleCount > 0 ? sumRealAssignments / feasibleCount : 0;

        // Promedio de todas las soluciones
        double avgObj2All = population.size() > 0 ? sumObj2All / population.size() : 0;
        double avgRealAssignmentsAll = population.size() > 0 ? sumRealAssignmentsAll / population.size() : 0;

        // Si no hay factibles, usar el mejor de todas las soluciones
        if (bestObj1 == Double.MAX_VALUE) {
            bestRealAssignments = bestRealAssignmentsAll != Integer.MAX_VALUE ? bestRealAssignmentsAll : 0;
            bestObj1 = bestObj1All != Double.MAX_VALUE ? bestObj1All : 0;
        }
        if (bestObj2 == Double.MAX_VALUE) {
            bestObj2 = bestObj2All != Double.MAX_VALUE ? bestObj2All : 0;
        }

        // Si no hay factibles, usar promedios de todas las soluciones
        if (feasibleCount == 0) {
            avgObj2 = avgObj2All;
            avgRealAssignments = avgRealAssignmentsAll;
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        double bestSeparation = -bestObj2; // Convertir a positivo (mayor es mejor)
        double avgSeparation = -avgObj2;

        GenerationData data = new GenerationData(
                generation,
                bestRealAssignments, // Usar asignaciones reales en lugar de objetivo 1
                avgRealAssignments,
                bestSeparation,
                avgSeparation,
                feasibleCount,
                population.size(),
                elapsedTime);

        history.add(data);

        // Imprimir información de la generación (usando asignaciones reales)
        String feasibleMarker = feasibleCount == 0 ? " [INFEASIBLE]" : "";
        System.out.printf("Generation %d: Best = ( %d asignaciones reales, %.2f días separación )%s, "
                + "Avg = ( %.2f, %.2f ), Factibles = %d/%d\n",
                generation, bestRealAssignments, bestSeparation, feasibleMarker, avgRealAssignments, avgSeparation,
                feasibleCount, population.size());
    }

    /**
     * Cuenta las asignaciones reales (sin penalización) de una solución.
     */
    private int countRealAssignments(IntegerSolution solution) {
        Map<Integer, ClassroomAssignmentProblem.DecodedAssignment> assignments = problem.decode(solution);
        return assignments.values().stream()
                .filter(a -> a.assigned)
                .mapToInt(a -> a.classrooms.size())
                .sum();

    }

    /**
     * Guarda los datos de evolución a un archivo CSV.
     * 
     * @param baseFileName Nombre base del archivo (sin extensión)
     * @return El nombre completo del archivo generado
     */
    public String saveToCsv(String baseFileName) {
        String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String fileName = baseFileName + "_" + dateTime + ".csv";

        try {
            Files.createDirectories(Paths.get(fileName).getParent());
        } catch (IOException e) {
            System.err.println("Error creating directories: " + e.getMessage());
        }

        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write(
                    "generacion,mejor_asignaciones,promedio_asignaciones,mejor_separacion,promedio_separacion,soluciones_factibles,poblacion_total,tiempo_ms\n");

            for (GenerationData data : history) {
                writer.write(String.format("%d,%.2f,%.2f,%.4f,%.4f,%d,%d,%d\n",
                        data.generation,
                        data.bestObj1,
                        data.avgObj1,
                        data.bestSeparation,
                        data.avgSeparation,
                        data.feasibleCount,
                        data.populationSize,
                        data.elapsedTimeMs));
            }

            System.out.println("Results from evolution tracker saved to: " + fileName);
            return fileName;
        } catch (IOException e) {
            System.err.println("An error occurred while saving the results: " + e.getMessage());
            return null;
        }
    }

    /**
     * Genera un script de Python para graficar la evolución.
     */
    public void generatePythonPlotScript(String csvPath, String scriptPath) throws IOException {
        Files.createDirectories(Paths.get(scriptPath).getParent());

        try (FileWriter writer = new FileWriter(scriptPath)) {
            writer.write("#!/usr/bin/env python3\n");
            writer.write("# -*- coding: utf-8 -*-\n");
            writer.write("\"\"\"\n");
            writer.write("Script para graficar la evolución del algoritmo NSGA-II\n");
            writer.write("Generado automáticamente por EvolutionTracker\n");
            writer.write("\"\"\"\n\n");
            writer.write("import pandas as pd\n");
            writer.write("import matplotlib.pyplot as plt\n");
            writer.write("import numpy as np\n\n");
            writer.write("# Cargar datos\n");
            writer.write("df = pd.read_csv('" + csvPath + "')\n");
            writer.write("df = df.sort_values('generacion').reset_index(drop=True)\n\n");
            writer.write("print(f'Datos cargados: {len(df)} registros')\n");
            writer.write(
                    "print(f'Rango de generaciones: {df[\"generacion\"].min()} - {df[\"generacion\"].max()}')\n\n");
            writer.write("# Crear figura con subplots\n");
            writer.write("fig, axes = plt.subplots(2, 2, figsize=(14, 10))\n");
            writer.write("fig.suptitle('Evolución del Algoritmo NSGA-II', fontsize=14, fontweight='bold')\n\n");
            writer.write("# Gráfica 1: Asignaciones (Objetivo 1)\n");
            writer.write("ax1 = axes[0, 0]\n");
            writer.write(
                    "ax1.plot(df['generacion'], df['mejor_asignaciones'], 'b-', label='Mejor', linewidth=2, marker='o', markersize=4)\n");
            writer.write(
                    "ax1.plot(df['generacion'], df['promedio_asignaciones'], 'b--', alpha=0.6, label='Promedio', linewidth=1.5)\n");
            writer.write("ax1.set_xlabel('Generación', fontsize=11)\n");
            writer.write("ax1.set_ylabel('Asignaciones', fontsize=11)\n");
            writer.write("ax1.set_title('Objetivo 1: Minimizar Asignaciones', fontsize=12, fontweight='bold')\n");
            writer.write("ax1.legend(loc='best', fontsize=10)\n");
            writer.write("ax1.grid(True, alpha=0.3, linestyle=':')\n\n");
            writer.write("# Gráfica 2: Separación (Objetivo 2)\n");
            writer.write("ax2 = axes[0, 1]\n");
            writer.write(
                    "ax2.plot(df['generacion'], df['mejor_separacion'], 'g-', label='Mejor', linewidth=2, marker='s', markersize=4)\n");
            writer.write(
                    "ax2.plot(df['generacion'], df['promedio_separacion'], 'g--', alpha=0.6, label='Promedio', linewidth=1.5)\n");
            writer.write("ax2.set_xlabel('Generación', fontsize=11)\n");
            writer.write("ax2.set_ylabel('Separación (días)', fontsize=11)\n");
            writer.write("ax2.set_title('Objetivo 2: Maximizar Separación', fontsize=12, fontweight='bold')\n");
            writer.write("ax2.legend(loc='best', fontsize=10)\n");
            writer.write("ax2.grid(True, alpha=0.3, linestyle=':')\n\n");
            writer.write("# Gráfica 3: Soluciones factibles\n");
            writer.write("ax3 = axes[1, 0]\n");
            writer.write(
                    "ax3.plot(df['generacion'], df['soluciones_factibles'], 'r-', linewidth=2, marker='^', markersize=4, label='Factibles')\n");
            writer.write(
                    "ax3.axhline(y=df['poblacion_total'].iloc[0], color='gray', linestyle='--', linewidth=1.5, label='Población total')\n");
            writer.write("ax3.set_xlabel('Generación', fontsize=11)\n");
            writer.write("ax3.set_ylabel('Cantidad', fontsize=11)\n");
            writer.write("ax3.set_title('Soluciones Factibles por Generación', fontsize=12, fontweight='bold')\n");
            writer.write("ax3.legend(loc='best', fontsize=10)\n");
            writer.write("ax3.grid(True, alpha=0.3, linestyle=':')\n\n");
            writer.write("# Gráfica 4: Evolución en el espacio de objetivos\n");
            writer.write("ax4 = axes[1, 1]\n");
            writer.write(
                    "scatter = ax4.scatter(df['mejor_asignaciones'], df['mejor_separacion'], c=df['generacion'], cmap='viridis', alpha=0.7, s=60, edgecolors='black', linewidths=0.5)\n");
            writer.write("ax4.set_xlabel('Asignaciones (menor es mejor)', fontsize=11)\n");
            writer.write("ax4.set_ylabel('Separación (mayor es mejor)', fontsize=11)\n");
            writer.write("ax4.set_title('Evolución en el Espacio de Objetivos', fontsize=12, fontweight='bold')\n");
            writer.write("cbar = plt.colorbar(scatter, ax=ax4)\n");
            writer.write("cbar.set_label('Generación', fontsize=10)\n");
            writer.write("ax4.grid(True, alpha=0.3, linestyle=':')\n\n");
            writer.write("plt.tight_layout()\n");
            writer.write("plt.savefig('output/evolucion_nsga2.png', dpi=150, bbox_inches='tight')\n");
            writer.write("print('\\n✓ Gráfica guardada en: output/evolucion_nsga2.png')\n");
            writer.write("plt.show()\n");
        }

        System.out.println("  ✓ Script de Python generado: " + scriptPath);
    }

    /**
     * Imprime un resumen de la evolución.
     */
    public void printSummary() {
        if (history.isEmpty()) {
            System.out.println("No hay datos de evolución registrados.");
            return;
        }

        GenerationData first = history.get(0);
        GenerationData last = history.get(history.size() - 1);

        System.out.println("\n=== RESUMEN DE EVOLUCIÓN ===");
        System.out.println("Generaciones registradas: " + history.size());
        System.out.println("Última generación: " + last.generation);
        System.out.println("Tiempo total: " + last.elapsedTimeMs + " ms");

        System.out.println("\nMejora en Objetivo 1 (Asignaciones):");
        System.out.printf("  Inicial: %.2f → Final: %.2f (%.1f%% mejora)\n",
                first.bestObj1, last.bestObj1,
                first.bestObj1 > 0 ? 100.0 * (first.bestObj1 - last.bestObj1) / first.bestObj1 : 0);

        System.out.println("\nMejora en Objetivo 2 (Separación):");
        System.out.printf("  Inicial: %.2f días → Final: %.2f días (%.1f%% mejora)\n",
                first.bestSeparation, last.bestSeparation,
                first.bestSeparation > 0
                        ? 100.0 * (last.bestSeparation - first.bestSeparation) / first.bestSeparation
                        : 0);

        System.out.println("\nSoluciones factibles:");
        System.out.printf("  Inicial: %d/%d → Final: %d/%d\n",
                first.feasibleCount, first.populationSize,
                last.feasibleCount, last.populationSize);
    }

    public int getGeneration() {
        return generation;
    }

    public List<GenerationData> getHistory() {
        return history;
    }

    /**
     * Clase interna para almacenar datos de una generación.
     */
    public static class GenerationData {
        public final int generation;
        public final double bestObj1;
        public final double avgObj1;
        public final double bestSeparation;
        public final double avgSeparation;
        public final int feasibleCount;
        public final int populationSize;
        public final long elapsedTimeMs;

        public GenerationData(int generation, double bestObj1, double avgObj1,
                double bestSeparation, double avgSeparation,
                int feasibleCount, int populationSize, long elapsedTimeMs) {
            this.generation = generation;
            this.bestObj1 = bestObj1;
            this.avgObj1 = avgObj1;
            this.bestSeparation = bestSeparation;
            this.avgSeparation = avgSeparation;
            this.feasibleCount = feasibleCount;
            this.populationSize = populationSize;
            this.elapsedTimeMs = elapsedTimeMs;
        }
    }
}
