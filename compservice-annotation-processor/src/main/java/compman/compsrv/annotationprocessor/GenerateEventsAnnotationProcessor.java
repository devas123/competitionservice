package compman.compsrv.annotationprocessor;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.squareup.javapoet.*;
import compman.compsrv.annotations.CommandPayload;
import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.commands.CommandType;
import compman.compsrv.model.events.EventType;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Set;

@SupportedAnnotationTypes({"compman.compsrv.annotations.EventPayload", "compman.compsrv.annotations.CommandPayload"})
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class GenerateEventsAnnotationProcessor extends AbstractProcessor {
    private Filer filer;
    private Messager messager;

    private final Map<EventType, TypeMirror> eventTypeToPayloadClass = Maps.newHashMap();
    private final Map<CommandType, TypeMirror> commandTypeToPayloadClass = Maps.newHashMap();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        try {
            for (Element annotatedElement : env.getElementsAnnotatedWith(EventPayload.class)) {
                info("Found event element: %s, kind: %s\n", annotatedElement.toString(), annotatedElement.getKind());
                if (annotatedElement.getKind() == ElementKind.CLASS) {
                    EventPayload annotation = annotatedElement.getAnnotation(EventPayload.class);
                    EventType[] superTypes = annotation.type();
                    for (EventType superType : superTypes) {
                        eventTypeToPayloadClass.put(superType, annotatedElement.asType());
                    }
                }
            }
            for (Element annotatedElement : env.getElementsAnnotatedWith(CommandPayload.class)) {
                info("Found command element: %s, kind: %s\n", annotatedElement.toString(), annotatedElement.getKind());
                if (annotatedElement.getKind() == ElementKind.CLASS) {
                    CommandPayload annotation = annotatedElement.getAnnotation(CommandPayload.class);
                    CommandType[] superTypes = annotation.type();
                    for (CommandType superType : superTypes) {
                        commandTypeToPayloadClass.put(superType, annotatedElement.asType());
                    }
                }
            }
        } catch (Throwable t) {
            output(t);
            return false;
        }

        if (!eventTypeToPayloadClass.isEmpty() && !commandTypeToPayloadClass.isEmpty()) {
            try {
                processPayloads();
                eventTypeToPayloadClass.clear();
                commandTypeToPayloadClass.clear();
            } catch (Throwable e) {
                output(e);
                return false;
            }
        }
        return false;
    }

    private void output(Throwable e) {
        try (StringWriter sw = new StringWriter();
             PrintWriter pw = new PrintWriter(sw)) {
            e.printStackTrace(pw);
            error(sw.toString());
        } catch (IOException t) {
            error(e.getMessage());
        }
    }

    private void processPayloads() throws IOException {
        info("eventTypeToPayloadClass %s\n", eventTypeToPayloadClass);
        info("commandTypeToPayloadClass %s\n", commandTypeToPayloadClass);
        ClassName payload = ClassName.get("compman.compsrv.model", "Payload");
        ClassName cls = ClassName.get("java.lang", "Class");
        TypeName parameterizedClass = ParameterizedTypeName.get(cls, WildcardTypeName.subtypeOf(payload));
        TypeSpec.Builder payloadsBuilder = TypeSpec.classBuilder("Payloads")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        MethodSpec.Builder getEventPayload = MethodSpec.methodBuilder("getPayload")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(parameterizedClass)
                .addParameter(EventType.class, "type")
                .beginControlFlow("switch(type)");

        MethodSpec.Builder getCommandPayload = MethodSpec.methodBuilder("getPayload")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(parameterizedClass)
                .addParameter(CommandType.class, "type")
                .beginControlFlow("switch(type)");

        Set<String> usedFields = Sets.newHashSet();
        for (Map.Entry<EventType, TypeMirror> eventTypeStringEntry : eventTypeToPayloadClass.entrySet()) {
            addCase(payloadsBuilder, usedFields, eventTypeStringEntry.getValue(), getEventPayload
                    .beginControlFlow("case $L:", eventTypeStringEntry.getKey()));
        }

        for (Map.Entry<CommandType, TypeMirror> eventTypeStringEntry : commandTypeToPayloadClass.entrySet()) {
            addCase(payloadsBuilder, usedFields, eventTypeStringEntry.getValue(), getCommandPayload
                    .beginControlFlow("case $L:", eventTypeStringEntry.getKey()));
        }


        getEventPayload
                .beginControlFlow("default:")
                .addStatement("return null")
                .endControlFlow()
                .endControlFlow();

        getCommandPayload
                .beginControlFlow("default:")
                .addStatement("return null")
                .endControlFlow()
                .endControlFlow();

        TypeSpec eventPayloads = payloadsBuilder
                .addMethod(getEventPayload.build())
                .addMethod(getCommandPayload.build())
                .build();

        JavaFile javaFile = JavaFile.builder("compman.compsrv.model", eventPayloads)
                .build();
        javaFile.writeTo(filer);
    }

    private void addCase(TypeSpec.Builder eventPayloadsBuilder, Set<String> usedFields, TypeMirror value, MethodSpec.Builder builder) {
        TypeName payloadClass = ClassName.get(value);
        String className = value.toString();
        String fieldName = className.substring(className.lastIndexOf(".") + 1);
        if (!usedFields.contains(fieldName)) {
            usedFields.add(fieldName);
            eventPayloadsBuilder.addField(TypeName.get(value),
                    className.substring(className.lastIndexOf(".") + 1).toLowerCase(),
                    Modifier.PUBLIC);
        }
        builder
                .addStatement("return $T.class", payloadClass)
                .endControlFlow();
    }


    private void error(String msg, Object... args) {
        if (messager == null) {
            return;
        }
        messager.printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg, args));
    }

    private void info(String msg, Object... args) {
        System.out.printf(msg, args);
    }

}
