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
            typeScriptFile.setBody(printNamedType(name, objectType));
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

    private String printNamedType(String name, NamedType t) {
        StringBuilder body = new StringBuilder();
        if(t instanceof ObjectType o) {
            body.append("const ").append(name).append("Model = z.object({\n")


            body.append("export default interface ").append(name).append(" {\n");
            o.getFields().forEach(field ->
                    body.append("  ").append(field.getName()).append(": ").append(TypeWriter.printType(field.getType())).append(";\n"));
            body.append("}\n");
        } else if (t instanceof EnumType e) {
            body.append("enum ").append(name).append(" {\n");
            e.getValues().forEach(s ->
                    body.append("  ").append(s).append(" = '").append(s).append("',\n")
            );
            body.append("}\n");
            body.append("export default ").append(name).append(";\n");
        }
        return body.toString();
    }
}
