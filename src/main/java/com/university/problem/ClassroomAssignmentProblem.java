package com.university.problem;

import com.university.domain.ProblemInstance;
import com.university.domain.Subject;
import org.uma.jmetal.problem.integerproblem.impl.AbstractIntegerProblem;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.util.*;

public class ClassroomAssignmentProblem extends AbstractIntegerProblem {

    private final ProblemInstance instance;

    // Constantes del modelo
    public static final int BLOCKS_PER_DAY = 26;
    public static final int MAX_CLASSROOMS_PER_SLOT = 4;
    public static final int SLOT_SIZE = 1 + 1 + MAX_CLASSROOMS_PER_SLOT;

    private final int numClassrooms;
    private final int numSubjects;
    private final int numSlots;
    private final int emptyExamIndex;
    private final int noClassroomIndex;
    private final int vectorSize;

    private final List<Integer> sortedClassroomsByCapacityDesc;
    private final List<Integer> sortedClassroomsByCapacityAsc;

    private final int[] blocksPerSubject;

    private final int[] minClassroomsPerSubject;

    private final Random random;

    public ClassroomAssignmentProblem(ProblemInstance instance) {
        this(instance, null);
    }

    public ClassroomAssignmentProblem(ProblemInstance instance, Long seed) {
        this.random = seed != null ? new Random(seed) : new Random();
        this.instance = instance;
        this.numClassrooms = instance.getClassrooms().size();
        this.numSubjects = instance.getSubjects().size();
        this.numSlots = numSubjects;
        this.emptyExamIndex = numSubjects;
        this.noClassroomIndex = numClassrooms;
        this.vectorSize = numSlots * SLOT_SIZE;

        this.sortedClassroomsByCapacityDesc = new ArrayList<>();
        for (int i = 0; i < numClassrooms; i++) {
            sortedClassroomsByCapacityDesc.add(i);
        }
        sortedClassroomsByCapacityDesc.sort((a, b) -> Integer.compare(
                instance.getClassroomByIndex(b).getCapacity(),
                instance.getClassroomByIndex(a).getCapacity()));

        this.sortedClassroomsByCapacityAsc = new ArrayList<>(sortedClassroomsByCapacityDesc);
        Collections.reverse(sortedClassroomsByCapacityAsc);

        this.blocksPerSubject = new int[numSubjects];
        for (int i = 0; i < numSubjects; i++) {
            blocksPerSubject[i] = instance.getSubjects().get(i).getDurationBlocks();
        }

        this.minClassroomsPerSubject = calculateMinClassroomsPerSubject();

        numberOfObjectives(2);
        numberOfConstraints(2);
        name("ClassroomAssignmentProblem");

        List<Integer> lowerBounds = new ArrayList<>(vectorSize);
        List<Integer> upperBounds = new ArrayList<>(vectorSize);

        for (int slot = 0; slot < numSlots; slot++) {
            lowerBounds.add(0);
            upperBounds.add(numSubjects);

            lowerBounds.add(0);
            upperBounds.add(ProblemInstance.MAX_DAYS - 1);

            for (int s = 0; s < MAX_CLASSROOMS_PER_SLOT; s++) {
                lowerBounds.add(0);
                upperBounds.add(numClassrooms);
            }
        }

        variableBounds(lowerBounds, upperBounds);
    }

    private int[] calculateMinClassroomsPerSubject() {
        int[] minClassrooms = new int[numSubjects];

        for (int i = 0; i < numSubjects; i++) {
            Subject subject = instance.getSubjects().get(i);
            int enrolled = subject.getEnrolledStudents();

            int classroomsNeeded = 0;
            int capacityAccumulated = 0;

            for (int classroomIdx : sortedClassroomsByCapacityDesc) {
                if (capacityAccumulated >= enrolled)
                    break;
                capacityAccumulated += instance.getClassroomByIndex(classroomIdx).getCapacity();
                classroomsNeeded++;
            }

            minClassrooms[i] = Math.max(1, classroomsNeeded);
        }

        return minClassrooms;
    }

    public int getExamIndex(IntegerSolution solution, int slot) {
        return solution.variables().get(slot * SLOT_SIZE);
    }


    public void setExamIndex(IntegerSolution solution, int slot, int examIndex) {
        solution.variables().set(slot * SLOT_SIZE, examIndex);
    }

    public int getDayIndex(IntegerSolution solution, int slot) {
        return solution.variables().get(slot * SLOT_SIZE + 1);
    }

    public void setDayIndex(IntegerSolution solution, int slot, int dayIndex) {
        solution.variables().set(slot * SLOT_SIZE + 1, dayIndex);
    }

    public int getClassroomIndex(IntegerSolution solution, int slot, int classroomPos) {
        return solution.variables().get(slot * SLOT_SIZE + 2 + classroomPos);
    }

    public void setClassroomIndex(IntegerSolution solution, int slot, int classroomPos, int classroomIndex) {
        solution.variables().set(slot * SLOT_SIZE + 2 + classroomPos, classroomIndex);
    }

    public List<Integer> getValidClassrooms(IntegerSolution solution, int slot) {
        List<Integer> classrooms = new ArrayList<>();
        for (int i = 0; i < MAX_CLASSROOMS_PER_SLOT; i++) {
            int classroomIdx = getClassroomIndex(solution, slot, i);
            if (classroomIdx < numClassrooms) {
                classrooms.add(classroomIdx);
            }
        }
        return classrooms;
    }


    @Override
    public IntegerSolution createSolution() {
        double rand = random.nextDouble();

        if (rand < 0.50) {
            return createGreedySolution();
        } else if (rand < 0.80) {
            return createGreedyWithNoise();
        } else {
            return createRandomSolution();
        }
    }

    /**
     * Crea una solución greedy: asigna cada examen a los mejores salones.
     */
    public IntegerSolution createGreedySolution() {
        IntegerSolution solution = initializeEmptySolution();

        List<Integer> examOrder = new ArrayList<>();
        for (int i = 0; i < numSubjects; i++) {
            examOrder.add(i);
        }
        examOrder.sort((a, b) -> Integer.compare(
                instance.getSubjectByIndex(b).getEnrolledStudents(),
                instance.getSubjectByIndex(a).getEnrolledStudents()));

        for (int slotIdx = 0; slotIdx < numSubjects; slotIdx++) {
            int examIdx = examOrder.get(slotIdx);
            Subject subject = instance.getSubjectByIndex(examIdx);
            int enrolled = subject.getEnrolledStudents();

            setExamIndex(solution, slotIdx, examIdx);

            int day = slotIdx % ProblemInstance.MAX_DAYS;
            setDayIndex(solution, slotIdx, day);

            List<Integer> classroomsToUse = findClassroomsForCapacity(enrolled, Collections.emptySet());

            if (classroomsToUse.isEmpty()) {
                for (int i = 0; i < Math.min(MAX_CLASSROOMS_PER_SLOT, sortedClassroomsByCapacityDesc.size()); i++) {
                    classroomsToUse.add(sortedClassroomsByCapacityDesc.get(i));
                }
            }

            for (int i = 0; i < MAX_CLASSROOMS_PER_SLOT; i++) {
                if (i < classroomsToUse.size()) {
                    int classroomIdx = classroomsToUse.get(i);
                    setClassroomIndex(solution, slotIdx, i, classroomIdx);
                } else {
                    setClassroomIndex(solution, slotIdx, i, noClassroomIndex); // "-"
                }
            }
        }

        return solution;
    }

    /**
     * Encuentra los salones necesarios para cubrir la capacidad requerida.
     */
    private List<Integer> findClassroomsForCapacity(int enrolled, Set<Integer> usedClassrooms) {
        List<Integer> result = new ArrayList<>();
        int capacityAccumulated = 0;

        for (int classroomIdx : sortedClassroomsByCapacityAsc) {
            if (usedClassrooms.contains(classroomIdx))
                continue;
            int cap = instance.getClassroomByIndex(classroomIdx).getCapacity();
            if (cap >= enrolled) {
                result.add(classroomIdx);
                return result;
            }
        }

        for (int classroomIdx : sortedClassroomsByCapacityDesc) {
            if (capacityAccumulated >= enrolled)
                break;
            if (usedClassrooms.contains(classroomIdx))
                continue;

            result.add(classroomIdx);
            capacityAccumulated += instance.getClassroomByIndex(classroomIdx).getCapacity();
        }

        return result;
    }

    /**
     * Crea una solución greedy con mutaciones aleatorias.
     */
    private IntegerSolution createGreedyWithNoise() {
        IntegerSolution solution = createGreedySolution();

        for (int i = 0; i < numSlots / 10; i++) {
            int slot1 = random.nextInt(numSlots);
            int slot2 = random.nextInt(numSlots);
            swapSlots(solution, slot1, slot2);
        }

        for (int slot = 0; slot < numSlots; slot++) {
            if (random.nextDouble() < 0.10) {
                int pos = random.nextInt(MAX_CLASSROOMS_PER_SLOT);
                int newClassroom = random.nextInt(numClassrooms + 1);
                setClassroomIndex(solution, slot, pos, newClassroom);
            }
        }

        return solution;
    }

    /**
     * Intercambia dos slots completos.
     */
    private void swapSlots(IntegerSolution solution, int slot1, int slot2) {
        for (int i = 0; i < SLOT_SIZE; i++) {
            int pos1 = slot1 * SLOT_SIZE + i;
            int pos2 = slot2 * SLOT_SIZE + i;
            int temp = solution.variables().get(pos1);
            solution.variables().set(pos1, solution.variables().get(pos2));
            solution.variables().set(pos2, temp);
        }
    }

    /**
     * Crea una solución aleatoria pero válida.
     */
    private IntegerSolution createRandomSolution() {
        IntegerSolution solution = initializeEmptySolution();

        // Mezclar orden de exámenes
        List<Integer> examOrder = new ArrayList<>();
        for (int i = 0; i < numSubjects; i++) {
            examOrder.add(i);
        }
        Collections.shuffle(examOrder, random);

        for (int slotIdx = 0; slotIdx < numSubjects; slotIdx++) {
            int examIdx = examOrder.get(slotIdx);
            setExamIndex(solution, slotIdx, examIdx);

            int day = random.nextInt(ProblemInstance.MAX_DAYS);
            setDayIndex(solution, slotIdx, day);

            Set<Integer> usedInSlot = new HashSet<>();
            int enrolled = instance.getSubjectByIndex(examIdx).getEnrolledStudents();
            int capacityNeeded = enrolled;

            for (int i = 0; i < MAX_CLASSROOMS_PER_SLOT; i++) {
                if (capacityNeeded <= 0 || random.nextDouble() < 0.3) {
                    setClassroomIndex(solution, slotIdx, i, noClassroomIndex);
                } else {
                    int classroomIdx;
                    int attempts = 0;
                    do {
                        classroomIdx = random.nextInt(numClassrooms);
                        attempts++;
                    } while (usedInSlot.contains(classroomIdx) && attempts < 10);

                    if (!usedInSlot.contains(classroomIdx)) {
                        setClassroomIndex(solution, slotIdx, i, classroomIdx);
                        usedInSlot.add(classroomIdx);
                        capacityNeeded -= instance.getClassroomByIndex(classroomIdx).getCapacity();
                    } else {
                        setClassroomIndex(solution, slotIdx, i, noClassroomIndex);
                    }
                }
            }
        }

        return solution;
    }

    /**
     * Inicializa una solución con todos los slots vacíos.
     */
    private IntegerSolution initializeEmptySolution() {
        IntegerSolution solution = super.createSolution();
        for (int slot = 0; slot < numSlots; slot++) {
            setExamIndex(solution, slot, emptyExamIndex);
            setDayIndex(solution, slot, 0);
            for (int i = 0; i < MAX_CLASSROOMS_PER_SLOT; i++) {
                setClassroomIndex(solution, slot, i, noClassroomIndex);
            }
        }
        return solution;
    }

    /**
     * Resultado de la decodificación: asignación de cada examen a un horario.
     */
    public static class DecodedAssignment {
        public int examIndex;
        public int day;
        public int startBlock;
        public List<Integer> classrooms;
        public int totalCapacity;
        public boolean assigned;

        public DecodedAssignment(int examIndex) {
            this.examIndex = examIndex;
            this.day = -1;
            this.startBlock = -1;
            this.classrooms = new ArrayList<>();
            this.totalCapacity = 0;
            this.assigned = false;
        }
    }

    /**
     * Decodifica la solución asignando horarios automáticamente.
     * 
     * Para cada slot en orden:
     * 1. Si el examen es V, ignorar
     * 2. Obtener salones válidos
     * 3. Buscar el horario más temprano donde TODOS los salones estén libres
     * 4. Asignar el examen
     * 
     */
    public Map<Integer, DecodedAssignment> decode(IntegerSolution solution) {
        Map<Integer, DecodedAssignment> assignments = new HashMap<>();

        for (int i = 0; i < numSubjects; i++) {
            assignments.put(i, new DecodedAssignment(i));
        }

        boolean[][][] occupied = new boolean[ProblemInstance.MAX_DAYS][BLOCKS_PER_DAY][numClassrooms];

        Set<Integer> assignedExams = new HashSet<>();

        for (int slot = 0; slot < numSlots; slot++) {
            int examIdx = getExamIndex(solution, slot);

            if (examIdx == emptyExamIndex || examIdx < 0 || examIdx >= numSubjects) {
                continue;
            }
            if (assignedExams.contains(examIdx)) {
                continue;
            }

            List<Integer> classrooms = getValidClassrooms(solution, slot);
            if (classrooms.isEmpty()) {
                continue;
            }

            classrooms = new ArrayList<>(new LinkedHashSet<>(classrooms));

            int durationBlocks = blocksPerSubject[examIdx];
            int enrolled = instance.getSubjectByIndex(examIdx).getEnrolledStudents();

            int preferredDay = getDayIndex(solution, slot);

            int[] bestTimeSlot = findEarliestAvailableTimeSlotInDay(occupied, classrooms, durationBlocks, preferredDay);

            if (bestTimeSlot == null && classrooms.size() > 1) {
                int originalCapacity = 0;
                for (int classroomIdx : classrooms) {
                    originalCapacity += instance.getClassroomByIndex(classroomIdx).getCapacity();
                }

                if (originalCapacity >= enrolled) {
                    List<Integer> sortedClassrooms = new ArrayList<>(classrooms);
                    sortedClassrooms.sort((a, b) -> Integer.compare(
                            instance.getClassroomByIndex(b).getCapacity(),
                            instance.getClassroomByIndex(a).getCapacity()));

                    for (int subsetSize = sortedClassrooms.size() - 1; subsetSize >= 1; subsetSize--) {
                        List<Integer> subset = new ArrayList<>();
                        int subsetCapacity = 0;

                        for (int i = 0; i < subsetSize; i++) {
                            subset.add(sortedClassrooms.get(i));
                            subsetCapacity += instance.getClassroomByIndex(sortedClassrooms.get(i)).getCapacity();
                        }

                        if (subsetCapacity >= enrolled) {
                            bestTimeSlot = findEarliestAvailableTimeSlotInDay(occupied, subset, durationBlocks,
                                    preferredDay);
                            if (bestTimeSlot != null) {
                                classrooms = subset;
                                break;
                            }
                        }
                    }
                }
            }

            if (bestTimeSlot == null) {
                for (int offset = 1; offset < ProblemInstance.MAX_DAYS; offset++) {
                    int day = (preferredDay + offset) % ProblemInstance.MAX_DAYS;
                    bestTimeSlot = findEarliestAvailableTimeSlotInDay(occupied, classrooms, durationBlocks, day);
                    if (bestTimeSlot != null) {
                        break;
                    }

                    day = (preferredDay - offset + ProblemInstance.MAX_DAYS) % ProblemInstance.MAX_DAYS;
                    bestTimeSlot = findEarliestAvailableTimeSlotInDay(occupied, classrooms, durationBlocks, day);
                    if (bestTimeSlot != null) {
                        break;
                    }
                }
            }

            if (bestTimeSlot == null) {
                List<Integer> dayOrder = getDaysOrderedByOccupancy(occupied);
                for (int day : dayOrder) {
                    for (int startBlock = 0; startBlock <= BLOCKS_PER_DAY - durationBlocks; startBlock++) {
                        for (int classroomIdx = 0; classroomIdx < numClassrooms; classroomIdx++) {
                            boolean isFree = true;
                            for (int b = 0; b < durationBlocks; b++) {
                                if (occupied[day][startBlock + b][classroomIdx]) {
                                    isFree = false;
                                    break;
                                }
                            }

                            if (isFree) {
                                int cap = instance.getClassroomByIndex(classroomIdx).getCapacity();
                                if (cap >= enrolled) {
                                    bestTimeSlot = new int[] { day, startBlock };
                                    classrooms = new ArrayList<>();
                                    classrooms.add(classroomIdx);
                                    break;
                                }
                            }
                        }
                        if (bestTimeSlot != null)
                            break;
                    }
                    if (bestTimeSlot != null)
                        break;
                }
            }

            if (bestTimeSlot != null) {
                int day = bestTimeSlot[0];
                int startBlock = bestTimeSlot[1];

                for (int classroomIdx : classrooms) {
                    for (int b = 0; b < durationBlocks; b++) {
                        occupied[day][startBlock + b][classroomIdx] = true;
                    }
                }

                DecodedAssignment assignment = assignments.get(examIdx);
                assignment.day = day;
                assignment.startBlock = startBlock;
                assignment.classrooms = new ArrayList<>(classrooms);
                assignment.assigned = true;

                for (int classroomIdx : classrooms) {
                    assignment.totalCapacity += instance.getClassroomByIndex(classroomIdx).getCapacity();
                }

                assignedExams.add(examIdx);
            }
        }

        return assignments;
    }

    /**
     * Calcula el orden de días según su ocupación.
     */
    private List<Integer> getDaysOrderedByOccupancy(boolean[][][] occupied) {
        int[] dayOccupancy = new int[ProblemInstance.MAX_DAYS];
        for (int day = 0; day < ProblemInstance.MAX_DAYS; day++) {
            for (int block = 0; block < BLOCKS_PER_DAY; block++) {
                for (int classroomIdx = 0; classroomIdx < numClassrooms; classroomIdx++) {
                    if (occupied[day][block][classroomIdx]) {
                        dayOccupancy[day]++;
                    }
                }
            }
        }

        List<Integer> dayOrder = new ArrayList<>();
        for (int i = 0; i < ProblemInstance.MAX_DAYS; i++) {
            dayOrder.add(i);
        }
        dayOrder.sort((a, b) -> Integer.compare(dayOccupancy[a], dayOccupancy[b]));

        return dayOrder;
    }

    /**
     * Encuentra el horario más temprano en un día específico donde todos los
     * salones estén libres.
     */
    private int[] findEarliestAvailableTimeSlotInDay(boolean[][][] occupied, List<Integer> classrooms,
            int durationBlocks, int day) {
        if (day < 0 || day >= ProblemInstance.MAX_DAYS) {
            return null;
        }

        for (int startBlock = 0; startBlock <= BLOCKS_PER_DAY - durationBlocks; startBlock++) {
            boolean allFree = true;

            // Verificar si todos los salones están libres en este rango
            for (int classroomIdx : classrooms) {
                for (int b = 0; b < durationBlocks; b++) {
                    if (occupied[day][startBlock + b][classroomIdx]) {
                        allFree = false;
                        break;
                    }
                }
                if (!allFree)
                    break;
            }

            if (allFree) {
                return new int[] { day, startBlock };
            }
        }
        return null;
    }

    @Override
    public IntegerSolution evaluate(IntegerSolution solution) {
        // Decodificar la solución
        Map<Integer, DecodedAssignment> assignments = decode(solution);

        // Objetivo 1: Minimizar número total de asignaciones (materia-salón)
        int totalAssignments = 0;

        // Restricción 1: Déficit de capacidad
        int capacityDeficit = 0;

        // Restricción 2: Materias no asignadas
        int unassignedSubjects = 0;

        for (int examIdx = 0; examIdx < numSubjects; examIdx++) {
            DecodedAssignment assignment = assignments.get(examIdx);

            if (!assignment.assigned || assignment.classrooms.isEmpty()) {
                unassignedSubjects++;
            } else {
                int numClassroomsUsed = assignment.classrooms.size();
                totalAssignments += numClassroomsUsed;

                // Verificar capacidad
                int enrolled = instance.getSubjectByIndex(examIdx).getEnrolledStudents();
                if (enrolled > assignment.totalCapacity) {
                    capacityDeficit += (enrolled - assignment.totalCapacity);
                }
            }
        }

        // Objetivo 1: Minimizar asignaciones + penalización por exceso
        double objective1 = totalAssignments;

        // Objetivo 2: Maximizar separación entre materias en conflicto
        double separationScore = calculateSeparationScore(assignments);
        double objective2 = -separationScore; // Negativo porque jMetal minimiza

        solution.objectives()[0] = objective1;
        solution.objectives()[1] = objective2;
        solution.constraints()[0] = -capacityDeficit;
        solution.constraints()[1] = -unassignedSubjects;

        return solution;
    }

    /**
     * Calcula el puntaje de separación entre materias en conflicto.
     */
    private double calculateSeparationScore(Map<Integer, DecodedAssignment> assignments) {
        List<int[]> conflictPairs = instance.getConflictPairs();

        if (conflictPairs.isEmpty()) {
            return 0;
        }

        double totalSeparation = 0;
        int validPairs = 0;

        for (int[] pair : conflictPairs) {
            int subject1 = pair[0];
            int subject2 = pair[1];

            DecodedAssignment a1 = assignments.get(subject1);
            DecodedAssignment a2 = assignments.get(subject2);

            if (a1 != null && a2 != null && a1.assigned && a2.assigned) {
                int separation = Math.abs(a1.day - a2.day);
                totalSeparation += separation;
                validPairs++;
            }
        }

        if (validPairs == 0) {
            return 0;
        }

        return totalSeparation / validPairs;
    }
}
