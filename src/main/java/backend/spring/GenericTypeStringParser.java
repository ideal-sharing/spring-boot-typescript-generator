package backend.spring;

import javassist.CtClass;
import javassist.NotFoundException;
import lombok.Data;
import model.TypeContext;
import model.types.Intermediate;

import java.util.ArrayList;
import java.util.List;

public class GenericTypeStringParser {

    private String current;

    private final TypeContext context;

    public GenericTypeStringParser(String s, TypeContext context) {
        this.current = s;
        this.context = context;
    }

    public List<Intermediate> parseGenericArgs() {
        return parseGenericArgs(0);
    }

    private List<Intermediate> parseGenericArgs(int level) {
        List<Intermediate> types = new ArrayList<>();

        while (current.trim().length() > 0 && !current.startsWith(">;")) {
            types.add(consumeNext(level));
        }

        if(current.startsWith(">;")) {
            if(level > 0) {
                current = current.substring(2);
                return types;
            } else {
                throw new RuntimeException("Misaligned > found");
            }
        }


        return types;
    }


    private Intermediate consumeNext(int level) {
        if (current.startsWith("D")) {
            current = current.substring(1);
            return new IntermediateType(loadClass("java/lang/Double"));
        }

        if (current.startsWith("J")) {
            current = current.substring(1);
            return new IntermediateType(loadClass("java/lang/Long"));
        }

        if (current.startsWith("I")) {
            current = current.substring(1);
            return new IntermediateType(loadClass("java/lang/Integer"));
        }

        if (current.startsWith("B")) {
            current = current.substring(1);
            return new IntermediateType(loadClass("java/lang/Byte"));
        }

        if (current.startsWith("Z")) {
            current = current.substring(1);
            return new IntermediateType(loadClass("java/lang/Boolean"));
        }

        if (current.startsWith("V")) {
            current = current.substring(1);
            return new IntermediateType(loadClass("java/lang/Void"));
        }

        if(current.startsWith("[")) {
            current = current.substring(1);
            return new IntermediateArray(consumeNext(level));
        }

        while (current.startsWith("L")) {
            current = current.substring(1);
        }

        int nextSubtype = current.indexOf("<");
        int nextBreak = current.indexOf(";");

        if(nextBreak == -1 ){
            throw new RuntimeException("Found no break ';' in current string");
        }

        if(nextSubtype == -1 || nextBreak < nextSubtype) {
            Intermediate intermediate = new IntermediateType(loadClass(current.substring(0, nextBreak)));
            current = current.substring(nextBreak + 1);
            return intermediate;
        } else {
            IntermediateType intermediateType = new IntermediateType(loadClass(current.substring(0, nextSubtype)));
            current = current.substring(nextSubtype + 1);
            intermediateType.genericArgs.addAll(parseGenericArgs(level + 1));
            return intermediateType;
        }
    }

    private CtClass loadClass(String path)  {
        try {
            return this.context.getClassPool().get(path.replaceAll("/", "."));
        } catch (NotFoundException e) {
            System.err.println("Could not load class " + path);
            return null;
        }
    }


    public record IntermediateArray(Intermediate subType) implements Intermediate {
    }


    @Data
    public static class IntermediateType implements Intermediate {
        private final CtClass clazz ;
        private final List<Intermediate> genericArgs = new ArrayList<>();
    }
}
