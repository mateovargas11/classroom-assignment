package com.university;

import com.university.decoder.SolutionDecoder;
import com.university.domain.ProblemInstance;
import com.university.io.InstanceLoader;
import com.university.problem.ClassroomAssignmentProblem;
import com.university.problem.SoftRepairOperator;
import com.university.solver.GreedySolver;
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

        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║     COMPARACIÓN: GREEDY vs NSGA-II                        ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Instancia: " + instanceName);
        System.out.println("Materias: " + instance.getSubjects().size());
        System.out.println("Salones: " + instance.getClassrooms().size());
        System.out.println("Pares de conflicto: " + instance.getConflictPairs().size());
        System.out.println();

        ClassroomAssignmentProblem problem = new ClassroomAssignmentProblem(instance);
        SolutionDecoder decoder = new SolutionDecoder(instance, problem);

        System.out.println("Mínimo teórico de asignaciones: " + problem.getTotalMinClassrooms());

        // ═══════════════════════════════════════════════════════════════
        // FASE 1: SOLUCIÓN GREEDY PURA
        // ═══════════════════════════════════════════════════════════════
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  FASE 1: ALGORITMO GREEDY (Best-Fit)                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        long greedyStartTime = System.currentTimeMillis();

        GreedySolver greedySolver = new GreedySolver(instance, problem);
        IntegerSolution greedySolution = greedySolver.solve();

        long greedyEndTime = System.currentTimeMillis();
        long greedyTime = greedyEndTime - greedyStartTime;

        System.out.println("Tiempo de ejecución: " + greedyTime + " ms");
        System.out.println();
        printSolutionMetrics("GREEDY", greedySolution);

        // ═══════════════════════════════════════════════════════════════
        // FASE 2: NSGA-II
        // ═══════════════════════════════════════════════════════════════
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  FASE 2: ALGORITMO NSGA-II (Multi-objetivo)                ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Configuración:");
        System.out.println("  - Objetivo 1: Minimizar asignaciones materia-salón");
        System.out.println("  - Objetivo 2: Maximizar separación entre materias conflictivas");
        System.out.println("  - Inicialización: ALEATORIA (sin heurística)");
        System.out.println("  - Reparación: MÍNIMA (solo restricciones críticas)");
        System.out.println();

        // Configuración NSGA-II
        int populationSize = 100;
        int maxEvaluations = 1000000; // Más evaluaciones para dar tiempo a NSGA-II

        double crossoverProbability = 0.9;
        double crossoverDistributionIndex = 20.0;
        var crossover = new IntegerSBXCrossover(crossoverProbability, crossoverDistributionIndex);

        double mutationProbability = 1.0 / problem.numberOfVariables();
        double mutationDistributionIndex = 20.0;
        var mutation = new IntegerPolynomialMutation(mutationProbability, mutationDistributionIndex);

        var selection = new BinaryTournamentSelection<IntegerSolution>(
                new RankingAndCrowdingDistanceComparator<>());

        // Operador de reparación SUAVE (solo restricciones críticas)
        SoftRepairOperator softRepair = new SoftRepairOperator(instance, problem.getMaxClassroomsPerSubject());

        long nsgaStartTime = System.currentTimeMillis();

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

        // Reparar y evaluar soluciones finales
        for (IntegerSolution sol : population) {
            softRepair.repair(sol);
            problem.evaluate(sol);
        }

        long nsgaEndTime = System.currentTimeMillis();
        long nsgaTime = nsgaEndTime - nsgaStartTime;

        System.out.println("Tiempo de ejecución: " + nsgaTime + " ms");
        System.out.println("Evaluaciones: " + maxEvaluations);
        System.out.println("Tamaño de población: " + populationSize);
        System.out.println();

        // Filtrar soluciones factibles
        List<IntegerSolution> feasibleSolutions = population.stream()
                .filter(Main::isFeasible)
                .toList();

        System.out.println("Soluciones en el frente de Pareto: " + population.size());
        System.out.println("Soluciones factibles: " + feasibleSolutions.size());

        IntegerSolution bestNSGAII = null;
        if (!feasibleSolutions.isEmpty()) {
            // Mostrar frente de Pareto
            System.out.println();
            System.out.println("Frente de Pareto (top 10):");
            System.out.println("  Asignaciones | Separación (días)");
            System.out.println("  " + "-".repeat(35));

            List<IntegerSolution> sortedPareto = feasibleSolutions.stream()
                    .sorted(Comparator.comparingDouble(s -> s.objectives()[0]))
                    .toList();

            for (int i = 0; i < Math.min(10, sortedPareto.size()); i++) {
                IntegerSolution sol = sortedPareto.get(i);
                System.out.printf("  %12d | %.2f\n",
                        (int) sol.objectives()[0],
                        -sol.objectives()[1]);
            }

            bestNSGAII = selectBestCompromiseSolution(feasibleSolutions);
            System.out.println();
            printSolutionMetrics("NSGA-II (mejor compromiso)", bestNSGAII);
        } else {
            System.out.println("\n⚠ No se encontraron soluciones factibles con NSGA-II.");
            // Tomar la mejor solución no factible
            bestNSGAII = population.stream()
                    .min(Comparator.comparingDouble(s -> s.objectives()[0]))
                    .orElse(null);
            if (bestNSGAII != null) {
                printSolutionMetrics("NSGA-II (mejor no factible)", bestNSGAII);
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // FASE 3: COMPARACIÓN
        // ═══════════════════════════════════════════════════════════════
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  COMPARACIÓN DE RESULTADOS                                 ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // RE-EVALUAR ambas soluciones para asegurar valores correctos
        problem.evaluate(greedySolution);
        if (bestNSGAII != null) {
            problem.evaluate(bestNSGAII);
        }

        // Calcular asignaciones REALES (sin penalización)
        int greedyRealAssign = countRealAssignments(greedySolution, problem);
        int nsgaRealAssign = bestNSGAII != null ? countRealAssignments(bestNSGAII, problem) : -1;

        // Calcular exceso de salones
        int greedyExcess = countExcessClassrooms(greedySolution, problem);
        int nsgaExcess = bestNSGAII != null ? countExcessClassrooms(bestNSGAII, problem) : 0;

        double greedySep = -greedySolution.objectives()[1];
        double nsgaSep = bestNSGAII != null ? -bestNSGAII.objectives()[1] : 0;

        System.out.println();
        System.out.println("┌────────────────────────┬────────────────┬────────────────┐");
        System.out.println("│ Métrica                │ GREEDY         │ NSGA-II        │");
        System.out.println("├────────────────────────┼────────────────┼────────────────┤");

        String assignWinner = greedyRealAssign < nsgaRealAssign ? " ✓" : "";
        String nsgaAssignWinner = nsgaRealAssign < greedyRealAssign ? " ✓" : "";

        System.out.printf("│ Asignaciones reales    │ %12d%s │ %12d%s │\n",
                greedyRealAssign, assignWinner, nsgaRealAssign, nsgaAssignWinner);
        System.out.printf("│ Exceso de salones      │ %14d │ %14d │\n",
                greedyExcess, nsgaExcess);

        String sepWinner = greedySep > nsgaSep ? " ✓" : "";
        String nsgaSepWinner = nsgaSep > greedySep ? " ✓" : "";

        System.out.printf("│ Separación (días)      │ %12.2f%s │ %12.2f%s │\n",
                greedySep, sepWinner, nsgaSep, nsgaSepWinner);
        System.out.printf("│ Tiempo (ms)            │ %14d │ %14d │\n", greedyTime, nsgaTime);

        boolean greedyFeasible = isFeasible(greedySolution);
        boolean nsgaFeasible = bestNSGAII != null && isFeasible(bestNSGAII);
        System.out.printf("│ Factible               │ %14s │ %14s │\n",
                greedyFeasible ? "Sí" : "No", nsgaFeasible ? "Sí" : "No");

        System.out.println("└────────────────────────┴────────────────┴────────────────┘");

        // Análisis de mejora
        System.out.println();
        System.out.println("ANÁLISIS:");
        if (nsgaRealAssign < greedyRealAssign) {
            double improvement = 100.0 * (greedyRealAssign - nsgaRealAssign) / greedyRealAssign;
            System.out.printf("  ✓ NSGA-II redujo las asignaciones en %.1f%% (%d menos)\n",
                    improvement, greedyRealAssign - nsgaRealAssign);
        } else if (nsgaRealAssign > greedyRealAssign) {
            double worse = 100.0 * (nsgaRealAssign - greedyRealAssign) / greedyRealAssign;
            System.out.printf("  ✗ NSGA-II tiene %.1f%% más asignaciones que Greedy\n", worse);
        } else {
            System.out.println("  = Ambos algoritmos tienen el mismo número de asignaciones");
        }

        if (nsgaSep > greedySep) {
            double improvement = 100.0 * (nsgaSep - greedySep) / (greedySep > 0 ? greedySep : 1);
            System.out.printf("  ✓ NSGA-II mejoró la separación en %.1f%% (%.2f días más)\n",
                    improvement, nsgaSep - greedySep);
        } else if (nsgaSep < greedySep) {
            System.out.printf("  ✗ Greedy tiene mejor separación (%.2f días más)\n", greedySep - nsgaSep);
        } else {
            System.out.println("  = Ambos algoritmos tienen la misma separación");
        }

        // ═══════════════════════════════════════════════════════════════
        // EXPORTAR MEJORES SOLUCIONES
        // ═══════════════════════════════════════════════════════════════
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  EXPORTANDO SOLUCIONES                                     ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        decoder.exportToCSV(greedySolution, "output/" + instanceName + "_greedy");
        System.out.println("  ✓ Greedy exportado a: output/" + instanceName + "_greedy.csv");

        if (bestNSGAII != null) {
            decoder.exportToCSV(bestNSGAII, "output/" + instanceName + "_nsga2");
            System.out.println("  ✓ NSGA-II exportado a: output/" + instanceName + "_nsga2.csv");
        }

        // Mostrar detalle de la mejor solución
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  DETALLE DE LA MEJOR SOLUCIÓN                              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        IntegerSolution bestOverall = (nsgaRealAssign <= greedyRealAssign && nsgaFeasible) ? bestNSGAII
                : greedySolution;
        String bestAlgorithm = (bestOverall == greedySolution) ? "GREEDY" : "NSGA-II";

        System.out.println("Mejor algoritmo: " + bestAlgorithm);
        decoder.printSummary(bestOverall);
        decoder.printScheduleSummary(bestOverall);
        decoder.printMatrix(bestOverall, 3);
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

    /**
     * Cuenta las asignaciones REALES (sin penalización).
     * Es decir, el número total de pares materia-salón asignados.
     */
    private static int countRealAssignments(IntegerSolution solution, ClassroomAssignmentProblem problem) {
        var assignments = problem.decodeAssignments(solution);
        return assignments.values().stream().mapToInt(java.util.Set::size).sum();
    }

    /**
     * Cuenta el exceso de salones asignados por encima del mínimo necesario.
     */
    private static int countExcessClassrooms(IntegerSolution solution, ClassroomAssignmentProblem problem) {
        var assignments = problem.decodeAssignments(solution);
        int excess = 0;
        int[] minClassrooms = problem.getMinClassroomsPerSubject();

        for (var entry : assignments.entrySet()) {
            int subjectIdx = entry.getKey();
            int assigned = entry.getValue().size();
            int min = minClassrooms[subjectIdx];
            if (assigned > min) {
                excess += (assigned - min);
            }
        }
        return excess;
    }
}
