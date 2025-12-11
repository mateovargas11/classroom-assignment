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

        ClassroomAssignmentProblem problem = new ClassroomAssignmentProblem(instance);
        SolutionRepairOperator repairOperator = new SolutionRepairOperator(
                instance, problem.getMaxClassroomsPerSubject());

        System.out.println("Minimo teorico de asignaciones: " + problem.getTotalMinClassrooms());

        IntegerSolution greedySolution = problem.createSolution();
        repairOperator.repair(greedySolution);
        problem.evaluate(greedySolution);

        System.out.println("\n=== Solucion Greedy Inicial ===");
        System.out.println("Objetivo: " + (int) greedySolution.objectives()[0]);
        System.out.println("Deficit capacidad: " + (int) (-greedySolution.constraints()[0]));
        System.out.println("Materias no asignadas: " + (int) (-greedySolution.constraints()[1]));

        SolutionDecoder decoder = new SolutionDecoder(instance, problem);
        decoder.printSummary(greedySolution);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Iniciando optimizacion con NSGA-II...");
        System.out.println("=".repeat(60));

        int populationSize = 100;
        int maxEvaluations = 25000;

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
        System.out.println("Soluciones en el frente: " + population.size());

        IntegerSolution bestSolution = selectBestFeasibleSolution(population);

        if (bestSolution != null) {
            System.out.println("\n=== Mejor Solucion NSGA-II ===");
            System.out.println("Objetivo: " + (int) bestSolution.objectives()[0]);
            System.out.println("Deficit capacidad: " + (int) (-bestSolution.constraints()[0]));
            System.out.println("Materias no asignadas: " + (int) (-bestSolution.constraints()[1]));

            decoder.printSummary(bestSolution);
            decoder.printScheduleSummary(bestSolution);
            decoder.printMatrix(bestSolution, 3);
        } else {
            System.out.println("\nNo se encontro solucion factible con NSGA-II.");
            System.out.println("Usando solucion greedy.");
            decoder.printSummary(greedySolution);
            decoder.printScheduleSummary(greedySolution);
            decoder.printMatrix(greedySolution, 3);
        }
    }

    private static IntegerSolution selectBestFeasibleSolution(List<IntegerSolution> population) {
        return population.stream()
                .filter(Main::isFeasible)
                .min(Comparator.comparingDouble(s -> s.objectives()[0]))
                .orElse(null);
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
