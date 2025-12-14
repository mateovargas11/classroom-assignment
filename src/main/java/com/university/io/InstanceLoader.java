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

    private static ProblemInstance parseInstance(JsonObject data) {
        List<Subject> subjects = new ArrayList<>();
        JsonArray examenesArray = data.getAsJsonArray("examenes");
        for (int i = 0; i < examenesArray.size(); i++) {
            JsonObject exam = examenesArray.get(i).getAsJsonObject();
            subjects.add(new Subject(
                    String.valueOf(exam.get("id").getAsInt()),
                    exam.get("nombre").getAsString(),
                    exam.get("inscritos").getAsInt(),
                    exam.get("duracion").getAsDouble()));
        }

        List<Classroom> classrooms = new ArrayList<>();
        JsonArray salonesArray = data.getAsJsonArray("salones");
        for (int i = 0; i < salonesArray.size(); i++) {
            JsonObject salon = salonesArray.get(i).getAsJsonObject();
            classrooms.add(new Classroom(
                    salon.get("id").getAsString(),
                    salon.get("nombre").getAsString(),
                    salon.get("aforo").getAsInt()));
        }

        List<int[]> conflictPairs = new ArrayList<>();
        if (data.has("conflict_pairs")) {
            JsonArray pairsArray = data.getAsJsonArray("conflict_pairs");
            for (int i = 0; i < pairsArray.size(); i++) {
                JsonArray pair = pairsArray.get(i).getAsJsonArray();
                conflictPairs.add(new int[] { pair.get(0).getAsInt(), pair.get(1).getAsInt() });
            }
        }

        return new ProblemInstance(subjects, classrooms, conflictPairs);
    }
}
