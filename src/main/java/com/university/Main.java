package com.university;

import com.university.algorithm.NSGAII_WithTelemetry;
import com.university.decoder.SolutionDecoder;
import com.university.domain.ProblemInstance;
import com.university.io.InstanceLoader;
import com.university.problem.ClassroomAssignmentProblem;
import com.university.problem.SolutionRepairOperator;
import com.university.solver.GreedySolver;
import com.university.telemetry.EvolutionTracker;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.crossover.impl.TwoPointCrossover;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.mutation.impl.IntegerPolynomialMutation;
import org.uma.jmetal.operator.selection.impl.BinaryTournamentSelection;
import org.uma.jmetal.solution.integersolution.IntegerSolution;
import org.uma.jmetal.util.comparator.RankingAndCrowdingDistanceComparator;
import org.uma.jmetal.util.evaluator.impl.SequentialSolutionListEvaluator;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

public class Main {

    public static void main(String[] args) throws IOException {
        ProblemInstance instance;
        String instanceName = args.length > 0 ? args[0] : "febrero_2024";
        int populationSize = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        double crossoverProbability = args.length > 2 ? Double.parseDouble(args[2]) : 0.8;
        double mutationProbability = args.length > 3 ? Double.parseDouble(args[3]) : 0.001;
        Long randomSeed = args.length > 4 ? Long.parseLong(args[4]) : null;

        instance = InstanceLoader.loadFromResources(instanceName);

        ClassroomAssignmentProblem problem = new ClassroomAssignmentProblem(instance, randomSeed);
        SolutionDecoder decoder = new SolutionDecoder(instance, problem);

        System.out.println();
        System.out.println("##################################### FASE 1: ALGORITMO GREEDY #####################################");

        long greedyStartTime = System.currentTimeMillis();

        GreedySolver greedySolver = new GreedySolver(instance, problem);
        IntegerSolution greedySolution = greedySolver.solve();

        long greedyEndTime = System.currentTimeMillis();
        long greedyTime = greedyEndTime - greedyStartTime;

        System.out.println("Tiempo de ejecución: " + greedyTime + " ms");
        System.out.println();
        printSolutionMetrics("GREEDY", greedySolution);

        System.out.println();
        System.out.println("##################################### FASE 2: ALGORITMO NSGA-II #####################################");
        System.out.println();
        System.out.println("  - Tamaño de población: " + populationSize);
        System.out.println("  - Probabilidad de cruzamiento: " + crossoverProbability);
        System.out.println("  - Probabilidad de mutación: " + mutationProbability);
        System.out.println();

        int maxEvaluations = 25000;
        int recordEveryNGenerations = 10;

        CrossoverOperator<IntegerSolution> crossover = new TwoPointCrossover(crossoverProbability);
        MutationOperator<IntegerSolution> mutation = new IntegerPolynomialMutation(mutationProbability, 5);

        var selection = new BinaryTournamentSelection<IntegerSolution>(
                new RankingAndCrowdingDistanceComparator<>());

        SolutionRepairOperator repairOperator = new SolutionRepairOperator(instance, problem, randomSeed);

        EvolutionTracker tracker = new EvolutionTracker(problem);

        long nsgaStartTime = System.currentTimeMillis();

        NSGAII_WithTelemetry algorithm = new NSGAII_WithTelemetry(
                problem,
                maxEvaluations,
                populationSize,
                populationSize,
                populationSize,
                crossover,
                mutation,
                selection,
                new SequentialSolutionListEvaluator<>(),
                tracker,
                repairOperator,
                recordEveryNGenerations);

        algorithm.run();

        List<IntegerSolution> population = algorithm.result();

        for (IntegerSolution sol : population) {
            repairOperator.repair(sol);
            problem.evaluate(sol);
        }

        long nsgaEndTime = System.currentTimeMillis();
        long nsgaTime = nsgaEndTime - nsgaStartTime;

        System.out.println("Tiempo de ejecución: " + nsgaTime + " ms");
        System.out.println("Evaluaciones: " + maxEvaluations);
        System.out.println();

        // Filtrar soluciones factibles
        List<IntegerSolution> feasibleSolutions = population.stream()
                .filter(Main::isFeasible)
                .toList();

        IntegerSolution bestNSGAII = null;
        if (!feasibleSolutions.isEmpty()) {
            bestNSGAII = selectBestCompromiseSolution(feasibleSolutions);
            System.out.println();
            printSolutionMetrics("NSGA-II (mejor compromiso)", bestNSGAII);
        } else {
            System.out.println("\nNo se encontraron soluciones factibles con NSGA-II.");
            bestNSGAII = population.stream()
                    .min(Comparator.comparingDouble(s -> s.objectives()[0]))
                    .orElse(null);
            if (bestNSGAII != null) {
                printSolutionMetrics("NSGA-II (mejor no factible)", bestNSGAII);
            }
        }

        decoder.exportToCSV(greedySolution, "output/" + instanceName + "_greedy");

        if (bestNSGAII != null) {
            decoder.exportToCSV(bestNSGAII, "output/" + instanceName + "_nsga2");
        }

        String telemetryBasePath = "output/" + instanceName + "_evolucion";
        tracker.saveToCsv(telemetryBasePath);

        List<IntegerSolution> solutionsToExport = !feasibleSolutions.isEmpty()
                ? feasibleSolutions
                : population;
        int instanceSize = instance.getSubjects().size();
        exportParetoFront(solutionsToExport, "output/" + instanceName + "_pareto_front.csv",
                instanceName, instanceSize, populationSize,
                crossoverProbability, mutationProbability);

    }

    private static void printSolutionMetrics(String name, IntegerSolution solution) {
        System.out.println("Resultados " + name + ":");
        System.out.println("  - Asignaciones totales: " + (int) solution.objectives()[0]);
        System.out.println("  - Separación promedio: " + String.format("%.2f días", -solution.objectives()[1]));
        System.out.println("  - Déficit capacidad: " + (int) (-solution.constraints()[0]));
        System.out.println("  - Materias sin asignar: " + (int) (-solution.constraints()[1]));
        System.out.println("  - Factible: " + (isFeasible(solution) ? "Sí" : "No"));
    }

    private static IntegerSolution selectBestCompromiseSolution(List<IntegerSolution> population) {
        double minObj1 = population.stream().mapToDouble(s -> s.objectives()[0]).min().orElse(0);
        double maxObj1 = population.stream().mapToDouble(s -> s.objectives()[0]).max().orElse(1);
        double minObj2 = population.stream().mapToDouble(s -> s.objectives()[1]).min().orElse(0);
        double maxObj2 = population.stream().mapToDouble(s -> s.objectives()[1]).max().orElse(1);

        double rangeObj1 = maxObj1 - minObj1;
        double rangeObj2 = maxObj2 - minObj2;

        if (rangeObj1 == 0)
            rangeObj1 = 1;
        if (rangeObj2 == 0)
            rangeObj2 = 1;

        final double r1 = rangeObj1;
        final double r2 = rangeObj2;
        final double m1 = minObj1;
        final double m2 = minObj2;

        return population.stream()
                .min(Comparator.comparingDouble(s -> {
                    double norm1 = (s.objectives()[0] - m1) / r1;
                    double norm2 = (s.objectives()[1] - m2) / r2;
                    return Math.sqrt(norm1 * norm1 + norm2 * norm2);
                }))
                .orElse(population.get(0));
    }

    private static boolean isFeasible(IntegerSolution solution) {
        for (double constraint : solution.constraints()) {
            if (constraint < 0) {
                return false;
            }
        }
        return true;
    }

    private static void exportParetoFront(List<IntegerSolution> population, String filePath,
                                          String instanceName, int instanceSize, int populationSize,
                                          double crossoverProb, double mutationProb) throws IOException {
        new java.io.File("output").mkdirs();

        List<IntegerSolution> solutionsForHV = new ArrayList<>();

        java.io.File file = new java.io.File(filePath);
        boolean fileExists = file.exists();

        try (java.io.PrintWriter writer = new java.io.PrintWriter(
                new java.io.FileWriter(filePath, true))) { // append mode
            if (!fileExists) {
                writer.println("f1,f2");
            }

            for (IntegerSolution solution : population) {
                double obj1 = solution.objectives()[0];
                double obj2 = -solution.objectives()[1];
                writer.printf("%.6f,%.6f\n", obj1, obj2);

                solutionsForHV.add(solution);
            }
        }

        double hypervolume = 0.0;
        if (!solutionsForHV.isEmpty()) {
            try {
                List<Double> referencePoint = new ArrayList<>();
                referencePoint.add(1.0);
                referencePoint.add(1.0);

                hypervolume = calculateHypervolume2DNormalized(solutionsForHV, referencePoint);

            } catch (Exception e) {
                System.err.println("Error calculando hipervolumen: " + e.getMessage());
                e.printStackTrace();
                hypervolume = 0.0;
            }
        }

        saveHypervolumeStatistics(instanceName, instanceSize, populationSize, crossoverProb, mutationProb,
                hypervolume, filePath);

    }

    private static double calculateHypervolume2DNormalized(List<IntegerSolution> solutions,
            List<Double> referencePoint) {
        if (solutions.isEmpty()) {
            return 0.0;
        }

        final double MAX_SEPARATION = 25.0;
        final double MAX_ASSIGNMENTS = 1200.0;

        double refF1 = referencePoint.get(0);
        double refF2 = referencePoint.get(1);

        List<SolutionNormalized> normalizedSolutions = new ArrayList<>();

        for (IntegerSolution sol : solutions) {
            double assignments = sol.objectives()[0];
            double separationNeg = sol.objectives()[1];
            double separation = -separationNeg;

            double f1_norm = 1.0 - (separation / MAX_SEPARATION);

            double f2_norm = assignments / MAX_ASSIGNMENTS;

            if (f1_norm <= refF1 && f2_norm <= refF2) {
                normalizedSolutions.add(new SolutionNormalized(sol, f1_norm, f2_norm));
            }
        }

        if (normalizedSolutions.isEmpty()) {
            return 0.0;
        }

        List<SolutionNormalized> nonDominated = new ArrayList<>();
        for (int i = 0; i < normalizedSolutions.size(); i++) {
            SolutionNormalized sol1 = normalizedSolutions.get(i);
            boolean isDominated = false;

            for (int j = 0; j < normalizedSolutions.size(); j++) {
                if (i == j)
                    continue;
                SolutionNormalized sol2 = normalizedSolutions.get(j);

                if (sol2.f1_norm <= sol1.f1_norm &&
                        sol2.f2_norm <= sol1.f2_norm &&
                        (sol2.f1_norm < sol1.f1_norm ||
                                sol2.f2_norm < sol1.f2_norm)) {
                    isDominated = true;
                    break;
                }
            }

            if (!isDominated) {
                nonDominated.add(sol1);
            }
        }

        if (nonDominated.isEmpty()) {
            return 0.0;
        }

        nonDominated.sort(Comparator.comparingDouble(s -> s.f1_norm));

        double hv = 0.0;
        double prevF1 = refF1;
        double prevF2 = refF2;

        for (SolutionNormalized sol : nonDominated) {
            double f1 = sol.f1_norm;
            double f2 = sol.f2_norm;

            double width = prevF1 - f1;
            double height = prevF2 - f2;

            if (width > 0 && height > 0) {
                hv += width * height;
                prevF1 = f1;
                prevF2 = f2;
            }
        }

        return Math.max(0.0, hv);
    }

    /**
     * Clase auxiliar para almacenar soluciones con valores normalizados.
     */
    private static class SolutionNormalized {
        final IntegerSolution solution;
        final double f1_norm;
        final double f2_norm;

        SolutionNormalized(IntegerSolution solution, double f1_norm, double f2_norm) {
            this.solution = solution;
            this.f1_norm = f1_norm;
            this.f2_norm = f2_norm;
        }
    }

    /**
     * Guarda las estadísticas de hipervolumen en un archivo CSV.
     */
    private static void saveHypervolumeStatistics(String instanceName, int instanceSize, int populationSize,
            double crossoverProb, double mutationProb,
            double hypervolume, String paretoFilePath) throws IOException {
        String statsFilePath = "output/" + instanceName + "_hypervolume_stats.csv";
        java.io.File statsFile = new java.io.File(statsFilePath);

        boolean fileExists = statsFile.exists();

        try (java.io.PrintWriter writer = new java.io.PrintWriter(
                new java.io.FileWriter(statsFilePath, true))) { // append mode

            if (!fileExists) {
                writer.println("INSTANCIA,TAM,POB,CRU,MUT,HV,PARETO_FILE");
            }

            writer.printf("%s,%d,%d,%.2f,%.3f,%.6f,%s%n",
                    instanceName,
                    instanceSize,
                    populationSize,
                    crossoverProb,
                    mutationProb,
                    hypervolume,
                    paretoFilePath);
        }
    }
}
