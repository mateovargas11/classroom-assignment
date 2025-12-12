package com.university.problem;

import com.university.domain.ProblemInstance;
import com.university.domain.Subject;
import org.uma.jmetal.problem.integerproblem.impl.AbstractIntegerProblem;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.util.*;

/**
 * Problema de asignación de salones usando representación directa de matriz.
 * 
 * El vector de decisión representa directamente la ocupación de cada bloque de
 * tiempo:
 * - Cada posición representa un bloque de 0.5h en un salón específico en un día
 * específico
 * - El valor en cada posición es el índice de la materia que ocupa ese bloque
 * - La materia "V" (vacío) indica que el bloque está libre
 * 
 * Tamaño del vector: NUM_CLASSROOMS × BLOCKS_PER_DAY × MAX_DAYS
 * Donde BLOCKS_PER_DAY = 26 (13 horas × 2 bloques/hora)
 */
public class ClassroomAssignmentProblem extends AbstractIntegerProblem {

    private final ProblemInstance instance;

    // Constantes del modelo
    public static final int BLOCKS_PER_DAY = 26; // 13 horas × 2 bloques/hora

    private final int numClassrooms;
    private final int numSubjects;
    private final int emptySubjectIndex;
    private final int vectorSize;

    // Pre-calculado: salones ordenados por capacidad
    private final List<Integer> sortedClassroomsByCapacityDesc;
    private final List<Integer> sortedClassroomsByCapacityAsc;

    // Pre-calculado: bloques necesarios por materia
    private final int[] blocksPerSubject;

    // Pre-calculado: mínimo de salones necesarios por materia
    private final int[] minClassroomsPerSubject;

    private final Random random = new Random();

    public ClassroomAssignmentProblem(ProblemInstance instance) {
        this.instance = instance;
        this.numClassrooms = instance.getClassrooms().size();
        this.numSubjects = instance.getSubjects().size();
        this.emptySubjectIndex = instance.getEmptySubjectIndex(); // índice de "V"

        // Vector: bloques × salones × días
        this.vectorSize = numClassrooms * BLOCKS_PER_DAY * ProblemInstance.MAX_DAYS;

        // Ordenar salones por capacidad
        this.sortedClassroomsByCapacityDesc = new ArrayList<>();
        for (int i = 0; i < numClassrooms; i++) {
            sortedClassroomsByCapacityDesc.add(i);
        }
        sortedClassroomsByCapacityDesc.sort((a, b) -> Integer.compare(
                instance.getClassroomByIndex(b).getCapacity(),
                instance.getClassroomByIndex(a).getCapacity()));

        this.sortedClassroomsByCapacityAsc = new ArrayList<>(sortedClassroomsByCapacityDesc);
        Collections.reverse(sortedClassroomsByCapacityAsc);

        // Calcular slots necesarios por materia (1 hora = 1 slot)
        this.blocksPerSubject = new int[numSubjects];
        for (int i = 0; i < numSubjects; i++) {
            blocksPerSubject[i] = instance.getSubjects().get(i).getDurationSlots();
        }

        // Calcular mínimo de salones por materia
        this.minClassroomsPerSubject = calculateMinClassroomsPerSubject();

        // Configurar el problema
        numberOfObjectives(2);
        numberOfConstraints(3); // Agregada restricción de sincronización
        name("ClassroomAssignmentProblem");

        // Bounds: cada posición puede tener valor 0 a numSubjects (incluyendo V)
        List<Integer> lowerBounds = new ArrayList<>(vectorSize);
        List<Integer> upperBounds = new ArrayList<>(vectorSize);

        for (int i = 0; i < vectorSize; i++) {
            lowerBounds.add(0);
            upperBounds.add(numSubjects); // numSubjects es el índice de "V"
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

    // ==================== CODIFICACIÓN/DECODIFICACIÓN ====================

    /**
     * Convierte una posición lineal del vector a (día, salón, bloque)
     */
    public int[] decodePosition(int position) {
        int blocksPerDay = numClassrooms * BLOCKS_PER_DAY;
        int day = position / blocksPerDay;
        int remainder = position % blocksPerDay;
        int classroom = remainder / BLOCKS_PER_DAY;
        int block = remainder % BLOCKS_PER_DAY;
        return new int[] { day, classroom, block };
    }

    /**
     * Convierte (día, salón, bloque) a posición lineal del vector
     */
    public int encodePosition(int day, int classroom, int block) {
        return day * numClassrooms * BLOCKS_PER_DAY + classroom * BLOCKS_PER_DAY + block;
    }

    // ==================== CREACIÓN DE SOLUCIONES ====================

    /**
     * Crea una solución usando inicialización híbrida:
     * - 50%: Greedy (asignación óptima)
     * - 30%: Greedy con ruido
     * - 20%: Aleatoria estructurada
     */
    @Override
    public IntegerSolution createSolution() {
        double rand = random.nextDouble();

        if (rand < 0.50) {
            return createGreedySolution();
        } else if (rand < 0.80) {
            return createGreedyWithNoise();
        } else {
            return createRandomStructuredSolution();
        }
    }

    /**
     * Crea una solución greedy: asigna cada materia al mejor salón disponible
     * en el primer día/bloque donde quepa.
     */
    public IntegerSolution createGreedySolution() {
        IntegerSolution solution = initializeEmptySolution();

        // Ordenar materias por inscriptos (descendente) para asignar las más grandes
        // primero
        List<Integer> subjectOrder = new ArrayList<>();
        for (int i = 0; i < numSubjects; i++) {
            subjectOrder.add(i);
        }
        subjectOrder.sort((a, b) -> Integer.compare(
                instance.getSubjectByIndex(b).getEnrolledStudents(),
                instance.getSubjectByIndex(a).getEnrolledStudents()));

        // Track de disponibilidad: classroomAvailability[classroom][day] = próximo
        // bloque libre
        int[][] nextFreeBlock = new int[numClassrooms][ProblemInstance.MAX_DAYS];

        for (int subjectIdx : subjectOrder) {
            assignSubjectGreedy(solution, subjectIdx, nextFreeBlock);
        }

        return solution;
    }

    private void assignSubjectGreedy(IntegerSolution solution, int subjectIdx, int[][] nextFreeBlock) {
        Subject subject = instance.getSubjectByIndex(subjectIdx);
        int enrolled = subject.getEnrolledStudents();
        int durationSlots = blocksPerSubject[subjectIdx];

        // Encontrar salones necesarios para cubrir la capacidad
        List<Integer> classroomsToUse = findClassroomsForCapacity(enrolled);

        // Estrategia 1: Buscar día donde todos los salones tengan espacio simultáneo
        for (int day = 0; day < ProblemInstance.MAX_DAYS; day++) {
            int maxStartBlock = 0;
            boolean allFit = true;

            for (int classroomIdx : classroomsToUse) {
                int startBlock = nextFreeBlock[classroomIdx][day];
                if (startBlock + durationSlots > BLOCKS_PER_DAY) {
                    allFit = false;
                    break;
                }
                maxStartBlock = Math.max(maxStartBlock, startBlock);
            }

            if (allFit && maxStartBlock + durationSlots <= BLOCKS_PER_DAY) {
                // Asignar la materia a todos los salones necesarios
                for (int classroomIdx : classroomsToUse) {
                    for (int b = 0; b < durationSlots; b++) {
                        int pos = encodePosition(day, classroomIdx, maxStartBlock + b);
                        solution.variables().set(pos, subjectIdx);
                    }
                    nextFreeBlock[classroomIdx][day] = maxStartBlock + durationSlots;
                }
                return;
            }
        }

        // Estrategia 2: Si no encontró espacio simultáneo, buscar CUALQUIER salón
        // disponible
        // NOTA: Esta estrategia permite asignar salones en diferentes días/horarios.
        // NSGA-II puede decidir si mantener esta asignación o buscar sincronización.
        // La restricción de sincronización penalizará soluciones no sincronizadas,
        // pero permite que el algoritmo explore el espacio de soluciones.
        int capacityAssigned = 0;

        // Primero intentar con los salones preferidos
        for (int classroomIdx : classroomsToUse) {
            if (capacityAssigned >= enrolled)
                break;

            for (int day = 0; day < ProblemInstance.MAX_DAYS; day++) {
                int startBlock = nextFreeBlock[classroomIdx][day];
                if (startBlock + durationSlots <= BLOCKS_PER_DAY) {
                    for (int b = 0; b < durationSlots; b++) {
                        int pos = encodePosition(day, classroomIdx, startBlock + b);
                        solution.variables().set(pos, subjectIdx);
                    }
                    nextFreeBlock[classroomIdx][day] = startBlock + durationSlots;
                    capacityAssigned += instance.getClassroomByIndex(classroomIdx).getCapacity();
                    break;
                }
            }
        }

        // Si aún no hay suficiente capacidad, buscar en TODOS los salones
        if (capacityAssigned < enrolled) {
            for (int classroomIdx : sortedClassroomsByCapacityDesc) {
                if (capacityAssigned >= enrolled)
                    break;
                if (classroomsToUse.contains(classroomIdx))
                    continue; // Ya lo intentamos

                for (int day = 0; day < ProblemInstance.MAX_DAYS; day++) {
                    int startBlock = nextFreeBlock[classroomIdx][day];
                    if (startBlock + durationSlots <= BLOCKS_PER_DAY) {
                        for (int b = 0; b < durationSlots; b++) {
                            int pos = encodePosition(day, classroomIdx, startBlock + b);
                            solution.variables().set(pos, subjectIdx);
                        }
                        nextFreeBlock[classroomIdx][day] = startBlock + durationSlots;
                        capacityAssigned += instance.getClassroomByIndex(classroomIdx).getCapacity();
                        break;
                    }
                }
            }
        }
    }

    private List<Integer> findClassroomsForCapacity(int enrolled) {
        List<Integer> result = new ArrayList<>();
        int capacityAccumulated = 0;

        // Primero intentar con un solo salón (best-fit)
        for (int classroomIdx : sortedClassroomsByCapacityAsc) {
            int cap = instance.getClassroomByIndex(classroomIdx).getCapacity();
            if (cap >= enrolled) {
                result.add(classroomIdx);
                return result;
            }
        }

        // Si no cabe en uno solo, usar los más grandes
        for (int classroomIdx : sortedClassroomsByCapacityDesc) {
            if (capacityAccumulated >= enrolled)
                break;
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

        // Aplicar mutaciones al 10% de las materias
        for (int subjectIdx = 0; subjectIdx < numSubjects; subjectIdx++) {
            if (random.nextDouble() < 0.10) {
                // Mover esta materia a otro día aleatorio
                moveSubjectToRandomDay(solution, subjectIdx);
            }
        }

        return solution;
    }

    private void moveSubjectToRandomDay(IntegerSolution solution, int subjectIdx) {
        int durationBlocks = blocksPerSubject[subjectIdx];
        int enrolled = instance.getSubjectByIndex(subjectIdx).getEnrolledStudents();

        // Primero, eliminar la materia de donde esté
        for (int i = 0; i < vectorSize; i++) {
            if (solution.variables().get(i) == subjectIdx) {
                solution.variables().set(i, emptySubjectIndex);
            }
        }

        // Elegir un día aleatorio
        int newDay = random.nextInt(ProblemInstance.MAX_DAYS);
        int startBlock = random.nextInt(Math.max(1, BLOCKS_PER_DAY - durationBlocks));

        List<Integer> classrooms = findClassroomsForCapacity(enrolled);

        for (int classroomIdx : classrooms) {
            for (int b = 0; b < durationBlocks && startBlock + b < BLOCKS_PER_DAY; b++) {
                int pos = encodePosition(newDay, classroomIdx, startBlock + b);
                solution.variables().set(pos, subjectIdx);
            }
        }
    }

    /**
     * Crea una solución aleatoria pero estructurada (materias en bloques
     * contiguos).
     */
    private IntegerSolution createRandomStructuredSolution() {
        IntegerSolution solution = initializeEmptySolution();

        // Mezclar el orden de materias
        List<Integer> subjectOrder = new ArrayList<>();
        for (int i = 0; i < numSubjects; i++) {
            subjectOrder.add(i);
        }
        Collections.shuffle(subjectOrder, random);

        int[][] nextFreeBlock = new int[numClassrooms][ProblemInstance.MAX_DAYS];

        for (int subjectIdx : subjectOrder) {
            int durationSlots = blocksPerSubject[subjectIdx];
            int enrolled = instance.getSubjectByIndex(subjectIdx).getEnrolledStudents();
            List<Integer> classrooms = findClassroomsForCapacity(enrolled);

            // Elegir un día aleatorio como punto de partida
            int startDay = random.nextInt(ProblemInstance.MAX_DAYS);
            boolean assigned = false;

            // Permitir que NSGA-II explore: 70% intenta sincronizar, 30% permite no
            // sincronizar
            boolean trySynchronized = random.nextDouble() < 0.7;

            if (trySynchronized) {
                // Buscar día donde todos los salones quepan simultáneamente
                for (int offset = 0; offset < ProblemInstance.MAX_DAYS && !assigned; offset++) {
                    int day = (startDay + offset) % ProblemInstance.MAX_DAYS;

                    int maxStartBlock = 0;
                    boolean allFit = true;
                    for (int classroomIdx : classrooms) {
                        int startBlock = nextFreeBlock[classroomIdx][day];
                        if (startBlock + durationSlots > BLOCKS_PER_DAY) {
                            allFit = false;
                            break;
                        }
                        maxStartBlock = Math.max(maxStartBlock, startBlock);
                    }

                    if (allFit && maxStartBlock + durationSlots <= BLOCKS_PER_DAY) {
                        for (int classroomIdx : classrooms) {
                            for (int b = 0; b < durationSlots; b++) {
                                int pos = encodePosition(day, classroomIdx, maxStartBlock + b);
                                solution.variables().set(pos, subjectIdx);
                            }
                            nextFreeBlock[classroomIdx][day] = maxStartBlock + durationSlots;
                        }
                        assigned = true;
                    }
                }
            }

            // Si no se asignó sincronizada (o se eligió no sincronizar), asignar en
            // días/horarios diferentes
            if (!assigned) {
                int capacityAssigned = 0;
                for (int classroomIdx : classrooms) {
                    if (capacityAssigned >= enrolled)
                        break;

                    for (int offset = 0; offset < ProblemInstance.MAX_DAYS; offset++) {
                        int day = (startDay + offset) % ProblemInstance.MAX_DAYS;
                        int startBlock = nextFreeBlock[classroomIdx][day];
                        if (startBlock + durationSlots <= BLOCKS_PER_DAY) {
                            for (int b = 0; b < durationSlots; b++) {
                                int pos = encodePosition(day, classroomIdx, startBlock + b);
                                solution.variables().set(pos, subjectIdx);
                            }
                            nextFreeBlock[classroomIdx][day] = startBlock + durationSlots;
                            capacityAssigned += instance.getClassroomByIndex(classroomIdx).getCapacity();
                            break;
                        }
                    }
                }
                assigned = true;
            }
        }

        return solution;
    }

    /**
     * Inicializa una solución con todos los bloques vacíos (materia "V").
     */
    private IntegerSolution initializeEmptySolution() {
        IntegerSolution solution = super.createSolution();
        for (int i = 0; i < vectorSize; i++) {
            solution.variables().set(i, emptySubjectIndex);
        }
        return solution;
    }

    // ==================== EVALUACIÓN ====================

    @Override
    public IntegerSolution evaluate(IntegerSolution solution) {
        // Decodificar asignaciones: para cada materia, encontrar en qué día(s) y
        // salón(es) está
        Map<Integer, SubjectAssignment> assignments = decodeAssignments(solution);

        // Objetivo 1: Minimizar número total de asignaciones (materia-salón)
        int totalAssignments = 0;
        int excessClassrooms = 0;

        // Restricción 1: Déficit de capacidad
        int capacityDeficit = 0;

        // Restricción 2: Materias no asignadas
        int unassignedSubjects = 0;

        // Restricción 3: Materias con múltiples salones no sincronizados
        // NSGA-II puede decidir si sincronizar o no, pero se penaliza la falta de
        // sincronización
        int unsynchronizedSubjects = 0;

        for (int subjectIdx = 0; subjectIdx < numSubjects; subjectIdx++) {
            SubjectAssignment assignment = assignments.get(subjectIdx);

            if (assignment == null || assignment.classrooms.isEmpty()) {
                unassignedSubjects++;
            } else {
                int numClassrooms = assignment.classrooms.size();
                totalAssignments += numClassrooms;

                // Verificar exceso de salones
                int minNeeded = minClassroomsPerSubject[subjectIdx];
                if (numClassrooms > minNeeded) {
                    excessClassrooms += (numClassrooms - minNeeded);
                }

                // Verificar capacidad
                int totalCapacity = 0;
                for (int classroomIdx : assignment.classrooms) {
                    totalCapacity += instance.getClassroomByIndex(classroomIdx).getCapacity();
                }

                int enrolled = instance.getSubjectByIndex(subjectIdx).getEnrolledStudents();
                if (enrolled > totalCapacity) {
                    capacityDeficit += (enrolled - totalCapacity);
                }

                // Verificar sincronización: si una materia tiene múltiples salones,
                // deben estar en el mismo horario (mismo día y mismo bloque de inicio)
                if (numClassrooms > 1 && !isSynchronized(assignment)) {
                    unsynchronizedSubjects++;
                }
            }
        }

        // Objetivo 1: Minimizar asignaciones + penalización por exceso
        double objective1 = totalAssignments + (excessClassrooms * 10);

        // Objetivo 2: Maximizar separación entre materias en conflicto
        double separationScore = calculateSeparationScore(assignments);
        double objective2 = -separationScore; // Negativo porque jMetal minimiza

        solution.objectives()[0] = objective1;
        solution.objectives()[1] = objective2;
        solution.constraints()[0] = -capacityDeficit;
        solution.constraints()[1] = -unassignedSubjects;
        solution.constraints()[2] = -unsynchronizedSubjects; // Nueva restricción

        return solution;
    }

    /**
     * Decodifica el vector de solución a asignaciones por materia.
     * Ahora captura información detallada de horarios para verificar
     * sincronización.
     */
    public Map<Integer, SubjectAssignment> decodeAssignments(IntegerSolution solution) {
        Map<Integer, SubjectAssignment> assignments = new HashMap<>();

        for (int subjectIdx = 0; subjectIdx < numSubjects; subjectIdx++) {
            assignments.put(subjectIdx, new SubjectAssignment());
        }

        // Primera pasada: identificar bloques contiguos por materia
        for (int pos = 0; pos < vectorSize; pos++) {
            int subjectIdx = solution.variables().get(pos);

            if (subjectIdx >= 0 && subjectIdx < numSubjects) {
                int[] decoded = decodePosition(pos);
                int day = decoded[0];
                int classroom = decoded[1];
                int block = decoded[2];

                SubjectAssignment assignment = assignments.get(subjectIdx);
                assignment.classrooms.add(classroom);
                assignment.days.add(day);

                // Agregar información de horario
                assignment.classroomTimeSlots.putIfAbsent(classroom, new HashSet<>());

                // Buscar el bloque de inicio de esta asignación contigua
                // (el primer bloque de una secuencia contigua de bloques de la misma materia)
                int startBlock = findStartBlockOfContiguousAssignment(solution, subjectIdx, day, classroom, block);
                assignment.classroomTimeSlots.get(classroom).add(
                        new SubjectAssignment.TimeSlot(day, startBlock));
            }
        }

        return assignments;
    }

    /**
     * Encuentra el bloque de inicio de una asignación contigua.
     * Una asignación contigua es una secuencia de bloques consecutivos con la misma
     * materia.
     */
    private int findStartBlockOfContiguousAssignment(IntegerSolution solution, int subjectIdx,
            int day, int classroom, int block) {
        // Retroceder hasta encontrar el inicio de la secuencia contigua
        int startBlock = block;
        while (startBlock > 0) {
            int prevPos = encodePosition(day, classroom, startBlock - 1);
            if (solution.variables().get(prevPos) != subjectIdx) {
                break;
            }
            startBlock--;
        }
        return startBlock;
    }

    /**
     * Verifica si una asignación con múltiples salones está sincronizada.
     * Está sincronizada si todos los salones comparten al menos un horario común
     * (mismo día y mismo bloque de inicio).
     */
    private boolean isSynchronized(SubjectAssignment assignment) {
        if (assignment.classrooms.size() <= 1) {
            return true; // No hay nada que sincronizar
        }

        // Encontrar intersección de horarios entre todos los salones
        Set<SubjectAssignment.TimeSlot> commonTimeSlots = null;

        for (int classroomIdx : assignment.classrooms) {
            Set<SubjectAssignment.TimeSlot> timeSlots = assignment.classroomTimeSlots.get(classroomIdx);
            if (timeSlots == null || timeSlots.isEmpty()) {
                return false;
            }

            if (commonTimeSlots == null) {
                // Primera iteración: inicializar con los horarios del primer salón
                commonTimeSlots = new HashSet<>(timeSlots);
            } else {
                // Intersección: mantener solo los horarios que están en ambos
                commonTimeSlots.retainAll(timeSlots);
                if (commonTimeSlots.isEmpty()) {
                    return false; // No hay horario común
                }
            }
        }

        // Si hay al menos un horario común entre todos los salones, está sincronizada
        return commonTimeSlots != null && !commonTimeSlots.isEmpty();
    }

    /**
     * Calcula el puntaje de separación entre materias en conflicto.
     */
    private double calculateSeparationScore(Map<Integer, SubjectAssignment> assignments) {
        List<int[]> conflictPairs = instance.getConflictPairs();

        if (conflictPairs.isEmpty()) {
            return 0;
        }

        double totalSeparation = 0;
        int validPairs = 0;

        for (int[] pair : conflictPairs) {
            int subject1 = pair[0];
            int subject2 = pair[1];

            SubjectAssignment a1 = assignments.get(subject1);
            SubjectAssignment a2 = assignments.get(subject2);

            if (a1 != null && a2 != null && !a1.days.isEmpty() && !a2.days.isEmpty()) {
                // Usar el día principal (el más común o el primero)
                int day1 = a1.days.iterator().next();
                int day2 = a2.days.iterator().next();

                int separation = Math.abs(day1 - day2);
                totalSeparation += separation;
                validPairs++;
            }
        }

        if (validPairs == 0) {
            return 0;
        }

        return totalSeparation / validPairs;
    }

    // ==================== CLASE AUXILIAR ====================

    public static class SubjectAssignment {
        public Set<Integer> classrooms = new HashSet<>();
        public Set<Integer> days = new HashSet<>();

        // Mapa: classroomIdx -> Set de (day, startBlock) donde está asignada la materia
        // Permite verificar si múltiples salones están sincronizados
        public Map<Integer, Set<TimeSlot>> classroomTimeSlots = new HashMap<>();

        public static class TimeSlot {
            public final int day;
            public final int startBlock;

            public TimeSlot(int day, int startBlock) {
                this.day = day;
                this.startBlock = startBlock;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o)
                    return true;
                if (o == null || getClass() != o.getClass())
                    return false;
                TimeSlot timeSlot = (TimeSlot) o;
                return day == timeSlot.day && startBlock == timeSlot.startBlock;
            }

            @Override
            public int hashCode() {
                return 31 * day + startBlock;
            }
        }
    }

    // ==================== GETTERS ====================

    public ProblemInstance getInstance() {
        return instance;
    }

    public int getEmptySubjectIndex() {
        return emptySubjectIndex;
    }

    public int[] getMinClassroomsPerSubject() {
        return minClassroomsPerSubject;
    }

    public int getTotalMinClassrooms() {
        int total = 0;
        for (int min : minClassroomsPerSubject) {
            total += min;
        }
        return total;
    }

    public int getMaxClassroomsPerSubject() {
        return 5; // Mantener compatibilidad
    }

    public int getVectorSize() {
        return vectorSize;
    }

    public int getNumClassrooms() {
        return numClassrooms;
    }

    public int getNumSubjects() {
        return numSubjects;
    }
}
