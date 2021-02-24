package compman.compsrv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kjetland.jackson.jsonSchema.JsonSchemaConfig;
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator;
import com.kjetland.jackson.jsonSchema.SubclassesResolver;
import com.kjetland.jackson.jsonSchema.SubclassesResolverImpl;
import compman.compsrv.model.Payloads;
import compman.compsrv.model.commands.CommandDTO;
import compman.compsrv.model.events.EventDTO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Locale;

public class SchemaGenerator {
    public static void main(String[] args) {
        String outputPath = args.length > 0 ? args[0] : "target/avro";
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false);
        parseClass(Payloads.class, outputPath + "/model", objectMapper);
        parseClass(EventDTO.class, outputPath + "/model", objectMapper);
        parseClass(CommandDTO.class, outputPath + "/model", objectMapper);
    }

    private static <T> void parseClass(Class<T> cls, String outputPath, ObjectMapper mapper) {
        final SubclassesResolver resolver = new SubclassesResolverImpl()
                .withPackagesToScan(Collections.singletonList("compman.compsrv.model"));
        final JsonSchemaConfig config = JsonSchemaConfig.vanillaJsonSchemaDraft4()
                .withSubclassesResolver(resolver);
        JsonSchemaGenerator generator = new JsonSchemaGenerator(mapper, config);
        try {
            System.out.println(cls.getSimpleName() + ": " + String.format("interface: %s", cls.isInterface()));
            JsonNode jsonSchema = generator.generateJsonSchema(cls);
            String asJson = mapper.writeValueAsString(jsonSchema);
            Path output = Paths.get(outputPath);
            if (!Files.exists(Paths.get(outputPath))) {
                Files.createDirectories(output);
            }
            Files.writeString(Paths.get(outputPath, cls.getSimpleName().toLowerCase(Locale.ROOT) + ".json"), asJson, StandardOpenOption.CREATE);
        } catch (Throwable e) {
            System.out.println("Error for class " + cls.getCanonicalName());
            e.printStackTrace();
        }

    }
}
