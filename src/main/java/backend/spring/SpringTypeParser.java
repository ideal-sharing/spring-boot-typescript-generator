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
import model.validation.Arrays;
import model.validation.Numbers;
import model.validation.Strings;
import model.validation.Validation;
import org.springframework.lang.Nullable;
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
                if(field.getType().getName().equals(ctClass.getName())) {
                    enumType.getValues().add(field.getName());
                }
            }
            return enumType;
        }

        ObjectType objectType = new ObjectType(ctClass.getSimpleName());
        context.getNamedObjects().put(ctClass.getSimpleName(), objectType);
        for(CtField field: ctClass.getDeclaredFields()) {
            if(field.getAnnotation(JsonIgnore.class) != null) {
                continue;
            }
            if((field.getModifiers() & Modifier.STATIC) != 0) {
                continue;
            }
            Field f = new Field(field.getName(), parseType(field));
            f.setRequired(!field.hasAnnotation(Nullable.class));
            f.getValidations().addAll(getNeededValidation(field));
            objectType.getFields().add(f);
        }

        if (ctClass.getSuperclass() != null && !ctClass.getSuperclass().getName().equals(Object.class.getName())) {
            ObjectType o = (ObjectType) parseObject(ctClass.getSuperclass());
            objectType.getFields().addAll(o.getFields());
        }
        return objectType;
    }

    @SneakyThrows
    private List<Validation> getNeededValidation(CtField ctField) {
        List<Validation> validations = new ArrayList<>();

        for (Object annotation : ctField.getAvailableAnnotations()) {
            if(annotation instanceof jakarta.validation.constraints.Min ann) {
                validations.add(new Numbers.MinValue(ann.value(), ann.message()));
            }
            if(annotation instanceof jakarta.validation.constraints.Max ann) {
                validations.add(new Numbers.MaxValue(ann.value(), ann.message()));
            }
            if(annotation instanceof jakarta.validation.constraints.Size ann) {
                if(ann.min() != Integer.MIN_VALUE) {
                    validations.add(new Arrays.MinLength(ann.min(), ann.message()));
                }

                if(ann.max() != Integer.MAX_VALUE) {
                    validations.add(new Arrays.MaxLength(ann.max(), ann.message()));
                }
            }
            if(annotation instanceof jakarta.validation.constraints.NotBlank ann) {
                validations.add(new Strings.Regex("/^(?!\\s*$).+/", ann.message()));
            }
            if(annotation instanceof jakarta.validation.constraints.Pattern ann) {
                validations.add(new Strings.Regex("/" + ann.regexp() + "/", ann.message()));
            }
        }
        return validations;
    }
}
