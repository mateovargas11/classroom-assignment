package com.university.metrics.algorithms;

import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAII;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.selection.SelectionOperator;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.solution.Solution;
import org.uma.jmetal.util.evaluator.SolutionListEvaluator;
import org.uma.jmetal.util.evaluator.impl.SequentialSolutionListEvaluator;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Wrapper de NSGAII que captura telemetría durante la ejecución.
 * Registra métricas en cada generación: fitness, fitness promedio, mejor fitness y frente de Pareto.
 */
public class NSGAIIWithTelemetry<S extends Solution<?>> implements Algorithm<List<S>> {
    
    private final NSGAII<S> nsgaii;
    private final TelemetryData telemetryData;
    private int currentGeneration;
    private final int populationSize;
    private final Problem<S> problem;
    
    public NSGAIIWithTelemetry(
            Problem<S> problem,
            int maxEvaluations,
            int populationSize,
            CrossoverOperator<S> crossoverOperator,
            MutationOperator<S> mutationOperator,
            SelectionOperator<List<S>, S> selectionOperator,
            SolutionListEvaluator<S> evaluator,
            String outputPath) {
        this.problem = problem;
        this.populationSize = populationSize;
        this.telemetryData = new TelemetryData(outputPath);
        this.currentGeneration = 0;
        
        // Construir NSGAII usando el builder
        if (evaluator == null) {
            evaluator = new SequentialSolutionListEvaluator<>();
        }
        
        this.nsgaii = new NSGAIIBuilder<>(problem, crossoverOperator, mutationOperator, populationSize)
                .setSelectionOperator(selectionOperator)
                .setMaxEvaluations(maxEvaluations)
                .setSolutionListEvaluator(evaluator)
                .build();
    }
    
    @Override
    public void run() {
        // Inicializar telemetría
        telemetryData.initialize();
        
        // Capturar población inicial
        try {
            Thread.sleep(100); // Dar tiempo para que se inicialice
            captureGenerationData();
        } catch (Exception e) {
            // Continuar aunque falle
        }
        
        // Ejecutar algoritmo y capturar datos periódicamente
        Thread monitoringThread = startMonitoringThread();
        
        try {
            nsgaii.run();
        } finally {
            // Esperar un poco para que el algoritmo termine completamente
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Detener monitoreo
            if (monitoringThread != null && monitoringThread.isAlive()) {
                monitoringThread.interrupt();
                try {
                    monitoringThread.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Capturar datos finales garantizado
            captureGenerationData();
            
            // Guardar datos finales
            telemetryData.finalize();
            System.out.println("\n  ✓ Telemetría guardada (" + currentGeneration + " generaciones capturadas) en: " + telemetryData.getOutputPath());
        }
    }
    
    private Thread startMonitoringThread() {
        Thread thread = new Thread(() -> {
            int lastEvaluations = -1;
            long lastCaptureTime = System.currentTimeMillis();
            int captureCount = 0;
            
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(100); // Capturar datos cada 100ms
                    
                    long currentTime = System.currentTimeMillis();
                    int currentEvaluations = getEvaluations();
                    
                    // Capturar si:
                    // 1. Hay un cambio significativo en evaluaciones (cada populationSize/2)
                    // 2. Han pasado más de 500ms desde la última captura (captura por tiempo)
                    boolean shouldCapture = false;
                    
                    if (currentEvaluations > lastEvaluations && currentEvaluations > 0) {
                        int threshold = Math.max(populationSize / 2, 20);
                        if (lastEvaluations == -1 || currentEvaluations >= lastEvaluations + threshold) {
                            shouldCapture = true;
                        }
                    }
                    
                    // Captura por tiempo para asegurar que siempre capturemos datos
                    if (currentTime - lastCaptureTime > 500) {
                        shouldCapture = true;
                    }
                    
                    if (shouldCapture) {
                        boolean captured = captureGenerationData();
                        if (captured) {
                            captureCount++;
                            lastEvaluations = currentEvaluations;
                            lastCaptureTime = currentTime;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Continuar monitoreando aunque haya errores
                    // System.err.println("Error en monitoreo: " + e.getMessage());
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
    
    private int getEvaluations() {
        try {
            // Intentar varios métodos posibles
            try {
                Method getEvaluationsMethod = NSGAII.class.getDeclaredMethod("getEvaluations");
                getEvaluationsMethod.setAccessible(true);
                Object result = getEvaluationsMethod.invoke(nsgaii);
                if (result instanceof Integer) {
                    return (Integer) result;
                }
            } catch (NoSuchMethodException e) {
                // Intentar con otro nombre
                try {
                    Method method = NSGAII.class.getDeclaredMethod("evaluations");
                    method.setAccessible(true);
                    Object result = method.invoke(nsgaii);
                    if (result instanceof Integer) {
                        return (Integer) result;
                    }
                } catch (Exception e2) {
                    // Intentar acceder al campo directamente
                    try {
                        java.lang.reflect.Field field = NSGAII.class.getDeclaredField("evaluations");
                        field.setAccessible(true);
                        Object result = field.get(nsgaii);
                        if (result instanceof Integer) {
                            return (Integer) result;
                        }
                    } catch (Exception e3) {
                        // Si todo falla, usar el número de generación estimado
                        return currentGeneration * populationSize;
                    }
                }
            }
        } catch (Exception e) {
            // Si todo falla, usar el número de generación estimado
            return currentGeneration * populationSize;
        }
        return 0;
    }
    
    private boolean captureGenerationData() {
        try {
            List<S> currentPopulation = getPopulation();
            if (currentPopulation == null || currentPopulation.isEmpty()) {
                return false;
            }
            
            currentGeneration++;
            
            // Calcular métricas
            double[] avgFitness = calculateAverageFitness(currentPopulation);
            double[] bestFitness = calculateBestFitness(currentPopulation);
            int paretoFrontSize = getNonDominatedSolutions(currentPopulation).size();
            
            // Guardar telemetría
            telemetryData.recordGeneration(
                currentGeneration,
                avgFitness,
                bestFitness,
                paretoFrontSize,
                currentPopulation
            );
            
            // Mostrar progreso cada 10 generaciones
            if (currentGeneration % 10 == 0) {
                System.out.printf("  Generación %d: Mejor [%.2f, %.2f] | Promedio [%.2f, %.2f] | Frente Pareto: %d%n",
                    currentGeneration,
                    bestFitness[0], bestFitness[1],
                    avgFitness[0], avgFitness[1],
                    paretoFrontSize);
            }
            
            return true;
        } catch (Exception e) {
            System.err.println("Error capturando datos de generación " + currentGeneration + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<S> getPopulation() {
        try {
            // Intentar método getPopulation()
            try {
                Method getPopulationMethod = NSGAII.class.getDeclaredMethod("getPopulation");
                getPopulationMethod.setAccessible(true);
                Object result = getPopulationMethod.invoke(nsgaii);
                if (result instanceof List && result != null) {
                    List<S> population = (List<S>) result;
                    if (!population.isEmpty()) {
                        return population;
                    }
                }
            } catch (NoSuchMethodException e) {
                // Intentar acceder al campo population directamente
                try {
                    java.lang.reflect.Field field = NSGAII.class.getDeclaredField("population");
                    field.setAccessible(true);
                    Object result = field.get(nsgaii);
                    if (result instanceof List && result != null) {
                        List<S> population = (List<S>) result;
                        if (!population.isEmpty()) {
                            return population;
                        }
                    }
                } catch (Exception e2) {
                    // Intentar con otro nombre de campo
                    try {
                        java.lang.reflect.Field field = NSGAII.class.getDeclaredField("list");
                        field.setAccessible(true);
                        Object result = field.get(nsgaii);
                        if (result instanceof List && result != null) {
                            List<S> population = (List<S>) result;
                            if (!population.isEmpty()) {
                                return population;
                            }
                        }
                    } catch (Exception e3) {
                        // Continuar al siguiente método
                    }
                }
            }
            
            // Si getPopulation() existe pero retorna null o vacío, usar result()
            try {
                List<S> result = nsgaii.result();
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            } catch (Exception e) {
                // Ignorar
            }
            
            return null;
        } catch (Exception e) {
            // Si no se puede acceder, intentar obtener el resultado
            try {
                List<S> result = nsgaii.result();
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            } catch (Exception e2) {
                // No mostrar error si es solo que la población aún no está lista
                return null;
            }
            return null;
        }
    }
    
    @Override
    public List<S> result() {
        return nsgaii.result();
    }
    
    @Override
    public String name() {
        return "NSGA-II with Telemetry";
    }
    
    @Override
    public String description() {
        return "NSGA-II algorithm with telemetry tracking";
    }
    
    /**
     * Calcula el fitness promedio de la población para cada objetivo.
     */
    private double[] calculateAverageFitness(List<S> population) {
        int numObjectives = problem.numberOfObjectives();
        double[] sum = new double[numObjectives];
        
        for (S solution : population) {
            for (int i = 0; i < numObjectives; i++) {
                sum[i] += solution.objectives()[i];
            }
        }
        
        double[] avg = new double[numObjectives];
        for (int i = 0; i < numObjectives; i++) {
            avg[i] = sum[i] / population.size();
        }
        
        return avg;
    }
    
    /**
     * Calcula el mejor fitness para cada objetivo (mejor = menor valor para minimización).
     */
    private double[] calculateBestFitness(List<S> population) {
        int numObjectives = problem.numberOfObjectives();
        double[] best = new double[numObjectives];
        
        // Inicializar con la primera solución
        S first = population.get(0);
        for (int i = 0; i < numObjectives; i++) {
            best[i] = first.objectives()[i];
        }
        
        // Encontrar el mejor para cada objetivo
        for (S solution : population) {
            for (int i = 0; i < numObjectives; i++) {
                if (solution.objectives()[i] < best[i]) {
                    best[i] = solution.objectives()[i];
                }
            }
        }
        
        return best;
    }
    
    /**
     * Obtiene las soluciones no dominadas (frente de Pareto).
     * 
     * Nota: NSGA-II mantiene toda la población en el resultado final (todas están rank 0),
     * así que en realidad todas las soluciones están en el frente de Pareto.
     * Sin embargo, calculamos el verdadero frente no dominado para métricas más precisas.
     */
    private List<S> getNonDominatedSolutions(List<S> population) {
        if (population == null || population.isEmpty()) {
            return List.of();
        }
        
        // Calcular el verdadero frente de Pareto (soluciones no dominadas)
        return population.stream()
            .filter(solution -> {
                // Una solución es no dominada si no hay otra que la domine
                return population.stream().noneMatch(other -> {
                    // No comparar consigo misma
                    if (other == solution) {
                        return false;
                    }
                    return dominates(other, solution);
                });
            })
            .toList();
    }
    
    /**
     * Verifica si solution1 domina a solution2.
     * solution1 domina a solution2 si es mejor o igual en todos los objetivos y mejor en al menos uno.
     */
    private boolean dominates(S solution1, S solution2) {
        boolean betterInAtLeastOne = false;
        boolean worseInAny = false;
        
        for (int i = 0; i < problem.numberOfObjectives(); i++) {
            double obj1 = solution1.objectives()[i];
            double obj2 = solution2.objectives()[i];
            
            if (obj1 < obj2) {  // Minimización: menor es mejor
                betterInAtLeastOne = true;
            } else if (obj1 > obj2) {
                worseInAny = true;
            }
        }
        
        return betterInAtLeastOne && !worseInAny;
    }
    
    /**
     * Obtiene los datos de telemetría para acceso externo.
     */
    public TelemetryData getTelemetryData() {
        return telemetryData;
    }
    
    /**
     * Builder para facilitar la construcción de NSGAIIWithTelemetry.
     */
    public static class Builder<S extends Solution<?>> {
        private Problem<S> problem;
        private int maxEvaluations;
        private int populationSize;
        private CrossoverOperator<S> crossoverOperator;
        private MutationOperator<S> mutationOperator;
        private SelectionOperator<List<S>, S> selectionOperator;
        private SolutionListEvaluator<S> evaluator;
        private String outputPath;
        
        public Builder<S> setProblem(Problem<S> problem) {
            this.problem = problem;
            return this;
        }
        
        public Builder<S> setMaxEvaluations(int maxEvaluations) {
            this.maxEvaluations = maxEvaluations;
            return this;
        }
        
        public Builder<S> setPopulationSize(int populationSize) {
            this.populationSize = populationSize;
            return this;
        }
        
        public Builder<S> setCrossoverOperator(CrossoverOperator<S> crossoverOperator) {
            this.crossoverOperator = crossoverOperator;
            return this;
        }
        
        public Builder<S> setMutationOperator(MutationOperator<S> mutationOperator) {
            this.mutationOperator = mutationOperator;
            return this;
        }
        
        public Builder<S> setSelectionOperator(SelectionOperator<List<S>, S> selectionOperator) {
            this.selectionOperator = selectionOperator;
            return this;
        }
        
        public Builder<S> setEvaluator(SolutionListEvaluator<S> evaluator) {
            this.evaluator = evaluator;
            return this;
        }
        
        public Builder<S> setOutputPath(String outputPath) {
            this.outputPath = outputPath;
            return this;
        }
        
        public NSGAIIWithTelemetry<S> build() {
            if (evaluator == null) {
                evaluator = new SequentialSolutionListEvaluator<>();
            }
            return new NSGAIIWithTelemetry<>(
                problem,
                maxEvaluations,
                populationSize,
                crossoverOperator,
                mutationOperator,
                selectionOperator,
                evaluator,
                outputPath
            );
        }
    }
}
