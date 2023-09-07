package model;

import frontend.TypeScriptFile;
import javassist.ClassPool;
import lombok.Data;
import model.types.NamedType;

import java.util.HashMap;
import java.util.Map;

@Data
public class TypeContext {
    private final ClassPool classPool;

    private final Map<String, NamedType> namedObjects = new HashMap<>();

    private final Map<String, TypeScriptFile> namedObjectFiles = new HashMap<>();

    private final boolean useStringAsDate;
}
