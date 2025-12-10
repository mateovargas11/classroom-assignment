package com.university;

import com.university.decoder.SolutionDecoder;
import com.university.domain.ProblemInstance;
import com.university.io.InstanceLoader;
import com.university.problem.ClassroomAssignmentProblem;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;
import org.uma.jmetal.operator.crossover.impl.IntegerSBXCrossover;
import org.uma.jmetal.operator.mutation.impl.IntegerPolynomialMutation;
import org.uma.jmetal.operator.selection.impl.BinaryTournamentSelection;
import org.uma.jmetal.solution.integersolution.IntegerSolution;
import org.uma.jmetal.util.comparator.RankingAndCrowdingDistanceComparator;

import java.util.Comparator;
import java.util.List;

public class Main {
    
    public static void main(String[] args) {
        ProblemInstance instance;
        
        String instanceName = args.length > 0 ? args[0] : "promedio_2024";
        
        try {
            instance = InstanceLoader.loadFromResources(instanceName);
            System.out.println("Instancia cargada: " + instanceName);
        } catch (Exception e) {
            System.err.println("Error cargando instancia: " + e.getMessage());
            System.out.println("Usando instancia de ejemplo...");
            instance = InstanceLoader.createSampleInstance();
        }
        
        System.out.println("Materias: " + instance.getSubjects().size());
        System.out.println("Salones: " + instance.getClassrooms().size());
        
        ClassroomAssignmentProblem problem = new ClassroomAssignmentProblem(instance);
        
        System.out.println("Tamano del vector: " + problem.numberOfVariables());
        System.out.println("Max salones por materia: " + problem.getMaxClassroomsPerSubject());
        System.out.println("Minimo teorico de asignaciones: " + problem.getTotalMinClassrooms());
        
        int populationSize = 200;
        int maxEvaluations = 50000;
        
        double crossoverProbability = 0.9;
        double crossoverDistributionIndex = 20.0;
        var crossover = new IntegerSBXCrossover(crossoverProbability, crossoverDistributionIndex);
        
        double mutationProbability = 1.0 / problem.numberOfVariables();
        double mutationDistributionIndex = 20.0;
        var mutation = new IntegerPolynomialMutation(mutationProbability, mutationDistributionIndex);
        
        var selection = new BinaryTournamentSelection<IntegerSolution>(
            new RankingAndCrowdingDistanceComparator<>()
        );
        
        Algorithm<List<IntegerSolution>> algorithm = new NSGAIIBuilder<>(
            problem,
            crossover,
            mutation,
            populationSize
        )
        .setSelectionOperator(selection)
        .setMaxEvaluations(maxEvaluations)
        .build();
        
        System.out.println("\nIniciando NSGA-II...");
        System.out.println("Poblacion: " + populationSize);
        System.out.println("Evaluaciones maximas: " + maxEvaluations);
        
        algorithm.run();
        
        List<IntegerSolution> population = algorithm.result();
        
        System.out.println("\nOptimizacion completada.");
        System.out.println("Soluciones en el frente de Pareto: " + population.size());
        
        IntegerSolution bestSolution = selectBestFeasibleSolution(population);
        
        SolutionDecoder decoder = new SolutionDecoder(instance, problem);
        
        if (bestSolution != null) {
            System.out.println("\n=== Mejor Solucion Factible ===");
            System.out.println("Objetivo (total asignaciones): " + (int) bestSolution.objectives()[0]);
            System.out.println("Restriccion capacidad: " + (int) bestSolution.constraints()[0]);
            System.out.println("Restriccion materias asignadas: " + (int) bestSolution.constraints()[1]);
            
            decoder.printSummary(bestSolution);
            decoder.printClassroomUsage(bestSolution);
        } else {
            System.out.println("\nNo se encontro ninguna solucion factible.");
            IntegerSolution best = selectBestInfeasible(population);
            if (best != null) {
                System.out.println("Mejor solucion (infactible):");
                System.out.println("  Objetivo (asignaciones): " + (int) best.objectives()[0]);
                System.out.println("  Deficit de capacidad: " + (int)(-best.constraints()[0]));
                System.out.println("  Materias no asignadas: " + (int)(-best.constraints()[1]));
                
                decoder.printSummary(best);
            }
        }
    }
    
    private static IntegerSolution selectBestFeasibleSolution(List<IntegerSolution> population) {
        return population.stream()
            .filter(Main::isFeasible)
            .min(Comparator.comparingDouble(s -> s.objectives()[0]))
            .orElse(null);
    }
    
    private static IntegerSolution selectBestInfeasible(List<IntegerSolution> population) {
        return population.stream()
            .min(Comparator.comparingDouble(s -> 
                s.objectives()[0] - s.constraints()[0] * 10 - s.constraints()[1] * 1000))
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
