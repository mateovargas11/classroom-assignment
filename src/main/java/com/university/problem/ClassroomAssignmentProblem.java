package com.university.problem;

import com.university.domain.ProblemInstance;
import com.university.domain.Subject;
import org.uma.jmetal.problem.integerproblem.impl.AbstractIntegerProblem;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.util.*;

/**
 * Problema de asignación de salones con representación basada en slots.
 * 
 * REPRESENTACIÓN DEL VECTOR:
 * El vector tiene numSlots elementos lógicos, donde cada elemento es:
 * [examen_index, dia, salon_1, salon_2, salon_3, salon_4]
 * 
 * Ejemplo: [(E1, D3, S2, S1, S4, -), (E4, D1, S7, S9, S6, -), (V, D0, S4, S5,
 * S3, -), ...]
 * 
 * Donde:
 * - E_i representa un examen (índice 0 a numSubjects-1)
 * - V representa vacío (índice = numSubjects)
 * - D_i representa un día (índice 0 a MAX_DAYS-1)
 * - S_i representa un salón (índice 0 a numClassrooms-1)
 * - "-" representa sin salón adicional (índice = numClassrooms)
 * 
 * DECODIFICACIÓN:
 * Para cada slot en orden del vector:
 * 1. Si el examen es V (vacío), se ignora
 * 2. Se obtiene el día del vector
 * 3. Se obtienen los salones válidos (excluir "-")
 * 4. Se busca el horario más temprano en el día especificado donde TODOS los
 * salones estén libres
 * 5. Se asigna el examen a ese horario en todos los salones
 * 
 * Esto GARANTIZA que todos los salones de un examen tengan el mismo horario.
 * El día es optimizado por NSGA2 como parte de la solución.
 */
public class ClassroomAssignmentProblem extends AbstractIntegerProblem {

    private final ProblemInstance instance;

    // Constantes del modelo
    public static final int BLOCKS_PER_DAY = 26; // 13 horas × 2 bloques/hora
    public static final int MAX_CLASSROOMS_PER_SLOT = 4; // Máximo salones por slot
    public static final int SLOT_SIZE = 1 + 1 + MAX_CLASSROOMS_PER_SLOT; // 1 examen + 1 día + 4 salones

    private final int numClassrooms;
    private final int numSubjects;
    private final int numSlots;
    private final int emptyExamIndex; // Índice para examen vacío "V"
    private final int noClassroomIndex; // Índice para "sin salón" "-"
    private final int vectorSize;

    // Pre-calculado: salones ordenados por capacidad
    private final List<Integer> sortedClassroomsByCapacityDesc;
    private final List<Integer> sortedClassroomsByCapacityAsc;

    // Pre-calculado: bloques necesarios por materia
    private final int[] blocksPerSubject;

    // Pre-calculado: mínimo de salones necesarios por materia
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
        this.numSlots = numSubjects; // Un slot por cada examen posible
        this.emptyExamIndex = numSubjects; // "V" = numSubjects
        this.noClassroomIndex = numClassrooms; // "-" = numClassrooms
        this.vectorSize = numSlots * SLOT_SIZE;

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

        // Calcular bloques necesarios por materia
        this.blocksPerSubject = new int[numSubjects];
        for (int i = 0; i < numSubjects; i++) {
            blocksPerSubject[i] = instance.getSubjects().get(i).getDurationBlocks();
        }

        // Calcular mínimo de salones por materia
        this.minClassroomsPerSubject = calculateMinClassroomsPerSubject();

        // Configurar el problema
        numberOfObjectives(2);
        numberOfConstraints(2); // Capacidad y materias no asignadas
        name("ClassroomAssignmentProblem");

        // Bounds del vector
        List<Integer> lowerBounds = new ArrayList<>(vectorSize);
        List<Integer> upperBounds = new ArrayList<>(vectorSize);

        for (int slot = 0; slot < numSlots; slot++) {
            int basePos = slot * SLOT_SIZE;

            // Posición 0: índice del examen (0 a numSubjects, donde numSubjects = V)
            lowerBounds.add(0);
            upperBounds.add(numSubjects); // Incluye vacío

            // Posición 1: índice del día (0 a MAX_DAYS-1)
            lowerBounds.add(0);
            upperBounds.add(ProblemInstance.MAX_DAYS - 1);

            // Posiciones 2-5: índices de salones (0 a numClassrooms, donde numClassrooms =
            // -)
            for (int s = 0; s < MAX_CLASSROOMS_PER_SLOT; s++) {
                lowerBounds.add(0);
                upperBounds.add(numClassrooms); // Incluye "sin salón"
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

    // ==================== ACCESO AL VECTOR ====================

    /**
     * Obtiene el índice del examen en un slot.
     */
    public int getExamIndex(IntegerSolution solution, int slot) {
        return solution.variables().get(slot * SLOT_SIZE);
    }

    /**
     * Establece el índice del examen en un slot.
     */
    public void setExamIndex(IntegerSolution solution, int slot, int examIndex) {
        solution.variables().set(slot * SLOT_SIZE, examIndex);
    }

    /**
     * Obtiene el índice del día en un slot.
     */
    public int getDayIndex(IntegerSolution solution, int slot) {
        return solution.variables().get(slot * SLOT_SIZE + 1);
    }

    /**
     * Establece el índice del día en un slot.
     */
    public void setDayIndex(IntegerSolution solution, int slot, int dayIndex) {
        solution.variables().set(slot * SLOT_SIZE + 1, dayIndex);
    }

    /**
     * Obtiene el índice del salón en la posición classroomPos (0-3) del slot.
     */
    public int getClassroomIndex(IntegerSolution solution, int slot, int classroomPos) {
        return solution.variables().get(slot * SLOT_SIZE + 2 + classroomPos);
    }

    /**
     * Establece el índice del salón en la posición classroomPos (0-3) del slot.
     */
    public void setClassroomIndex(IntegerSolution solution, int slot, int classroomPos, int classroomIndex) {
        solution.variables().set(slot * SLOT_SIZE + 2 + classroomPos, classroomIndex);
    }

    /**
     * Obtiene la lista de salones válidos (no "-") para un slot.
     */
    public List<Integer> getValidClassrooms(IntegerSolution solution, int slot) {
        List<Integer> classrooms = new ArrayList<>();
        for (int i = 0; i < MAX_CLASSROOMS_PER_SLOT; i++) {
            int classroomIdx = getClassroomIndex(solution, slot, i);
            if (classroomIdx < numClassrooms) { // No es "-"
                classrooms.add(classroomIdx);
            }
        }
        return classrooms;
    }

    // ==================== CREACIÓN DE SOLUCIONES ====================

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
     * El orden de los slots determina la prioridad de horarios.
     */
    public IntegerSolution createGreedySolution() {
        IntegerSolution solution = initializeEmptySolution();

        // Ordenar exámenes por inscriptos (descendente) para priorizar los más grandes
        List<Integer> examOrder = new ArrayList<>();
        for (int i = 0; i < numSubjects; i++) {
            examOrder.add(i);
        }
        examOrder.sort((a, b) -> Integer.compare(
                instance.getSubjectByIndex(b).getEnrolledStudents(),
                instance.getSubjectByIndex(a).getEnrolledStudents()));

        // Asignar cada examen a un slot
        // NOTA: No restringimos la reutilización de salones porque pueden usarse
        // en diferentes horarios. El decode() se encargará de asignar horarios sin
        // conflictos.

        for (int slotIdx = 0; slotIdx < numSubjects; slotIdx++) {
            int examIdx = examOrder.get(slotIdx);
            Subject subject = instance.getSubjectByIndex(examIdx);
            int enrolled = subject.getEnrolledStudents();

            // Establecer el examen
            setExamIndex(solution, slotIdx, examIdx);

            // Asignar día de forma distribuida (round-robin para distribuir mejor)
            int day = slotIdx % ProblemInstance.MAX_DAYS;
            setDayIndex(solution, slotIdx, day);

            // Encontrar los mejores salones para este examen (sin restricción de
            // reutilización)
            List<Integer> classroomsToUse = findClassroomsForCapacity(enrolled, Collections.emptySet());

            // Si no se encontraron salones (poco probable pero posible), intentar con
            // los salones más grandes disponibles
            if (classroomsToUse.isEmpty()) {
                // Usar los 4 salones más grandes como último recurso
                for (int i = 0; i < Math.min(MAX_CLASSROOMS_PER_SLOT, sortedClassroomsByCapacityDesc.size()); i++) {
                    classroomsToUse.add(sortedClassroomsByCapacityDesc.get(i));
                }
            }

            // Asignar salones al slot
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

        // Primero intentar con un solo salón (best-fit)
        for (int classroomIdx : sortedClassroomsByCapacityAsc) {
            if (usedClassrooms.contains(classroomIdx))
                continue;
            int cap = instance.getClassroomByIndex(classroomIdx).getCapacity();
            if (cap >= enrolled) {
                result.add(classroomIdx);
                return result;
            }
        }

        // Si no cabe en uno solo, usar los más grandes disponibles
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

        // Intercambiar aleatoriamente algunos slots (cambia prioridad de horarios)
        for (int i = 0; i < numSlots / 10; i++) {
            int slot1 = random.nextInt(numSlots);
            int slot2 = random.nextInt(numSlots);
            swapSlots(solution, slot1, slot2);
        }

        // Mutar algunos salones
        for (int slot = 0; slot < numSlots; slot++) {
            if (random.nextDouble() < 0.10) {
                int pos = random.nextInt(MAX_CLASSROOMS_PER_SLOT);
                int newClassroom = random.nextInt(numClassrooms + 1); // Incluye "-"
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

            // Asignar día aleatorio
            int day = random.nextInt(ProblemInstance.MAX_DAYS);
            setDayIndex(solution, slotIdx, day);

            // Asignar salones aleatorios
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
            setDayIndex(solution, slot, 0); // Inicializar día a 0 (se actualizará en métodos de creación)
            for (int i = 0; i < MAX_CLASSROOMS_PER_SLOT; i++) {
                setClassroomIndex(solution, slot, i, noClassroomIndex);
            }
        }
        return solution;
    }

    // ==================== DECODIFICACIÓN Y EVALUACIÓN ====================

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
     * @return Mapa de examIndex -> DecodedAssignment
     */
    public Map<Integer, DecodedAssignment> decode(IntegerSolution solution) {
        Map<Integer, DecodedAssignment> assignments = new HashMap<>();

        // Inicializar todas las asignaciones como no asignadas
        for (int i = 0; i < numSubjects; i++) {
            assignments.put(i, new DecodedAssignment(i));
        }

        // Matriz de ocupación: [día][bloque][salón] -> true si ocupado
        boolean[][][] occupied = new boolean[ProblemInstance.MAX_DAYS][BLOCKS_PER_DAY][numClassrooms];

        // Track de exámenes ya asignados (para evitar duplicados)
        Set<Integer> assignedExams = new HashSet<>();

        // Procesar cada slot en orden
        for (int slot = 0; slot < numSlots; slot++) {
            int examIdx = getExamIndex(solution, slot);

            // Ignorar slots vacíos o exámenes ya asignados
            if (examIdx == emptyExamIndex || examIdx < 0 || examIdx >= numSubjects) {
                continue;
            }
            if (assignedExams.contains(examIdx)) {
                continue; // El examen ya fue asignado en un slot anterior
            }

            // Obtener salones válidos para este slot
            List<Integer> classrooms = getValidClassrooms(solution, slot);
            if (classrooms.isEmpty()) {
                continue;
            }

            // Remover duplicados de salones
            classrooms = new ArrayList<>(new LinkedHashSet<>(classrooms));

            int durationBlocks = blocksPerSubject[examIdx];
            int enrolled = instance.getSubjectByIndex(examIdx).getEnrolledStudents();

            // Obtener el día del vector (optimizado por NSGA2)
            int preferredDay = getDayIndex(solution, slot);

            // Buscar el horario más temprano en el día preferido donde TODOS los salones
            // estén libres
            int[] bestTimeSlot = findEarliestAvailableTimeSlotInDay(occupied, classrooms, durationBlocks, preferredDay);

            // Si no se puede programar con los salones originales, intentar con
            // subconjuntos que aún tengan capacidad suficiente
            if (bestTimeSlot == null && classrooms.size() > 1) {
                // Calcular capacidad total de los salones originales
                int originalCapacity = 0;
                for (int classroomIdx : classrooms) {
                    originalCapacity += instance.getClassroomByIndex(classroomIdx).getCapacity();
                }

                // Si la capacidad original es suficiente, intentar subconjuntos
                if (originalCapacity >= enrolled) {
                    // Ordenar salones por capacidad (mayor primero) para priorizar los más grandes
                    List<Integer> sortedClassrooms = new ArrayList<>(classrooms);
                    sortedClassrooms.sort((a, b) -> Integer.compare(
                            instance.getClassroomByIndex(b).getCapacity(),
                            instance.getClassroomByIndex(a).getCapacity()));

                    // Intentar con subconjuntos progresivamente más pequeños
                    for (int subsetSize = sortedClassrooms.size() - 1; subsetSize >= 1; subsetSize--) {
                        List<Integer> subset = new ArrayList<>();
                        int subsetCapacity = 0;

                        // Tomar los primeros N salones más grandes
                        for (int i = 0; i < subsetSize; i++) {
                            subset.add(sortedClassrooms.get(i));
                            subsetCapacity += instance.getClassroomByIndex(sortedClassrooms.get(i)).getCapacity();
                        }

                        // Si este subconjunto tiene capacidad suficiente, intentar programarlo
                        if (subsetCapacity >= enrolled) {
                            bestTimeSlot = findEarliestAvailableTimeSlotInDay(occupied, subset, durationBlocks,
                                    preferredDay);
                            if (bestTimeSlot != null) {
                                classrooms = subset; // Usar este subconjunto
                                break;
                            }
                        }
                    }
                }
            }

            // Si aún no se puede programar en el día preferido, intentar días cercanos
            if (bestTimeSlot == null) {
                // Buscar en días cercanos al preferido (primero hacia adelante, luego hacia
                // atrás)
                for (int offset = 1; offset < ProblemInstance.MAX_DAYS; offset++) {
                    // Intentar día después
                    int day = (preferredDay + offset) % ProblemInstance.MAX_DAYS;
                    bestTimeSlot = findEarliestAvailableTimeSlotInDay(occupied, classrooms, durationBlocks, day);
                    if (bestTimeSlot != null) {
                        break;
                    }

                    // Intentar día antes
                    day = (preferredDay - offset + ProblemInstance.MAX_DAYS) % ProblemInstance.MAX_DAYS;
                    bestTimeSlot = findEarliestAvailableTimeSlotInDay(occupied, classrooms, durationBlocks, day);
                    if (bestTimeSlot != null) {
                        break;
                    }
                }
            }

            // Si aún no se puede programar, intentar con cualquier salón disponible
            // que tenga capacidad suficiente (último recurso)
            if (bestTimeSlot == null) {
                // Buscar cualquier salón disponible que pueda contener el examen
                // Usar orden de días por ocupación para distribuir mejor
                List<Integer> dayOrder = getDaysOrderedByOccupancy(occupied);
                for (int day : dayOrder) {
                    for (int startBlock = 0; startBlock <= BLOCKS_PER_DAY - durationBlocks; startBlock++) {
                        // Buscar un salón que esté libre en este horario y tenga capacidad suficiente
                        for (int classroomIdx = 0; classroomIdx < numClassrooms; classroomIdx++) {
                            // Verificar si el salón está libre
                            boolean isFree = true;
                            for (int b = 0; b < durationBlocks; b++) {
                                if (occupied[day][startBlock + b][classroomIdx]) {
                                    isFree = false;
                                    break;
                                }
                            }

                            // Si está libre y tiene capacidad suficiente, usarlo
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

                // Marcar como ocupado
                for (int classroomIdx : classrooms) {
                    for (int b = 0; b < durationBlocks; b++) {
                        occupied[day][startBlock + b][classroomIdx] = true;
                    }
                }

                // Guardar la asignación
                DecodedAssignment assignment = assignments.get(examIdx);
                assignment.day = day;
                assignment.startBlock = startBlock;
                assignment.classrooms = new ArrayList<>(classrooms);
                assignment.assigned = true;

                // Calcular capacidad total
                for (int classroomIdx : classrooms) {
                    assignment.totalCapacity += instance.getClassroomByIndex(classroomIdx).getCapacity();
                }

                assignedExams.add(examIdx);
            }
        }

        return assignments;
    }

    /**
     * Calcula el orden de días según su ocupación (menos ocupados primero).
     * Esto ayuda a distribuir los exámenes entre días de manera más uniforme.
     */
    private List<Integer> getDaysOrderedByOccupancy(boolean[][][] occupied) {
        // Calcular ocupación total por día (todos los salones) para distribuir mejor
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

        // Crear lista de días ordenados por ocupación (menos ocupados primero)
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
     * 
     * @param occupied       Matriz de ocupación
     * @param classrooms     Lista de salones a verificar
     * @param durationBlocks Duración en bloques
     * @param day            Día específico donde buscar
     * @return [day, startBlock] o null si no hay espacio en ese día
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
        int excessClassrooms = 0;

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

                // Verificar exceso de salones
                int minNeeded = minClassroomsPerSubject[examIdx];
                if (numClassroomsUsed > minNeeded) {
                    excessClassrooms += (numClassroomsUsed - minNeeded);
                }

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

    // ==================== CLASE AUXILIAR LEGACY (para compatibilidad)
    // ====================

    /**
     * Convierte DecodedAssignment a SubjectAssignment para compatibilidad.
     */
    public Map<Integer, SubjectAssignment> decodeAssignments(IntegerSolution solution) {
        Map<Integer, DecodedAssignment> decoded = decode(solution);
        Map<Integer, SubjectAssignment> result = new HashMap<>();

        for (var entry : decoded.entrySet()) {
            int examIdx = entry.getKey();
            DecodedAssignment da = entry.getValue();

            SubjectAssignment sa = new SubjectAssignment();
            sa.classrooms = new HashSet<>(da.classrooms);
            if (da.assigned) {
                sa.days.add(da.day);
                for (int classroomIdx : da.classrooms) {
                    sa.classroomTimeSlots.put(classroomIdx,
                            new HashSet<>(List.of(new SubjectAssignment.TimeSlot(da.day, da.startBlock))));
                }
            }
            result.put(examIdx, sa);
        }

        return result;
    }

    public static class SubjectAssignment {
        public Set<Integer> classrooms = new HashSet<>();
        public Set<Integer> days = new HashSet<>();
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
        return emptyExamIndex;
    }

    public int getNoClassroomIndex() {
        return noClassroomIndex;
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
        return MAX_CLASSROOMS_PER_SLOT;
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

    public int getNumSlots() {
        return numSlots;
    }

    public int[] getBlocksPerSubject() {
        return blocksPerSubject;
    }

    /**
     * Imprime la representación del vector para debugging.
     */
    public void printSolutionVector(IntegerSolution solution) {
        System.out.println("Vector de solución [examen, dia, salones...]:");
        for (int slot = 0; slot < numSlots; slot++) {
            int examIdx = getExamIndex(solution, slot);
            String examName = examIdx < numSubjects ? "E" + examIdx : "V";
            int dayIdx = getDayIndex(solution, slot);

            StringBuilder sb = new StringBuilder();
            sb.append("Slot ").append(slot).append(": (").append(examName).append(", D").append(dayIdx);

            for (int i = 0; i < MAX_CLASSROOMS_PER_SLOT; i++) {
                int classroomIdx = getClassroomIndex(solution, slot, i);
                String classroomName = classroomIdx < numClassrooms ? " S" + classroomIdx : " -";
                sb.append(classroomName);
            }
            sb.append(")");

            System.out.println(sb.toString());
        }
    }
}
