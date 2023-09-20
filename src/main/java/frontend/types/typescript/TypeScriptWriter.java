package frontend.types.typescript;

import frontend.TypeScriptFile;
import frontend.types.TypeWriter;
import lombok.RequiredArgsConstructor;
import model.TypeContext;
import model.types.*;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class TypeScriptWriter implements TypeWriter {
    private final String basePath;

    public List<TypeScriptFile> printAllTypes(TypeContext context) {
        List<TypeScriptFile> files = new ArrayList<>();

        context.getNamedObjects().forEach((name, objectType) -> {
            TypeScriptFile typeScriptFile = new TypeScriptFile();
            typeScriptFile.setLocation(basePath + TYPE_DECLARATIONS_DIR + "/" + name);
            typeScriptFile.setBody(printNamedType(name, objectType, context));
            files.add(typeScriptFile);
            context.getNamedObjectFiles().put(name, typeScriptFile);
        });

        // resolving imports:
        context.getNamedObjects().forEach((name, namedType) -> {
            if(namedType instanceof ObjectType objectType) {
                TypeScriptFile file = context.getNamedObjectFiles().get(name);
                objectType.getFields().forEach(field -> file.addImport(field.getType(), context));
            }
        });

        return files;
    }

    public List<TypeScriptFile> printAllNonValidatedTypes(TypeContext context) {
        List<TypeScriptFile> files = new ArrayList<>();

        context.getNamedObjects().forEach((name, namedType) -> {
            if(!namedType.needsValidation() && !(namedType instanceof EnumType)) {
                TypeScriptFile typeScriptFile = new TypeScriptFile();
                typeScriptFile.setLocation(basePath + TYPE_DECLARATIONS_DIR + "/" + name);
                typeScriptFile.setBody(printNamedType(name, namedType, context));
                files.add(typeScriptFile);
                context.getNamedObjectFiles().put(name, typeScriptFile);
            }
        });

        // resolving imports:
        context.getNamedObjects().forEach((name, namedType) -> {
            if(!namedType.needsValidation() && !(namedType instanceof EnumType)) {
                if (namedType instanceof ObjectType objectType) {
                    TypeScriptFile file = context.getNamedObjectFiles().get(name);
                    objectType.getFields().forEach(field -> file.addImport(field.getType(), context));
                }

            }
        });

        return files;
    }

    private String printNamedType(String name, NamedType t, TypeContext context) {
        StringBuilder body = new StringBuilder();
        if(t instanceof ObjectType o) {
            body.append("export default interface ").append(name).append(" {\n");
            o.getFields().forEach(field -> {
                body.append("  ").append(field.getName());
                if(!field.isRequired()) {
                    body.append("?");
                }
                body.append(": ").append(TypeWriter.printType(field.getType(), context)).append(";\n");
            });
            body.append("}\n");
        } else if (t instanceof EnumType e) {
            body.append("type ").append(name).append(" = ");
            for (int i = 0; i < e.getValues().size(); i++) {
                if (i != 0) {
                    body.append(" | ");
                }
                body.append("'").append(e.getValues().get(i)).append("'");
            }
            String first = String.valueOf(name.charAt(0));
            body.append(";\nexport const ").append(first.toLowerCase()).append(name.substring(1))
                    .append("Values: ").append(name).append("[] = [");
            for (int i = 0; i < e.getValues().size(); i++) {
                if (i != 0) {
                    body.append(", ");
                }
                body.append("'").append(e.getValues().get(i)).append("'");
            }
            body.append("];\nexport default ").append(name).append(";\n");
        }
        return body.toString();
    }
}
