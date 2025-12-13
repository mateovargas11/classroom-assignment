package com.university.solver;

import com.university.domain.ProblemInstance;
import com.university.problem.ClassroomAssignmentProblem;
import com.university.problem.ClassroomAssignmentProblem.DecodedAssignment;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.util.*;

/**
 * Algoritmo Greedy puro para asignación de salones.
 * Usa una estrategia best-fit para asignar materias a salones.
 * 
 * Este solver genera soluciones usando la representación basada en slots,
 * donde el orden de los slots determina la prioridad de asignación de horarios.
 */
public class GreedySolver {

    private final ProblemInstance instance;
    private final ClassroomAssignmentProblem problem;

    public GreedySolver(ProblemInstance instance, ClassroomAssignmentProblem problem) {
        this.instance = instance;
        this.problem = problem;
    }

    /**
     * Resuelve el problema de forma puramente greedy.
     * Para cada materia, asigna el salón más pequeño que pueda contener a todos los
     * inscriptos.
     * Si no cabe en un solo salón, usa múltiples salones.
     * Los horarios se asignan automáticamente durante la decodificación (el más
     * temprano disponible).
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

        Map<Integer, DecodedAssignment> assignments = problem.decode(solution);

        for (var entry : assignments.entrySet()) {
            DecodedAssignment assignment = entry.getValue();
            if (!assignment.assigned)
                continue;

            int numClassrooms = assignment.classrooms.size();
            if (numClassrooms == 1) {
                metrics.subjectsWithSingleClassroom++;
            } else if (numClassrooms > 1) {
                metrics.subjectsWithMultipleClassrooms++;
            }
            metrics.totalAssignments += numClassrooms;
        }

        metrics.separationScore = -solution.objectives()[1];
        metrics.isFeasible = solution.constraints()[0] >= 0 && solution.constraints()[1] >= 0;

        return metrics;
    }
}
