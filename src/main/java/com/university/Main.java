package com.university;

import com.university.algorithm.NSGAII_WithTelemetry;
import com.university.decoder.SolutionDecoder;
import com.university.domain.ProblemInstance;
import com.university.io.InstanceLoader;
import com.university.problem.ClassroomAssignmentProblem;
import com.university.problem.ClassroomAssignmentProblem.DecodedAssignment;
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
import java.util.Map;
import java.util.ArrayList;

public class Main {

    public static void main(String[] args) throws IOException {
        ProblemInstance instance;

        // Parse command-line arguments
        // args[0]: instance name
        // args[1]: population size
        // args[2]: crossover probability
        // args[3]: mutation probability
        // args[4]: random seed (optional)
        String instanceName = args.length > 0 ? args[0] : "promedio_2024";
        int populationSize = args.length > 1 ? Integer.parseInt(args[1]) : 50;
        double crossoverProbability = args.length > 2 ? Double.parseDouble(args[2]) : 0.8;
        double mutationProbability = args.length > 3 ? Double.parseDouble(args[3]) : 0.001;
        Long randomSeed = args.length > 4 ? Long.parseLong(args[4]) : null;

        instance = InstanceLoader.loadFromResources(instanceName);

        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║     COMPARACIÓN: GREEDY vs NSGA-II                        ║");
        System.out.println("║     (Representación: Slots con decodificación automática)  ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Instancia: " + instanceName);
        System.out.println("Materias: " + instance.getSubjects().size());
        System.out.println("Salones: " + instance.getClassrooms().size());
        System.out.println("Pares de conflicto: " + instance.getConflictPairs().size());
        System.out.println();

        ClassroomAssignmentProblem problem = new ClassroomAssignmentProblem(instance, randomSeed);
        SolutionDecoder decoder = new SolutionDecoder(instance, problem);

        System.out.println("REPRESENTACIÓN DEL VECTOR:");
        System.out.println("  - Cada slot: [examen, salón1, salón2, salón3, salón4]");
        System.out.println("  - Total slots: " + problem.getNumSlots());
        System.out.println("  - Tamaño del vector: " + problem.getVectorSize());
        System.out.println("  - Decodificación: asigna horario más temprano disponible");
        System.out.println("  - Sincronización: GARANTIZADA (todos los salones mismo horario)");
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
        System.out.println("  - Restricción 1: Capacidad suficiente para todos los estudiantes");
        System.out.println("  - Restricción 2: Todas las materias deben estar asignadas");
        System.out.println("  - Modelo: Slots con decodificación automática de horarios");
        System.out.println("  - Inicialización: Híbrida (50% greedy, 30% greedy+ruido, 20% aleatoria)");
        System.out.println("  - Sincronización: GARANTIZADA por diseño de la representación");
        System.out.println("  - Tamaño de población: " + populationSize);
        System.out.println("  - Probabilidad de cruzamiento: " + crossoverProbability);
        System.out.println("  - Probabilidad de mutación: " + mutationProbability);
        System.out.println();

        // Configuración NSGA-II
        int maxEvaluations = 25000;
        int recordEveryNGenerations = 10;

        CrossoverOperator<IntegerSolution> crossover = new TwoPointCrossover(crossoverProbability);
        MutationOperator<IntegerSolution> mutation = new IntegerPolynomialMutation(mutationProbability, 5);

        var selection = new BinaryTournamentSelection<IntegerSolution>(
                new RankingAndCrowdingDistanceComparator<>());

        // Operador de reparación
        SolutionRepairOperator repairOperator = new SolutionRepairOperator(instance, problem, randomSeed);

        // Tracker de evolución para telemetría
        EvolutionTracker tracker = new EvolutionTracker(problem);

        long nsgaStartTime = System.currentTimeMillis();

        // NSGA-II con telemetría integrada
        NSGAII_WithTelemetry algorithm = new NSGAII_WithTelemetry(
                problem,
                maxEvaluations,
                populationSize,
                populationSize, // matingPoolSize
                populationSize, // offspringPopulationSize
                crossover,
                mutation,
                selection,
                new SequentialSolutionListEvaluator<>(),
                tracker,
                repairOperator,
                recordEveryNGenerations);

        algorithm.run();

        List<IntegerSolution> population = algorithm.result();

        // Reparar y evaluar soluciones finales
        for (IntegerSolution sol : population) {
            repairOperator.repair(sol);
            problem.evaluate(sol);
        }

        long nsgaEndTime = System.currentTimeMillis();
        long nsgaTime = nsgaEndTime - nsgaStartTime;

        // Mostrar resumen de evolución
        tracker.printSummary();

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

        // Exportar telemetría de evolución
        String telemetryBasePath = "output/" + instanceName + "_evolucion";
        String scriptPath = "output/plot_evolucion.py";
        String telemetryPath = tracker.saveToCsv(telemetryBasePath);
        if (telemetryPath != null) {
            tracker.generatePythonPlotScript(telemetryPath, scriptPath);
        }

        // Exportar frente de Pareto completo con hipervolumen
        int instanceSize = instance.getSubjects().size();
        double hypervolume = exportParetoFront(population, "output/" + instanceName + "_pareto_front.csv",
                instanceName, instanceSize, populationSize,
                crossoverProbability, mutationProbability);
        System.out.println("  ✓ Frente de Pareto exportado a: output/" + instanceName + "_pareto_front.csv");
        System.out.println("  ✓ Hipervolumen calculado: " + String.format("%.6f", hypervolume));

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
        Map<Integer, DecodedAssignment> assignments = problem.decode(solution);
        return assignments.values().stream()
                .filter(a -> a.assigned)
                .mapToInt(a -> a.classrooms.size())
                .sum();
    }

    /**
     * Cuenta el exceso de salones asignados por encima del mínimo necesario.
     */
    private static int countExcessClassrooms(IntegerSolution solution, ClassroomAssignmentProblem problem) {
        Map<Integer, DecodedAssignment> assignments = problem.decode(solution);
        int excess = 0;
        int[] minClassrooms = problem.getMinClassroomsPerSubject();

        for (var entry : assignments.entrySet()) {
            int subjectIdx = entry.getKey();
            DecodedAssignment assignment = entry.getValue();
            if (!assignment.assigned)
                continue;

            int assigned = assignment.classrooms.size();
            int min = minClassrooms[subjectIdx];
            excess += (assigned - min);
        }
        return excess;
    }

    /**
     * Exporta todas las soluciones del frente de Pareto a un archivo CSV y calcula
     * el hipervolumen.
     * Formato: f1,f2 (donde f1=asignaciones, f2=separación)
     * 
     * @return El valor del hipervolumen calculado
     */
    private static double exportParetoFront(List<IntegerSolution> population, String filePath,
            String instanceName, int instanceSize, int populationSize,
            double crossoverProb, double mutationProb) throws IOException {
        new java.io.File("output").mkdirs();

        // Preparar lista de soluciones para el cálculo de hipervolumen
        // Necesitamos transformar los objetivos para que ambos se minimicen
        List<IntegerSolution> solutionsForHV = new ArrayList<>();

        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(filePath))) {
            // Encabezado
            writer.println("f1,f2");

            // Escribir cada solución y preparar para HV
            for (IntegerSolution solution : population) {
                double obj1 = solution.objectives()[0]; // Asignaciones (minimizar)
                double obj2 = -solution.objectives()[1]; // Separación (maximizar, pero almacenada como negativa)
                writer.printf("%.6f,%.6f\n", obj1, obj2);

                // Agregar solución directamente (no necesitamos copia)
                // Los objetivos ya están en formato correcto para HV (ambos minimizan)
                // obj1: asignaciones (minimizar) ✓
                // obj2: -separación (minimizar, porque separación se maximiza) ✓
                solutionsForHV.add(solution);
            }
        }

        // Calcular hipervolumen usando PISAHypervolume
        double hypervolume = 0.0;
        if (!solutionsForHV.isEmpty()) {
            try {
                // Calcular hipervolumen usando valores normalizados
                // Punto de referencia fijo: (1.0, 1.0) en espacio normalizado
                List<Double> referencePoint = new ArrayList<>();
                referencePoint.add(1.0); // refF1
                referencePoint.add(1.0); // refF2

                // Calcular hipervolumen usando implementación manual con normalización
                hypervolume = calculateHypervolume2DNormalized(solutionsForHV, referencePoint);

            } catch (Exception e) {
                System.err.println("Error calculando hipervolumen: " + e.getMessage());
                e.printStackTrace();
                hypervolume = 0.0;
            }
        }

        // Guardar hipervolumen y parámetros en un archivo de estadísticas
        saveHypervolumeStatistics(instanceName, instanceSize, populationSize, crossoverProb, mutationProb,
                hypervolume, filePath);

        return hypervolume;
    }

    /**
     * Calcula el hipervolumen en 2D usando valores normalizados y el método de
     * sweep line.
     * 
     * NORMALIZACIÓN:
     * - Objetivo 1 (separación): Originalmente maximización en [0, 25]
     * → Transformar a minimización: f1_norm = 1.0 - (separación / 25.0)
     * → Como separación está almacenada como negativa: separación =
     * -objectives()[1]
     * → f1_norm = 1.0 + (objectives()[1] / 25.0)
     * 
     * - Objetivo 2 (asignaciones): Minimización en [0, 1200]
     * → Normalizar: f2_norm = objectives()[0] / 1200.0
     * 
     * PUNTO DE REFERENCIA: (1.0, 1.0) en espacio normalizado
     * 
     * ALGORITMO: Sweep line 2D para minimización
     * 1. Filtrar soluciones dentro del punto de referencia (f1_norm <= 1.0 &&
     * f2_norm <= 1.0)
     * 2. Eliminar soluciones dominadas (Pareto)
     * 3. Ordenar por f1_norm ascendente
     * 4. Para cada solución:
     * - width = refF1 - f1_norm
     * - height = prevF2 - f2_norm
     * - hv += width * height
     * - prevF2 = f2_norm
     */
    private static double calculateHypervolume2DNormalized(List<IntegerSolution> solutions,
            List<Double> referencePoint) {
        if (solutions.isEmpty()) {
            return 0.0;
        }

        // Constantes de normalización
        final double MAX_SEPARATION = 25.0; // Máxima separación posible (días)
        final double MAX_ASSIGNMENTS = 1200.0; // Máximo número de asignaciones posible

        double refF1 = referencePoint.get(0); // 1.0
        double refF2 = referencePoint.get(1); // 1.0

        // Paso 1: Normalizar objetivos y filtrar soluciones dentro del punto de
        // referencia
        List<SolutionNormalized> normalizedSolutions = new ArrayList<>();

        for (IntegerSolution sol : solutions) {
            // Obtener valores originales
            double assignments = sol.objectives()[0]; // Asignaciones (minimizar, positivo)
            double separationNeg = sol.objectives()[1]; // Separación almacenada como negativo

            // Calcular separación real (positiva)
            double separation = -separationNeg;

            // Normalizar objetivos
            // f1_norm: separación transformada a minimización [0, 1]
            // separación en [0, 25] → f1_norm = 1.0 - (separación / 25.0)
            double f1_norm = 1.0 - (separation / MAX_SEPARATION);

            // f2_norm: asignaciones normalizadas [0, 1]
            // asignaciones en [0, 1200] → f2_norm = asignaciones / 1200.0
            double f2_norm = assignments / MAX_ASSIGNMENTS;

            // Filtrar soluciones que estén dentro del punto de referencia
            // (f1_norm <= 1.0 && f2_norm <= 1.0)
            if (f1_norm <= refF1 && f2_norm <= refF2) {
                normalizedSolutions.add(new SolutionNormalized(sol, f1_norm, f2_norm));
            }
        }

        if (normalizedSolutions.isEmpty()) {
            return 0.0;
        }

        // Paso 2: Eliminar soluciones dominadas (Pareto)
        List<SolutionNormalized> nonDominated = new ArrayList<>();
        for (int i = 0; i < normalizedSolutions.size(); i++) {
            SolutionNormalized sol1 = normalizedSolutions.get(i);
            boolean isDominated = false;

            for (int j = 0; j < normalizedSolutions.size(); j++) {
                if (i == j)
                    continue;
                SolutionNormalized sol2 = normalizedSolutions.get(j);

                // sol2 domina a sol1 si ambos objetivos de sol2 son mejores (menores)
                // En minimización: menor es mejor
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

        // Paso 3: Ordenar soluciones por f1_norm ascendente
        nonDominated.sort(Comparator.comparingDouble(s -> s.f1_norm));

        // Paso 4: Calcular hipervolumen usando sweep line 2D
        double hv = 0.0;
        double prevF1 = refF1;
        double prevF2 = refF2;

        for (SolutionNormalized sol : nonDominated) {
            double f1 = sol.f1_norm;
            double f2 = sol.f2_norm;

            double width  = prevF1 - f1;
            double height = prevF2 - f2;

            if (width > 0 && height > 0) {
                hv += width * height;
                prevF1 = f1;
                prevF2 = f2;
            }
        }

        // Asegurar que el hipervolumen nunca sea negativo
        return Math.max(0.0, hv);
    }

    /**
     * Clase auxiliar para almacenar soluciones con valores normalizados.
     */
    private static class SolutionNormalized {
        final IntegerSolution solution;
        final double f1_norm; // Separación normalizada (minimización)
        final double f2_norm; // Asignaciones normalizadas (minimización)

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

        // Verificar si el archivo existe para decidir si escribir encabezado
        boolean fileExists = statsFile.exists();

        try (java.io.PrintWriter writer = new java.io.PrintWriter(
                new java.io.FileWriter(statsFilePath, true))) { // append mode

            // Escribir encabezado si el archivo es nuevo
            if (!fileExists) {
                writer.println("INSTANCIA,TAM,POB,CRU,MUT,HV,PARETO_FILE");
            }

            // Escribir datos
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
