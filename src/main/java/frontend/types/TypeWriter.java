package frontend.types;

import frontend.TypeScriptFile;
import model.TypeContext;
import model.types.*;

import java.util.List;

public interface TypeWriter {
    String TYPE_DECLARATIONS_DIR = "types";

    /**
     * Creates a typescript representation of all types in a {@link TypeContext}. Each generated typescript file default
     * exports a type declaration, named after the type.
     *
     * @param context A {@link TypeContext},
     * @return A list of {@link TypeScriptFile} containing the generated TypeScript code
     */
    List<TypeScriptFile> printAllTypes(TypeContext context);


    static String printType(Type t) {
        if(t instanceof NamedType o) {
            return o.getName();
        }

        if(t instanceof ArrayType arr) {
            return printType(arr.getSubType()) + "[]";
        }

        if(t instanceof MapType map) {
            return "Record<" + printType(map.getKeySubType()) + ", " + printType(map.getValueSubType()) + ">";
        }

        if(t instanceof PrimitiveType p) {
            return switch (p) {
                case Int, Double -> "number";
                case String, Date -> "string";
                case Boolean -> "boolean";
                case Void -> "void";
            };
        }

        throw new UnsupportedOperationException("Unsupported Type: " + t.getClass().getName());
    }
}
