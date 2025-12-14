package com.university.problem;

import com.university.domain.ProblemInstance;
import com.university.domain.Subject;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.util.*;

/**
 * Operador de reparación para la representación basada en slots.
 * 
 * Asegura que:
 * 1. Cada examen aparezca exactamente una vez en el vector
 * 2. Cada slot tenga salones válidos con capacidad suficiente
 * 3. No haya salones duplicados dentro de un mismo slot
 */
public class SolutionRepairOperator {

    private final ProblemInstance instance;
    private final ClassroomAssignmentProblem problem;
    private final int numClassrooms;
    private final int numSubjects;

    private final List<Integer> sortedClassroomsByCapacityDesc;
    private final List<Integer> sortedClassroomsByCapacityAsc;

    private final Random random;

    public SolutionRepairOperator(ProblemInstance instance, int maxClassroomsPerSubject) {
        this(instance, maxClassroomsPerSubject, null);
    }

    public SolutionRepairOperator(ProblemInstance instance, int maxClassroomsPerSubject, Long seed) {
        this.random = seed != null ? new Random(seed) : new Random();
        this.instance = instance;
        this.problem = null;
        this.numClassrooms = instance.getClassrooms().size();
        this.numSubjects = instance.getSubjects().size();

        this.sortedClassroomsByCapacityDesc = new ArrayList<>();
        for (int i = 0; i < numClassrooms; i++) {
            sortedClassroomsByCapacityDesc.add(i);
        }
        sortedClassroomsByCapacityDesc.sort((a, b) -> Integer.compare(
                instance.getClassroomByIndex(b).getCapacity(),
                instance.getClassroomByIndex(a).getCapacity()));

        this.sortedClassroomsByCapacityAsc = new ArrayList<>(sortedClassroomsByCapacityDesc);
        Collections.reverse(sortedClassroomsByCapacityAsc);
    }

    public SolutionRepairOperator(ProblemInstance instance, ClassroomAssignmentProblem problem) {
        this(instance, problem, null);
    }

    public SolutionRepairOperator(ProblemInstance instance, ClassroomAssignmentProblem problem, Long seed) {
        this.random = seed != null ? new Random(seed) : new Random();
        this.instance = instance;
        this.problem = problem;
        this.numClassrooms = instance.getClassrooms().size();
        this.numSubjects = instance.getSubjects().size();

        this.sortedClassroomsByCapacityDesc = new ArrayList<>();
        for (int i = 0; i < numClassrooms; i++) {
            sortedClassroomsByCapacityDesc.add(i);
        }
        sortedClassroomsByCapacityDesc.sort((a, b) -> Integer.compare(
                instance.getClassroomByIndex(b).getCapacity(),
                instance.getClassroomByIndex(a).getCapacity()));

        this.sortedClassroomsByCapacityAsc = new ArrayList<>(sortedClassroomsByCapacityDesc);
        Collections.reverse(sortedClassroomsByCapacityAsc);
    }

    public void repair(IntegerSolution solution) {
        ensureUniqueExams(solution);
        repairClassroomsInSlots(solution);
        ensureSufficientCapacity(solution);
        ensureAllExamsCanBeScheduled(solution);
    }

    /**
     * Asegura que cada examen aparezca exactamente una vez en el vector.
     * Si un examen aparece múltiples veces, se mantiene solo la primera ocurrencia.
     * Si un examen no aparece, se asigna a un slot vacío.
     */
    private void ensureUniqueExams(IntegerSolution solution) {
        int numSlots = numSubjects;
        int emptyExamIndex = numSubjects;

        Map<Integer, Integer> examToSlot = new HashMap<>();
        Set<Integer> duplicateSlots = new HashSet<>();

        for (int slot = 0; slot < numSlots; slot++) {
            int examIdx = getExamIndex(solution, slot);

            if (examIdx >= 0 && examIdx < numSubjects) {
                if (examToSlot.containsKey(examIdx)) {
                    duplicateSlots.add(slot);
                } else {
                    examToSlot.put(examIdx, slot);
                }
            }
        }

        for (int slot : duplicateSlots) {
            setExamIndex(solution, slot, emptyExamIndex);
        }

        List<Integer> missingExams = new ArrayList<>();
        for (int i = 0; i < numSubjects; i++) {
            if (!examToSlot.containsKey(i)) {
                missingExams.add(i);
            }
        }

        List<Integer> emptySlots = new ArrayList<>();
        for (int slot = 0; slot < numSlots; slot++) {
            int examIdx = getExamIndex(solution, slot);
            if (examIdx == emptyExamIndex || examIdx < 0 || examIdx >= numSubjects) {
                emptySlots.add(slot);
            }
        }

        for (int i = 0; i < missingExams.size() && i < emptySlots.size(); i++) {
            int examIdx = missingExams.get(i);
            int slot = emptySlots.get(i);
            setExamIndex(solution, slot, examIdx);

            assignClassroomsForExam(solution, slot, examIdx);
        }
    }

    /**
     * Repara los salones dentro de cada slot:
     * - Elimina salones duplicados
     * - Valida que los índices estén en rango
     */
    private void repairClassroomsInSlots(IntegerSolution solution) {
        int numSlots = numSubjects;
        int noClassroomIndex = numClassrooms;

        for (int slot = 0; slot < numSlots; slot++) {
            int examIdx = getExamIndex(solution, slot);

            if (examIdx == numSubjects || examIdx < 0 || examIdx >= numSubjects) {
                for (int i = 0; i < ClassroomAssignmentProblem.MAX_CLASSROOMS_PER_SLOT; i++) {
                    setClassroomIndex(solution, slot, i, noClassroomIndex);
                }
                continue;
            }

            Set<Integer> usedClassrooms = new LinkedHashSet<>();
            List<Integer> validClassrooms = new ArrayList<>();

            for (int i = 0; i < ClassroomAssignmentProblem.MAX_CLASSROOMS_PER_SLOT; i++) {
                int classroomIdx = getClassroomIndex(solution, slot, i);

                if (classroomIdx >= 0 && classroomIdx < numClassrooms &&
                        !usedClassrooms.contains(classroomIdx)) {
                    usedClassrooms.add(classroomIdx);
                    validClassrooms.add(classroomIdx);
                }
            }

            for (int i = 0; i < ClassroomAssignmentProblem.MAX_CLASSROOMS_PER_SLOT; i++) {
                if (i < validClassrooms.size()) {
                    setClassroomIndex(solution, slot, i, validClassrooms.get(i));
                } else {
                    setClassroomIndex(solution, slot, i, noClassroomIndex);
                }
            }
        }
    }

    private void ensureSufficientCapacity(IntegerSolution solution) {
        int numSlots = numSubjects;

        for (int slot = 0; slot < numSlots; slot++) {
            int examIdx = getExamIndex(solution, slot);

            if (examIdx < 0 || examIdx >= numSubjects)
                continue;

            Subject subject = instance.getSubjectByIndex(examIdx);
            int enrolled = subject.getEnrolledStudents();

            List<Integer> currentClassrooms = new ArrayList<>();
            int currentCapacity = 0;

            for (int i = 0; i < ClassroomAssignmentProblem.MAX_CLASSROOMS_PER_SLOT; i++) {
                int classroomIdx = getClassroomIndex(solution, slot, i);
                if (classroomIdx >= 0 && classroomIdx < numClassrooms) {
                    currentClassrooms.add(classroomIdx);
                    currentCapacity += instance.getClassroomByIndex(classroomIdx).getCapacity();
                }
            }

            if (currentCapacity < enrolled) {
                Set<Integer> usedClassrooms = new HashSet<>(currentClassrooms);

                for (int classroomIdx : sortedClassroomsByCapacityDesc) {
                    if (currentCapacity >= enrolled)
                        break;
                    if (usedClassrooms.contains(classroomIdx))
                        continue;
                    if (currentClassrooms.size() >= ClassroomAssignmentProblem.MAX_CLASSROOMS_PER_SLOT)
                        break;

                    currentClassrooms.add(classroomIdx);
                    usedClassrooms.add(classroomIdx);
                    currentCapacity += instance.getClassroomByIndex(classroomIdx).getCapacity();
                }

                for (int i = 0; i < ClassroomAssignmentProblem.MAX_CLASSROOMS_PER_SLOT; i++) {
                    if (i < currentClassrooms.size()) {
                        setClassroomIndex(solution, slot, i, currentClassrooms.get(i));
                    } else {
                        setClassroomIndex(solution, slot, i, numClassrooms);
                    }
                }
            }
        }
    }

    private void assignClassroomsForExam(IntegerSolution solution, int slot, int examIdx) {
        Subject subject = instance.getSubjectByIndex(examIdx);
        int enrolled = subject.getEnrolledStudents();

        List<Integer> classroomsToUse = findClassroomsForCapacity(enrolled);

        for (int i = 0; i < ClassroomAssignmentProblem.MAX_CLASSROOMS_PER_SLOT; i++) {
            if (i < classroomsToUse.size()) {
                setClassroomIndex(solution, slot, i, classroomsToUse.get(i));
            } else {
                setClassroomIndex(solution, slot, i, numClassrooms);
            }
        }
    }

    private List<Integer> findClassroomsForCapacity(int enrolled) {
        List<Integer> result = new ArrayList<>();
        int capacityAccumulated = 0;

        for (int classroomIdx : sortedClassroomsByCapacityAsc) {
            int cap = instance.getClassroomByIndex(classroomIdx).getCapacity();
            if (cap >= enrolled) {
                result.add(classroomIdx);
                return result;
            }
        }

        for (int classroomIdx : sortedClassroomsByCapacityDesc) {
            if (capacityAccumulated >= enrolled)
                break;
            result.add(classroomIdx);
            capacityAccumulated += instance.getClassroomByIndex(classroomIdx).getCapacity();
        }

        return result;
    }

    private void ensureAllExamsCanBeScheduled(IntegerSolution solution) {
        int numSlots = numSubjects;

        List<SlotInfo> slotInfos = new ArrayList<>();
        for (int slot = 0; slot < numSlots; slot++) {
            int examIdx = getExamIndex(solution, slot);
            if (examIdx < 0 || examIdx >= numSubjects) {
                continue;
            }

            List<Integer> classrooms = new ArrayList<>();
            for (int i = 0; i < ClassroomAssignmentProblem.MAX_CLASSROOMS_PER_SLOT; i++) {
                int classroomIdx = getClassroomIndex(solution, slot, i);
                if (classroomIdx >= 0 && classroomIdx < numClassrooms) {
                    classrooms.add(classroomIdx);
                }
            }

            if (!classrooms.isEmpty()) {
                Subject subject = instance.getSubjectByIndex(examIdx);
                int duration = subject.getDurationBlocks();
                int enrolled = subject.getEnrolledStudents();

                double difficulty = classrooms.size() * 100.0 + duration * 10.0 + enrolled;
                slotInfos.add(new SlotInfo(slot, examIdx, classrooms, difficulty));
            }
        }

        slotInfos.sort((a, b) -> Double.compare(b.difficulty, a.difficulty));

        int[] newSlotOrder = new int[numSlots];
        for (int i = 0; i < numSlots; i++) {
            newSlotOrder[i] = -1;
        }

        for (int i = 0; i < slotInfos.size(); i++) {
            newSlotOrder[i] = slotInfos.get(i).slot;
        }

        int nextPos = slotInfos.size();
        for (int slot = 0; slot < numSlots; slot++) {
            boolean alreadyAssigned = false;
            for (SlotInfo info : slotInfos) {
                if (info.slot == slot) {
                    alreadyAssigned = true;
                    break;
                }
            }
            if (!alreadyAssigned && nextPos < numSlots) {
                newSlotOrder[nextPos++] = slot;
            }
        }

        List<Integer> currentState = new ArrayList<>(solution.variables());

        for (int newSlot = 0; newSlot < numSlots; newSlot++) {
            int oldSlot = newSlotOrder[newSlot];
            if (oldSlot >= 0 && oldSlot < numSlots) {
                for (int i = 0; i < ClassroomAssignmentProblem.SLOT_SIZE; i++) {
                    int oldPos = oldSlot * ClassroomAssignmentProblem.SLOT_SIZE + i;
                    int newPos = newSlot * ClassroomAssignmentProblem.SLOT_SIZE + i;
                    if (oldPos < currentState.size() && newPos < solution.variables().size()) {
                        solution.variables().set(newPos, currentState.get(oldPos));
                    }
                }
            } else {
                setExamIndex(solution, newSlot, numSubjects);
                for (int i = 0; i < ClassroomAssignmentProblem.MAX_CLASSROOMS_PER_SLOT; i++) {
                    setClassroomIndex(solution, newSlot, i, numClassrooms);
                }
            }
        }
    }

    /**
     * Información de un slot para reordenamiento.
     */
    private static class SlotInfo {
        int slot;
        int examIdx;
        List<Integer> classrooms;
        double difficulty;

        SlotInfo(int slot, int examIdx, List<Integer> classrooms, double difficulty) {
            this.slot = slot;
            this.examIdx = examIdx;
            this.classrooms = classrooms;
            this.difficulty = difficulty;
        }
    }

    private int getExamIndex(IntegerSolution solution, int slot) {
        return solution.variables().get(slot * ClassroomAssignmentProblem.SLOT_SIZE);
    }

    private void setExamIndex(IntegerSolution solution, int slot, int examIndex) {
        solution.variables().set(slot * ClassroomAssignmentProblem.SLOT_SIZE, examIndex);
    }

    private int getClassroomIndex(IntegerSolution solution, int slot, int classroomPos) {
        return solution.variables().get(slot * ClassroomAssignmentProblem.SLOT_SIZE + 2 + classroomPos);
    }

    private void setClassroomIndex(IntegerSolution solution, int slot, int classroomPos, int classroomIndex) {
        solution.variables().set(slot * ClassroomAssignmentProblem.SLOT_SIZE + 2 + classroomPos, classroomIndex);
    }
}
