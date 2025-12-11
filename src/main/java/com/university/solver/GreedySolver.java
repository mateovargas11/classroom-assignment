package com.university.solver;

import com.university.domain.ProblemInstance;
import com.university.domain.Subject;
import com.university.problem.ClassroomAssignmentProblem;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.util.*;

/**
 * Algoritmo Greedy puro para asignación de salones.
 * Usa una estrategia best-fit para asignar materias a salones.
 */
public class GreedySolver {

    private final ProblemInstance instance;
    private final ClassroomAssignmentProblem problem;
    private final int maxClassroomsPerSubject;
    private final List<Integer> sortedClassroomsByCapacityAsc;
    private final List<Integer> sortedClassroomsByCapacityDesc;

    public GreedySolver(ProblemInstance instance, ClassroomAssignmentProblem problem) {
        this.instance = instance;
        this.problem = problem;
        this.maxClassroomsPerSubject = problem.getMaxClassroomsPerSubject();

        // Ordenar salones por capacidad ascendente (para best-fit)
        this.sortedClassroomsByCapacityAsc = new ArrayList<>();
        for (int i = 0; i < instance.getClassrooms().size(); i++) {
            sortedClassroomsByCapacityAsc.add(i);
        }
        sortedClassroomsByCapacityAsc.sort((a, b) -> Integer.compare(
                instance.getClassroomByIndex(a).getCapacity(),
                instance.getClassroomByIndex(b).getCapacity()));

        this.sortedClassroomsByCapacityDesc = new ArrayList<>(sortedClassroomsByCapacityAsc);
        Collections.reverse(sortedClassroomsByCapacityDesc);
    }

    /**
     * Resuelve el problema de forma puramente greedy.
     * Para cada materia, asigna el salón más pequeño que pueda contener a todos los
     * inscriptos.
     * Si no cabe en un solo salón, usa múltiples salones grandes.
     */
    public IntegerSolution solve() {
        IntegerSolution solution = problem.createSolution();

        // Limpiar la solución
        for (int i = 0; i < solution.variables().size(); i++) {
            solution.variables().set(i, -1);
        }

        // Ordenar materias por inscriptos descendente (asignar las más grandes primero)
        List<Integer> subjectOrder = new ArrayList<>();
        for (int i = 0; i < instance.getSubjects().size(); i++) {
            subjectOrder.add(i);
        }
        subjectOrder.sort((a, b) -> Integer.compare(
                instance.getSubjectByIndex(b).getEnrolledStudents(),
                instance.getSubjectByIndex(a).getEnrolledStudents()));

        // Asignar cada materia de forma greedy
        for (int subjectIdx : subjectOrder) {
            assignSubjectGreedy(solution, subjectIdx);
        }

        problem.evaluate(solution);
        return solution;
    }

    private void assignSubjectGreedy(IntegerSolution solution, int subjectIdx) {
        Subject subject = instance.getSubjectByIndex(subjectIdx);
        int enrolled = subject.getEnrolledStudents();
        int basePos = subjectIdx * maxClassroomsPerSubject;

        // Estrategia best-fit: buscar el salón más pequeño que pueda contener a todos
        int bestFitClassroom = findBestFitClassroom(enrolled);

        if (bestFitClassroom >= 0) {
            // Un solo salón es suficiente
            solution.variables().set(basePos, bestFitClassroom);
        } else {
            // Necesita múltiples salones - usar los más grandes
            int capacityAccumulated = 0;
            int slot = 0;

            for (int classroomIdx : sortedClassroomsByCapacityDesc) {
                if (capacityAccumulated >= enrolled || slot >= maxClassroomsPerSubject) {
                    break;
                }

                int cap = instance.getClassroomByIndex(classroomIdx).getCapacity();
                if (cap > 0) {
                    solution.variables().set(basePos + slot, classroomIdx);
                    capacityAccumulated += cap;
                    slot++;
                }
            }
        }
    }

    /**
     * Encuentra el salón más pequeño que pueda contener la cantidad de inscriptos.
     */
    private int findBestFitClassroom(int enrolled) {
        for (int classroomIdx : sortedClassroomsByCapacityAsc) {
            int cap = instance.getClassroomByIndex(classroomIdx).getCapacity();
            if (cap >= enrolled) {
                return classroomIdx;
            }
        }
        return -1; // No hay un solo salón suficiente
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

        Map<Integer, Set<Integer>> assignments = problem.decodeAssignments(solution);

        for (Map.Entry<Integer, Set<Integer>> entry : assignments.entrySet()) {
            Set<Integer> classrooms = entry.getValue();
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
