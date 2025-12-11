package com.university;

import com.university.decoder.SolutionDecoder;
import com.university.domain.ProblemInstance;
import com.university.io.InstanceLoader;
import com.university.problem.ClassroomAssignmentProblem;
import com.university.problem.SolutionRepairOperator;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;
import org.uma.jmetal.operator.crossover.impl.IntegerSBXCrossover;
import org.uma.jmetal.operator.mutation.impl.IntegerPolynomialMutation;
import org.uma.jmetal.operator.selection.impl.BinaryTournamentSelection;
import org.uma.jmetal.solution.integersolution.IntegerSolution;
import org.uma.jmetal.util.comparator.RankingAndCrowdingDistanceComparator;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {
        ProblemInstance instance;

        String instanceName = args.length > 0 ? args[0] : "promedio_2024";

        instance = InstanceLoader.loadFromResources(instanceName);

        System.out.println("Instancia: " + instanceName);
        System.out.println("Materias: " + instance.getSubjects().size());
        System.out.println("Salones: " + instance.getClassrooms().size());
        System.out.println("Pares de conflicto (mismo semestre/carrera): " + instance.getConflictPairs().size());

        ClassroomAssignmentProblem problem = new ClassroomAssignmentProblem(instance);
        SolutionRepairOperator repairOperator = new SolutionRepairOperator(
                instance, problem.getMaxClassroomsPerSubject());

        System.out.println("Minimo teorico de asignaciones: " + problem.getTotalMinClassrooms());

        IntegerSolution greedySolution = problem.createSolution();
        repairOperator.repair(greedySolution);
        problem.evaluate(greedySolution);

        System.out.println("\n=== Solucion Greedy Inicial ===");
        System.out.println("Objetivo 1 (asignaciones): " + (int) greedySolution.objectives()[0]);
        System.out.println(
                "Objetivo 2 (separacion, mayor=mejor): " + String.format("%.2f", -greedySolution.objectives()[1]));
        System.out.println("Deficit capacidad: " + (int) (-greedySolution.constraints()[0]));
        System.out.println("Materias no asignadas: " + (int) (-greedySolution.constraints()[1]));

        SolutionDecoder decoder = new SolutionDecoder(instance, problem);
        decoder.printSummary(greedySolution);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Iniciando optimizacion con NSGA-II (2 objetivos)...");
        System.out.println("  Objetivo 1: Minimizar asignaciones materia-salon");
        System.out.println("  Objetivo 2: Maximizar separacion entre materias conflictivas");
        System.out.println("=".repeat(60));

        int populationSize = 100;
        int maxEvaluations = 250000;

        double crossoverProbability = 0.9;
        double crossoverDistributionIndex = 20.0;
        var crossover = new IntegerSBXCrossover(crossoverProbability, crossoverDistributionIndex);

        double mutationProbability = 1.0 / problem.numberOfVariables();
        double mutationDistributionIndex = 20.0;
        var mutation = new IntegerPolynomialMutation(mutationProbability, mutationDistributionIndex);

        var selection = new BinaryTournamentSelection<IntegerSolution>(
                new RankingAndCrowdingDistanceComparator<>());

        Algorithm<List<IntegerSolution>> algorithm = new NSGAIIBuilder<>(
                problem,
                crossover,
                mutation,
                populationSize)
                .setSelectionOperator(selection)
                .setMaxEvaluations(maxEvaluations)
                .build();

        algorithm.run();

        List<IntegerSolution> population = algorithm.result();

        for (IntegerSolution sol : population) {
            repairOperator.repair(sol);
            problem.evaluate(sol);
        }

        System.out.println("\nOptimizacion completada.");
        System.out.println("Soluciones en el frente de Pareto: " + population.size());

        List<IntegerSolution> feasibleSolutions = population.stream()
                .filter(Main::isFeasible)
                .toList();

        System.out.println("Soluciones factibles: " + feasibleSolutions.size());

        if (!feasibleSolutions.isEmpty()) {
            System.out.println("\n=== Frente de Pareto (soluciones factibles) ===");
            System.out.println("Asignaciones | Separacion Promedio");
            System.out.println("-".repeat(40));

            List<IntegerSolution> sortedPareto = feasibleSolutions.stream()
                    .sorted(Comparator.comparingDouble(s -> s.objectives()[0]))
                    .toList();

            for (int i = 0; i < Math.min(10, sortedPareto.size()); i++) {
                IntegerSolution sol = sortedPareto.get(i);
                System.out.printf("%12d | %.2f dias\n",
                        (int) sol.objectives()[0],
                        -sol.objectives()[1]);
            }
            if (sortedPareto.size() > 10) {
                System.out.println("... y " + (sortedPareto.size() - 10) + " soluciones mas");
            }

            IntegerSolution bestSolution = selectBestCompromiseSolution(feasibleSolutions);

            System.out.println("\n=== Mejor Solucion de Compromiso ===");
            System.out.println("Objetivo 1 (asignaciones): " + (int) bestSolution.objectives()[0]);
            System.out.println(
                    "Objetivo 2 (separacion promedio): " + String.format("%.2f dias", -bestSolution.objectives()[1]));
            System.out.println("Deficit capacidad: " + (int) (-bestSolution.constraints()[0]));
            System.out.println("Materias no asignadas: " + (int) (-bestSolution.constraints()[1]));

            decoder.printSummary(bestSolution);
            decoder.printScheduleSummary(bestSolution);
            decoder.printMatrix(bestSolution, 3);

            decoder.exportToCSV(bestSolution, "output/" + instanceName);
        } else {
            System.out.println("\nNo se encontraron soluciones factibles.");
            System.out.println("Usando solucion greedy.");
            decoder.printSummary(greedySolution);
            decoder.printScheduleSummary(greedySolution);
            decoder.printMatrix(greedySolution, 3);

            decoder.exportToCSV(greedySolution, "output/" + instanceName);
        }
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
}
