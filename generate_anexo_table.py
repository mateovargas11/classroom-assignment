#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script para generar una tabla LaTeX con las asignaciones de salones y exámenes
ordenadas por día, salón y horario de inicio.
"""

import csv
from pathlib import Path
import math

def decimal_to_time(decimal_hour):
    """
    Convierte una hora decimal a formato hora:minuto.
    Ejemplos: 8.0 -> 8:00, 8.5 -> 8:30, 14.5 -> 14:30
    """
    hours = int(decimal_hour)
    decimal_part = decimal_hour - hours
    
    if abs(decimal_part - 0.5) < 0.01:  # Aproximadamente 0.5
        minutes = 30
    elif abs(decimal_part) < 0.01:  # Aproximadamente 0.0
        minutes = 0
    else:
        # Para otros casos, convertir directamente
        minutes = int(round(decimal_part * 60))
    
    return f"{hours}:{minutes:02d}"

def process_assignments_csv(csv_file):
    """
    Procesa el CSV de asignaciones y genera filas ordenadas.
    """
    rows = []
    
    with open(csv_file, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            materia_id = row['materia_id']
            materia_nombre = row['materia_nombre']
            inscriptos = int(row['inscriptos'])
            duracion_horas = float(row['duracion_horas'])
            dia = int(row['dia'])
            hora_inicio = float(row['hora_inicio'])
            salones_str = row['salones']
            capacidad_total = int(row['capacidad_total'])
            estado = row['estado']
            
            # Si hay múltiples salones separados por ;, crear una fila por cada uno
            salones = [s.strip() for s in salones_str.split(';')]
            
            for salon in salones:
                rows.append({
                    'dia': dia,
                    'salon': salon,
                    'hora_inicio': hora_inicio,
                    'materia_id': materia_id,
                    'materia_nombre': materia_nombre,
                    'inscriptos': inscriptos,
                    'duracion_horas': duracion_horas,
                    'capacidad_total': capacidad_total,
                    'estado': estado
                })
    
    # Ordenar por día, luego por salón, luego por hora_inicio
    rows.sort(key=lambda x: (x['dia'], x['salon'], x['hora_inicio']))
    
    return rows

def generate_latex_table(rows):
    """
    Genera código LaTeX para una tabla con las asignaciones usando longtable.
    """
    latex = []
    latex.append("\\begin{longtable}{|c|c|c|c|c|c|c|}")
    latex.append("\\caption{Asignación de salones y exámenes} \\label{tab:asignaciones} \\\\")
    latex.append("\\hline")
    latex.append("\\textbf{Día} & \\textbf{Salón} & \\textbf{Hora Inicio} & \\textbf{Hora Fin} & \\textbf{Materia} & \\textbf{Inscriptos} \\\\")
    latex.append("\\hline")
    latex.append("\\endfirsthead")
    latex.append("\\multicolumn{7}{c}")
    latex.append("{{\\bfseries \\tablename\\ \\thetable{} -- continuaci\\'on de la p\\'agina anterior}} \\\\")
    latex.append("\\hline")
    latex.append("\\textbf{Día} & \\textbf{Salón} & \\textbf{Hora Inicio} & \\textbf{Hora Fin} & \\textbf{Materia} & \\textbf{Inscriptos} \\\\")
    latex.append("\\hline")
    latex.append("\\endhead")
    latex.append("\\hline \\multicolumn{7}{|r|}{{Contin\\'ua en la p\\'agina siguiente}} \\\\ \\hline")
    latex.append("\\endfoot")
    latex.append("\\hline")
    latex.append("\\endlastfoot")
    
    for row in rows:
        # Escapar caracteres especiales de LaTeX
        materia_nombre = row['materia_nombre'].replace('&', '\\&').replace('%', '\\%').replace('_', '\\_')
        # Truncar nombres muy largos
        if len(materia_nombre) > 40:
            materia_nombre = materia_nombre[:37] + "..."
        
        dia = str(row['dia'])
        salon = row['salon']
        hora_inicio = row['hora_inicio']
        hora_fin = hora_inicio + row['duracion_horas']
        inscriptos = str(row['inscriptos'])
        
        hora_inicio_str = decimal_to_time(hora_inicio)
        hora_fin_str = decimal_to_time(hora_fin)
        
        latex.append(f"{dia} & {salon} & {hora_inicio_str} & {hora_fin_str} & {materia_nombre} & {inscriptos} \\\\")
    
    latex.append("\\end{longtable}")
    
    return "\n".join(latex)

def main():
    csv_file = "output/promedio_08_001_100/promedio_2024_nsga2_asignaciones.csv"
    
    print("Procesando CSV...")
    rows = process_assignments_csv(csv_file)
    print(f"Total de asignaciones procesadas: {len(rows)}")
    
    print("\nGenerando tabla LaTeX...")
    latex_table = generate_latex_table(rows)
    
    print("\n" + "="*80)
    print("CÓDIGO LATEX GENERADO:")
    print("="*80)
    print(latex_table)
    print("="*80)
    
    # Guardar en un archivo
    output_file = "Informe/anexo_table.tex"
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(latex_table)
    
    print(f"\n✅ Tabla guardada en: {output_file}")

if __name__ == "__main__":
    main()
