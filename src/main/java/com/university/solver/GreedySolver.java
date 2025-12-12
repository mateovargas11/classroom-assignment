package com.university.solver;

import com.university.domain.ProblemInstance;
import com.university.domain.Subject;
import com.university.problem.ClassroomAssignmentProblem;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.util.*;

/**
 * Algoritmo Greedy puro para asignación de salones.
 * Usa una estrategia best-fit para asignar materias a salones.
 * 
 * Este solver genera soluciones usando el modelo de matriz directa,
 * donde NSGA-II decide tanto los salones como los días.
 */
public class GreedySolver {

    private final ProblemInstance instance;
    private final ClassroomAssignmentProblem problem;
    private final int numClassrooms;
    private final int numSubjects;
    private final int emptySubjectIndex;

    private final List<Integer> sortedClassroomsByCapacityAsc;
    private final List<Integer> sortedClassroomsByCapacityDesc;
    private final int[] blocksPerSubject;

    public GreedySolver(ProblemInstance instance, ClassroomAssignmentProblem problem) {
        this.instance = instance;
        this.problem = problem;
        this.numClassrooms = instance.getClassrooms().size();
        this.numSubjects = instance.getSubjects().size();
        this.emptySubjectIndex = instance.getEmptySubjectIndex();

        // Ordenar salones por capacidad ascendente (para best-fit)
        this.sortedClassroomsByCapacityAsc = new ArrayList<>();
        for (int i = 0; i < numClassrooms; i++) {
            sortedClassroomsByCapacityAsc.add(i);
        }
        sortedClassroomsByCapacityAsc.sort((a, b) -> Integer.compare(
                instance.getClassroomByIndex(a).getCapacity(),
                instance.getClassroomByIndex(b).getCapacity()));

        this.sortedClassroomsByCapacityDesc = new ArrayList<>(sortedClassroomsByCapacityAsc);
        Collections.reverse(sortedClassroomsByCapacityDesc);

        // Calcular slots por materia (1 hora = 1 slot)
        this.blocksPerSubject = new int[numSubjects];
        for (int i = 0; i < numSubjects; i++) {
            blocksPerSubject[i] = instance.getSubjects().get(i).getDurationSlots();
        }
    }

    /**
     * Resuelve el problema de forma puramente greedy.
     * Para cada materia, asigna el salón más pequeño que pueda contener a todos los
     * inscriptos.
     * Si no cabe en un solo salón, usa múltiples salones grandes.
     * Los días se asignan secuencialmente (primer día disponible donde quepan todos
     * los salones).
     */
    public IntegerSolution solve() {
        // Usar la solución greedy del problema
        IntegerSolution solution = problem.createGreedySolution();
        problem.evaluate(solution);
        return solution;
    }

    /**
     * Calcula métricas de la solución greedy para reporte.
     */
    public static class GreedyMetrics {
        public int totalAssignments;
        public int subjectsWithSingleClassroom;
        public int subjectsWithMultipleClassrooms;
        public double separationScore;
        public boolean isFeasible;

        @Override
        public String toString() {
            return String.format(
                    "Asignaciones totales: %d\n" +
                            "Materias con 1 salón: %d\n" +
                            "Materias con múltiples salones: %d\n" +
                            "Separación promedio: %.2f días\n" +
                            "Factible: %s",
                    totalAssignments, subjectsWithSingleClassroom,
                    subjectsWithMultipleClassrooms, separationScore,
                    isFeasible ? "Sí" : "No");
        }
    }

    public GreedyMetrics calculateMetrics(IntegerSolution solution) {
        GreedyMetrics metrics = new GreedyMetrics();

        Map<Integer, ClassroomAssignmentProblem.SubjectAssignment> assignments = problem.decodeAssignments(solution);

        for (Map.Entry<Integer, ClassroomAssignmentProblem.SubjectAssignment> entry : assignments.entrySet()) {
            Set<Integer> classrooms = entry.getValue().classrooms;
            if (classrooms.size() == 1) {
                metrics.subjectsWithSingleClassroom++;
            } else if (classrooms.size() > 1) {
                metrics.subjectsWithMultipleClassrooms++;
            }
            metrics.totalAssignments += classrooms.size();
        }

        metrics.separationScore = -solution.objectives()[1];
        metrics.isFeasible = solution.constraints()[0] >= 0 && solution.constraints()[1] >= 0;

        return metrics;
    }
}
