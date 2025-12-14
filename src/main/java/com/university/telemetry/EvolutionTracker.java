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
     */
    public void update(List<IntegerSolution> population) {
        generation++;

        double bestObj1 = Double.MAX_VALUE;
        double bestObj2 = Double.MAX_VALUE;
        double sumObj1Feasible = 0;
        double sumObj2Feasible = 0;
        int feasibleCount = 0;

        int bestRealAssignments = Integer.MAX_VALUE;
        double sumRealAssignments = 0;

        double bestObj1All = Double.MAX_VALUE;
        double bestObj2All = Double.MAX_VALUE;
        int bestRealAssignmentsAll = Integer.MAX_VALUE;
        double sumObj1All = 0;
        double sumObj2All = 0;
        double sumRealAssignmentsAll = 0;

        for (IntegerSolution sol : population) {
            double obj1 = sol.objectives()[0];
            double obj2 = sol.objectives()[1];

            boolean feasible = true;
            for (double c : sol.constraints()) {
                if (c < 0) {
                    feasible = false;
                    break;
                }
            }

            int realAssignments = countRealAssignments(sol);
            sumObj1All += obj1;
            sumObj2All += obj2;
            sumRealAssignmentsAll += realAssignments;

            if (obj1 < bestObj1All) {
                bestObj1All = obj1;
                bestRealAssignmentsAll = realAssignments;
            }
            if (obj2 < bestObj2All) {
                bestObj2All = obj2;
            }

            if (feasible) {
                sumObj1Feasible += obj1;
                sumObj2Feasible += obj2;
                feasibleCount++;
                sumRealAssignments += realAssignments;

                if (obj1 < bestObj1) {
                    bestObj1 = obj1;
                    bestRealAssignments = realAssignments;
                }
                if (obj2 < bestObj2) {
                    bestObj2 = obj2;
                }
            }
        }

        double avgObj1 = feasibleCount > 0 ? sumObj1Feasible / feasibleCount : 0;
        double avgObj2 = feasibleCount > 0 ? sumObj2Feasible / feasibleCount : 0;
        double avgRealAssignments = feasibleCount > 0 ? sumRealAssignments / feasibleCount : 0;

        double avgObj1All = population.size() > 0 ? sumObj1All / population.size() : 0;
        double avgObj2All = population.size() > 0 ? sumObj2All / population.size() : 0;
        double avgRealAssignmentsAll = population.size() > 0 ? sumRealAssignmentsAll / population.size() : 0;

        if (bestObj1 == Double.MAX_VALUE) {
            bestRealAssignments = bestRealAssignmentsAll != Integer.MAX_VALUE ? bestRealAssignmentsAll : 0;
            bestObj1 = bestObj1All != Double.MAX_VALUE ? bestObj1All : 0;
        }
        if (bestObj2 == Double.MAX_VALUE) {
            bestObj2 = bestObj2All != Double.MAX_VALUE ? bestObj2All : 0;
        }

        if (feasibleCount == 0) {
            avgObj1 = avgObj1All;
            avgObj2 = avgObj2All;
            avgRealAssignments = avgRealAssignmentsAll;
        }

        double sumSquaredDiffObj1 = 0;
        double sumSquaredDiffObj2 = 0;
        double sumSquaredDiffRealAssignments = 0;
        int countForStdDev = feasibleCount > 0 ? feasibleCount : population.size();
        double meanObj1ForStdDev = feasibleCount > 0 ? avgObj1 : avgObj1All;
        double meanObj2ForStdDev = feasibleCount > 0 ? avgObj2 : avgObj2All;
        double meanRealAssignmentsForStdDev = feasibleCount > 0 ? avgRealAssignments : avgRealAssignmentsAll;

        for (IntegerSolution sol : population) {
            boolean feasible = true;
            for (double c : sol.constraints()) {
                if (c < 0) {
                    feasible = false;
                    break;
                }
            }

            if ((feasibleCount > 0 && feasible) || (feasibleCount == 0)) {
                double obj1 = sol.objectives()[0];
                double obj2 = sol.objectives()[1];
                int realAssignments = countRealAssignments(sol);

                sumSquaredDiffObj1 += Math.pow(obj1 - meanObj1ForStdDev, 2);
                sumSquaredDiffObj2 += Math.pow(obj2 - meanObj2ForStdDev, 2);
                sumSquaredDiffRealAssignments += Math.pow(realAssignments - meanRealAssignmentsForStdDev, 2);
            }
        }

        double stdDevObj1 = countForStdDev > 1
                ? Math.sqrt(sumSquaredDiffObj1 / countForStdDev)
                : 0.0;
        double stdDevObj2 = countForStdDev > 1
                ? Math.sqrt(sumSquaredDiffObj2 / countForStdDev)
                : 0.0;
        double stdDevRealAssignments = countForStdDev > 1
                ? Math.sqrt(sumSquaredDiffRealAssignments / countForStdDev)
                : 0.0;

        long elapsedTime = System.currentTimeMillis() - startTime;
        double bestSeparation = -bestObj2;
        double avgSeparation = -avgObj2;

        GenerationData data = new GenerationData(
                generation,
                bestRealAssignments,
                avgRealAssignments,
                bestSeparation,
                avgSeparation,
                bestObj1,
                avgObj1,
                bestObj2,
                avgObj2,
                stdDevObj1,
                stdDevObj2,
                stdDevRealAssignments,
                feasibleCount,
                population.size(),
                elapsedTime);

        history.add(data);
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

    public void saveToCsv(String baseFileName) {
        String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String fileName = baseFileName + "_" + dateTime + ".csv";

        try {
            Files.createDirectories(Paths.get(fileName).getParent());
        } catch (IOException e) {
            System.err.println("Error creating directories: " + e.getMessage());
        }

        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write(
                    "generacion,mejor_asignaciones,promedio_asignaciones,desv_std_asignaciones,mejor_separacion,promedio_separacion,mejor_fitness_obj1,promedio_fitness_obj1,desv_std_fitness_obj1,mejor_fitness_obj2,promedio_fitness_obj2,desv_std_fitness_obj2,soluciones_factibles,poblacion_total,tiempo_ms\n");

            for (GenerationData data : history) {
                writer.write(String.format("%d,%.2f,%.2f,%.6f,%.4f,%.4f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d,%d\n",
                        data.generation,
                        data.bestObj1,
                        data.avgObj1,
                        data.stdDevRealAssignments,
                        data.bestSeparation,
                        data.avgSeparation,
                        data.bestFitnessObj1,
                        data.avgFitnessObj1,
                        data.stdDevObj1,
                        data.bestFitnessObj2,
                        data.avgFitnessObj2,
                        data.stdDevObj2,
                        data.feasibleCount,
                        data.populationSize,
                        data.elapsedTimeMs));
            }

        } catch (IOException e) {
            System.err.println("An error occurred while saving the results: " + e.getMessage());
        }
    }

    public int getGeneration() {
        return generation;
    }

    public List<GenerationData> getHistory() {
        return history;
    }

    public static class GenerationData {
        public final int generation;
        public final double bestObj1;
        public final double avgObj1;
        public final double bestSeparation;
        public final double avgSeparation;
        public final double bestFitnessObj1;
        public final double avgFitnessObj1;
        public final double bestFitnessObj2;
        public final double avgFitnessObj2;
        public final double stdDevObj1;
        public final double stdDevObj2;
        public final double stdDevRealAssignments;
        public final int feasibleCount;
        public final int populationSize;
        public final long elapsedTimeMs;

        public GenerationData(int generation, double bestObj1, double avgObj1,
                double bestSeparation, double avgSeparation,
                double bestFitnessObj1, double avgFitnessObj1,
                double bestFitnessObj2, double avgFitnessObj2,
                double stdDevObj1, double stdDevObj2, double stdDevRealAssignments,
                int feasibleCount, int populationSize, long elapsedTimeMs) {
            this.generation = generation;
            this.bestObj1 = bestObj1;
            this.avgObj1 = avgObj1;
            this.bestSeparation = bestSeparation;
            this.avgSeparation = avgSeparation;
            this.bestFitnessObj1 = bestFitnessObj1;
            this.avgFitnessObj1 = avgFitnessObj1;
            this.bestFitnessObj2 = bestFitnessObj2;
            this.avgFitnessObj2 = avgFitnessObj2;
            this.stdDevObj1 = stdDevObj1;
            this.stdDevObj2 = stdDevObj2;
            this.stdDevRealAssignments = stdDevRealAssignments;
            this.feasibleCount = feasibleCount;
            this.populationSize = populationSize;
            this.elapsedTimeMs = elapsedTimeMs;
        }
    }
}
