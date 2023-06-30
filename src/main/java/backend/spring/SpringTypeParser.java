package backend.spring;

import backend.TypeParser;
import com.fasterxml.jackson.annotation.JsonIgnore;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import lombok.SneakyThrows;
import model.TypeContext;
import model.types.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class SpringTypeParser implements TypeParser {

    private final TypeContext context;

    private static final Set<String> genericArrayTypes = Set.of(
            List.class.getName(),
            ArrayList.class.getName(),
            Set.class.getName(),
            HashSet.class.getName(),
            Flux.class.getName(),
            Iterable.class.getName(),
            Collection.class.getName()
    );

    private static final Set<String> genericMapTypes = Set.of(
            HashMap.class.getName(),
            Map.class.getName()
    );

    private static final Set<String> genericNestedTypes = Set.of(
            Mono.class.getName(),
            Optional.class.getName()
    );

    private static final Set<String> integerTypes = Set.of(
            "int", "long", "short", "byte",
            Integer.class.getName(),
            Long.class.getName(),
            Short.class.getName(),
            Byte.class.getName()
    );

    private static final Set<String> floatTypes = Set.of(
            "float", "double",
            Float.class.getName(),
            Double.class.getName()
    );

    private static final Set<String> dateTypes = Set.of(
            Date.class.getName(),
            LocalDateTime.class.getName(),
            LocalDate.class.getName(),
            Instant.class.getName()
    );

    public SpringTypeParser(TypeContext context) {
        this.context = context;
    }

    @Override
    @SneakyThrows
    public Type parseType(CtMethod ctMethod) {
        if(ctMethod.getGenericSignature() != null) {
            String returnTypeStr = ctMethod.getGenericSignature().replaceFirst("\\(.*\\)", "");
            GenericTypeStringParser parser = new GenericTypeStringParser(returnTypeStr, context);
            return parseType(parser.parseGenericArgs().get(0));
        } else {
            return parseType(ctMethod.getReturnType());
        }
    }

    @Override
    @SneakyThrows
    public Type parseType(CtField ctField) {
        if(ctField.getGenericSignature() != null) {
            GenericTypeStringParser parser = new GenericTypeStringParser(ctField.getGenericSignature(), context);
            return parseType(parser.parseGenericArgs().get(0));
        } else {
            return parseType(ctField.getType());
        }
    }

    @Override
    public Type parseType(Intermediate intermediate) {
        if(intermediate instanceof GenericTypeStringParser.IntermediateArray at) {
            return new ArrayType(parseType(at.subType()));
        }

        if(intermediate instanceof GenericTypeStringParser.IntermediateType t) {
            if(genericNestedTypes.contains(t.getClazz().getName())) {
                return parseType(t.getGenericArgs().get(0));
            }
            if(genericArrayTypes.contains(t.getClazz().getName())) {
                return new ArrayType(parseType(t.getGenericArgs().get(0)));
            }

            if(genericMapTypes.contains(t.getClazz().getName())) {
                return new MapType(parseType(t.getGenericArgs().get(0)), parseType(t.getGenericArgs().get(1)));
            }

            return parseType(t.getClazz());
        }

        throw new UnsupportedOperationException("Unsupported type " + intermediate.getClass().getName());
    }

    @SneakyThrows
    private Type parseType(CtClass ctClass) {
        Type primitive = parsePrimitiveType(ctClass);
        return primitive == null ? parseObject(ctClass) : primitive;
    }

    private PrimitiveType parsePrimitiveType(CtClass ctClass) {
        if(ctClass.getName().equals(String.class.getName())){
            return PrimitiveType.String;
        }

        if(integerTypes.contains(ctClass.getName())){
            return PrimitiveType.Int;
        }

        if(floatTypes.contains(ctClass.getName())){
            return PrimitiveType.Double;
        }

        if(Set.of("boolean", Boolean.class.getName()).contains(ctClass.getName())){
            return PrimitiveType.Boolean;
        }

        if(Set.of("void", Void.class.getName()).contains(ctClass.getName())){
            return PrimitiveType.Void;
        }

        if(dateTypes.contains(ctClass.getName())) {
            return PrimitiveType.Date;
        }

        return null;
    }

    @SneakyThrows
    private NamedType parseObject(CtClass ctClass) {
        if(context.getNamedObjects().containsKey(ctClass.getSimpleName())) {
            return context.getNamedObjects().get(ctClass.getSimpleName());
        }

        if(ctClass.isEnum()) {
            EnumType enumType = new EnumType(ctClass.getSimpleName());
            context.getNamedObjects().put(ctClass.getSimpleName(), enumType);
            for (CtField field : ctClass.getFields()) {
                enumType.getValues().add(field.getName());
            }
            return enumType;
        }

        ObjectType objectType = new ObjectType(ctClass.getSimpleName());
        context.getNamedObjects().put(ctClass.getSimpleName(), objectType);
        for(CtField field: ctClass.getFields()) {
            if(field.getAnnotation(JsonIgnore.class) != null) {
                continue;
            }
            if((field.getModifiers() & Modifier.STATIC) != 0) {
                continue;
            }
            objectType.getFields().add(new Field(field.getName(), parseType(field)));
        }

        for(CtMethod ctMethod: ctClass.getMethods()) {
            if((ctMethod.getModifiers() & Modifier.PUBLIC) == 0) {
                continue;
            }

            if(ctMethod.getDeclaringClass().getName().equals(Object.class.getName())) {
                continue;
            }

            String name = ctMethod.getName();
            if((name.startsWith("get") || name.startsWith("is")) && ctMethod.getParameterTypes().length == 0) {
                String fieldName = name.replaceAll("^get", "")
                        .replaceAll("^is", "");

                fieldName = Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
                objectType.getFields().add(new Field(fieldName, parseType(ctMethod)));
            }
        }
        return objectType;
    }
}
