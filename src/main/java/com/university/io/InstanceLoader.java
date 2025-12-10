package com.university.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.university.domain.Classroom;
import com.university.domain.ProblemInstance;
import com.university.domain.Subject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class InstanceLoader {
    
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    public static ProblemInstance loadFromResources(String instanceName) throws IOException {
        String path = "processed/" + instanceName + ".json";
        try (InputStream is = InstanceLoader.class.getClassLoader().getResourceAsStream(path);
             InputStreamReader reader = new InputStreamReader(is)) {
            JsonObject data = gson.fromJson(reader, JsonObject.class);
            return parseInstance(data);
        }
    }
    
    public static ProblemInstance loadFromJson(String filePath) throws IOException {
        try (FileReader reader = new FileReader(filePath)) {
            JsonObject data = gson.fromJson(reader, JsonObject.class);
            return parseInstance(data);
        }
    }
    
    private static ProblemInstance parseInstance(JsonObject data) {
        List<Subject> subjects = new ArrayList<>();
        JsonArray examenesArray = data.getAsJsonArray("examenes");
        for (int i = 0; i < examenesArray.size(); i++) {
            JsonObject exam = examenesArray.get(i).getAsJsonObject();
            subjects.add(new Subject(
                String.valueOf(exam.get("id").getAsInt()),
                exam.get("nombre").getAsString(),
                exam.get("inscritos").getAsInt(),
                exam.get("duracion").getAsDouble()
            ));
        }
        
        List<Classroom> classrooms = new ArrayList<>();
        JsonArray salonesArray = data.getAsJsonArray("salones");
        for (int i = 0; i < salonesArray.size(); i++) {
            JsonObject salon = salonesArray.get(i).getAsJsonObject();
            classrooms.add(new Classroom(
                salon.get("id").getAsString(),
                salon.get("nombre").getAsString(),
                salon.get("aforo").getAsInt()
            ));
        }
        
        return new ProblemInstance(subjects, classrooms);
    }
    
    public static void saveToJson(ProblemInstance instance, String filePath) throws IOException {
        JsonObject data = new JsonObject();
        
        JsonArray examenesArray = new JsonArray();
        for (int i = 0; i < instance.getSubjects().size(); i++) {
            Subject s = instance.getSubjects().get(i);
            JsonObject exam = new JsonObject();
            exam.addProperty("id", i);
            exam.addProperty("nombre", s.getName());
            exam.addProperty("inscritos", s.getEnrolledStudents());
            exam.addProperty("duracion", s.getDurationHours());
            examenesArray.add(exam);
        }
        data.add("examenes", examenesArray);
        
        JsonArray salonesArray = new JsonArray();
        for (Classroom c : instance.getClassrooms()) {
            JsonObject salon = new JsonObject();
            salon.addProperty("id", c.getId());
            salon.addProperty("nombre", c.getName());
            salon.addProperty("aforo", c.getCapacity());
            salonesArray.add(salon);
        }
        data.add("salones", salonesArray);
        
        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(data, writer);
        }
    }
    
    public static ProblemInstance createSampleInstance() {
        List<Subject> subjects = new ArrayList<>();
        subjects.add(new Subject("MAT1", "Matematica I", 45, 3.0));
        subjects.add(new Subject("FIS1", "Fisica I", 38, 2.0));
        subjects.add(new Subject("PRG1", "Programacion I", 60, 4.0));
        subjects.add(new Subject("QUI1", "Quimica I", 32, 2.0));
        subjects.add(new Subject("ALG1", "Algebra", 50, 3.0));
        subjects.add(new Subject("EST1", "Estadistica", 42, 2.0));
        subjects.add(new Subject("CAL1", "Calculo", 55, 3.0));
        subjects.add(new Subject("BD1", "Bases de Datos", 48, 3.0));
        subjects.add(new Subject("RED1", "Redes", 35, 2.0));
        subjects.add(new Subject("SO1", "Sistemas Operativos", 40, 3.0));
        
        List<Classroom> classrooms = new ArrayList<>();
        for (int i = 0; i < ProblemInstance.NUM_CLASSROOMS; i++) {
            char letter = (char) ('A' + (i % 26));
            String suffix = i >= 26 ? String.valueOf(i / 26) : "";
            String name = "Salon" + letter + suffix;
            int capacity = 30 + (i % 5) * 10;
            classrooms.add(new Classroom(String.valueOf(i + 1), name, capacity));
        }
        
        return new ProblemInstance(subjects, classrooms);
    }
}
