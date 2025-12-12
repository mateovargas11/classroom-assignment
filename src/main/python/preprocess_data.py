#!/usr/bin/env python3
"""
Script de preprocesamiento de datos para el problema de asignación de salones.

Este script:
1. Carga los datos de exámenes y salones desde CSV
2. Limpia y normaliza los datos
3. Genera archivos JSON estructurados para usar en Java
4. Calcula grupos de conflicto (materias mismo semestre/carrera)
"""

import csv
import json
import os
import re
from pathlib import Path
from collections import defaultdict
from typing import Dict, List, Set, Tuple

# Configuración de rutas
BASE_DIR = Path(__file__).parent.parent.parent.parent
RESOURCES_DIR = BASE_DIR / "src" / "main" / "resources"
DATASETS_DIR = RESOURCES_DIR / "datasets"
OUTPUT_DIR = RESOURCES_DIR / "processed"


def normalize_carrera(carrera: str) -> Set[str]:
    """Normaliza y separa las carreras de un string."""
    if not carrera or carrera.strip() == '':
        return set()
    
    # Separar por ; o ,
    carreras = re.split(r'[;,]', carrera.lower())
    # Limpiar espacios y normalizar
    carreras = {c.strip() for c in carreras if c.strip()}
    
    # Normalización de nombres de carrera
    normalization_map = {
        'todas': 'todas',
        'computacion': 'computacion',
        'computación': 'computacion',
        'electronica': 'electronica',
        'electrónica': 'electronica',
        'electrica': 'electrica',
        'eléctrica': 'electrica',
        'civil': 'civil',
        'mecanica': 'mecanica',
        'mecánica': 'mecanica',
        'mecania': 'mecanica',  # typo en datos
        'produccion': 'produccion',
        'producción': 'produccion',
        'quimica': 'quimica',
        'química': 'quimica',
        'alimentos': 'alimentos',
        'naval': 'naval',
        'agrimensura': 'agrimensura',
        'sistemas de comunicacion': 'sistemas_comunicacion',
        'sistemas de comunicación': 'sistemas_comunicacion',
        'tecnologo telecomunicacione': 'tecnologo_telecom',
        'tecnologo telecomunicaciones': 'tecnologo_telecom',
    }
    
    normalized = set()
    for c in carreras:
        c_lower = c.lower().strip()
        if c_lower in normalization_map:
            normalized.add(normalization_map[c_lower])
        elif c_lower:
            # Si no está en el mapa, usar tal cual
            normalized.add(c_lower.replace(' ', '_'))
    
    return normalized


def load_salones(filepath: Path) -> List[Dict]:
    """Carga los datos de salones desde CSV."""
    salones = []
    
    with open(filepath, 'r', encoding='utf-8') as f:
        reader = csv.reader(f)
        header = next(reader)  # Saltar header
        
        for row in reader:
            if len(row) >= 8:
                # Columnas: descripcion, Salón, Pertenece, Edificio, Ubicacion, ?, Aforo clase, Aforo pruebas
                salon_id = row[1].strip() if row[1] else row[0].split()[0]
                
                # Obtener aforo de pruebas (última columna)
                try:
                    aforo_str = row[7].strip()
                    # Manejar casos como "--" o valores no numéricos
                    if aforo_str == '--' or not aforo_str:
                        aforo = 0
                    else:
                        aforo = int(aforo_str)
                except (ValueError, IndexError):
                    aforo = 0
                
                if aforo > 0:  # Solo incluir salones con aforo válido
                    salones.append({
                        'id': salon_id,
                        'nombre': row[0].strip() if row[0] else salon_id,
                        'edificio': row[3].strip() if len(row) > 3 else '',
                        'ubicacion': row[4].strip() if len(row) > 4 else '',
                        'aforo': aforo
                    })
    
    # Ordenar por aforo descendente (para asignación greedy)
    salones.sort(key=lambda x: x['aforo'], reverse=True)
    
    return salones


def load_examenes(filepath: Path, instance_name: str) -> List[Dict]:
    """Carga los datos de exámenes desde CSV."""
    examenes = []
    
    with open(filepath, 'r', encoding='utf-8') as f:
        reader = csv.reader(f)
        header = next(reader)  # nombre_uc, inscritos, Duracion, semestre, carrera
        
        # Identificar columna de inscritos (puede variar el nombre)
        inscritos_col = 1  # Asumimos segunda columna
        
        exam_id = 0
        for row in reader:
            if len(row) >= 3:
                nombre = row[0].strip()
                
                try:
                    inscritos = int(row[1].strip()) if row[1].strip() else 0
                except ValueError:
                    inscritos = 0
                
                try:
                    duracion = float(row[2].strip()) if row[2].strip() else 3.0
                except ValueError:
                    duracion = 3.0
                
                try:
                    semestre = int(row[3].strip()) if len(row) > 3 and row[3].strip() else 0
                except ValueError:
                    semestre = 0
                
                carrera_raw = row[4].strip() if len(row) > 4 else ''
                carreras = list(normalize_carrera(carrera_raw))
                
                # Solo incluir exámenes con inscritos > 0
                if inscritos > 0:
                    examenes.append({
                        'id': exam_id,
                        'nombre': nombre,
                        'inscritos': inscritos,
                        'duracion': duracion,
                        'semestre': semestre,
                        'carreras': carreras
                    })
                    exam_id += 1
    
    return examenes


def calculate_conflict_groups(examenes: List[Dict]) -> Dict[str, List[int]]:
    """
    Calcula grupos de conflicto basados en semestre y carrera.
    
    Dos exámenes están en conflicto si:
    - Pertenecen al mismo semestre Y
    - Comparten al menos una carrera
    
    Si una materia tiene 'todas', se agrega a todas las carreras del semestre.
    Si no hay carreras específicas en el semestre, se crea un grupo especial para 'todas'.
    """
    # Primero, recopilar todas las carreras únicas por semestre (excluyendo 'todas')
    semestre_carreras = defaultdict(set)
    for exam in examenes:
        if exam['semestre'] > 0:
            for carrera in exam['carreras']:
                if carrera and carrera != 'todas':
                    semestre_carreras[exam['semestre']].add(carrera)
    
    # Crear índice por (semestre, carrera)
    semestre_carrera_index = defaultdict(list)
    
    for exam in examenes:
        if exam['semestre'] > 0:
            semestre = exam['semestre']
            carreras = set(exam['carreras'])
            
            # Si tiene 'todas', expandir a todas las carreras del semestre
            if 'todas' in carreras:
                if semestre_carreras[semestre]:
                    # Agregar a todas las carreras del semestre
                    for carrera in semestre_carreras[semestre]:
                        key = f"{semestre}_{carrera}"
                        semestre_carrera_index[key].append(exam['id'])
                else:
                    # Si no hay carreras específicas, crear grupo especial para 'todas'
                    key = f"{semestre}_todas"
                    semestre_carrera_index[key].append(exam['id'])
            else:
                # Agregar a las carreras específicas
                for carrera in carreras:
                    if carrera:  # Ignorar strings vacíos
                        key = f"{semestre}_{carrera}"
                        semestre_carrera_index[key].append(exam['id'])
    
    return dict(semestre_carrera_index)


def calculate_conflict_matrix(examenes: List[Dict]) -> List[List[int]]:
    """
    Genera una matriz de adyacencia de conflictos.
    conflict_matrix[i][j] = 1 si examen i y j están en conflicto
    
    Dos exámenes están en conflicto si:
    - Pertenecen al mismo semestre Y
    - Comparten al menos una carrera (o alguno tiene 'todas')
    """
    n = len(examenes)
    matrix = [[0] * n for _ in range(n)]
    
    # Primero, recopilar todas las carreras únicas por semestre (excluyendo 'todas')
    semestre_carreras = defaultdict(set)
    for exam in examenes:
        if exam['semestre'] > 0:
            for carrera in exam['carreras']:
                if carrera and carrera != 'todas':
                    semestre_carreras[exam['semestre']].add(carrera)
    
    # Expandir 'todas' a todas las carreras del semestre para cada examen
    examenes_expandidos = []
    for exam in examenes:
        carreras_expandidas = set()
        if exam['semestre'] > 0:
            if 'todas' in exam['carreras']:
                # Expandir 'todas' a todas las carreras del semestre
                carreras_expandidas = semestre_carreras[exam['semestre']].copy()
                # Si no hay carreras específicas, usar un marcador especial para que 'todas' conflictúen entre sí
                if not carreras_expandidas:
                    carreras_expandidas = {'todas'}
            else:
                # Usar las carreras específicas
                carreras_expandidas = {c for c in exam['carreras'] if c and c != 'todas'}
        examenes_expandidos.append({
            'id': exam['id'],
            'semestre': exam['semestre'],
            'carreras': carreras_expandidas
        })
    
    # Generar matriz de conflictos
    for i, exam1 in enumerate(examenes_expandidos):
        for j, exam2 in enumerate(examenes_expandidos):
            if i >= j:
                continue
            
            # Verificar conflicto: mismo semestre y carrera compartida
            if exam1['semestre'] > 0 and exam2['semestre'] > 0:
                if exam1['semestre'] == exam2['semestre']:
                    carreras1 = exam1['carreras']
                    carreras2 = exam2['carreras']
                    
                    # Hay conflicto si comparten al menos una carrera
                    if carreras1 & carreras2:  # Intersección no vacía
                        matrix[i][j] = 1
                        matrix[j][i] = 1
    
    return matrix


def process_instance(instance_file: Path, salones: List[Dict], instance_name: str) -> Dict:
    """Procesa una instancia de exámenes."""
    examenes = load_examenes(instance_file, instance_name)
    conflict_groups = calculate_conflict_groups(examenes)
    conflict_matrix = calculate_conflict_matrix(examenes)
    
    # Calcular estadísticas
    total_inscritos = sum(e['inscritos'] for e in examenes)
    total_aforo = sum(s['aforo'] for s in salones)
    
    # Calcular pares de conflicto para las funciones objetivo
    conflict_pairs = []
    for i in range(len(examenes)):
        for j in range(i + 1, len(examenes)):
            if conflict_matrix[i][j] == 1:
                conflict_pairs.append([i, j])
    
    return {
        'instance_name': instance_name,
        'examenes': examenes,
        'salones': salones,
        'conflict_groups': conflict_groups,
        'conflict_pairs': conflict_pairs,
        'stats': {
            'num_examenes': len(examenes),
            'num_salones': len(salones),
            'total_inscritos': total_inscritos,
            'total_aforo': total_aforo,
            'num_conflict_pairs': len(conflict_pairs),
            'max_inscritos': max(e['inscritos'] for e in examenes) if examenes else 0,
            'min_inscritos': min(e['inscritos'] for e in examenes) if examenes else 0
        }
    }


def main():
    """Función principal de preprocesamiento."""
    # Crear directorio de salida
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    
    # Cargar salones
    salones_file = DATASETS_DIR / "inscritos_examenes_2024-Salones.csv"
    salones = load_salones(salones_file)
    
    print(f"Salones cargados: {len(salones)}")
    print(f"Aforo total: {sum(s['aforo'] for s in salones)}")
    
    # Guardar salones procesados
    with open(OUTPUT_DIR / "salones.json", 'w', encoding='utf-8') as f:
        json.dump(salones, f, indent=2, ensure_ascii=False)
    
    # Procesar cada instancia
    instances = [
        ("inscritos_examenes_2024-inst1.csv", "febrero_2024"),
        ("inscritos_examenes_2024-inst2.csv", "julio_2024"),
        ("inscritos_examenes_2024-inst3.csv", "diciembre_2024"),
        ("inscritos_examenes_2024-inst4.csv", "promedio_2024"),
    ]
    
    all_instances = {}
    
    for filename, instance_name in instances:
        filepath = DATASETS_DIR / filename
        if filepath.exists():
            print(f"\nProcesando {instance_name}...")
            data = process_instance(filepath, salones, instance_name)
            all_instances[instance_name] = data
            
            # Guardar instancia individual
            output_file = OUTPUT_DIR / f"{instance_name}.json"
            with open(output_file, 'w', encoding='utf-8') as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
            
            print(f"  Exámenes: {data['stats']['num_examenes']}")
            print(f"  Pares en conflicto: {data['stats']['num_conflict_pairs']}")
            print(f"  Total inscritos: {data['stats']['total_inscritos']}")
            print(f"  Max inscritos: {data['stats']['max_inscritos']}")
        else:
            print(f"Archivo no encontrado: {filepath}")
    
    # Guardar resumen de todas las instancias
    summary = {
        instance_name: data['stats'] 
        for instance_name, data in all_instances.items()
    }
    
    with open(OUTPUT_DIR / "summary.json", 'w', encoding='utf-8') as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)
    
    print("\n" + "="*50)
    print("Preprocesamiento completado!")
    print(f"Archivos generados en: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
