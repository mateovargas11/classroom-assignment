package com.university.metrics.algorithms;

import org.uma.jmetal.solution.Solution;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Almacena y exporta datos de telemetría del algoritmo NSGA-II.
 * Guarda métricas por generación: fitness promedio, mejor fitness, tamaño del frente de Pareto,
 * y el frente de Pareto completo.
 */
public class TelemetryData {
    
    private final String outputPath;
    private final List<GenerationMetrics> generations;
    private final List<ParetoFrontEntry> paretoFrontHistory;
    
    public TelemetryData(String outputPath) {
        this.outputPath = outputPath;
        this.generations = new ArrayList<>();
        this.paretoFrontHistory = new ArrayList<>();
    }
    
    public void initialize() {
        generations.clear();
        paretoFrontHistory.clear();
    }
    
    public void recordGeneration(
            int generation,
            double[] avgFitness,
            double[] bestFitness,
            int paretoFrontSize,
            List<? extends Solution<?>> population) {
        
        // Guardar métricas de la generación
        GenerationMetrics metrics = new GenerationMetrics();
        metrics.generation = generation;
        metrics.avgFitnessObj1 = avgFitness[0];
        metrics.avgFitnessObj2 = avgFitness[1];
        metrics.bestFitnessObj1 = bestFitness[0];
        metrics.bestFitnessObj2 = bestFitness[1];
        metrics.paretoFrontSize = paretoFrontSize;
        
        generations.add(metrics);
        
        // Guardar frente de Pareto completo
        List<? extends Solution<?>> nonDominated = getNonDominatedSolutions(population);
        
        // Si el frente es muy pequeño, también guardar algunas soluciones dominadas para tener más puntos
        // (útil para visualización, aunque no sean estrictamente no dominadas)
        java.util.List<Solution<?>> solutionsToSave = new java.util.ArrayList<>(nonDominated);
        
        if (nonDominated.size() < 10 && population.size() > nonDominated.size()) {
            // Agregar algunas soluciones adicionales ordenadas por mejor objetivo 1
            List<? extends Solution<?>> additional = population.stream()
                .filter(s -> !nonDominated.contains(s))
                .sorted((s1, s2) -> Double.compare(s1.objectives()[0], s2.objectives()[0]))
                .limit(20)
                .toList();
            solutionsToSave.addAll(additional);
        }
        
        for (Solution<?> solution : solutionsToSave) {
            ParetoFrontEntry entry = new ParetoFrontEntry();
            entry.generation = generation;
            entry.objective1 = solution.objectives()[0];
            entry.objective2 = solution.objectives()[1];
            paretoFrontHistory.add(entry);
        }
    }
    
    public void finalize() {
        try {
            // Guardar métricas de generaciones
            saveGenerationMetrics();
            
            // Guardar frente de Pareto
            saveParetoFront();
            
        } catch (IOException e) {
            System.err.println("Error al guardar telemetría: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void saveGenerationMetrics() throws IOException {
        String filePath = outputPath + "_generations.csv";
        
        try (FileWriter writer = new FileWriter(filePath)) {
            // Encabezado
            writer.write("generation,avg_fitness_obj1,avg_fitness_obj2,best_fitness_obj1,best_fitness_obj2,pareto_front_size\n");
            
            // Datos
            for (GenerationMetrics metrics : generations) {
                writer.write(String.format("%d,%.6f,%.6f,%.6f,%.6f,%d\n",
                    metrics.generation,
                    metrics.avgFitnessObj1,
                    metrics.avgFitnessObj2,
                    metrics.bestFitnessObj1,
                    metrics.bestFitnessObj2,
                    metrics.paretoFrontSize));
            }
        }
    }
    
    private void saveParetoFront() throws IOException {
        String filePath = outputPath + "_pareto_front.csv";
        
        try (FileWriter writer = new FileWriter(filePath)) {
            // Encabezado
            writer.write("generation,objective1,objective2\n");
            
            // Datos
            for (ParetoFrontEntry entry : paretoFrontHistory) {
                writer.write(String.format("%d,%.6f,%.6f\n",
                    entry.generation,
                    entry.objective1,
                    entry.objective2));
            }
        }
    }
    
    private List<? extends Solution<?>> getNonDominatedSolutions(List<? extends Solution<?>> population) {
        return population.stream()
            .filter(solution -> {
                return population.stream().noneMatch(other -> dominates(other, solution));
            })
            .toList();
    }
    
    private boolean dominates(Solution<?> solution1, Solution<?> solution2) {
        boolean betterInAtLeastOne = false;
        boolean worseInAny = false;
        
        int numObjectives = solution1.objectives().length;
        for (int i = 0; i < numObjectives; i++) {
            double obj1 = solution1.objectives()[i];
            double obj2 = solution2.objectives()[i];
            
            if (obj1 < obj2) {
                betterInAtLeastOne = true;
            } else if (obj1 > obj2) {
                worseInAny = true;
            }
        }
        
        return betterInAtLeastOne && !worseInAny;
    }
    
    public String getOutputPath() {
        return outputPath;
    }
    
    // Clases internas para almacenar datos
    private static class GenerationMetrics {
        int generation;
        double avgFitnessObj1;
        double avgFitnessObj2;
        double bestFitnessObj1;
        double bestFitnessObj2;
        int paretoFrontSize;
    }
    
    private static class ParetoFrontEntry {
        int generation;
        double objective1;
        double objective2;
    }
}

